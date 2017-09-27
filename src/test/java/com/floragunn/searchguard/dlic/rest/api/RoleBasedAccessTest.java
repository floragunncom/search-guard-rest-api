package com.floragunn.searchguard.dlic.rest.api;

import org.apache.http.HttpStatus;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public class RoleBasedAccessTest extends AbstractRestApiUnitTest {

	@Test
	public void testActionGroupsApi() throws Exception {

		setup();

		rh.sendHTTPClientCertificate = false;

		// worf and sarek have access, worf has some endpoints disabled

		// user API, accessible for worf
		HttpResponse response = rh.executeGetRequest("/_searchguard/api/user/admin", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Settings settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertTrue(settings.getAsMap().containsKey("admin.hash"));

		// license API, not accessible for worf
		response = rh.executeGetRequest("_searchguard/api/license", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());
		Assert.assertTrue(response.getBody().contains("does not have any access to endpoint LICENSE"));

		// configuration API, not accessible for worf
		response = rh.executeGetRequest("_searchguard/api/configuration/actiongroups", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());
		Assert.assertTrue(response.getBody().contains("does not have any access to endpoint CONFIGURATION"));

		// cache API, not accessible for worf since it's disabled globally
		response = rh.executeDeleteRequest("_searchguard/api/cache", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());
		Assert.assertTrue(response.getBody().contains("does not have any access to endpoint CACHE"));

		// cache API, not accessible for sarek since it's disabled globally
		response = rh.executeDeleteRequest("_searchguard/api/cache", encodeBasicHeader("sarek", "sarek"));
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());
		Assert.assertTrue(response.getBody().contains("does not have any access to endpoint CACHE"));
		
		// Admin user has no eligibale role
		response = rh.executeGetRequest("/_searchguard/api/user/admin", encodeBasicHeader("admin", "admin"));
		Assert.assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode());
		Assert.assertTrue(response.getBody().contains("does not have any role privileged for admin access"));

		// Try the same, but now with admin certificate
		rh.sendHTTPClientCertificate = true;
		
		// admin
		response = rh.executeGetRequest("/_searchguard/api/user/admin", encodeBasicHeader("admin", "admin"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertTrue(settings.getAsMap().containsKey("admin.hash"));

		// worf and config
		response = rh.executeGetRequest("_searchguard/api/configuration/actiongroups", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		
		// cache
		response = rh.executeDeleteRequest("_searchguard/api/cache", encodeBasicHeader("worf", "worf"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());

	}
}
