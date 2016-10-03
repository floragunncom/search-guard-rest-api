package com.floragunn.dlic.rest.api;

import org.elasticsearch.rest.RestModule;

import com.floragunn.searchguard.dlic.rest.api.ActionGroupsApiAction;
import com.floragunn.searchguard.dlic.rest.api.GetConfigurationApiAction;
import com.floragunn.searchguard.dlic.rest.api.RolesMappingApiAction;
import com.floragunn.searchguard.dlic.rest.api.UserApiAction;

public class SearchGuardRestApiActions {

	public static void addActions(final RestModule module) {
		module.addRestAction(UserApiAction.class);
		module.addRestAction(RolesMappingApiAction.class);
		module.addRestAction(ActionGroupsApiAction.class);
		module.addRestAction(GetConfigurationApiAction.class);
	}
}
