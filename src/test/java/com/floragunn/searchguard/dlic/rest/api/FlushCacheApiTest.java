package com.floragunn.searchguard.dlic.rest.api;

import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public class FlushCacheApiTest extends AbstractRestApiUnitTest {

	@Test
	public void testFlushCache() throws Exception {
		
		setup();
		
		// Only DELETE is allowed for flush cache
		rh.keystore = "kirk-keystore.jks";
		rh.sendHTTPClientCertificate = true;

		// GET
		HttpResponse response = rh.executeGetRequest("/_searchguard/api/cache");
		Assert.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getStatusCode());
		Settings settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Map<String, String> settingsAsMap = settings.getAsMap();
		Assert.assertEquals(settingsAsMap.get("message"), "Method GET not supported for this action.");

		// PUT
		response = rh.executePutRequest("/_searchguard/api/cache", "{}", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		settingsAsMap = settings.getAsMap();
		Assert.assertEquals(settingsAsMap.get("message"), "Method PUT not supported for this action.");

		// POST
		response = rh.executePostRequest("/_searchguard/api/cache", "{}", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		settingsAsMap = settings.getAsMap();
		Assert.assertEquals(settingsAsMap.get("message"), "Method POST not supported for this action.");

		// DELETE
		response = rh.executeDeleteRequest("/_searchguard/api/cache", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		settingsAsMap = settings.getAsMap();
		Assert.assertEquals(settingsAsMap.get("message"), "Cache flushed successfully.");

	}
}
