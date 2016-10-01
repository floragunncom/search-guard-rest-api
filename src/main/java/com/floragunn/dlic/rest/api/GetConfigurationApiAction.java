package com.floragunn.dlic.rest.api;

import java.io.IOException;
import java.util.Arrays;

import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestRequest.Method;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;

import com.floragunn.dlic.rest.validation.AbstractConfigurationValidator;
import com.floragunn.dlic.rest.validation.NoOpValidator;
import com.floragunn.searchguard.auditlog.AuditLog;
import com.floragunn.searchguard.configuration.AdminDNs;
import com.floragunn.searchguard.configuration.ConfigurationLoader;
import com.floragunn.searchguard.configuration.ConfigurationService;
import com.google.common.base.Joiner;

public class GetConfigurationApiAction extends AbstractApiAction {

	@Inject
	public GetConfigurationApiAction(final Settings settings, final RestController controller, final Client client,
			final AdminDNs adminDNs, final ConfigurationLoader cl, final ClusterService cs, final AuditLog auditLog) {
		super(settings, controller, client, adminDNs, cl, cs, auditLog);
		controller.registerHandler(Method.GET, "/_searchguard/api/configuration/{configname}", this);
		logger.info("Registered ConfigurationApiAction");
	}

	@Override
	protected Tuple<String[], RestResponse> handleGet(RestRequest request, Client client,
			final Settings.Builder additionalSettingsBuilder) throws Throwable {
		final String configname = request.param("configname");

		if (configname == null || configname.length() == 0
				|| !Arrays.asList(ConfigurationService.CONFIGNAMES).contains(configname)) {
			return badRequestResponse("No configuration name given, must be one of "
					+ Joiner.on(",").join(ConfigurationService.CONFIGNAMES));

		}

		final Settings config = loadAsSettings(configname);

		return new Tuple<String[], RestResponse>(new String[0],
				new BytesRestResponse(RestStatus.OK, convertToJson(config)));
	}

	private static XContentBuilder convertToJson(Settings settings) throws IOException {
		XContentBuilder builder = XContentFactory.jsonBuilder();
		builder.prettyPrint();
		builder.startObject();
		settings.toXContent(builder, ToXContent.EMPTY_PARAMS);
		builder.endObject();
		return builder;
	}

	@Override
	protected AbstractConfigurationValidator getValidator(Method method, BytesReference ref) {
		return new NoOpValidator(method, ref);
	}
}
