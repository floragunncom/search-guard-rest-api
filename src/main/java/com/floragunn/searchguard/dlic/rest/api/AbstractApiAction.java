/*
 * Copyright 2016 by floragunn UG (haftungsbeschränkt) - All rights reserved
 * 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed here is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * 
 * This software is free of charge for non-commercial and academic use.
 * For commercial use in a production environment you have to obtain a license
 * from https://floragunn.com
 * 
 */

package com.floragunn.searchguard.dlic.rest.api;

import java.io.IOException;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.Settings.Builder;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestRequest.Method;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;

import com.floragunn.searchguard.action.configupdate.ConfigUpdateAction;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateNodeResponse;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateRequest;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateResponse;
import com.floragunn.searchguard.configuration.AdminDNs;
import com.floragunn.searchguard.configuration.ConfigurationRepository;
import com.floragunn.searchguard.configuration.IndexBaseConfigurationRepository;
import com.floragunn.searchguard.dlic.rest.validation.AbstractConfigurationValidator;
import com.floragunn.searchguard.dlic.rest.validation.AbstractConfigurationValidator.ErrorType;
import com.floragunn.searchguard.ssl.transport.PrincipalExtractor;
import com.floragunn.searchguard.ssl.util.SSLRequestHelper;
import com.floragunn.searchguard.ssl.util.SSLRequestHelper.SSLInfo;
import com.floragunn.searchguard.support.ConfigConstants;

public abstract class AbstractApiAction extends BaseRestHandler {

	protected final Logger log = LogManager.getLogger(this.getClass());
	
	private final AdminDNs adminDNs;
	protected final IndexBaseConfigurationRepository cl;
	protected final ClusterService cs;
	private final PrincipalExtractor principalExtractor;
	private String searchguardIndex;
	private final Path configPath;

	static {
		printLicenseInfo();
	}

	protected AbstractApiAction(final Settings settings, final Path configPath, final RestController controller, final Client client,
			final AdminDNs adminDNs, final IndexBaseConfigurationRepository cl, final ClusterService cs,
			final PrincipalExtractor principalExtractor) {
		super(settings);
		this.configPath = configPath;
		this.searchguardIndex = settings.get(ConfigConstants.SEARCHGUARD_CONFIG_INDEX_NAME, ConfigConstants.SG_DEFAULT_CONFIG_INDEX);
		this.adminDNs = adminDNs;
		this.cl = cl;
		this.cs = cs;
		this.principalExtractor = principalExtractor;
	}

	protected abstract AbstractConfigurationValidator getValidator(final Method method, BytesReference ref);

	protected abstract String getResourceName();

	protected abstract String getConfigName();
	
	protected Tuple<String[], RestResponse> handleApiRequest(final RestRequest request, final Client client)
			throws Throwable {
		
		// consume all parameters first so we can return a correct HTTP status, not 400
		consumeParameters(request);
		
		// check if SG index has been initialized
		if(!ensureIndexExists(client)) {
			return internalErrorResponse(ErrorType.SG_NOT_INITIALIZED.getMessage());			
		}

		// validate additional settings, if any
		AbstractConfigurationValidator validator = getValidator(request.method(), request.content());
		if (!validator.validateSettings()) {
		    request.params().clear();
			return new Tuple<String[], RestResponse>(new String[0],
					new BytesRestResponse(RestStatus.BAD_REQUEST, validator.errorsAsXContent()));
		}
		switch (request.method()) {
		case DELETE:
			return handleDelete(request, client, validator.settingsBuilder());
		case POST:
			return handlePost(request, client, validator.settingsBuilder());
		case PUT:
			return handlePut(request, client, validator.settingsBuilder());
		case GET:
			return handleGet(request, client, validator.settingsBuilder());
		default:
			throw new IllegalArgumentException(request.method() + " not supported");
		}		 
	}

	protected Tuple<String[], RestResponse> handleDelete(final RestRequest request, final Client client,
			final Settings.Builder additionalSettingsBuilder) throws Throwable {
		final String name = request.param("name");

		if (name == null || name.length() == 0) {
			return badRequestResponse("No " + getResourceName() + " specified");
		}

		final Settings.Builder existing = load(getConfigName());
		
		Map<String, String> removedEntries = removeKeysStartingWith(existing.internalMap(), name + "."); 
		boolean modified = !removedEntries.isEmpty();

		if (modified) {
			save(client, request, getConfigName(), existing);
			return successResponse(getResourceName() + " " + name + " deleted.", getConfigName());
		} else {
			return notFound(getResourceName() + " " + name + " not found.");
		}
	}

