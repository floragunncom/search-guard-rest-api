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

import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.Settings.Builder;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestRequest.Method;
import org.elasticsearch.rest.RestResponse;

import com.floragunn.searchguard.configuration.AdminDNs;
import com.floragunn.searchguard.configuration.IndexBaseConfigurationRepository;
import com.floragunn.searchguard.dlic.rest.validation.AbstractConfigurationValidator;
import com.floragunn.searchguard.dlic.rest.validation.RolesMappingValidator;
import com.floragunn.searchguard.ssl.transport.PrincipalExtractor;
import com.floragunn.searchguard.support.ConfigConstants;

public class RolesMappingApiAction extends AbstractApiAction {

	@Inject
	public RolesMappingApiAction(final Settings settings, final RestController controller, final Client client,
			final AdminDNs adminDNs, final IndexBaseConfigurationRepository cl, final ClusterService cs,
            final PrincipalExtractor principalExtractor) {
		super(settings, controller, client, adminDNs, cl, cs, principalExtractor);
		controller.registerHandler(Method.GET, "/_searchguard/api/rolesmapping/", this);
		controller.registerHandler(Method.GET, "/_searchguard/api/rolesmapping/{name}", this);
		controller.registerHandler(Method.DELETE, "/_searchguard/api/rolesmapping/{name}", this);
		controller.registerHandler(Method.PUT, "/_searchguard/api/rolesmapping/{name}", this);
		controller.registerHandler(Method.POST, "/_searchguard/api/rolesmapping/", this);
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
		return ConfigConstants.CONFIGNAME_ROLES_MAPPING;
	}

    @Override
    protected Tuple<String[], RestResponse> handlePost(RestRequest request, Client client, Builder additionalSettings) throws Throwable {
        return handlePostAsPatch(request, client, additionalSettings);
    }
}
