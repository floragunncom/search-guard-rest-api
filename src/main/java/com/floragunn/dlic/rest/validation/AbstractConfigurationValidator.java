package com.floragunn.dlic.rest.validation;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

public abstract class AbstractConfigurationValidator {

	/* public for testing */
	public final static String INVALID_CONFIGURATION_MESSAGE = "invalid configuration";
	
	/* public for testing */
	public final static String INVALID_KEYS_KEY = "invalid_keys";
	
	/* public for testing */
	public final static String MISSING_MANDATORY_KEYS_KEY = "missing_mandatory_keys";
	
	protected final ESLogger log = Loggers.getLogger(this.getClass());

	protected Set<String> missingMandatoryKeys = new HashSet<>();

	protected Set<String> invalidKeys = new HashSet<>();

	public abstract boolean validateSettings(Settings settings);

	public XContentBuilder errorsAsXContent() {
		try {
			final XContentBuilder builder = XContentFactory.jsonBuilder();
			builder.startObject();
			builder.field("status", INVALID_CONFIGURATION_MESSAGE);
			addErrorMessage(builder, INVALID_KEYS_KEY, invalidKeys);
			addErrorMessage(builder, MISSING_MANDATORY_KEYS_KEY, missingMandatoryKeys);
			return builder;
		} catch (IOException ex) {
			log.error("Cannot build error settings", ex);
			return null;
		}
	}

	public boolean isValid() {
		return missingMandatoryKeys.isEmpty() && invalidKeys.isEmpty();
	}

	private void addErrorMessage(final XContentBuilder builder, final String message, final Set<String> keys)
			throws IOException {
		if (!keys.isEmpty()) {
			builder.startObject(message);
			builder.field("keys", String.join(",", keys.toArray(new String[0])));
			builder.endObject();
		}
	}
}
