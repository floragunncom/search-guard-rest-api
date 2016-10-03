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
import com.floragunn.searchguard.dlic.rest.validation.RolesMappingValidator;

public class RolesMappingApiAction extends AbstractApiAction {

	@Inject
	public RolesMappingApiAction(final Settings settings, final RestController controller, final Client client,
			final AdminDNs adminDNs, final ConfigurationLoader cl, final ClusterService cs, final AuditLog auditLog) {
		super(settings, controller, client, adminDNs, cl, cs, auditLog);
		controller.registerHandler(Method.GET, "/_searchguard/api/rolesmapping/", this);
		controller.registerHandler(Method.GET, "/_searchguard/api/rolesmapping/{name}", this);
		controller.registerHandler(Method.DELETE, "/_searchguard/api/rolesmapping/{name}", this);
		controller.registerHandler(Method.PUT, "/_searchguard/api/rolesmapping/{name}", this);
	}

	@Override
	protected AbstractConfigurationValidator getValidator(Method method, BytesReference ref) {
		return new RolesMappingValidator(method, ref);
	}

	@Override
	protected String getResourceName() {
		return "rolesmapping";
	}

	@Override
	protected String getConfigName() {
		return ConfigurationService.CONFIGNAME_ROLES_MAPPING;
	}

}
