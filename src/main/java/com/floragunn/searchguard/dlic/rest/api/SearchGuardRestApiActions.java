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

import org.elasticsearch.rest.RestHandler;

public class SearchGuardRestApiActions {

	public static Collection<Class<? extends RestHandler>> getHandler() {
	    List<Class<? extends RestHandler>> handlers = new ArrayList<Class<? extends RestHandler>>(5);
	    handlers.add(UserApiAction.class);
	    handlers.add(RolesMappingApiAction.class);
	    handlers.add(RolesApiAction.class);
	    handlers.add(ActionGroupsApiAction.class);
	    handlers.add(GetConfigurationApiAction.class);
	    return Collections.unmodifiableCollection(handlers);
	}
}