	protected Tuple<String[], RestResponse> handlePut(final RestRequest request, final Client client,
			final Settings.Builder additionalSettingsBuilder) throws Throwable {
		final String name = request.param("name");

		if (name == null || name.length() == 0) {
			return badRequestResponse("No " + getResourceName() + " specified");
		}

		final Settings.Builder existing = load(getConfigName());
		
		if (log.isTraceEnabled()) {
			log.trace(additionalSettingsBuilder.build().getAsMap().toString());	
		}
		

		Map<String, String> removedEntries = removeKeysStartingWith(existing.internalMap(), name + "."); 
		boolean existed = !removedEntries.isEmpty();
				
		existing.put(prependValueToEachKey(additionalSettingsBuilder.build().getAsMap(), name + "."));
		save(client, request, getConfigName(), existing);
		if (existed) {
			return successResponse(getResourceName() + " " + name + " updated.", getConfigName());
		} else {
			return createdResponse(getResourceName() + " " + name + " created.", getConfigName());
		}
	}

	protected Tuple<String[], RestResponse> handlePost(final RestRequest request, final Client client,
			final Settings.Builder additionalSettings) throws Throwable {
		return notImplemented(Method.POST);
	}

	protected Tuple<String[], RestResponse> handleGet(RestRequest request, Client client, Builder additionalSettings)
			throws Throwable {

		final String resourcename = request.param("name");

		if (resourcename == null || resourcename.length() == 0) {
			return badRequestResponse("No " + getResourceName() + " specified.");
		}

		final Settings.Builder configuration = load(getConfigName());

		final Settings.Builder requestedConfiguration = copyKeysStartingWith(configuration.internalMap(),
				resourcename + ".");

		if (requestedConfiguration.internalMap().size() == 0) {
			return notFound("Resource '" + resourcename + "' not found.");
		}

		return new Tuple<String[], RestResponse>(new String[0],
				new BytesRestResponse(RestStatus.OK, convertToJson(requestedConfiguration.build())));
	}

	protected final Settings.Builder load(final String config) {
		return Settings.builder().put(loadAsSettings(config));
	}

	protected final Settings loadAsSettings(final String config) {
		return cl.getConfiguration(config);
	}

	protected boolean ensureIndexExists(final Client client) throws Throwable {
		return cs.state().metaData().hasConcreteIndex(this.searchguardIndex);
	}
	
	protected void save(final Client client, final RestRequest request, final String config,
			final Settings.Builder settings) throws Throwable {
		final Semaphore sem = new Semaphore(0);
		final List<Throwable> exception = new ArrayList<Throwable>(1);
		final IndexRequest ir = new IndexRequest(this.searchguardIndex);

		String type = "sg";
		String id = config;
		
		if(cs.state().metaData().index(this.searchguardIndex).mapping("config") != null) {
		    type = config;
	        id = "0";
		}
		
		
		client.index(ir.type(type).id(id).setRefreshPolicy(RefreshPolicy.IMMEDIATE)
				.source(config, toSource(settings)), new ActionListener<IndexResponse>() {

					@Override
					public void onResponse(final IndexResponse response) {
						sem.release();
						if (logger.isDebugEnabled()) {
							logger.debug("{} successfully updated", config);
						}
					}

					@Override
					public void onFailure(final Exception e) {
						sem.release();
						exception.add(e);
						logger.error("Cannot update {} due to {}", e, config, e);
					}
				});

		if (!sem.tryAcquire(2, TimeUnit.MINUTES)) {
			// timeout
			logger.error("Cannot update {} due to timeout}", config);
			throw new ElasticsearchException("Timeout updating " + config);
		}

		if (exception.size() > 0) {
			throw exception.get(0);
		}

	}

	
	
