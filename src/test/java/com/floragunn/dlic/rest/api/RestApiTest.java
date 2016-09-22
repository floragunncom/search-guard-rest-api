package com.floragunn.dlic.rest.api;

import java.net.InetSocketAddress;

import javax.xml.ws.Response;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import com.floragunn.searchguard.SearchGuardPlugin;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateAction;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateRequest;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateResponse;
import com.floragunn.searchguard.configuration.ConfigurationService;
import com.floragunn.searchguard.ssl.SearchGuardSSLPlugin;
import com.floragunn.searchguard.ssl.util.SSLConfigConstants;

import test.AbstractSGUnitTest;
import test.helper.cluster.ClusterConfiguration;
import test.helper.cluster.ClusterHelper;
import test.helper.cluster.ClusterInfo;
import test.helper.file.FileHelper;
import test.helper.rest.RestHelper;
import test.helper.rest.RestHelper.HttpResponse;

public class RestApiTest extends AbstractSGUnitTest {

	
	   	@Test
	    public void testRestApi() throws Exception {
	   			        
		   	final Settings settings = defaultNodeSettings(true);
	        		   	
	        log.debug("Starting nodes");        
	        ClusterInfo clInfo = clusterHelper.startCluster(settings, ClusterConfiguration.SINGLENODE);
	        log.debug("Started nodes");        
	        
	        log.debug("Setup index");        
	        setupSearchGuardIndex(clInfo);
	        log.debug("Setup done");
	        
	        RestHelper restHelper = new RestHelper(clInfo);
	        		
	        // test with no cert, must fail
	        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, restHelper.executeGetRequest("_searchguard/api/configuration/internalusers").getStatusCode());	        
	        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, restHelper.executeGetRequest("_searchguard/api/configuration/internalusers", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin"))).getStatusCode());
	        
	        // test with non-admin cert, must fail
	        restHelper.keystore = "node-0-keystore.jks";
	        restHelper.sendHTTPClientCertificate = true;
	        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, restHelper.executeGetRequest("_searchguard/api/configuration/internalusers").getStatusCode());	        
	        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, restHelper.executeGetRequest("_searchguard/api/configuration/internalusers", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin"))).getStatusCode());
	        
	        // test with admin cert, basic auth
	        restHelper.keystore = "kirk-keystore.jks";
	        HttpResponse response = restHelper.executeGetRequest("_searchguard/api/configuration/internalusers"); 
	        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
	        
	   }
	    

	   private Settings defaultNodeSettings(boolean enableRestSSL) {
		   Settings.Builder builder = Settings.settingsBuilder().put("searchguard.ssl.transport.enabled", true)
	                .put(SSLConfigConstants.SEARCHGUARD_SSL_HTTP_ENABLE_OPENSSL_IF_AVAILABLE, false)
	                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_ENABLE_OPENSSL_IF_AVAILABLE, false)
	                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_ALIAS, "node-0")
	                .put("searchguard.ssl.transport.keystore_filepath", FileHelper.getAbsoluteFilePathFromClassPath("node-0-keystore.jks"))
	                .put("searchguard.ssl.transport.truststore_filepath", FileHelper.getAbsoluteFilePathFromClassPath("truststore.jks"))
	                .put("searchguard.ssl.transport.enforce_hostname_verification", false)
	                .put("searchguard.ssl.transport.resolve_hostname", false)
	                .putArray("searchguard.authcz.admin_dn", "CN=kirk,OU=client,O=client,l=tEst, C=De");
		   
	                if (enableRestSSL) {
	                	builder.put("searchguard.ssl.http.enabled", true)
	                	.put("searchguard.ssl.http.keystore_filepath", FileHelper.getAbsoluteFilePathFromClassPath("node-0-keystore.jks"))
	                	.put("searchguard.ssl.http.truststore_filepath", FileHelper.getAbsoluteFilePathFromClassPath("truststore.jks"));	                		                	
	                }
	                return builder.build();
	   }
	   
	private void setupSearchGuardIndex(ClusterInfo cInfo) {
		Settings tcSettings = Settings.builder().put("cluster.name", clusterName).put(defaultNodeSettings(false))
				.put("searchguard.ssl.transport.keystore_filepath",
						FileHelper.getAbsoluteFilePathFromClassPath("kirk-keystore.jks"))
				.put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_ALIAS, "kirk").put("path.home", ".").build();

		try (TransportClient tc = TransportClient.builder().settings(tcSettings).addPlugin(SearchGuardSSLPlugin.class).addPlugin(SearchGuardPlugin.class).build()) {

			log.debug("Start transport client to init");

			tc.addTransportAddress(new InetSocketTransportAddress(new InetSocketAddress(cInfo.nodeHost, cInfo.nodePort)));
			Assert.assertEquals(3,
					tc.admin().cluster().nodesInfo(new NodesInfoRequest()).actionGet().getNodes().length);

			tc.admin().indices().create(new CreateIndexRequest("searchguard")).actionGet();
			
			tc.index(new IndexRequest("searchguard").type("dummy").id("0").refresh(true)
					.source(FileHelper.readYamlContent("sg_config.yml"))).actionGet();

			tc.index(new IndexRequest("searchguard").type("config").id("0").refresh(true)
					.source(FileHelper.readYamlContent("sg_config.yml"))).actionGet();
			tc.index(new IndexRequest("searchguard").type("internalusers").refresh(true).id("0")
					.source(FileHelper.readYamlContent("sg_internal_users.yml"))).actionGet();
			tc.index(new IndexRequest("searchguard").type("roles").id("0").refresh(true)
					.source(FileHelper.readYamlContent("sg_roles.yml"))).actionGet();
			tc.index(new IndexRequest("searchguard").type("rolesmapping").refresh(true).id("0")
					.source(FileHelper.readYamlContent("sg_roles_mapping.yml"))).actionGet();
			tc.index(new IndexRequest("searchguard").type("actiongroups").refresh(true).id("0")
					.source(FileHelper.readYamlContent("sg_action_groups.yml"))).actionGet();

            ConfigUpdateResponse cur = tc.execute(ConfigUpdateAction.INSTANCE, new ConfigUpdateRequest(ConfigurationService.CONFIGNAMES)).actionGet();
            Assert.assertEquals(3, cur.getNodes().length);

		}
	}
}
