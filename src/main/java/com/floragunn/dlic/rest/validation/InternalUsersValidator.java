package com.floragunn.dlic.rest.validation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.elasticsearch.common.settings.Settings;

public class InternalUsersValidator extends AbstractConfigurationValidator {
	
	static {
		allowedKeys.add("hash");
		allowedKeys.add("password");
		mandatoryOrKeys.add("hash");
		mandatoryOrKeys.add("password");
	}
		

	
	
}
