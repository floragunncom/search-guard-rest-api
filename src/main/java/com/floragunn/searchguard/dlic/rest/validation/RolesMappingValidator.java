package com.floragunn.searchguard.dlic.rest.validation;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.rest.RestRequest.Method;

public class RolesMappingValidator extends AbstractConfigurationValidator {

	public RolesMappingValidator(final Method method, final BytesReference ref) {
		super(method, ref);

		allowedKeys.add("backendroles");
		allowedKeys.add("hosts");
		allowedKeys.add("users");

		if (method.equals(Method.PUT) || method.equals(Method.POST)) {
			// means replace complete roles entry, we need at least one config
			// key of ...
			mandatoryOrKeys.add("backendroles");
			mandatoryOrKeys.add("hosts");
			mandatoryOrKeys.add("users");
		}
	}
}
