package com.floragunn.searchguard.dlic.rest.api;

import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.elasticsearch.common.settings.Settings;
import org.junit.Assert;
import org.junit.Test;

import com.floragunn.searchguard.dlic.rest.validation.AbstractConfigurationValidator.ErrorType;
import com.floragunn.searchguard.test.helper.cluster.ClusterConfiguration;
import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;


public class IndexMissingTest extends AbstractRestApiUnitTest {
	
	
	// don't setup index for this test
	protected void setup(ClusterConfiguration configuration) throws Exception {
		final Settings nodeSettings = defaultNodeSettings(true);
		log.debug("Starting nodes");
		this.ci = ch.startCluster(nodeSettings, configuration);
		log.debug("Started nodes");
		RestHelper rh = new RestHelper(ci);
		this.rh = rh;
	}

	@Test
	public void testGetConfiguration() throws Exception {
		setup(ClusterConfiguration.SINGLENODE);

		// test with no SG index at all
		testHttpOperations();
		
	}
	
	protected void testHttpOperations() throws Exception {
		rh.keystore = "kirk-keystore.jks";
		rh.sendHTTPClientCertificate = true;

		// GET configuration
		HttpResponse response = rh.executeGetRequest("_searchguard/api/configuration/roles");
		Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
		Settings settings = Settings.builder().loadFromSource(response.getBody()).build();
		Map<String, String> settingsAsMap = settings.getAsMap();
		Assert.assertEquals(ErrorType.SG_NOT_INITIALIZED.getMessage(), settingsAsMap.get("message"));
	
		// GET roles
		response = rh.executeGetRequest("/_searchguard/api/roles/sg_role_starfleet", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody()).build();
		settingsAsMap = settings.getAsMap();
		Assert.assertEquals(ErrorType.SG_NOT_INITIALIZED.getMessage(), settingsAsMap.get("message"));

		// GET rolesmapping
		response = rh.executeGetRequest("/_searchguard/api/rolesmapping/sg_role_starfleet", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody()).build();
		settingsAsMap = settings.getAsMap();
		Assert.assertEquals(ErrorType.SG_NOT_INITIALIZED.getMessage(), settingsAsMap.get("message"));

		
		// GET actiongroups
		response = rh.executeGetRequest("_searchguard/api/actiongroup/READ");
		Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody()).build();
		settingsAsMap = settings.getAsMap();
		Assert.assertEquals(ErrorType.SG_NOT_INITIALIZED.getMessage(), settingsAsMap.get("message"));

		// GET internalusers
		response = rh.executeGetRequest("_searchguard/api/user/picard");
		Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody()).build();
		settingsAsMap = settings.getAsMap();
		Assert.assertEquals(ErrorType.SG_NOT_INITIALIZED.getMessage(), settingsAsMap.get("message"));

		
		// PUT request
		response = rh.executePutRequest("/_searchguard/api/actiongroup/READ", FileHelper.loadFile("actiongroup_read.json"), new Header[0]);
		Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
		
		// DELETE request
		response = rh.executeDeleteRequest("/_searchguard/api/roles/sg_role_starfleet", new Header[0]);
		Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
		
		// setup index now
		setupAndInitializeSearchGuardIndex();
		
		// GET configuration
		response = rh.executeGetRequest("_searchguard/api/configuration/roles");
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
		settings = Settings.builder().loadFromSource(response.getBody()).build();
		settingsAsMap = settings.getAsMap();
		Assert.assertEquals("CLUSTER_ALL", settingsAsMap.get("sg_admin.cluster.0"));

	}
}
