package com.floragunn.searchguard.dlic.rest.api;

import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest.Method;

import com.floragunn.searchguard.auditlog.AuditLog;
import com.floragunn.searchguard.configuration.AdminDNs;
import com.floragunn.searchguard.configuration.ConfigurationLoader;
import com.floragunn.searchguard.configuration.ConfigurationService;
import com.floragunn.searchguard.dlic.rest.validation.AbstractConfigurationValidator;
import com.floragunn.searchguard.dlic.rest.validation.RolesValidator;

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
		return ConfigurationService.CONFIGNAME_ROLES;
	}

}
