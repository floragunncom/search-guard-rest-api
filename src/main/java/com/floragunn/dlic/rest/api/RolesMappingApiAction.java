package com.floragunn.dlic.rest.api;

import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.RestRequest.Method;

import com.floragunn.dlic.rest.validation.InternalUsersValidator;
import com.floragunn.searchguard.auditlog.AuditLog;
import com.floragunn.searchguard.configuration.AdminDNs;
import com.floragunn.searchguard.configuration.ConfigurationLoader;

public class RolesMappingApiAction extends AbstractApiAction {

    @Inject
    public RolesMappingApiAction(final Settings settings, final RestController controller, final Client client, final AdminDNs adminDNs,
            final ConfigurationLoader cl, final ClusterService cs, final AuditLog auditLog) {
        super(settings, controller, client, adminDNs, cl, cs, auditLog);
        controller.registerHandler(Method.DELETE, "/_searchguard/api/role/{name}", this);        
        controller.registerHandler(Method.POST, "/_searchguard/api/role/{name}", this);
        controller.registerHandler(Method.PUT, "/_searchguard/api/role/{name}", this);
    }

    protected Tuple<String[], RestResponse> handleDelete(final RestRequest request, final Client client) throws Throwable {
        final String rolename = request.param("name");
        
        if (rolename == null || rolename.length() == 0) {
            return new Tuple<String[], RestResponse>(new String[0], errorResponse(RestStatus.BAD_REQUEST, "No rolename specified"));
        }

        final Settings.Builder rolesmapping = load("rolesmapping");
        final Settings.Builder additionalSettingsBuilder = toSettingsBuilder(request.content());
        


        return new Tuple<String[], RestResponse>(new String[0], new BytesRestResponse(RestStatus.NOT_FOUND));
    }

    protected Tuple<String[], RestResponse> handlePost(final RestRequest request, final Client client) throws Throwable {
        final String username = request.param("name");

        if (username == null || username.length() == 0) {
            return new Tuple<String[], RestResponse>(new String[0], new BytesRestResponse(RestStatus.BAD_REQUEST, "No name given"));
        }

        final Settings additionalSettings = toSettings(request.content());
        
        InternalUsersValidator validator = new InternalUsersValidator();
        if (!validator.validateSettings(additionalSettings)) {
        	return new Tuple<String[], RestResponse>(new String[0], new BytesRestResponse(RestStatus.BAD_REQUEST, validator.errorsAsXContent()));
        }
        final Settings.Builder internaluser = load("internalusers");
        internaluser.put(prependValueToEachKey(additionalSettings.getAsMap(), username + "."));
        save(client, request, "internalusers", internaluser);
        return new Tuple<String[], RestResponse>(new String[] { "internalusers" }, new BytesRestResponse(RestStatus.OK));
    }
}
