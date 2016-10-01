package com.floragunn.dlic.rest.api;

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

		// no username given
		response = rh.executePutRequest("/_searchguard/api/user/", "{hash: \"123\"}", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());

		response = rh.executePutRequest("/_searchguard/api/user", "{hash: \"123\"}", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());

		// add user with wrong JSON payload and check error message
		response = rh.executePutRequest("/_searchguard/api/user/nagilum", "{some: \"thing\", other: \"thing\"}",
				new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody()).build();
		Assert.assertEquals(settings.get("status"), AbstractConfigurationValidator.INVALID_CONFIGURATION_MESSAGE);
		Assert.assertTrue(settings.get(AbstractConfigurationValidator.INVALID_KEYS_KEY + ".keys").contains("some"));
		Assert.assertTrue(settings.get(AbstractConfigurationValidator.INVALID_KEYS_KEY + ".keys").contains("other"));

		// add user with correct setting. User is in role "sg_all_access"
		// password: nagilum

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

		rh.sendHTTPClientCertificate = true;
		rh.executePutRequest("sf", null, new Header[0]);
		rh.executePutRequest("sf/ships/0", "{\"number\" : \"NCC-1701-D\"}", new Header[0]);
		rh.executePutRequest("sf/public/0", "{\"some\" : \"value\"}", new Header[0]);
		rh.sendHTTPClientCertificate = false;

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

	}

}
