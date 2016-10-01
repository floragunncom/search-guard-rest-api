package com.floragunn.dlic.rest.api;

import org.elasticsearch.rest.RestModule;

public class SearchGuardRestApiActions {

	public static void addActions(final RestModule module) {
		module.addRestAction(UserApiAction.class);
		module.addRestAction(RolesMappingApiAction.class);
		module.addRestAction(GetConfigurationApiAction.class);
	}
}
