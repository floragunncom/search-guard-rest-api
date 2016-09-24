package com.floragunn.dlic.rest.api;

import org.apache.http.HttpStatus;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.Assert;
import org.junit.Test;

import test.helper.cluster.ClusterConfiguration;
import test.helper.cluster.ClusterInfo;
import test.helper.content.ContentHelper;
import test.helper.rest.RestHelper;
import test.helper.rest.RestHelper.HttpResponse;

public class GetConfigurationApiTest extends AbstractRestApiUnitTest {

	
	   	@Test
	    public void testRestApi() throws Exception {
	   			        
		   	final Settings settings = defaultNodeSettings(true);
	        		   	
	        log.debug("Starting nodes");        
	        ClusterInfo clInfo = clusterHelper.startCluster(settings, ClusterConfiguration.SINGLENODE);
	        log.debug("Started nodes");        
	        
	        log.debug("Setup index");        
	        setupSearchGuardIndex(clInfo);
	        log.debug("Setup done");
	        
	        RestHelper rh = new RestHelper(clInfo);
	        		
//	        // test with no cert, must fail
//	        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, rh.executeGetRequest("_searchguard/api/configuration/internalusers").getStatusCode());	        
//	        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, rh.executeGetRequest("_searchguard/api/configuration/internalusers", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin"))).getStatusCode());
//	        
//	        // test with non-admin cert, must fail
//	        rh.keystore = "node-0-keystore.jks";
//	        rh.sendHTTPClientCertificate = true;
//	        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, rh.executeGetRequest("_searchguard/api/configuration/internalusers").getStatusCode());	        
//	        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, rh.executeGetRequest("_searchguard/api/configuration/internalusers", new BasicHeader("Authorization", "Basic "+encodeBasicHeader("admin", "admin"))).getStatusCode());
	        
	        // test with admin cert, basic auth
	        rh.keystore = "kirk-keystore.jks";	       
	        rh.sendHTTPClientCertificate = true;
	        HttpResponse response = rh.executeGetRequest("_searchguard/api/configuration/internalusers"); 
	        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
	        
	        //XContentBuilder builder = ContentHelper.parseJsonContent(response.getBody());
	        Settings builder = Settings.builder().loadFromSource(response.getBody()).build();
	        System.out.println(response.getBody());
	        
	   }
	    

}
