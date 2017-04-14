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
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsAction;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
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
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
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
import com.floragunn.searchguard.auditlog.AuditLog;
import com.floragunn.searchguard.configuration.AdminDNs;
import com.floragunn.searchguard.configuration.ConfigurationLoader;
import com.floragunn.searchguard.dlic.rest.validation.AbstractConfigurationValidator;
import com.floragunn.searchguard.dlic.rest.validation.AbstractConfigurationValidator.ErrorType;
import com.floragunn.searchguard.ssl.transport.PrincipalExtractor;
import com.floragunn.searchguard.ssl.util.SSLRequestHelper;
import com.floragunn.searchguard.ssl.util.SSLRequestHelper.SSLInfo;
import com.floragunn.searchguard.support.ConfigConstants;

public abstract class AbstractApiAction extends BaseRestHandler {

	private final AdminDNs adminDNs;
	private final ConfigurationLoader cl;
	private final ClusterService cs;
	private final PrincipalExtractor principalExtractor;
	private String searchguardIndex;

	static {
		printLicenseInfo();
	}

	protected AbstractApiAction(final Settings settings, final RestController controller, final Client client,
			final AdminDNs adminDNs, final ConfigurationLoader cl, final ClusterService cs, final AuditLog auditLog,
			final PrincipalExtractor principalExtractor) {
		super(settings);

		this.searchguardIndex = settings.get(ConfigConstants.SG_CONFIG_INDEX, ConfigConstants.SG_DEFAULT_CONFIG_INDEX);
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

		boolean modified = removeKeysStartingWith(existing.internalMap(), name + ".");

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
		boolean existed = removeKeysStartingWith(existing.internalMap(), name + ".");
		existing.put(prependValueToEachKey(additionalSettingsBuilder.build().getAsMap(), name + "."));
		save(client, request, getConfigName(), existing);
		if (existed) {
			return successResponse(getResourceName() + " " + name + " replaced.", getConfigName());
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
		try {
			return cl.load(new String[] { config }, 30, TimeUnit.SECONDS).get(config);
		} catch (InterruptedException e) {
			logger.error("Unable to retrieve configuration due to a thread interruption");
		} catch (TimeoutException e) {
			logger.error("Unable to retrieve configuration due to a timeout {}", e, e.toString());
		}
		return null;
	}

	   protected boolean ensureIndexExists(final Client client) throws Throwable {
	        IndicesExistsRequest ier = new IndicesExistsRequest(this.searchguardIndex);
			final Semaphore sem = new Semaphore(0);
			final List<Throwable> exception = new ArrayList<Throwable>(1);
			final Boolean[] exists = new Boolean[] {Boolean.FALSE};
			
			client.execute(
					IndicesExistsAction.INSTANCE,
					ier,
					new ActionListener<IndicesExistsResponse>() {

						@Override
						public void onResponse(IndicesExistsResponse response) {
							sem.release();
							exists[0] = response.isExists();
						}

						@Override
						public void onFailure(Exception e) {
							sem.release();
							exception.add(e);
							logger.error("Failed to get Search Guard index due to {}", e);
						}

					}
			);

			if (!sem.tryAcquire(30, TimeUnit.SECONDS)) {
				logger.error("Failed to get Search Guard index due to timeout");
				return false;
			}

			if (exception.size() > 0) {
				logger.error("Failed to get Search Guard index due to "+ exception.get(0).getMessage());
				return false;
			}		
			
			return exists[0];
	    }
	
	protected void save(final Client client, final RestRequest request, final String config,
			final Settings.Builder settings) throws Throwable {
		final Semaphore sem = new Semaphore(0);
		final List<Throwable> exception = new ArrayList<Throwable>(1);
		final IndexRequest ir = new IndexRequest(this.searchguardIndex);
		//ir.putInContext(ConfigConstants.SG_USER,
		//		new User((String) request.getFromContext(ConfigConstants.SG_SSL_PRINCIPAL)));

		client.index(ir.type(config).id("0").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
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

	    SSLInfo sslInfo = SSLRequestHelper.getSSLInfo(request, principalExtractor);
	    
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

	/*
	protected static Settings toSettings(final BytesReference ref) {
		if (ref == null || ref.length() == 0) {
			throw new ElasticsearchException("ref invalid");
		}

		try {
			return Settings.builder().put(new JsonSettingsLoader(true).load(XContentHelper.createParser(ref))).build();
		} catch (final IOException e) {
			throw ExceptionsHelper.convertToElastic(e);
		}
	}

	protected static Settings.Builder toSettingsBuilder(final BytesReference ref) {
		if (ref == null || ref.length() == 0) {
			throw new ElasticsearchException("ref invalid");
		}

		try {
			return Settings.builder().put(new JsonSettingsLoader(true).load(XContentHelper.createParser(ref)));
		} catch (final IOException e) {
			throw ExceptionsHelper.convertToElastic(e);
		}
	}*/

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

	protected boolean removeKeysStartingWith(final Map<String, String> map, final String startWith) {
		if (map == null || map.isEmpty() || startWith == null || startWith.isEmpty()) {
			return false;
		}

		boolean modified = false;

		for (final String key : new HashSet<String>(map.keySet())) {
			if (key != null && key.startsWith(startWith)) {
				if (map.remove(key) != null) {
					modified = true;
				}
			}
		}

		return modified;
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
		try (XContentParser parser = XContentFactory.xContent(XContentFactory.xContentType(bytes))
				.createParser(bytes.streamInput())) {
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

	public static void printLicenseInfo() {
		System.out.println("***************************************************");
		System.out.println("Searchguard Management API is not free software");
		System.out.println("for commercial use in production.");
		System.out.println("You have to obtain a license if you ");
		System.out.println("use it in production.");
		System.out.println("***************************************************");
	}
}
