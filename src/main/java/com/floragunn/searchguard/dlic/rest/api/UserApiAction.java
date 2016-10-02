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

import java.util.Objects;

import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestRequest.Method;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;

import com.floragunn.searchguard.auditlog.AuditLog;
import com.floragunn.searchguard.configuration.AdminDNs;
import com.floragunn.searchguard.configuration.ConfigurationLoader;
import com.floragunn.searchguard.configuration.ConfigurationService;
import com.floragunn.searchguard.crypto.BCrypt;
import com.floragunn.searchguard.dlic.rest.validation.AbstractConfigurationValidator;
import com.floragunn.searchguard.dlic.rest.validation.InternalUsersValidator;

public class UserApiAction extends AbstractApiAction {

	@Inject
	public UserApiAction(final Settings settings, final RestController controller, final Client client,
			final AdminDNs adminDNs, final ConfigurationLoader cl, final ClusterService cs, final AuditLog auditLog) {
		super(settings, controller, client, adminDNs, cl, cs, auditLog);
		controller.registerHandler(Method.GET, "/_searchguard/api/user/{name}", this);
		controller.registerHandler(Method.GET, "/_searchguard/api/user/", this);
		controller.registerHandler(Method.DELETE, "/_searchguard/api/user/{name}", this);
		controller.registerHandler(Method.PUT, "/_searchguard/api/user/{name}", this);
	}

	@Override
	protected Tuple<String[], RestResponse> handleDelete(final RestRequest request, final Client client,
			final Settings.Builder additionalSettingsBuilder) throws Throwable {
		final String username = request.param("name");

		if (username == null || username.length() == 0) {
			return badRequestResponse("No username given");
		}

		final Settings.Builder internaluser = load(ConfigurationService.CONFIGNAME_INTERNAL_USERS);

		if (removeKeysStartingWith(internaluser.internalMap(), username + ".")) {
			save(client, request, "internalusers", internaluser);
			return new Tuple<String[], RestResponse>(new String[] { ConfigurationService.CONFIGNAME_INTERNAL_USERS },
					new BytesRestResponse(RestStatus.OK));
		}

		return new Tuple<String[], RestResponse>(new String[0], new BytesRestResponse(RestStatus.NOT_FOUND));
	}

	@Override
	protected Tuple<String[], RestResponse> handlePut(final RestRequest request, final Client client,
			final Settings.Builder additionalSettingsBuilder) throws Throwable {
		final String username = request.param("name");

		if (username == null || username.length() == 0) {
			return badRequestResponse("No name given");
		}

		// if password is set, it takes precedence over hash
		String plainTextPassword = additionalSettingsBuilder.get("password");
		if (plainTextPassword != null && plainTextPassword.length() > 0) {
			additionalSettingsBuilder.remove("password");
			additionalSettingsBuilder.put("hash", hash(plainTextPassword.getBytes("UTF-8")));
		}

		final Settings additionalSettings = additionalSettingsBuilder.build();

		// first, remove any existing user
		final Settings.Builder internaluser = load(ConfigurationService.CONFIGNAME_INTERNAL_USERS);
		boolean userExisted = removeKeysStartingWith(internaluser.internalMap(), username + ".");

		// add user with settings
		internaluser.put(prependValueToEachKey(additionalSettings.getAsMap(), username + "."));
		save(client, request, ConfigurationService.CONFIGNAME_INTERNAL_USERS, internaluser);

		if (userExisted) {
			return successResponse("User " + username + " updated", ConfigurationService.CONFIGNAME_INTERNAL_USERS);
		} else {
			return createdResponse("User " + username + " created", ConfigurationService.CONFIGNAME_INTERNAL_USERS);
		}

	}

	public static String hash(final byte[] clearTextPassword) {
		return BCrypt.hashpw(Objects.requireNonNull(clearTextPassword), BCrypt.gensalt(12));
	}

	@Override
	protected String getResourceName() {
		return "user";
	}

	@Override
	protected String getConfigName() {
		return ConfigurationService.CONFIGNAME_INTERNAL_USERS;
	}

	@Override
	protected AbstractConfigurationValidator getValidator(Method method, BytesReference ref) {
		return new InternalUsersValidator(method, ref);
	}
}
