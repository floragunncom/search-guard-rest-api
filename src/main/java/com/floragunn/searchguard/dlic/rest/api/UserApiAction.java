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
import java.util.Set;

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
import com.floragunn.searchguard.crypto.BCrypt;
import com.floragunn.searchguard.dlic.rest.validation.AbstractConfigurationValidator;
import com.floragunn.searchguard.dlic.rest.validation.InternalUsersValidator;
import com.floragunn.searchguard.ssl.transport.PrincipalExtractor;
import com.floragunn.searchguard.support.ConfigConstants;

public class UserApiAction extends AbstractApiAction {

	@Inject
	public UserApiAction(final Settings settings, final RestController controller, final Client client,
			final AdminDNs adminDNs, final IndexBaseConfigurationRepository cl, final ClusterService cs,
            final PrincipalExtractor principalExtractor) {
		super(settings, controller, client, adminDNs, cl, cs, principalExtractor);
		controller.registerHandler(Method.GET, "/_searchguard/api/user/{name}", this);
		controller.registerHandler(Method.GET, "/_searchguard/api/user/", this);
		controller.registerHandler(Method.DELETE, "/_searchguard/api/user/{name}", this);
		controller.registerHandler(Method.PUT, "/_searchguard/api/user/{name}", this);
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
		final Tuple<Long, Settings.Builder> internaluser = load(ConfigConstants.CONFIGNAME_INTERNAL_USERS);
		boolean userExisted = removeKeysStartingWith(internaluser.v2().internalMap(), username + ".");

		// add user with settings
		internaluser.v2().put(prependValueToEachKey(additionalSettings.getAsMap(), username + "."));
		save(client, request, ConfigConstants.CONFIGNAME_INTERNAL_USERS, internaluser.v2(), internaluser.v1());

		if (userExisted) {
			return successResponse("User " + username + " updated", ConfigConstants.CONFIGNAME_INTERNAL_USERS);
		} else {
			return createdResponse("User " + username + " created", ConfigConstants.CONFIGNAME_INTERNAL_USERS);
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
		return ConfigConstants.CONFIGNAME_INTERNAL_USERS;
	}

	@Override
	protected AbstractConfigurationValidator getValidator(Method method, BytesReference ref) {
		return new InternalUsersValidator(method, ref);
	}
	
	@Override
    protected void filter(Settings.Builder builder) {
        super.filter(builder);
        // replace password hashes in addition. We must not remove them from the
        // Builder since this would remove users completely if they
        // do not have any addition properties like roles or attributes
        Set<String> entries = builder.build().getAsGroups().keySet();
        for (String key : entries) {
             builder.put(key + ".hash", "");
         }      
    }
}
