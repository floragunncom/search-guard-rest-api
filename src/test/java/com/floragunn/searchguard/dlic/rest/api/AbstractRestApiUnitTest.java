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

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.transport.Netty4Plugin;
import org.junit.Assert;

import com.floragunn.searchguard.SearchGuardPlugin;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateAction;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateRequest;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateResponse;
import com.floragunn.searchguard.ssl.util.SSLConfigConstants;
import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.test.AbstractSGUnitTest;
import com.floragunn.searchguard.test.helper.cluster.ClusterConfiguration;
import com.floragunn.searchguard.test.helper.cluster.ClusterHelper;
import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public abstract class AbstractRestApiUnitTest extends AbstractSGUnitTest {

	protected void setup() throws Exception {
		setup(ClusterConfiguration.SINGLENODE);
	}

	protected void setup(ClusterConfiguration configuration) throws Exception {
		final Settings nodeSettings = defaultNodeSettings(true);

		log.debug("Starting nodes");
		this.ci = ch.startCluster(nodeSettings, configuration);
		log.debug("Started nodes");

		log.debug("Setup index");
		setupAndInitializeSearchGuardIndex();
		log.debug("Setup done");

		RestHelper rh = new RestHelper(ci);

		this.rh = rh;
	}

	protected void deleteUser(String username) throws Exception {
		boolean sendHTTPClientCertificate = rh.sendHTTPClientCertificate;
		rh.sendHTTPClientCertificate = true;
		HttpResponse response = rh.executeDeleteRequest("/_searchguard/api/user/" + username, new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		rh.sendHTTPClientCertificate = sendHTTPClientCertificate;
	}

	protected void addUserWithPassword(String username, String password, int status) throws Exception {
		boolean sendHTTPClientCertificate = rh.sendHTTPClientCertificate;
		rh.sendHTTPClientCertificate = true;
		HttpResponse response = rh.executePutRequest("/_searchguard/api/user/" + username,
				"{\"password\": \"" + password + "\"}", new Header[0]);
		Assert.assertEquals(status, response.getStatusCode());
		rh.sendHTTPClientCertificate = sendHTTPClientCertificate;
	}

	protected void addUserWithPassword(String username, String password, String[] roles, int status) throws Exception {
		boolean sendHTTPClientCertificate = rh.sendHTTPClientCertificate;
		rh.sendHTTPClientCertificate = true;
		String payload = "{" + "\"password\": \"" + password + "\"," + "\"roles\": [";
		for (int i = 0; i < roles.length; i++) {
			payload += "\" " + roles[i] + " \"";
			if (i + 1 < roles.length) {
				payload += ",";
			}
		}
		payload += "]}";
		HttpResponse response = rh.executePutRequest("/_searchguard/api/user/" + username, payload, new Header[0]);
		Assert.assertEquals(status, response.getStatusCode());
		rh.sendHTTPClientCertificate = sendHTTPClientCertificate;
	}

	protected void addUserWithHash(String username, String hash) throws Exception {
		addUserWithHash(username, hash, HttpStatus.SC_OK);
	}

	protected void addUserWithHash(String username, String hash, int status) throws Exception {
		boolean sendHTTPClientCertificate = rh.sendHTTPClientCertificate;
		rh.sendHTTPClientCertificate = true;
		HttpResponse response = rh.executePutRequest("/_searchguard/api/user/" + username, "{\"hash\": \"" + hash + "\"}",
				new Header[0]);
		Assert.assertEquals(status, response.getStatusCode());
		rh.sendHTTPClientCertificate = sendHTTPClientCertificate;
	}

	protected void checkGeneralAccess(int status, String username, String password) throws Exception {
		boolean sendHTTPClientCertificate = rh.sendHTTPClientCertificate;
		rh.sendHTTPClientCertificate = false;
		Assert.assertEquals(status,
				rh.executeGetRequest("",
						new BasicHeader("Authorization", "Basic " + encodeBasicHeader(username, password)))
						.getStatusCode());
		rh.sendHTTPClientCertificate = sendHTTPClientCertificate;
	}

	protected String checkReadAccess(int status, String username, String password, String indexName, String type,
			int id) throws Exception {
		boolean sendHTTPClientCertificate = rh.sendHTTPClientCertificate;
		rh.sendHTTPClientCertificate = false;
		String action = indexName + "/" + type + "/" + id;
		HttpResponse response = rh.executeGetRequest(action,
				new BasicHeader("Authorization", "Basic " + encodeBasicHeader(username, password)));
		int returnedStatus = response.getStatusCode();
		Assert.assertEquals(status, returnedStatus);
		rh.sendHTTPClientCertificate = sendHTTPClientCertificate;
		return response.getBody();

	}

	protected String checkWriteAccess(int status, String username, String password, String indexName, String type,
			int id) throws Exception {

		boolean sendHTTPClientCertificate = rh.sendHTTPClientCertificate;
		rh.sendHTTPClientCertificate = false;
		String action = indexName + "/" + type + "/" + id;
		String payload = "{\"value\" : \"true\"}";
		HttpResponse response = rh.executePutRequest(action, payload,
				new BasicHeader("Authorization", "Basic " + encodeBasicHeader(username, password)));
		int returnedStatus = response.getStatusCode();
		Assert.assertEquals(status, returnedStatus);
		rh.sendHTTPClientCertificate = sendHTTPClientCertificate;
		return response.getBody();
	}

	protected void setupStarfleetIndex() throws Exception {
		boolean sendHTTPClientCertificate = rh.sendHTTPClientCertificate;
		rh.sendHTTPClientCertificate = true;
		rh.executePutRequest("sf", null, new Header[0]);
		rh.executePutRequest("sf/ships/0", "{\"number\" : \"NCC-1701-D\"}", new Header[0]);
		rh.executePutRequest("sf/public/0", "{\"some\" : \"value\"}", new Header[0]);
		rh.sendHTTPClientCertificate = sendHTTPClientCertificate;
	}
	
	protected void setupSearchGuardIndex() {
		Settings tcSettings = Settings.builder().put("cluster.name", ClusterHelper.clustername)
				.put(defaultNodeSettings(false))
				.put("searchguard.ssl.transport.keystore_filepath",
						FileHelper.getAbsoluteFilePathFromClassPath("kirk-keystore.jks"))
				.put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_ALIAS, "kirk").put("path.home", ".").build();

		try (TransportClient tc = new TransportClientImpl(tcSettings,asCollection(Netty4Plugin.class, SearchGuardPlugin.class))) {

			log.debug("Start transport client to init");

			tc.addTransportAddress(new InetSocketTransportAddress(new InetSocketAddress(ci.nodeHost, ci.nodePort)));
			Assert.assertEquals(ci.numNodes,
					tc.admin().cluster().nodesInfo(new NodesInfoRequest()).actionGet().getNodes().size());

			tc.admin().indices().create(new CreateIndexRequest("searchguard")).actionGet();


		}
		
	}
	
	protected void setupAndInitializeSearchGuardIndex() {
		Settings tcSettings = Settings.builder().put("cluster.name", ClusterHelper.clustername)
				.put(defaultNodeSettings(false))
				.put("searchguard.ssl.transport.keystore_filepath",
						FileHelper.getAbsoluteFilePathFromClassPath("kirk-keystore.jks"))
				.put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_ALIAS, "kirk").put("path.home", ".").build();

		try (TransportClient tc = new TransportClientImpl(tcSettings,asCollection(Netty4Plugin.class, SearchGuardPlugin.class))) {

			log.debug("Start transport client to init");

			tc.addTransportAddress(new InetSocketTransportAddress(new InetSocketAddress(ci.nodeHost, ci.nodePort)));
			Assert.assertEquals(ci.numNodes,
					tc.admin().cluster().nodesInfo(new NodesInfoRequest()).actionGet().getNodes().size());

			tc.admin().indices().create(new CreateIndexRequest("searchguard")).actionGet();

			tc.index(new IndexRequest("searchguard").type("config").id("0").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
					.source("config", FileHelper.readYamlContent("sg_config.yml"))).actionGet();
			tc.index(new IndexRequest("searchguard").type("internalusers").setRefreshPolicy(RefreshPolicy.IMMEDIATE).id("0")
					.source("internalusers", FileHelper.readYamlContent("sg_internal_users.yml"))).actionGet();
			tc.index(new IndexRequest("searchguard").type("roles").id("0").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
					.source("roles", FileHelper.readYamlContent("sg_roles.yml"))).actionGet();
			tc.index(new IndexRequest("searchguard").type("rolesmapping").setRefreshPolicy(RefreshPolicy.IMMEDIATE).id("0")
					.source("rolesmapping", FileHelper.readYamlContent("sg_roles_mapping.yml"))).actionGet();
			tc.index(new IndexRequest("searchguard").type("actiongroups").setRefreshPolicy(RefreshPolicy.IMMEDIATE).id("0")
					.source("actiongroups", FileHelper.readYamlContent("sg_action_groups.yml"))).actionGet();

			ConfigUpdateResponse cur = tc
					.execute(ConfigUpdateAction.INSTANCE, new ConfigUpdateRequest(ConfigConstants.CONFIGNAMES))
					.actionGet();
			Assert.assertEquals(ci.numNodes, cur.getNodes().size());

		}	
	}

	protected Settings defaultNodeSettings(boolean enableRestSSL) {
		Settings.Builder builder = Settings.builder().put("searchguard.ssl.transport.enabled", true)
				.put(SSLConfigConstants.SEARCHGUARD_SSL_HTTP_ENABLE_OPENSSL_IF_AVAILABLE, false)
				.put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_ENABLE_OPENSSL_IF_AVAILABLE, false)
				.put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_ALIAS, "node-0")
				.put("searchguard.ssl.transport.keystore_filepath",
						FileHelper.getAbsoluteFilePathFromClassPath("node-0-keystore.jks"))
				.put("searchguard.ssl.transport.truststore_filepath",
						FileHelper.getAbsoluteFilePathFromClassPath("truststore.jks"))
				.put("searchguard.ssl.transport.enforce_hostname_verification", false)
				.put("searchguard.ssl.transport.resolve_hostname", false)
				.putArray("searchguard.authcz.admin_dn", "CN=kirk,OU=client,O=client,l=tEst, C=De");

		if (enableRestSSL) {
			builder.put("searchguard.ssl.http.enabled", true)
					.put("searchguard.ssl.http.keystore_filepath",
							FileHelper.getAbsoluteFilePathFromClassPath("node-0-keystore.jks"))
					.put("searchguard.ssl.http.truststore_filepath",
							FileHelper.getAbsoluteFilePathFromClassPath("truststore.jks"));
		}
		return builder.build();
	}
	
	protected static class TransportClientImpl extends TransportClient {

        public TransportClientImpl(Settings settings, Collection<Class<? extends Plugin>> plugins) {
            super(settings, plugins);
        }

        public TransportClientImpl(Settings settings, Settings defaultSettings, Collection<Class<? extends Plugin>> plugins) {
            super(settings, defaultSettings, plugins, null);
        }       
    }
	
	protected static Collection<Class<? extends Plugin>> asCollection(Class<? extends Plugin>... plugins) {
        return Arrays.asList(plugins);
    }
}
