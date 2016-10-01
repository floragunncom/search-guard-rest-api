package com.floragunn.dlic.rest.validation;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.rest.RestRequest.Method;

public class InternalUsersValidator extends AbstractConfigurationValidator {
	
	static {
		allowedKeys.add("hash");
		allowedKeys.add("password");
		allowedKeys.add("roles");
		mandatoryOrKeys.add("hash");
		mandatoryOrKeys.add("password");
	}
		
	public InternalUsersValidator(final Method method, BytesReference ref) {
		super(method, ref);
	}	
	
}
