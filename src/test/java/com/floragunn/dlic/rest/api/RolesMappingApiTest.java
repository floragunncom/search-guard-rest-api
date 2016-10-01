package com.floragunn.dlic.rest.api;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Test;

import test.helper.rest.RestHelper.HttpResponse;

public class RolesMappingApiTest extends AbstractRestApiUnitTest {

	@Test
	public void testRolesMappingApi() throws Exception {

		setup();

		rh.keystore = "kirk-keystore.jks";
		rh.sendHTTPClientCertificate = true;

		// check rolesmapping exists
		HttpResponse response = rh.executeGetRequest("_searchguard/api/configuration/rolesmapping");
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());

		// create index
		setupStarfleetIndex();

		// add user picard, role captains initially maps to
		// sg_role_starfleet_captains and sg_role_starfleet
		addUserWithPassword("picard", "picard", new String[] { "captains" }, HttpStatus.SC_CREATED);
		checkWriteAccess(HttpStatus.SC_CREATED, "picard", "picard", "sf", "ships", 1);
		checkWriteAccess(HttpStatus.SC_CREATED, "picard", "picard", "sf", "public", 1);

		// remove complete role mapping for sg_role_starfleet_captains
		rh.sendHTTPClientCertificate = true;
		response = rh.executeDeleteRequest("/_searchguard/api/role/sg_role_starfleet_captains", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		response = rh.executeGetRequest("_searchguard/api/configuration/rolesmapping");
		rh.sendHTTPClientCertificate = false;

		// now picard is only in sg_role_starfleet, which has write access to
		// public, but not to ships
		checkWriteAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "ships", 1);
		checkReadAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 1);
		checkWriteAccess(HttpStatus.SC_OK, "picard", "picard", "sf", "public", 1);

		// remove also sg_role_starfleet, poor picard has no mapping left
		rh.sendHTTPClientCertificate = true;
		response = rh.executeDeleteRequest("/_searchguard/api/role/sg_role_starfleet", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		rh.sendHTTPClientCertificate = false;
		checkReadAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "ships", 1);
		checkReadAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "public", 1);
		checkWriteAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "ships", 1);
		checkWriteAccess(HttpStatus.SC_FORBIDDEN, "picard", "picard", "sf", "public", 1);

	}
}
