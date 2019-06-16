/*
 * Copyright 2016 by floragunn UG (haftungsbeschränkt) - All rights reserved
 * 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed here is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * 
 * This software is free of charge for non-commercial and academic use. 
 * For commercial use in a production environment you have to obtain a license 
 * from https://floragunn.com
 * 
 */

package com.floragunn.searchguard.dlic.rest.validation;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.rest.RestRequest.Method;

public class InternalUsersValidator extends AbstractConfigurationValidator {

	public InternalUsersValidator(final Method method, BytesReference ref) {
		super(method, ref);
		this.payloadMandatory = true;
		
		if(method == Method.POST) {
		    allowedKeys.put("hash", DataType.STRING);
	        allowedKeys.put("roles", DataType.ARRAY);
	        allowedKeys.put("username", DataType.STRING);
		} else {
		    allowedKeys.put("hash", DataType.STRING);
	        allowedKeys.put("password", DataType.STRING);
	        allowedKeys.put("roles", DataType.ARRAY);
	        allowedKeys.put("username", DataType.STRING);
	        mandatoryOrKeys.add("hash");
	        mandatoryOrKeys.add("password");
		}
		
		
	}

}
