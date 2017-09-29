package com.floragunn.searchguard.dlic.rest.api;

import java.util.List;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public class RestApInfoEndpointTest extends AbstractRestApiUnitTest {
	
	 @SuppressWarnings("unchecked")
	 @Test
	 public void testLicenseApiWithSettings() throws Exception {
	
	 setupWithRestRoles();
	
	 rh.keystore = "kirk-keystore.jks";
	 rh.sendHTTPClientCertificate = true;
	
	 HttpResponse response = rh.executeGetRequest("/_searchguard/restapiinfo", encodeBasicHeader("admin", "admin"));
	 Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
	 Settings settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
	 Map<String, Object> result = settings.getAsStructuredMap();
		String enabled = (String)result.get("rest_api_enabled");
		Assert.assertEquals("true", enabled);
	 List<String> enabledRoles = (List<String>)result.get("roles_enabled");
	 Assert.assertArrayEquals(enabledRoles.toArray(new String[0]), new String[]{"sg_role_klingons", "sg_role_vulcans"});
	 List<String> disabledKlingons = (List<String>)((Map<String,Object>)result.get("endpoints_disabled")).get("sg_role_klingons");
	 // API Info spits out raw settings values, only API performs sanity checks
	 Assert.assertArrayEquals(disabledKlingons.toArray(new String[0]), new String[]{"LICENSE", "ConfiGuration", "WRONGType"});
	 List<String> disabledGlobal = (List<String>)((Map<String,Object>)result.get("endpoints_disabled")).get("global");
	 Assert.assertArrayEquals(disabledGlobal.toArray(new String[0]), new String[]{"CACHE"});
	
	 tearDown();
	 }

	@SuppressWarnings("unchecked")
	@Test
	public void testLicenseApiWithoutSettings() throws Exception {

		setup();

		rh.keystore = "kirk-keystore.jks";
		rh.sendHTTPClientCertificate = true;

		HttpResponse response = rh.executeGetRequest("/_searchguard/restapiinfo", encodeBasicHeader("admin", "admin"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Settings settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Map<String, Object> result = settings.getAsStructuredMap();
		String enabled = (String)result.get("rest_api_enabled");
		Assert.assertEquals("true", enabled);
		List<String> enabledRoles = (List<String>) result.get("roles_enabled");
		Assert.assertNull(enabledRoles);
		Object disabledKlingons = result.get("endpoints_disabled");
		Assert.assertNull(disabledKlingons);
		Object disabledGlobal = result.get("endpoints_disabled");
		Assert.assertNull(disabledGlobal);
		tearDown();
	}
}
