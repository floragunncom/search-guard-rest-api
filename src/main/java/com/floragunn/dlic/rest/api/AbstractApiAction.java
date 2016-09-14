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

package com.floragunn.dlic.rest.api;

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

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.WriteConsistencyLevel;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.loader.JsonSettingsLoader;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;

import com.floragunn.searchguard.action.configupdate.ConfigUpdateAction;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateRequest;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateResponse;
import com.floragunn.searchguard.auditlog.AuditLog;
import com.floragunn.searchguard.configuration.AdminDNs;
import com.floragunn.searchguard.configuration.ConfigurationLoader;
import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.user.User;

public abstract class AbstractApiAction extends BaseRestHandler {

    private final AdminDNs adminDNs;
    private final ConfigurationLoader cl;
    private final ClusterService cs;
    private final AuditLog auditLog;

    static {
        printLicenseInfo();
    }

    protected AbstractApiAction(final Settings settings, final RestController controller, final Client client, final AdminDNs adminDNs,
            final ConfigurationLoader cl, final ClusterService cs, final AuditLog auditLog) {
        super(settings, controller, client);
        this.adminDNs = adminDNs;
        this.cl = cl;
        this.cs = cs;
        this.auditLog = auditLog;
    }

    protected abstract Tuple<String[], RestResponse> handleApiRequest(final RestRequest request, final Client client) throws Throwable;

    protected final Settings.Builder load(final String config) {
        return Settings.builder().put(loadAsSettings(config));
    }

    protected final Settings loadAsSettings(final String config) {
        return cl.load(new String[] { config }).get(config);
    }
    
    protected void save(final Client client, final RestRequest request, final String config, final Settings.Builder settings)
            throws Throwable {
        final Semaphore sem = new Semaphore(0);
        final List<Throwable> exception = new ArrayList<Throwable>(1);
        final IndexRequest ir = new IndexRequest("searchguard");
        ir.putInContext(ConfigConstants.SG_USER, new User((String) request.getFromContext(ConfigConstants.SG_SSL_PRINCIPAL)));

        client.index(ir.type(config).id("0").refresh(true).consistencyLevel(WriteConsistencyLevel.DEFAULT).source(toSource(settings)),
                new ActionListener<IndexResponse>() {

            @Override
            public void onResponse(final IndexResponse response) {
                sem.release();
                if (logger.isDebugEnabled()) {
                    logger.debug("{} successfully updated", config);
                }
            }

            @Override
            public void onFailure(final Throwable e) {
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
    protected final void handleRequest(final RestRequest request, final RestChannel channel, final Client client) {

        final X509Certificate[] certs = request.getFromContext(ConfigConstants.SG_SSL_PEER_CERTIFICATES);

        if (certs == null || certs.length == 0) {
            logger.error("No certificate found");
            // auditLog.logSgIndexAttempt(request, action); //TODO add method
            // for rest request
            final BytesRestResponse response = new BytesRestResponse(RestStatus.FORBIDDEN, "No certificates");
            channel.sendResponse(response);
            return;
        }

        if (!adminDNs.isAdmin((String) request.getFromContext(ConfigConstants.SG_SSL_PRINCIPAL))) {
            // auditLog.logSgIndexAttempt(request, action); //TODO add method
            // for rest request
            logger.error("SG admin permissions required but {} is not an admin", request.getFromContext(ConfigConstants.SG_SSL_PRINCIPAL));
            final BytesRestResponse response = new BytesRestResponse(RestStatus.FORBIDDEN, "SG admin permissions required");
            channel.sendResponse(response);
            return;
        }

        final Semaphore sem = new Semaphore(0);
        final List<Throwable> exception = new ArrayList<Throwable>(1);
        final Tuple<String[], RestResponse> response;
        try {
            response = handleApiRequest(request, client);

            if (response.v1().length > 0) {

                final ConfigUpdateRequest cur = new ConfigUpdateRequest(response.v1());
                cur.putInContext(ConfigConstants.SG_USER, new User((String) request.getFromContext(ConfigConstants.SG_SSL_PRINCIPAL)));

                client.execute(ConfigUpdateAction.INSTANCE, cur, new ActionListener<ConfigUpdateResponse>() {

                    @Override
                    public void onFailure(final Throwable e) {
                        sem.release();
                        logger.error("Cannot update {} due to {}", e, Arrays.toString(response.v1()), e);
                        exception.add(e);
                    }

                    @Override
                    public void onResponse(final ConfigUpdateResponse ur) {
                        sem.release();
                        if (!checkConfigUpdateResponse(ur)) {
                            logger.error("Cannot update {}", Arrays.toString(response.v1()));
                            exception.add(new ElasticsearchException("Unable to update " + Arrays.toString(response.v1())));
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
            channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, e.toString()));
            return;
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
            channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, exception.get(0).toString()));
            return;
        }

        channel.sendResponse(response.v2());

    }

    protected static XContentBuilder toSource(final Settings.Builder settingsBuilder) throws IOException {
        return XContentFactory.jsonBuilder().map(settingsBuilder.build().getAsStructuredMap());
    }

    protected boolean checkConfigUpdateResponse(final ConfigUpdateResponse response) {

        final int nodeCount = cs.state().getNodes().getNodes().size();
        final int expectedConfigCount = 1;

        boolean success = response.getNodes().length == nodeCount;
        if (!success) {
            logger.error("Expected " + nodeCount + " nodes to return response, but got only " + response.getNodes().length);
        }

        for (final String nodeId : response.getNodesMap().keySet()) {
            final ConfigUpdateResponse.Node node = response.getNodesMap().get(nodeId);
            final boolean successNode = node.getUpdatedConfigTypes() != null && node.getUpdatedConfigTypes().length == expectedConfigCount;

            if (!successNode) {
                logger.error("Expected " + expectedConfigCount + " config types for node " + nodeId + " but got only "
                        + Arrays.toString(node.getUpdatedConfigTypes()));
            }

            success = success & successNode;
        }

        return success;
    }

    protected static Settings toSettings(final BytesReference ref) {
        if (ref == null || ref.length() == 0) {
            throw new ElasticsearchException("ref invalid");
        }

        try {
            return Settings.builder().put(new JsonSettingsLoader().load(XContentHelper.createParser(ref))).build();
        } catch (final IOException e) {
            throw ExceptionsHelper.convertToElastic(e);
        }
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

    public static void printLicenseInfo() {
        System.out.println("***************************************************");
        System.out.println("Searchguard Management API is not free software");
        System.out.println("for commercial use in production.");
        System.out.println("You have to obtain a license if you ");
        System.out.println("use it in production.");
        System.out.println("***************************************************");
    }
}
