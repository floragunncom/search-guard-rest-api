package com.floragunn.searchguard.dlic.rest.api;

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

import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest.Method;

import com.floragunn.searchguard.auditlog.AuditLog;
import com.floragunn.searchguard.configuration.AdminDNs;
import com.floragunn.searchguard.configuration.ConfigurationLoader;
import com.floragunn.searchguard.dlic.rest.validation.AbstractConfigurationValidator;
import com.floragunn.searchguard.dlic.rest.validation.RolesValidator;
import com.floragunn.searchguard.support.ConfigConstants;

public class RolesApiAction extends AbstractApiAction {

	@Inject
	public RolesApiAction(Settings settings, RestController controller, Client client, AdminDNs adminDNs, ConfigurationLoader cl,
			ClusterService cs, AuditLog auditLog) {
		super(settings, controller, client, adminDNs, cl, cs, auditLog);
		controller.registerHandler(Method.GET, "/_searchguard/api/roles/", this);
		controller.registerHandler(Method.GET, "/_searchguard/api/roles/{name}", this);
		controller.registerHandler(Method.DELETE, "/_searchguard/api/roles/{name}", this);
		controller.registerHandler(Method.PUT, "/_searchguard/api/roles/{name}", this);
	}

	@Override
	protected AbstractConfigurationValidator getValidator(Method method, BytesReference ref) {
		return new RolesValidator(method, ref);
	}

	@Override
	protected String getResourceName() {
		return "role";
	}

	@Override
	protected String getConfigName() {
		return ConfigConstants.CONFIGNAME_ROLES;
	}

}
