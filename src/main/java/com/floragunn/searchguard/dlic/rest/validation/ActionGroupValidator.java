package com.floragunn.searchguard.dlic.rest.validation;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.rest.RestRequest.Method;

public class ActionGroupValidator extends AbstractConfigurationValidator {

	public ActionGroupValidator(Method method, BytesReference ref) {
		super(method, ref);
		this.payloadMandatory = true;
		allowedKeys.add("permissions");
	}

}
