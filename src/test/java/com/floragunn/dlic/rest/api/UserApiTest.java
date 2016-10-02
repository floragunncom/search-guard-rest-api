package com.floragunn.dlic.rest.api;

import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.elasticsearch.common.settings.Settings;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.dlic.rest.validation.AbstractConfigurationValidator;
import com.floragunn.searchguard.configuration.ConfigurationService;

import test.helper.rest.RestHelper.HttpResponse;

public class UserApiTest extends AbstractRestApiUnitTest {

	@Test
	public void testUserApi() throws Exception {

		setup();

		rh.keystore = "kirk-keystore.jks";
		rh.sendHTTPClientCertificate = true;

		// initial configuration, 2 users
		HttpResponse response = rh
				.executeGetRequest("_searchguard/api/configuration/" + ConfigurationService.CONFIGNAME_INTERNAL_USERS);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Settings settings = Settings.builder().loadFromSource(response.getBody()).build();
		Assert.assertEquals(settings.getAsMap().size(), 2);

		// --- GET

		// GET, user admin, exists
		response = rh.executeGetRequest("/_searchguard/api/user/admin", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody()).build();
		Map<String, String> settingsAsMap = settings.getAsMap();
		Assert.assertEquals(1, settingsAsMap.size());
		Assert.assertEquals("$2a$12$VcCDgh2NDk07JGN0rjGbM.Ad41qVR/YFJcgHp0UGns5JDymv..TOG",
				settingsAsMap.get("admin.hash"));

		// GET, user does not exist
		response = rh.executeGetRequest("/_searchguard/api/user/nothinghthere", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusCode());

		// GET, malformed URL
		response = rh.executeGetRequest("/_searchguard/api/user/", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());

		// GET, malformed URL
		response = rh.executeGetRequest("/_searchguard/api/user", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());

		// -- PUT

		// no username given
		response = rh.executePutRequest("/_searchguard/api/user/", "{hash: \"123\"}", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());

		response = rh.executePutRequest("/_searchguard/api/user", "{hash: \"123\"}", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());

		// Faulty JSON payload
		response = rh.executePutRequest("/_searchguard/api/user/nagilum", "{some: \"thing\" asd  other: \"thing\"}",
				new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody()).build();
		Assert.assertEquals(settings.get("reason"), AbstractConfigurationValidator.INVALID_PAYLOAD_MESSAGE);

		// Wrong config keys
		response = rh.executePutRequest("/_searchguard/api/user/nagilum", "{some: \"thing\", other: \"thing\"}",
				new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody()).build();
		Assert.assertEquals(settings.get("reason"), AbstractConfigurationValidator.INVALID_CONFIGURATION_MESSAGE);
		Assert.assertTrue(settings.get(AbstractConfigurationValidator.INVALID_KEYS_KEY + ".keys").contains("some"));
		Assert.assertTrue(settings.get(AbstractConfigurationValidator.INVALID_KEYS_KEY + ".keys").contains("other"));

		// add user with correct setting. User is in role "sg_all_access"

		// check access not allowed
		checkGeneralAccess(HttpStatus.SC_UNAUTHORIZED, "nagilum", "nagilum");

		// add users
		rh.sendHTTPClientCertificate = true;
		addUserWithHash("nagilum", "$2a$12$n5nubfWATfQjSYHiWtUyeOxMIxFInUHOAx8VMmGmxFNPGpaBmeB.m",
				HttpStatus.SC_CREATED);

		// access must be allowed now
		checkGeneralAccess(HttpStatus.SC_OK, "nagilum", "nagilum");

		// try remove user, no username
		rh.sendHTTPClientCertificate = true;
		response = rh.executeDeleteRequest("/_searchguard/api/user", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());

		response = rh.executeDeleteRequest("/_searchguard/api/user/", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());

		// try remove user, nonexisting user
		response = rh.executeDeleteRequest("/_searchguard/api/user/picard", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusCode());

		// now really remove user
		deleteUser("nagilum");

		// Access must be forbidden now
		rh.sendHTTPClientCertificate = false;
		checkGeneralAccess(HttpStatus.SC_UNAUTHORIZED, "nagilum", "nagilum");

		// use password instead of hash
		rh.sendHTTPClientCertificate = true;
		addUserWithPassword("nagilum", "correctpassword", HttpStatus.SC_CREATED);

		rh.sendHTTPClientCertificate = false;
		checkGeneralAccess(HttpStatus.SC_UNAUTHORIZED, "nagilum", "wrongpassword");
		checkGeneralAccess(HttpStatus.SC_OK, "nagilum", "correctpassword");

		deleteUser("nagilum");

		// ROLES

		// create index first
		setupStarfleetIndex();

		// use backendroles when creating user. User picard does not exist in
		// the internal user DB
		// and is also not assigned to any role by username
		addUserWithPassword("picard", "picard", HttpStatus.SC_CREATED);
		checkGeneralAccess(HttpStatus.SC_OK, "picard", "picard");

		// check read access to starfleet index and ships type, must fail
		checkReadAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "ships", 0);

		// overwrite user picard, and give him role "starfleet".
		addUserWithPassword("picard", "picard", new String[] { "starfleet" }, HttpStatus.SC_OK);
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "ships", 0);
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 0);
		checkWriteAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "ships", 1);
		checkWriteAccess(HttpStatus.SC_CREATED, "picard", "picard", "sf", "public", 1);

		// overwrite user picard, and give him role "starfleet" plus "captains
		addUserWithPassword("picard", "picard", new String[] { "starfleet", "captains" }, HttpStatus.SC_OK);
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "ships", 0);
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 0);
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 1);
		checkWriteAccess(HttpStatus.SC_CREATED, "picard", "picard", "sf", "ships", 1);
		checkWriteAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 1);

		rh.sendHTTPClientCertificate = true;
		response = rh.executeGetRequest("/_searchguard/api/user/picard", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody()).build();
		settingsAsMap = settings.getAsMap();
		Assert.assertEquals(3, settingsAsMap.size());
		Assert.assertEquals("$2a$12$x55HUv6I8Tb9EcBdLkHvlua3pD9zgA0/hR6Y9YqD3o47NK35J7cs2",
				settingsAsMap.get("picard.hash"));
		Assert.assertEquals("starfleet", settingsAsMap.get("picard.roles.0"));
		Assert.assertEquals("captains", settingsAsMap.get("picard.roles.1"));

	}

}
