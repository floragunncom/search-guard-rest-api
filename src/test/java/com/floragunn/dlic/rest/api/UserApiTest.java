package com.floragunn.dlic.rest.api;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.common.settings.Settings;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.dlic.rest.validation.AbstractConfigurationValidator;

import test.helper.rest.RestHelper.HttpResponse;

public class UserApiTest extends AbstractRestApiUnitTest {

	// controller.registerHandler(Method.DELETE,
	// "/_searchguard/api/user/{name}", this);
	// controller.registerHandler(Method.POST, "/_searchguard/api/user/{name}",
	// this);

	@Test
	public void testUserApi() throws Exception {

		setup();

		rh.keystore = "kirk-keystore.jks";
		rh.sendHTTPClientCertificate = true;

		// initial configuration, two users
		HttpResponse response = rh.executeGetRequest("_searchguard/api/configuration/internalusers");
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Settings settings = Settings.builder().loadFromSource(response.getBody()).build();
		Assert.assertEquals(settings.getAsMap().size(), 2);

		// no username given
		response = rh.executePostRequest("/_searchguard/api/user/", "{hash: \"123\"}", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());

		response = rh.executePostRequest("/_searchguard/api/user", "{hash: \"123\"}", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());

		// add user with wrong JSON payload and check error message
		response = rh.executePostRequest("/_searchguard/api/user/barthelmus", "{some: \"thing\", other: \"thing\"}",
				new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody()).build();
		Assert.assertEquals(settings.get("status"), AbstractConfigurationValidator.INVALID_CONFIGURATION_MESSAGE);
		Assert.assertTrue(
				settings.get(AbstractConfigurationValidator.MISSING_MANDATORY_KEYS_KEY + ".keys").contains("hash"));
		Assert.assertTrue(settings.get(AbstractConfigurationValidator.INVALID_KEYS_KEY + ".keys").contains("some"));
		Assert.assertTrue(settings.get(AbstractConfigurationValidator.INVALID_KEYS_KEY + ".keys").contains("other"));

		// add user with correct setting. User has role "sg_all_access"
		// password: barthelmus
		// $2a$12$E01dU1e1vZ2cmzGwx9TkhOYrWnG4PeIf.q.PM1KXySqg9Z3n4BEw2

		// check access not allowed
		rh.sendHTTPClientCertificate = false;
		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED,
				rh.executeGetRequest("",
						new BasicHeader("Authorization", "Basic " + encodeBasicHeader("barthelmus", "barthelmus")))
						.getStatusCode());

		// add users
		rh.sendHTTPClientCertificate = true;
		response = rh.executePostRequest("/_searchguard/api/user/barthelmus",
				"{hash: \"$2a$12$E01dU1e1vZ2cmzGwx9TkhOYrWnG4PeIf.q.PM1KXySqg9Z3n4BEw2\"}", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());

		// access must be allowed now
		rh.sendHTTPClientCertificate = false;
		Assert.assertEquals(HttpStatus.SC_OK,
				rh.executeGetRequest("",
						new BasicHeader("Authorization", "Basic " + encodeBasicHeader("barthelmus", "barthelmus")))
						.getStatusCode());

		// try remove user, no username
		rh.sendHTTPClientCertificate = true;
		response = rh.executeDeleteRequest("/_searchguard/api/user", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());

		response = rh.executeDeleteRequest("/_searchguard/api/user/", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());

		// now really remove user
		response = rh.executeDeleteRequest("/_searchguard/api/user/barthelmus", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());

		// Access must be forbidden now
		rh.sendHTTPClientCertificate = false;
		response = rh.executeGetRequest("",
				new BasicHeader("Authorization", "Basic " + encodeBasicHeader("barthelmus", "barthelmus")));
		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusCode());

	}

}
