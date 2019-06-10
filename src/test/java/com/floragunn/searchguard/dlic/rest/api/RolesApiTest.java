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
import com.floragunn.searchguard.dlic.rest.validation.AbstractConfigurationValidator.ErrorType;
import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public class RolesApiTest extends AbstractRestApiUnitTest {

    @Test
    public void testPatchApi() throws Exception {
        setup();
        
        rh.keystore = "kirk-keystore.jks";
        rh.sendHTTPClientCertificate = true;

        // check roles exists
        HttpResponse response = rh.executeGetRequest("_searchguard/api/configuration/roles");
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
        Assert.assertFalse(response.getBody().contains("newrole"));
        
        response = rh.executePostRequest("_searchguard/api/roles", "[{ \"op\": \"add\", \"path\": \"/newrole\", \"value\": { \"cluster\": [\"bar\"] } }]");
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
        
        response = rh.executeGetRequest("_searchguard/api/configuration/roles");
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
        Assert.assertTrue(response.getBody().contains("newrole"));
    }
    
	@Test
	public void testRolesApi() throws Exception {

		setup();

		rh.keystore = "kirk-keystore.jks";
		rh.sendHTTPClientCertificate = true;

		// check roles exists
		HttpResponse response = rh.executeGetRequest("_searchguard/api/configuration/roles");
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());

		// -- GET

		// GET sg_all_access, exists
		response = rh.executeGetRequest("/_searchguard/api/roles/sg_role_starfleet", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		Settings settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Map<String, String> settingsAsMap = settings.getAsMap();
		Assert.assertEquals(10, settingsAsMap.size());

		// GET, role does not exist
		response = rh.executeGetRequest("/_searchguard/api/roles/nothinghthere", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusCode());

		// GET, malformed URL
		response = rh.executeGetRequest("/_searchguard/api/roles/", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());

		// GET, malformed URL
		response = rh.executeGetRequest("/_searchguard/api/roles", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());

		// create index
		setupStarfleetIndex();

		// add user picard, role starfleet, maps to sg_role_starfleet
		addUserWithPassword("picard", "picard", new String[] { "starfleet", "captains" }, HttpStatus.SC_CREATED);
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "ships", 0);
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 0);
		checkWriteAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "ships", 0);
		checkWriteAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 0);

		// -- DELETE

		rh.sendHTTPClientCertificate = true;

		// Non-existing role
		response = rh.executeDeleteRequest("/_searchguard/api/roles/idonotexist", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusCode());

		// remove complete role mapping for sg_role_starfleet_captains
		response = rh.executeDeleteRequest("/_searchguard/api/roles/sg_role_starfleet_captains", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());

		rh.sendHTTPClientCertificate = false;
		// only starfleet role left, write access to ships is forbidden now
		checkWriteAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "ships", 1);
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 0);
		checkWriteAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "public", 0);

		rh.sendHTTPClientCertificate = true;
		// remove also starfleet role, nothing is allowed anymore
		response = rh.executeDeleteRequest("/_searchguard/api/roles/sg_role_starfleet", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		checkReadAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "ships", 0);
		checkReadAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "public", 0);
		checkWriteAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "ships", 0);
		checkWriteAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "public", 0);

		// -- PUT
		// put with empty roles, must fail
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet", "", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(AbstractConfigurationValidator.ErrorType.PAYLOAD_MANDATORY.getMessage(), settings.get("reason"));

		// put new configuration with invalid payload, must fail
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet",
				FileHelper.loadFile("roles_not_parseable.json"), new Header[0]);
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		Assert.assertEquals(AbstractConfigurationValidator.ErrorType.BODY_NOT_PARSEABLE.getMessage(), settings.get("reason"));

		// put new configuration with invalid keys, must fail
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet",
				FileHelper.loadFile("roles_invalid_keys.json"), new Header[0]);
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		Assert.assertEquals(AbstractConfigurationValidator.ErrorType.INVALID_CONFIGURATION.getMessage(), settings.get("reason"));
		Assert.assertTrue(settings.get(AbstractConfigurationValidator.INVALID_KEYS_KEY + ".keys").contains("indizes"));
		Assert.assertTrue(
				settings.get(AbstractConfigurationValidator.INVALID_KEYS_KEY + ".keys").contains("kluster"));

		// put new configuration with wrong datatypes, must fail
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet",
				FileHelper.loadFile("roles_wrong_datatype.json"), new Header[0]);
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		Assert.assertEquals(AbstractConfigurationValidator.ErrorType.WRONG_DATATYPE.getMessage(), settings.get("reason"));
		Assert.assertTrue(settings.get("indices").equals("Object expected"));
		Assert.assertTrue(settings.get("cluster").equals("Array expected"));


		
		// restore starfleet role
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet",
				FileHelper.loadFile("roles_starfleet.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusCode());
		rh.sendHTTPClientCertificate = false;
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "ships", 0);
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 0);
		checkWriteAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "ships", 0);
		checkWriteAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "public", 0);

		rh.sendHTTPClientCertificate = true;

		// restore captains role
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet_captains",
				FileHelper.loadFile("roles_captains.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusCode());
		rh.sendHTTPClientCertificate = false;
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "ships", 0);
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 0);
		checkWriteAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "ships", 0);
		checkWriteAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 0);

		rh.sendHTTPClientCertificate = true;
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet_captains",
				FileHelper.loadFile("roles_multiple.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());

		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet_captains",
				FileHelper.loadFile("roles_multiple_2.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		
		// check tenants
		rh.sendHTTPClientCertificate = true;
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet_captains",
				FileHelper.loadFile("roles_captains_tenants.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		settingsAsMap = settings.getAsMap();
		Assert.assertEquals(2, settingsAsMap.size());
		Assert.assertEquals(settingsAsMap.get("status"), "OK");
		
		
		response = rh.executeGetRequest("/_searchguard/api/roles/sg_role_starfleet_captains", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		settingsAsMap = settings.getAsMap();
		Assert.assertEquals(5, settingsAsMap.size());
		Assert.assertEquals(settingsAsMap.get("sg_role_starfleet_captains.tenants.tenant1"), "RO");
		Assert.assertEquals(settingsAsMap.get("sg_role_starfleet_captains.tenants.tenant2"), "RW");

		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet_captains",
				FileHelper.loadFile("roles_captains_tenants2.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		settingsAsMap = settings.getAsMap();
		Assert.assertEquals(2, settingsAsMap.size());
		Assert.assertEquals(settingsAsMap.get("status"), "OK");

		response = rh.executeGetRequest("/_searchguard/api/roles/sg_role_starfleet_captains", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		settingsAsMap = settings.getAsMap();
		Assert.assertEquals(7, settingsAsMap.size());
		Assert.assertEquals(settingsAsMap.get("sg_role_starfleet_captains.tenants.tenant1"), "RO");
		Assert.assertEquals(settingsAsMap.get("sg_role_starfleet_captains.tenants.tenant2"), "RW");
		Assert.assertEquals(settingsAsMap.get("sg_role_starfleet_captains.tenants.tenant3"), "RO");
		Assert.assertEquals(settingsAsMap.get("sg_role_starfleet_captains.tenants.tenant4"), "RW");
		
		// remove tenants from role
		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet_captains",
				FileHelper.loadFile("roles_captains_no_tenants.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		settingsAsMap = settings.getAsMap();
		Assert.assertEquals(2, settingsAsMap.size());
		Assert.assertEquals(settingsAsMap.get("status"), "OK");

		response = rh.executeGetRequest("/_searchguard/api/roles/sg_role_starfleet_captains", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		settingsAsMap = settings.getAsMap();
		Assert.assertEquals(3, settingsAsMap.size());	
		Assert.assertNull(settingsAsMap.get("sg_role_starfleet_captains.tenants.tenant1"));
		Assert.assertNull(settingsAsMap.get("sg_role_starfleet_captains.tenants.tenant2"));
		Assert.assertNull(settingsAsMap.get("sg_role_starfleet_captains.tenants.tenant3"));
		Assert.assertNull(settingsAsMap.get("sg_role_starfleet_captains.tenants.tenant4"));

		response = rh.executePutRequest("/_searchguard/api/roles/sg_role_starfleet_captains",
				FileHelper.loadFile("roles_captains_tenants_malformed.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody(), XContentType.JSON).build();
		settingsAsMap = settings.getAsMap();
		Assert.assertEquals(settingsAsMap.get("status"), "error");
		Assert.assertEquals(settingsAsMap.get("reason"), ErrorType.INVALID_CONFIGURATION.getMessage());
	}
}
