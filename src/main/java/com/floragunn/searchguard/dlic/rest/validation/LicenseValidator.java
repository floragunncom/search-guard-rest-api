package com.floragunn.searchguard.dlic.rest.validation;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.rest.RestRequest.Method;

public class LicenseValidator extends AbstractConfigurationValidator {

	public LicenseValidator(Method method, BytesReference ref) {
		super(method, ref);
		this.payloadMandatory = true;
		allowedKeys.put("sg_license", DataType.STRING);
	}

}

