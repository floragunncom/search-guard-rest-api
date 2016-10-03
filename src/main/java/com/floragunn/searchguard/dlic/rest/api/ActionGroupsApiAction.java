package com.floragunn.searchguard.dlic.rest.api;

import java.util.Map;

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

import com.floragunn.searchguard.auditlog.AuditLog;
import com.floragunn.searchguard.configuration.AdminDNs;
import com.floragunn.searchguard.configuration.ConfigurationLoader;
import com.floragunn.searchguard.configuration.ConfigurationService;
import com.floragunn.searchguard.dlic.rest.validation.AbstractConfigurationValidator;
import com.floragunn.searchguard.dlic.rest.validation.ActionGroupValidator;

public class ActionGroupsApiAction extends AbstractApiAction {

	@Inject
	public ActionGroupsApiAction(final Settings settings, final RestController controller, final Client client,
			final AdminDNs adminDNs, final ConfigurationLoader cl, final ClusterService cs, final AuditLog auditLog) {
		super(settings, controller, client, adminDNs, cl, cs, auditLog);
		controller.registerHandler(Method.GET, "/_searchguard/api/actiongroup/{name}", this);
		controller.registerHandler(Method.GET, "/_searchguard/api/actiongroup/", this);
		controller.registerHandler(Method.DELETE, "/_searchguard/api/actiongroup/{name}", this);
		controller.registerHandler(Method.PUT, "/_searchguard/api/actiongroup/{name}", this);
	}

	@Override // need to overwrite for we have no key to use
	protected Tuple<String[], RestResponse> handlePut(final RestRequest request, final Client client,
			final Settings.Builder additionalSettingsBuilder) throws Throwable {
		final String name = request.param("name");

		if (name == null || name.length() == 0) {
			return badRequestResponse("No " + getResourceName() + " specified");
		}

		final Settings.Builder existing = load(getConfigName());
		// remove all existing entries
		boolean existed = removeKeysStartingWith(existing.internalMap(), name + ".");
		// remove bogus "permissions" from the JSON payload
		Map<String, String> newSettings = additionalSettingsBuilder.build().getAsMap();
		newSettings = removeLeadingValueFromEachKey(newSettings, "permissions");
		newSettings = prependValueToEachKey(newSettings, name);
		existing.put(newSettings);

		save(client, request, getConfigName(), existing);
		if (existed) {
			return successResponse(getResourceName() + " " + name + " replaced.", getConfigName());
		} else {
			return createdResponse(getResourceName() + " " + name + " created.", getConfigName());
		}
	}

	@Override
	protected AbstractConfigurationValidator getValidator(Method method, BytesReference ref) {
		return new ActionGroupValidator(method, ref);
	}

	@Override
	protected String getResourceName() {
		return "actiongroup";
	}

	@Override
	protected String getConfigName() {
		return ConfigurationService.CONFIGNAME_ACTION_GROUPS;
	}

}
