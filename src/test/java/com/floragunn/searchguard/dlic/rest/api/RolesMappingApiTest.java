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

import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.dlic.rest.validation.AbstractConfigurationValidator;
import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public class RolesMappingApiTest extends AbstractRestApiUnitTest {

	@Test
	public void testRolesMappingApi() throws Exception {

		setup();

		rh.keystore = "kirk-keystore.jks";
		rh.sendHTTPClientCertificate = true;

		// check rolesmapping exists
		HttpResponse response = rh.executeGetRequest("_searchguard/api/configuration/rolesmapping");
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());

		// -- GET

		// GET sg_role_starfleet, exists
		response = rh.executeGetRequest("/_searchguard/api/rolesmapping/sg_role_starfleet", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Settings settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Map<String, String> settingsAsMap = settings.getAsMap();
		Assert.assertEquals(5, settingsAsMap.size());
		Assert.assertEquals("starfleet", settingsAsMap.get("sg_role_starfleet.backendroles.0"));
		Assert.assertEquals("captains", settingsAsMap.get("sg_role_starfleet.backendroles.1"));
		Assert.assertEquals("*.starfleetintranet.com", settingsAsMap.get("sg_role_starfleet.hosts.0"));
		Assert.assertEquals("nagilum", settingsAsMap.get("sg_role_starfleet.users.0"));

		// GET, rolesmapping does not exist
		response = rh.executeGetRequest("/_searchguard/api/rolesmapping/nothinghthere", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusCode());

		// GET, malformed URL
		response = rh.executeGetRequest("/_searchguard/api/rolesmapping/", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());

		// GET, malformed URL
		response = rh.executeGetRequest("/_searchguard/api/rolesmapping", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());

		// create index
		setupStarfleetIndex();

		// add user picard, role captains initially maps to
		// sg_role_starfleet_captains and sg_role_starfleet
		addUserWithPassword("picard", "picard", new String[] { "captains" }, HttpStatus.SC_CREATED);
		checkWriteAccess(HttpStatus.SC_CREATED, "picard", "picard", "sf", "ships", 1);
		checkWriteAccess(HttpStatus.SC_CREATED, "picard", "picard", "sf", "public", 1);

		// --- DELETE

		// Non-existing role
		rh.sendHTTPClientCertificate = true;

		response = rh.executeDeleteRequest("/_searchguard/api/rolesmapping/idonotexist", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusCode());

		// remove complete role mapping for sg_role_starfleet_captains
		response = rh.executeDeleteRequest("/_searchguard/api/rolesmapping/sg_role_starfleet_captains", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		response = rh.executeGetRequest("_searchguard/api/configuration/rolesmapping");
		rh.sendHTTPClientCertificate = false;

		// now picard is only in sg_role_starfleet, which has write access to
		// public, but not to ships
		checkWriteAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "ships", 1);
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 1);
		checkWriteAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "public", 1);

		// remove also sg_role_starfleet, poor picard has no mapping left
		rh.sendHTTPClientCertificate = true;
		response = rh.executeDeleteRequest("/_searchguard/api/rolesmapping/sg_role_starfleet", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		rh.sendHTTPClientCertificate = false;
		checkAllSfForbidden();

		rh.sendHTTPClientCertificate = true;

		// --- PUT

		// put with empty mapping, must fail
		response = rh.executePutRequest("/_searchguard/api/rolesmapping/sg_role_starfleet_captains", "", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(AbstractConfigurationValidator.ErrorType.PAYLOAD_MANDATORY.getMessage(), settings.get("reason"));

		// put new configuration with invalid payload, must fail
		response = rh.executePutRequest("/_searchguard/api/rolesmapping/sg_role_starfleet_captains",
				FileHelper.loadFile("rolesmapping_not_parseable.json"), new Header[0]);
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		Assert.assertEquals(AbstractConfigurationValidator.ErrorType.BODY_NOT_PARSEABLE.getMessage(), settings.get("reason"));

		// put new configuration with invalid keys, must fail
		response = rh.executePutRequest("/_searchguard/api/rolesmapping/sg_role_starfleet_captains",
				FileHelper.loadFile("rolesmapping_invalid_keys.json"), new Header[0]);
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		Assert.assertEquals(AbstractConfigurationValidator.ErrorType.INVALID_CONFIGURATION.getMessage(), settings.get("reason"));
		Assert.assertTrue(settings.get(AbstractConfigurationValidator.INVALID_KEYS_KEY + ".keys").contains("theusers"));
		Assert.assertTrue(
				settings.get(AbstractConfigurationValidator.INVALID_KEYS_KEY + ".keys").contains("thebackendroles"));
		Assert.assertTrue(settings.get(AbstractConfigurationValidator.INVALID_KEYS_KEY + ".keys").contains("thehosts"));

		// wrong datatypes
		response = rh.executePutRequest("/_searchguard/api/rolesmapping/sg_role_starfleet_captains",
				FileHelper.loadFile("rolesmapping_backendroles_captains_single_wrong_datatype.json"), new Header[0]);
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		Assert.assertEquals(AbstractConfigurationValidator.ErrorType.WRONG_DATATYPE.getMessage(), settings.get("reason"));
		Assert.assertTrue(settings.get("backendroles").equals("Array expected"));		
		Assert.assertTrue(settings.get("hosts") == null);
		Assert.assertTrue(settings.get("users") == null);

		response = rh.executePutRequest("/_searchguard/api/rolesmapping/sg_role_starfleet_captains",
				FileHelper.loadFile("rolesmapping_hosts_single_wrong_datatype.json"), new Header[0]);
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		Assert.assertEquals(AbstractConfigurationValidator.ErrorType.WRONG_DATATYPE.getMessage(), settings.get("reason"));
		Assert.assertTrue(settings.get("hosts").equals("Array expected"));		
		Assert.assertTrue(settings.get("backendroles") == null);
		Assert.assertTrue(settings.get("users") == null);		

		response = rh.executePutRequest("/_searchguard/api/rolesmapping/sg_role_starfleet_captains",
				FileHelper.loadFile("rolesmapping_users_picard_single_wrong_datatype.json"), new Header[0]);
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		Assert.assertEquals(AbstractConfigurationValidator.ErrorType.WRONG_DATATYPE.getMessage(), settings.get("reason"));
		Assert.assertTrue(settings.get("hosts").equals("Array expected"));		
		Assert.assertTrue(settings.get("users").equals("Array expected"));	
		Assert.assertTrue(settings.get("backendroles").equals("Array expected"));	
		
		response = rh.executePutRequest("/_searchguard/api/rolesmapping/sg_role_starfleet_captains",
				FileHelper.loadFile("rolesmapping_all_access.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusCode());

		// mapping with several backend roles, one of the is captain
		deleteAndputNewMapping("rolesmapping_backendroles_captains_list.json");
		checkAllSfAllowed();

		// mapping with one backend role, captain
		deleteAndputNewMapping("rolesmapping_backendroles_captains_single.json");
		checkAllSfAllowed();

		// mapping with several users, one is picard
		deleteAndputNewMapping("rolesmapping_users_picard_list.json");
		checkAllSfAllowed();

		// just user picard
		deleteAndputNewMapping("rolesmapping_users_picard_single.json");
		checkAllSfAllowed();

		// hosts
		deleteAndputNewMapping("rolesmapping_hosts_list.json");
		checkAllSfAllowed();

		// hosts
		deleteAndputNewMapping("rolesmapping_hosts_single.json");
		checkAllSfAllowed();

		// full settings, access
		deleteAndputNewMapping("rolesmapping_all_access.json");
		checkAllSfAllowed();

		// full settings, no access
		deleteAndputNewMapping("rolesmapping_all_noaccess.json");
		checkAllSfForbidden();

	}

	private void checkAllSfAllowed() throws Exception {
		rh.sendHTTPClientCertificate = false;
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "ships", 1);
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 1);
		checkWriteAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "ships", 1);
		checkWriteAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 1);
	}

	private void checkAllSfForbidden() throws Exception {
		rh.sendHTTPClientCertificate = false;
		checkReadAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "ships", 1);
		checkReadAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "public", 1);
		checkWriteAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "ships", 1);
		checkWriteAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "public", 1);
	}

	private HttpResponse deleteAndputNewMapping(String fileName) throws Exception {
		rh.sendHTTPClientCertificate = true;
		HttpResponse response = rh.executeDeleteRequest("/_searchguard/api/rolesmapping/sg_role_starfleet_captains",
				new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		response = rh.executePutRequest("/_searchguard/api/rolesmapping/sg_role_starfleet_captains",
				FileHelper.loadFile(fileName), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusCode());
		rh.sendHTTPClientCertificate = false;
		return response;
	}
}
