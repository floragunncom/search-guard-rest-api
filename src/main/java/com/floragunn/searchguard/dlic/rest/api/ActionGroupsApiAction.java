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

import java.nio.file.Path;
import java.util.Map;

import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestRequest.Method;
import org.elasticsearch.rest.RestResponse;

import com.floragunn.searchguard.configuration.AdminDNs;
import com.floragunn.searchguard.configuration.IndexBaseConfigurationRepository;
import com.floragunn.searchguard.dlic.rest.validation.AbstractConfigurationValidator;
import com.floragunn.searchguard.dlic.rest.validation.ActionGroupValidator;
import com.floragunn.searchguard.ssl.transport.PrincipalExtractor;
import com.floragunn.searchguard.support.ConfigConstants;

public class ActionGroupsApiAction extends AbstractApiAction {

	@Inject
	public ActionGroupsApiAction(final Settings settings, final Path configPath, final RestController controller, final Client client,
			final AdminDNs adminDNs, final IndexBaseConfigurationRepository cl, final ClusterService cs,
            final PrincipalExtractor principalExtractor) {
		super(settings, configPath, controller, client, adminDNs, cl, cs, principalExtractor);
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
		Map<String, String> removedEntries = removeKeysStartingWith(existing.internalMap(), name + ".");
		boolean existed = !removedEntries.isEmpty();
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
		return ConfigConstants.CONFIGNAME_ACTION_GROUPS;
	}

	@Override
	protected void consumeParameters(final RestRequest request) {
		request.param("name");		
	}

}
