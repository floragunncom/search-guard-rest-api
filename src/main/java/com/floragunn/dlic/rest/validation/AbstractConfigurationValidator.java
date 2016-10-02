package com.floragunn.dlic.rest.validation;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.loader.JsonSettingsLoader;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.rest.RestRequest.Method;

import com.google.common.base.Joiner;

public abstract class AbstractConfigurationValidator {

	/* public for testing */
	public final static String INVALID_CONFIGURATION_MESSAGE = "invalid configuration";

	public final static String INVALID_PAYLOAD_MESSAGE = "Coud not parse content of request.";

	/* public for testing */
	public final static String INVALID_KEYS_KEY = "invalid_keys";

	/* public for testing */
	public final static String MISSING_MANDATORY_KEYS_KEY = "missing_mandatory_keys";

	/* public for testing */
	public final static String MISSING_MANDATORY_OR_KEYS_KEY = "specify_one_of";

	protected final ESLogger log = Loggers.getLogger(this.getClass());

	/** Define the various keys for this validator */
	protected final Set<String> allowedKeys = new HashSet<>();

	protected final Set<String> mandatoryKeys = new HashSet<>();

	protected final Set<String> mandatoryOrKeys = new HashSet<>();

	/** Contain errorneous keys */
	protected final Set<String> missingMandatoryKeys = new HashSet<>();

	protected final Set<String> invalidKeys = new HashSet<>();

	protected final Set<String> missingMandatoryOrKeys = new HashSet<>();

	private Settings.Builder settingsBuilder;

	protected final Method method;

	protected final BytesReference content;

	protected boolean contentValid = true;

	public AbstractConfigurationValidator(final Method method, final BytesReference ref) {
		this.content = ref;
		this.method = method;
	}

	public boolean validateSettings() {
		// no payload for DELETE and GET requests
		if (method.equals(Method.DELETE) || method.equals(Method.GET)) {
			return true;
		}
		try {
			this.settingsBuilder = toSettingsBuilder(content);
		} catch (ElasticsearchException e) {
			this.contentValid = false;
			return false;
		}

		Set<String> requested = settingsBuilder.build().names();

		// mandatory settings, one of
		if (Collections.disjoint(requested, mandatoryOrKeys)) {
			this.missingMandatoryOrKeys.addAll(mandatoryOrKeys);
		}

		Set<String> mandatory = new HashSet<>(mandatoryKeys);
		mandatory.removeAll(requested);
		missingMandatoryKeys.addAll(mandatory);

		// invalid settings
		Set<String> allowed = new HashSet<>(allowedKeys);
		requested.removeAll(allowed);
		this.invalidKeys.addAll(requested);
		return this.isValid();
	}

	public XContentBuilder errorsAsXContent() {
		try {
			final XContentBuilder builder = XContentFactory.jsonBuilder();
			builder.startObject();
			if (!contentValid) {
				builder.field("status", "error");
				builder.field("reason", INVALID_PAYLOAD_MESSAGE);
			} else {
				builder.field("status", "error");
				builder.field("reason", INVALID_CONFIGURATION_MESSAGE);
				addErrorMessage(builder, INVALID_KEYS_KEY, invalidKeys);
				addErrorMessage(builder, MISSING_MANDATORY_KEYS_KEY, missingMandatoryKeys);
				addErrorMessage(builder, MISSING_MANDATORY_OR_KEYS_KEY, missingMandatoryKeys);
				builder.endObject();
			}
			return builder;
		} catch (IOException ex) {
			log.error("Cannot build error settings", ex);
			return null;
		}
	}

	public Settings.Builder settingsBuilder() {
		return settingsBuilder;
	}

	public boolean isValid() {
		return missingMandatoryKeys.isEmpty() && invalidKeys.isEmpty() && missingMandatoryOrKeys.isEmpty();
	}

	private void addErrorMessage(final XContentBuilder builder, final String message, final Set<String> keys)
			throws IOException {
		if (!keys.isEmpty()) {
			builder.startObject(message);
			builder.field("keys", Joiner.on(",").join(keys.toArray(new String[0])));
			builder.endObject();
		}
	}

	private Settings.Builder toSettingsBuilder(final BytesReference ref) {
		if (ref == null || ref.length() == 0) {
			return Settings.builder();
		}

		try {
			return Settings.builder().put(new JsonSettingsLoader().load(XContentHelper.createParser(ref)));
		} catch (final IOException e) {
			throw ExceptionsHelper.convertToElastic(e);
		}
	}
}
