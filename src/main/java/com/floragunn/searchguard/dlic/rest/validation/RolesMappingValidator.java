package com.floragunn.searchguard.dlic.rest.validation;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.rest.RestRequest.Method;

public class RolesMappingValidator extends AbstractConfigurationValidator {

	public RolesMappingValidator(final Method method, final BytesReference ref) {
		super(method, ref);
		this.payloadMandatory = true;
		allowedKeys.add("backendroles");
		allowedKeys.add("hosts");
		allowedKeys.add("users");

		mandatoryOrKeys.add("backendroles");
		mandatoryOrKeys.add("hosts");
		mandatoryOrKeys.add("users");
	}
}
