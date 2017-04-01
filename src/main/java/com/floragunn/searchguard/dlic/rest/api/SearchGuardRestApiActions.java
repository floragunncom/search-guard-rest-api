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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;

import com.floragunn.searchguard.configuration.AdminDNs;
import com.floragunn.searchguard.configuration.IndexBaseConfigurationRepository;
import com.floragunn.searchguard.ssl.transport.PrincipalExtractor;

public class SearchGuardRestApiActions {

	public static Collection<RestHandler> getHandler(Settings settings, RestController controller, Client client, 
	        AdminDNs adminDns, IndexBaseConfigurationRepository cr, ClusterService cs, PrincipalExtractor principalExtractor) {
	    final List<RestHandler> handlers = new ArrayList<RestHandler>(5);
	    handlers.add(new UserApiAction(settings, controller, client, adminDns, cr, cs, principalExtractor));
	    handlers.add(new RolesMappingApiAction(settings, controller, client, adminDns, cr, cs, principalExtractor));
	    handlers.add(new RolesApiAction(settings, controller, client, adminDns, cr, cs, principalExtractor));
	    handlers.add(new ActionGroupsApiAction(settings, controller, client, adminDns, cr, cs, principalExtractor));
	    handlers.add(new GetConfigurationApiAction(settings, controller, client, adminDns, cr, cs, principalExtractor));
	    return Collections.unmodifiableCollection(handlers);
	}
}
