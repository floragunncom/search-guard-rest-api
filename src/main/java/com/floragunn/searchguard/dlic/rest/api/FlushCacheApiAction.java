package com.floragunn.searchguard.dlic.rest.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Provider;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.Settings.Builder;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestRequest.Method;
import org.elasticsearch.rest.RestResponse;

import com.floragunn.searchguard.action.configupdate.ConfigUpdateAction;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateRequest;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateResponse;
import com.floragunn.searchguard.auditlog.AuditLog;
import com.floragunn.searchguard.auth.BackendRegistry;
import com.floragunn.searchguard.configuration.AdminDNs;
import com.floragunn.searchguard.configuration.ConfigurationLoader;
import com.floragunn.searchguard.dlic.rest.validation.AbstractConfigurationValidator;
import com.floragunn.searchguard.dlic.rest.validation.NoOpValidator;

public class FlushCacheApiAction extends AbstractApiAction {

	@Inject
	public FlushCacheApiAction(final Settings settings, final RestController controller, final Client client,
			final AdminDNs adminDNs, final ConfigurationLoader cl, final ClusterService cs, final AuditLog auditLog,
			final Provider<BackendRegistry> backendRegistryProvider) {
		super(settings, controller, client, adminDNs, cl, cs, auditLog, backendRegistryProvider);
		controller.registerHandler(Method.DELETE, "/_searchguard/api/cache", this);
		controller.registerHandler(Method.GET, "/_searchguard/api/cache", this);
		controller.registerHandler(Method.PUT, "/_searchguard/api/cache", this);
		controller.registerHandler(Method.POST, "/_searchguard/api/cache", this);
	}

	@Override
	protected Tuple<String[], RestResponse> handleDelete(RestRequest request, Client client, Builder additionalSettingsBuilder)
			throws Throwable {

		final Semaphore sem = new Semaphore(0);
		final List<Throwable> exception = new ArrayList<Throwable>(1);

		client.execute(
				ConfigUpdateAction.INSTANCE,
				new ConfigUpdateRequest(new String[] { "config", "roles", "rolesmapping", "internalusers", "actiongroups" }),
				new ActionListener<ConfigUpdateResponse>() {

					@Override
					public void onResponse(ConfigUpdateResponse response) {
						sem.release();
						if (logger.isDebugEnabled()) {
							logger.debug("cache flushed successfully");
						}
					}

					@Override
					public void onFailure(Throwable e) {
						sem.release();
						exception.add(e);
						logger.error("Cannot flush cache due to {}", e);
					}

				}
		);

		if (!sem.tryAcquire(30, TimeUnit.SECONDS)) {
			logger.error("Cannot flush cache due to timeout");
			return internalErrorResponse("Cannot flush cache due to timeout");
		}

		if (exception.size() > 0) {
			logger.error("Cannot flush cache due to {}", exception.get(0));
			return internalErrorResponse("Cannot flush cache due to "+ exception.get(0).getMessage());
		}		
		
		return successResponse("Cache flushed successfully.", new String[0]);
	}

	@Override
	protected Tuple<String[], RestResponse> handlePost(final RestRequest request, final Client client,
			final Settings.Builder additionalSettings) throws Throwable {
		return notImplemented(Method.POST);
	}

	@Override
	protected Tuple<String[], RestResponse> handleGet(final RestRequest request, final Client client,
			final Settings.Builder additionalSettings) throws Throwable {
		return notImplemented(Method.GET);
	}

	@Override
	protected Tuple<String[], RestResponse> handlePut(final RestRequest request, final Client client,
			final Settings.Builder additionalSettings) throws Throwable {
		return notImplemented(Method.PUT);
	}

	@Override
	protected AbstractConfigurationValidator getValidator(Method method, BytesReference ref) {		
		return new NoOpValidator(method, ref);
	}

	@Override
	protected String getResourceName() {
		// not needed
		return null;
	}

	@Override
	protected String getConfigName() {
		// not needed
		return null;
	}

}
