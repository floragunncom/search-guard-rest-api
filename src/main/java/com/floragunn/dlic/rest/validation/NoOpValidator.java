package com.floragunn.dlic.rest.validation;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.rest.RestRequest.Method;

public class NoOpValidator extends AbstractConfigurationValidator {

	public NoOpValidator(Method method, BytesReference ref) {
		super(method, ref);
	}

}
