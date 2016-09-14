package com.floragunn.dlic.rest.api;

import java.io.IOException;
import java.util.Arrays;

import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.ToXContent.Params;
import org.elasticsearch.common.xcontent.XContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.RestRequest.Method;

import com.floragunn.searchguard.auditlog.AuditLog;
import com.floragunn.searchguard.configuration.AdminDNs;
import com.floragunn.searchguard.configuration.ConfigurationLoader;
import com.floragunn.searchguard.configuration.ConfigurationService;

public class ConfigurationApiAction extends AbstractApiAction {
	
	 protected final ESLogger log = Loggers.getLogger(this.getClass());

    @Inject
    public ConfigurationApiAction(final Settings settings, final RestController controller, final Client client, final AdminDNs adminDNs,
            final ConfigurationLoader cl, final ClusterService cs, final AuditLog auditLog) {
        super(settings, controller, client, adminDNs, cl, cs, auditLog);
        controller.registerHandler(Method.GET, "/_searchguard/api/configuration/{configname}", this);
        log.info("Registered ConfigurationApiAction");
    }
	
    @Override
	protected Tuple<String[], RestResponse> handleApiRequest(RestRequest request, Client client) throws Throwable {
		final String configname = request.param("configname");

        if (configname == null || configname.length() == 0) {
            return new Tuple<String[], RestResponse>(new String[0], new BytesRestResponse(RestStatus.BAD_REQUEST, "No configuration name given, must be one of " + String.join(",", ConfigurationService.CONFIGNAMES)));
        }

		if (!Arrays.asList(ConfigurationService.CONFIGNAMES).contains(configname)) {
            return new Tuple<String[], RestResponse>(new String[0], new BytesRestResponse(RestStatus.BAD_REQUEST, "Bad configuration name given, must be one of " + String.join(",", ConfigurationService.CONFIGNAMES)));			
		}
		
		final Settings config = loadAsSettings(configname);
		
        return new Tuple<String[], RestResponse>(new String[] {convertToYaml(config)}, new BytesRestResponse(RestStatus.OK));
		
	}

    private static String convertToYaml(Settings settings) throws IOException {
    	XContentBuilder builder = settings.toXContent(XContentFactory.yamlBuilder(), ToXContent.EMPTY_PARAMS);
    	return builder.string();
    }
}
