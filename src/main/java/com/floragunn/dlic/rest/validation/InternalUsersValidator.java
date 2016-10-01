package com.floragunn.dlic.rest.validation;

public class InternalUsersValidator extends AbstractConfigurationValidator {
	
	static {
		allowedKeys.add("hash");
		allowedKeys.add("password");
		allowedKeys.add("roles");
		mandatoryOrKeys.add("hash");
		mandatoryOrKeys.add("password");
	}
		

	
	
}
