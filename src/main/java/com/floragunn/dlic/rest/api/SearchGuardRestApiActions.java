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

package com.floragunn.dlic.rest.api;

import org.elasticsearch.rest.RestModule;

import com.floragunn.searchguard.dlic.rest.api.ActionGroupsApiAction;
import com.floragunn.searchguard.dlic.rest.api.GetConfigurationApiAction;
import com.floragunn.searchguard.dlic.rest.api.RolesApiAction;
import com.floragunn.searchguard.dlic.rest.api.RolesMappingApiAction;
import com.floragunn.searchguard.dlic.rest.api.UserApiAction;

public class SearchGuardRestApiActions {

	public static void addActions(final RestModule module) {
		module.addRestAction(UserApiAction.class);
		module.addRestAction(RolesMappingApiAction.class);
		module.addRestAction(RolesApiAction.class);
		module.addRestAction(ActionGroupsApiAction.class);
		module.addRestAction(GetConfigurationApiAction.class);
	}
}