	@Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {

	    SSLInfo sslInfo = SSLRequestHelper.getSSLInfo(settings, configPath, request, principalExtractor);
	    
	    if (sslInfo == null) {
            logger.error("No ssl info found");
            // auditLog.logSgIndexAttempt(request, action); //TODO add method
            // for rest request
            request.params().clear();
            final BytesRestResponse response = new BytesRestResponse(RestStatus.FORBIDDEN, "No ssl info found");
            return channel -> channel.sendResponse(response);
        }
	    
        X509Certificate[] certs = sslInfo.getX509Certs();

		if (certs == null || certs.length == 0) {
			logger.error("No certificate found");
			// auditLog.logSgIndexAttempt(request, action); //TODO add method
			// for rest request
			request.params().clear();
			final BytesRestResponse response = new BytesRestResponse(RestStatus.FORBIDDEN, "No certificates");
			return channel -> channel.sendResponse(response);
		}

		if (!adminDNs.isAdmin(sslInfo.getPrincipal())) {
			// auditLog.logSgIndexAttempt(request, action); //TODO add method
			// for rest request
		    request.params().clear();
			logger.error("SG admin permissions required but {} is not an admin",
			        sslInfo.getPrincipal());
			final BytesRestResponse response = new BytesRestResponse(RestStatus.FORBIDDEN,
					"SG admin permissions required");
			return channel -> channel.sendResponse(response);
		}

		final Semaphore sem = new Semaphore(0);
		final List<Throwable> exception = new ArrayList<Throwable>(1);
		final Tuple<String[], RestResponse> response;
		try {
			response = handleApiRequest(request, client);

			if (response.v1().length > 0) {

				final ConfigUpdateRequest cur = new ConfigUpdateRequest(response.v1());
				//cur.putInContext(ConfigConstants.SG_USER,
				//		new User((String) request.getFromContext(ConfigConstants.SG_SSL_PRINCIPAL)));

				client.execute(ConfigUpdateAction.INSTANCE, cur, new ActionListener<ConfigUpdateResponse>() {

					@Override
					public void onFailure(final Exception e) {
						sem.release();
						logger.error("Cannot update {} due to {}", e, Arrays.toString(response.v1()), e);
						exception.add(e);
					}

					@Override
					public void onResponse(final ConfigUpdateResponse ur) {
						sem.release();
						if (!checkConfigUpdateResponse(ur)) {
							logger.error("Cannot update {}", Arrays.toString(response.v1()));
							exception.add(
									new ElasticsearchException("Unable to update " + Arrays.toString(response.v1())));
						} else if (logger.isDebugEnabled()) {
							logger.debug("Configs {} successfully updated", Arrays.toString(response.v1()));
						}
					}
				});

			} else {
				sem.release();
			}

		} catch (final Throwable e) {
			logger.error("Unexpected exception {}", e, e);
			request.params().clear();
			return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, e.toString()));
		}

		try {
			if (!sem.tryAcquire(2, TimeUnit.MINUTES)) {
				// timeout
				logger.error("Cannot update {} due to timeout", Arrays.toString(response.v1()));
				throw new ElasticsearchException("Timeout updating " + Arrays.toString(response.v1()));
			}
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		if (exception.size() > 0) {
		    request.params().clear();
	        return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, exception.get(0).toString()));
		}

		return channel -> channel.sendResponse(response.v2());

	}

	protected static BytesReference toSource(final Settings.Builder settingsBuilder) throws IOException {
		final XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject(); //1
        settingsBuilder.build().toXContent(builder, ToXContent.EMPTY_PARAMS);
        builder.endObject(); //2
        return builder.bytes(); 
	}
        
	protected boolean checkConfigUpdateResponse(final ConfigUpdateResponse response) {

		final int nodeCount = cs.state().getNodes().getNodes().size();
		final int expectedConfigCount = 1;

		boolean success = response.getNodes().size() == nodeCount;
		if (!success) {
			logger.error(
					"Expected " + nodeCount + " nodes to return response, but got only " + response.getNodes().size());
		}

		for (final String nodeId : response.getNodesMap().keySet()) {
			final ConfigUpdateNodeResponse node = response.getNodesMap().get(nodeId);
			final boolean successNode = node.getUpdatedConfigTypes() != null
					&& node.getUpdatedConfigTypes().length == expectedConfigCount;

			if (!successNode) {
				logger.error("Expected " + expectedConfigCount + " config types for node " + nodeId + " but got only "
						+ Arrays.toString(node.getUpdatedConfigTypes()));
			}

			success = success & successNode;
		}

		return success;
	}


	protected Settings.Builder copyKeysStartingWith(final Map<String, String> map, final String startWith) {
		if (map == null || map.isEmpty() || startWith == null || startWith.isEmpty()) {
			return Settings.builder();
		}

		Map<String, String> copiedValues = new HashMap<>();
		for (final String key : new HashSet<String>(map.keySet())) {
			if (key != null && key.startsWith(startWith)) {
				copiedValues.put(key, map.get(key));
			}
		}
		return Settings.builder().put(copiedValues);
	}

	protected Map<String, String> removeKeysStartingWith(final Map<String, String> map, final String startWith) {
		if (map == null || map.isEmpty() || startWith == null || startWith.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<String, String> removedEntries = new HashMap<>();
		
		for (final String key : new HashSet<String>(map.keySet())) {
			if (key != null && key.startsWith(startWith)) {
				String value = map.remove(key);
				if (value != null) {
					removedEntries.put(key, value);
				}

			}
		}
		return removedEntries;
	}

	protected Map<String, String> prependValueToEachKey(final Map<String, String> map, final String prepend) {
		if (map == null || map.isEmpty() || prepend == null || prepend.isEmpty()) {
			return map;
		}

		final Map<String, String> copy = new HashMap<String, String>();

		for (final String key : new HashSet<String>(map.keySet())) {
			if (key != null) {
				copy.put(prepend + key, map.get(key));
			}
		}

		return copy;
	}

	protected Map<String, String> removeLeadingValueFromEachKey(final Map<String, String> map, final String remove) {
		if (map == null || map.isEmpty() || remove == null || remove.isEmpty()) {
			return map;
		}

		final Map<String, String> copy = new HashMap<String, String>();

		for (final String key : new HashSet<String>(map.keySet())) {
			if (key != null) {
				copy.put(key.replaceAll(remove, ""), map.get(key));
			}
		}

		return copy;
	}

	protected static String convertToYaml(BytesReference bytes, boolean prettyPrint) throws IOException {
		try (XContentParser parser = JsonXContent.jsonXContent
				.createParser(NamedXContentRegistry.EMPTY, bytes.streamInput())) {
			parser.nextToken();
			XContentBuilder builder = XContentFactory.yamlBuilder();
			if (prettyPrint) {
				builder.prettyPrint();
			}
			builder.copyCurrentStructure(parser);
			return builder.string();
		}
	}

	protected static XContentBuilder convertToJson(Settings settings) throws IOException {
		XContentBuilder builder = XContentFactory.jsonBuilder();
		builder.prettyPrint();
		builder.startObject();
		settings.toXContent(builder, ToXContent.EMPTY_PARAMS);
		builder.endObject();
		return builder;
	}

	protected Tuple<String[], RestResponse> response(RestStatus status, String statusString, String message,
			String... configs) {

		try {
			final XContentBuilder builder = XContentFactory.jsonBuilder();
			builder.startObject();
			builder.field("status", statusString);
			builder.field("message", message);
			builder.endObject();
			String[] configsToUpdate = configs == null ? new String[0] : configs;
			return new Tuple<String[], RestResponse>(configsToUpdate, new BytesRestResponse(status, builder));
		} catch (IOException ex) {
			logger.error("Cannot build response", ex);
			return null;
		}
	}

	protected Tuple<String[], RestResponse> successResponse(String message, String... configs) {
		return response(RestStatus.OK, RestStatus.OK.name(), message, configs);
	}

	protected Tuple<String[], RestResponse> createdResponse(String message, String... configs) {
		return response(RestStatus.CREATED, RestStatus.CREATED.name(), message, configs);
	}

	protected Tuple<String[], RestResponse> badRequestResponse(String message) {
		return response(RestStatus.BAD_REQUEST, RestStatus.BAD_REQUEST.name(), message);
	}

	protected Tuple<String[], RestResponse> notFound(String message) {
		return response(RestStatus.NOT_FOUND, RestStatus.NOT_FOUND.name(), message);
	}

	protected Tuple<String[], RestResponse> internalErrorResponse(String message) {
		return response(RestStatus.INTERNAL_SERVER_ERROR, RestStatus.INTERNAL_SERVER_ERROR.name(), message);
	}

	protected Tuple<String[], RestResponse> unprocessable(String message) {
		return response(RestStatus.UNPROCESSABLE_ENTITY, RestStatus.UNPROCESSABLE_ENTITY.name(), message);
	}

	protected Tuple<String[], RestResponse> notImplemented(Method method) {
		return response(RestStatus.NOT_IMPLEMENTED, RestStatus.NOT_IMPLEMENTED.name(), "Method " + method.name() + " not supported for this action.");
	}
	
	/**
	 * Consume all defined parameters for the request. Before we handle the request
	 * in subclasses where we actually need the parameter, some global checks are
	 * performed, e.g. check whether the SG index exists. Thus, the parameter(s)
	 * have not been consumed, and ES will always return a 400 with an internal error message.
	 * 
	 * @param request
	 */
	protected void consumeParameters(final RestRequest request) {
		request.param("name");
	}
	
	private static void printLicenseInfo() {
        final StringBuilder sb = new StringBuilder();
        sb.append("******************************************************"+System.lineSeparator());
        sb.append("Search Guard REST Management API is not free software"+System.lineSeparator());
        sb.append("for commercial use in production."+System.lineSeparator());
        sb.append("You have to obtain a license if you "+System.lineSeparator());
        sb.append("use it in production."+System.lineSeparator());
        sb.append(System.lineSeparator());
        sb.append("See https://floragunn.com/searchguard-validate-license"+System.lineSeparator());
        sb.append("In case of any doubt mail to <sales@floragunn.com>"+System.lineSeparator());
        sb.append("*****************************************************"+System.lineSeparator());
        
        final String licenseInfo = sb.toString();
        
        if(!Boolean.getBoolean("sg.display_lic_none")) {
            
            if(!Boolean.getBoolean("sg.display_lic_only_stdout")) {
                LogManager.getLogger(AbstractApiAction.class).warn(licenseInfo);
                System.err.println(licenseInfo);
            }
    
            System.out.println(licenseInfo);
        }
        
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }
	
	
}
