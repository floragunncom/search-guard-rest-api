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

	/** The error type */
	protected ErrorType errorType = ErrorType.NONE;

	/** Behaviour regarding payload */
	protected boolean payloadMandatory = false;

	protected boolean payloadAllowed = true;

	private Settings.Builder settingsBuilder;

	protected final Method method;

	protected final BytesReference content;

	public AbstractConfigurationValidator(final Method method, final BytesReference ref) {
		this.content = ref;
		this.method = method;
	}

	public boolean validateSettings() {
		// no payload for DELETE and GET requests
		if (method.equals(Method.DELETE) || method.equals(Method.GET)) {
			return true;
		}
		// try to parse payload
		try {
			this.settingsBuilder = toSettingsBuilder(content);
		} catch (ElasticsearchException e) {
			this.errorType = ErrorType.BODY_NOT_PARSEABLE;
			return false;
		}

		Set<String> requested = settingsBuilder.build().names();
		// check if payload is accepted at all
		if (!this.payloadAllowed && !requested.isEmpty()) {
			this.errorType = ErrorType.PAYLOAD_NOT_ALLOWED;
			return false;
		}
		// check if payload is mandatory
		if (this.payloadMandatory && requested.isEmpty()) {
			this.errorType = ErrorType.PAYLOAD_MANDATORY;
			return false;
		}
		// payload ok, check allowed, mandatory and mandatory OR keys

		// mandatory settings, one of ...
		if (Collections.disjoint(requested, mandatoryOrKeys)) {
			this.missingMandatoryOrKeys.addAll(mandatoryOrKeys);
		}

		// mandatory settings
		Set<String> mandatory = new HashSet<>(mandatoryKeys);
		mandatory.removeAll(requested);
		missingMandatoryKeys.addAll(mandatory);

		// invalid settings
		Set<String> allowed = new HashSet<>(allowedKeys);
		requested.removeAll(allowed);
		this.invalidKeys.addAll(requested);
		boolean valid = missingMandatoryKeys.isEmpty() && invalidKeys.isEmpty() && missingMandatoryOrKeys.isEmpty();
		if (!valid) {
			this.errorType = ErrorType.INVALID_CONFIGURATION;
		}
		return valid;
	}

	public XContentBuilder errorsAsXContent() {
		try {
			final XContentBuilder builder = XContentFactory.jsonBuilder();
			builder.startObject();
			switch (this.errorType) {
			case NONE:
				return null;
			case INVALID_CONFIGURATION:
				builder.field("status", "error");
				builder.field("reason", ErrorType.INVALID_CONFIGURATION.getMessage());
				addErrorMessage(builder, INVALID_KEYS_KEY, invalidKeys);
				addErrorMessage(builder, MISSING_MANDATORY_KEYS_KEY, missingMandatoryKeys);
				addErrorMessage(builder, MISSING_MANDATORY_OR_KEYS_KEY, missingMandatoryKeys);
				break;
			default:
				builder.field("status", "error");
				builder.field("reason", errorType.getMessage());
			}
			builder.endObject();
			return builder;
		} catch (IOException ex) {
			log.error("Cannot build error settings", ex);
			return null;
		}
	}

	public Settings.Builder settingsBuilder() {
		return settingsBuilder;
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

	public static enum ErrorType {
		NONE("ok"),
		INVALID_CONFIGURATION("invalid configuration"),
		BODY_NOT_PARSEABLE("Coud not parse content of request."),
		PAYLOAD_NOT_ALLOWED("Request body not allowed for this action."),
		PAYLOAD_MANDATORY("Request body required for this action.");

		private String message;

		private ErrorType(String message) {
			this.message = message;
		}

		public String getMessage() {
			return message;
		}
	}
}
