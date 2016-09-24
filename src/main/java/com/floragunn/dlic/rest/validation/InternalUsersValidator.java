package com.floragunn.dlic.rest.validation;

import java.util.HashSet;
import java.util.Set;

import org.elasticsearch.common.settings.Settings;

public class InternalUsersValidator extends AbstractConfigurationValidator {

	private final static Set<String> allowedKeys = new HashSet<>();

	private final static Set<String> mandatoryKeys = new HashSet<>();
	
	static {
		// only has is allowed, no other settings
		allowedKeys.add("hash");
		mandatoryKeys.add("hash");
	}
	
	
	
	@Override
	public boolean validateSettings(Settings settings) {
		Set<String> requested = settings.names();
		// mandatory settings
		Set<String> mandatory = new HashSet<>(mandatoryKeys);
		mandatory.removeAll(requested);
		this.missingMandatoryKeys = mandatory;
		
		// invalid settings
		Set<String> allowed = new HashSet<>(allowedKeys);		
		requested.removeAll(allowed);
		this.invalidKeys = requested;		
		return this.isValid();
	}
	
	
}
