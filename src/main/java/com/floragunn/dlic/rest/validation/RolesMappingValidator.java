package com.floragunn.dlic.rest.validation;

import org.elasticsearch.rest.RestRequest.Method;

public class RolesMappingValidator extends AbstractConfigurationValidator {

	static {
		allowedKeys.add("backendroles");
		allowedKeys.add("hosts");
		allowedKeys.add("users");
	}

	public RolesMappingValidator() {
		// nothing to do
	}
	
	public RolesMappingValidator(Method method) {		
		if (method.equals(Method.PUT)) {
			// means replace complete roles entry, we need at least one config key of ...
			mandatoryKeys.add("backendroles");
			mandatoryKeys.add("hosts");
			mandatoryKeys.add("users");
		}
	}
}
