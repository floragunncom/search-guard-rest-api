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
import org.junit.Test;

import com.floragunn.searchguard.SearchGuardPlugin;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateAction;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateRequest;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateResponse;
import com.floragunn.searchguard.configuration.ConfigurationService;
import com.floragunn.searchguard.ssl.SearchGuardSSLPlugin;
import com.floragunn.searchguard.ssl.util.SSLConfigConstants;

public class RestApiTest extends AbstractUnitTest {

	   @Test
	    public void testRestApi() throws Exception {

		   	this.enableHTTPClientSSL = true;
	        this.trustHTTPServerCertificate = true;
	        
		   	final Settings settings = defaultNodeSettings();
	        log.debug("Starting nodes");        
	        startES(settings);
	        log.debug("Started nodes");        
	        
	        log.debug("Setup index");        
	        setupSearchGuardIndex();
	        log.debug("Setup done");
	        
	        // test with no cert, must fail
	        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, executeGetRequest("_searchguard/api/configuration/internalusers").getStatusCode());	        
	        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, executeGetRequest("_searchguard/api/configuration/internalusers", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin"))).getStatusCode());
	        
	        // test with non-admin cert, must fail
	        this.keystore = "node-0-keystore.jks";
	        this.sendHTTPClientCertificate = true;
	        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, executeGetRequest("_searchguard/api/configuration/internalusers").getStatusCode());	        
	        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, executeGetRequest("_searchguard/api/configuration/internalusers", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin"))).getStatusCode());
	        
	        // test with admin cert, basic auth
	        this.keystore = "kirk-keystore.jks";
	        HttpResponse response = executeGetRequest("_searchguard/api/configuration/internalusers"); 
	        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
	        
	   }
	    

	   private Settings defaultNodeSettings() {
		   Settings.Builder builder = Settings.settingsBuilder().put("searchguard.ssl.transport.enabled", true)
	                .put(SSLConfigConstants.SEARCHGUARD_SSL_HTTP_ENABLE_OPENSSL_IF_AVAILABLE, false)
	                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_ENABLE_OPENSSL_IF_AVAILABLE, false)
	                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_ALIAS, "node-0")
	                .put("searchguard.ssl.transport.keystore_filepath", getAbsoluteFilePathFromClassPath("node-0-keystore.jks"))
	                .put("searchguard.ssl.transport.truststore_filepath", getAbsoluteFilePathFromClassPath("truststore.jks"))
	                .put("searchguard.ssl.transport.enforce_hostname_verification", false)
	                .put("searchguard.ssl.transport.resolve_hostname", false)
	                .putArray("searchguard.authcz.admin_dn", "CN=kirk,OU=client,O=client,l=tEst, C=De");
		   
	                if (this.enableHTTPClientSSL) {
	                	builder.put("searchguard.ssl.http.enabled", true)
	                	.put("searchguard.ssl.http.keystore_filepath", getAbsoluteFilePathFromClassPath("node-0-keystore.jks"))
	                	.put("searchguard.ssl.http.truststore_filepath", getAbsoluteFilePathFromClassPath("truststore.jks"));
//	                	searchguard.ssl.http.keystore_password: changeit
//	                	searchguard.ssl.http.truststore_password: changeit	                		                	
	                }
	                return builder.build();
	   }
	   
	private void setupSearchGuardIndex() {
		Settings tcSettings = Settings.builder().put("cluster.name", clustername).put(defaultNodeSettings())
				.put("searchguard.ssl.transport.keystore_filepath",
						getAbsoluteFilePathFromClassPath("kirk-keystore.jks"))
				.put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_ALIAS, "kirk").put("path.home", ".").build();

		try (TransportClient tc = TransportClient.builder().settings(tcSettings).addPlugin(SearchGuardSSLPlugin.class).addPlugin(SearchGuardPlugin.class).build()) {

			log.debug("Start transport client to init");

			tc.addTransportAddress(new InetSocketTransportAddress(new InetSocketAddress(nodeHost, nodePort)));
			Assert.assertEquals(3,
					tc.admin().cluster().nodesInfo(new NodesInfoRequest()).actionGet().getNodes().length);

			tc.admin().indices().create(new CreateIndexRequest("searchguard")).actionGet();
			
			tc.index(new IndexRequest("searchguard").type("dummy").id("0").refresh(true)
					.source(readYamlContent("sg_config.yml"))).actionGet();

			tc.index(new IndexRequest("searchguard").type("config").id("0").refresh(true)
					.source(readYamlContent("sg_config.yml"))).actionGet();
			tc.index(new IndexRequest("searchguard").type("internalusers").refresh(true).id("0")
					.source(readYamlContent("sg_internal_users.yml"))).actionGet();
			tc.index(new IndexRequest("searchguard").type("roles").id("0").refresh(true)
					.source(readYamlContent("sg_roles.yml"))).actionGet();
			tc.index(new IndexRequest("searchguard").type("rolesmapping").refresh(true).id("0")
					.source(readYamlContent("sg_roles_mapping.yml"))).actionGet();
			tc.index(new IndexRequest("searchguard").type("actiongroups").refresh(true).id("0")
					.source(readYamlContent("sg_action_groups.yml"))).actionGet();

            ConfigUpdateResponse cur = tc.execute(ConfigUpdateAction.INSTANCE, new ConfigUpdateRequest(ConfigurationService.CONFIGNAMES)).actionGet();
            Assert.assertEquals(3, cur.getNodes().length);

		}
	}
}
