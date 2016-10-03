package com.floragunn.searchguard.dlic.rest.validation;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.rest.RestRequest.Method;

public class RolesValidator extends AbstractConfigurationValidator {

	public RolesValidator(final Method method, final BytesReference ref) {
		super(method, ref);
		this.payloadMandatory = true;
		allowedKeys.add("indices");
		allowedKeys.add("cluster");

		mandatoryOrKeys.add("indices");
		mandatoryOrKeys.add("cluster");
	}
}
