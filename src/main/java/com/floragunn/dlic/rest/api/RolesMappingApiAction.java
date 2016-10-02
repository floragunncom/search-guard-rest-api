package com.floragunn.dlic.rest.api;

import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestRequest.Method;
import org.elasticsearch.rest.RestResponse;

import com.floragunn.dlic.rest.validation.AbstractConfigurationValidator;
import com.floragunn.dlic.rest.validation.RolesMappingValidator;
import com.floragunn.searchguard.auditlog.AuditLog;
import com.floragunn.searchguard.configuration.AdminDNs;
import com.floragunn.searchguard.configuration.ConfigurationLoader;
import com.floragunn.searchguard.configuration.ConfigurationService;

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
	protected Tuple<String[], RestResponse> handleDelete(final RestRequest request, final Client client,
			final Settings.Builder additionalSettingsBuilder) throws Throwable {
		final String rolename = request.param("name");

		if (rolename == null || rolename.length() == 0) {
			return badRequestResponse("No rolename specified");
		}

		final Settings.Builder rolesmapping = load(ConfigurationService.CONFIGNAME_ROLES_MAPPING);

		boolean modified = removeKeysStartingWith(rolesmapping.internalMap(), rolename + ".");

		if (modified) {
			save(client, request, ConfigurationService.CONFIGNAME_ROLES_MAPPING, rolesmapping);
			return successResponse("Roles mapping " + rolename + " deleted.",
					ConfigurationService.CONFIGNAME_ROLES_MAPPING);
		} else {
			return notFound("Roles mapping " + rolename + " not found.");
		}
	}

	@Override
	protected Tuple<String[], RestResponse> handlePut(final RestRequest request, final Client client,
			final Settings.Builder additionalSettingsBuilder) throws Throwable {
		final String rolename = request.param("name");

		if (rolename == null || rolename.length() == 0) {
			return badRequestResponse("No rolename specified");
		}

		final Settings.Builder rolesmapping = load(ConfigurationService.CONFIGNAME_ROLES_MAPPING);
		boolean existed = removeKeysStartingWith(rolesmapping.internalMap(), rolename + ".");
		rolesmapping.put(prependValueToEachKey(additionalSettingsBuilder.build().getAsMap(), rolename + "."));
		save(client, request, ConfigurationService.CONFIGNAME_ROLES_MAPPING, rolesmapping);
		if (existed) {
			return successResponse("Roles mapping " + rolename + " replaced.",
					ConfigurationService.CONFIGNAME_ROLES_MAPPING);
		} else {
			return createdResponse("Roles mapping " + rolename + " created.",
					ConfigurationService.CONFIGNAME_ROLES_MAPPING);
		}
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
