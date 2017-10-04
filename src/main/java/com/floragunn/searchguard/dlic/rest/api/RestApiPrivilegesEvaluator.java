/*
 * Copyright 2017 by floragunn GmbH - All rights reserved
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

package com.floragunn.searchguard.dlic.rest.api;

import java.io.IOException;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.threadpool.ThreadPool;

import com.floragunn.searchguard.configuration.AdminDNs;
import com.floragunn.searchguard.configuration.PrivilegesEvaluator;
import com.floragunn.searchguard.ssl.transport.PrincipalExtractor;
import com.floragunn.searchguard.ssl.util.SSLRequestHelper;
import com.floragunn.searchguard.ssl.util.SSLRequestHelper.SSLInfo;
import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.user.User;

public class RestApiPrivilegesEvaluator {

	protected final Logger logger = LogManager.getLogger(this.getClass());
	
	private final AdminDNs adminDNs;
	private final PrivilegesEvaluator privilegesEvaluator;
	private final PrincipalExtractor principalExtractor;
	private final Path configPath;
	private final ThreadPool threadPool;
	private final Settings settings;
	
	private final Set<String> allowedRoles = new HashSet<>();
	private final Map<String, Set<String>> disabledEndpointsForRoles = new HashMap<>();
	
	private final Boolean roleBasedAccessEnabled;
	
	public RestApiPrivilegesEvaluator(Settings settings, AdminDNs adminDNs, PrivilegesEvaluator privilegesEvaluator,
			PrincipalExtractor principalExtractor, Path configPath, ThreadPool threadPool) {

		this.adminDNs = adminDNs;
		this.privilegesEvaluator = privilegesEvaluator;
		this.principalExtractor = principalExtractor;
		this.configPath = configPath;
		this.threadPool = threadPool;
		this.settings = settings;
		
		// setup role based permissions
		allowedRoles.addAll(Arrays.asList(settings.getAsArray(ConfigConstants.SEARCHGUARD_RESTAPI_ROLES_ENABLED)));
		
		this.roleBasedAccessEnabled = !allowedRoles.isEmpty();

		// globally disabled endpoints
		List<String> globallyDisabledEndpoints = new LinkedList<>();
		globallyDisabledEndpoints
				.addAll(Arrays.asList(settings.getAsArray(ConfigConstants.SEARCHGUARD_RESTAPI_ENDPOINTS_DISABLED + ".global")));
		
		if(logger.isDebugEnabled()) {
			logger.debug("Globally disabled endpoints: {}", globallyDisabledEndpoints);
		}

		// disabled endpoints per role
		for (String role : allowedRoles) {
			Set<String> disabledEndpointsForRole = new HashSet<>();
			// globally disabled endpoints apply for every role
			disabledEndpointsForRole.addAll(globallyDisabledEndpoints);
			// add other endpoints, check that they are valid
			for (String endpoint : settings.getAsArray(ConfigConstants.SEARCHGUARD_RESTAPI_ENDPOINTS_DISABLED + "." + role)) {
				try {
					Endpoint.valueOf(endpoint.toUpperCase());
					disabledEndpointsForRole.add(endpoint.toUpperCase());
				} catch (Exception e) {
					logger.warn("The disabled endpoint '{}' configured for role {} is not recognized, skipping it.", endpoint, role);
				}
			}
			if (!disabledEndpointsForRole.isEmpty()) {
				this.disabledEndpointsForRoles.put(role, disabledEndpointsForRole);
			}
		}
	}

	/**
	 * Check if the current request is allowed to use the REST API and the requested end point. Using an admin certificate grants all
	 * permissions. A user/role can have restricted end points.
	 * 
	 * @return an error message if user does not have access, null otherwise TODO: log failed attempt in audit log
	 */
	public String checkAccessPermissions(RestRequest request, Endpoint endpoint) throws IOException {

		String certBasedAccessFailureReason = checkAdminCertBasedAccessPermissions(request);
		// TLS access granted, skip checking roles
		if (certBasedAccessFailureReason == null) {
			return null;
		}

		String roleBasedAccessFailureReason = checkRoleBasedAccessPermissions(endpoint);

		// Role based access granted
		if (roleBasedAccessFailureReason == null) {
			return null;
		}

		return constructAccessErrorMessage(roleBasedAccessFailureReason, certBasedAccessFailureReason);
	}
	
	public Boolean currentUserHasRestApiAccess(Set<String> userRoles) {
				
		// check if user has any role that grants access
		return !Collections.disjoint(allowedRoles, userRoles);
		
	}
	
	public Set<String> getDisabledEndpointsForCurrentUser(Set<String> userRoles) {
		Set<String> disabledEndpointsForUser = new HashSet<>();
		// iterate over all roles and disabled end points. Globally disabled end points are
		// part of each role based disabled end points.

		// Collect all disabled end points first - streams seem overhead here since collections are small
		for (String userRole : userRoles) {
			if (disabledEndpointsForRoles.containsKey(userRole)) {
				disabledEndpointsForUser.addAll(disabledEndpointsForRoles.get(userRole));
				if(logger.isTraceEnabled()) {
					logger.trace("Disabled endpoints for user's role {} : {}", userRole, disabledEndpointsForRoles.get(userRole));
				}

			}
		}
		// Make sure to retain only end points configured for all roles
		for (String userRole : userRoles) {
			if (disabledEndpointsForRoles.containsKey(userRole)) {
				disabledEndpointsForUser.retainAll(disabledEndpointsForRoles.get(userRole));
			}
		}	
		if(logger.isTraceEnabled()) {
			logger.trace("Disabled endpoints after retaining all : {}", disabledEndpointsForUser);
		}

		return disabledEndpointsForUser;
	}
	
	private String checkRoleBasedAccessPermissions(Endpoint endpoint) {
		// Role based access. Check that user has role suitable for admin access
		// and that the role has also access to this endpoint.
		if (this.roleBasedAccessEnabled) {

			// get current user and roles
			final User user = (User) threadPool.getThreadContext().getTransient(ConfigConstants.SG_USER);
			final TransportAddress remoteAddress = (TransportAddress) threadPool.getThreadContext()
					.getTransient(ConfigConstants.SG_REMOTE_ADDRESS);
			
			// map the users SG roles
			Set<String> userRoles = privilegesEvaluator.mapSgRoles(user, remoteAddress);

			// check if user has any role that grants access
			if (currentUserHasRestApiAccess(userRoles)) {
				// yes, calculate disabled end points. Since a user can have multiple roles, the endpoint
				// needs to be disabled in all roles.
				Set<String> disabledEndpointsForUser = getDisabledEndpointsForCurrentUser(userRoles);
				
				if(logger.isDebugEnabled()) {
					logger.debug("Disabled endpoints for user {} : {} ", user, disabledEndpointsForUser);
				}
			
				// check if this endpoint is disabled igoring case
				boolean isDisabled = disabledEndpointsForUser.stream().filter(s -> s.equalsIgnoreCase(endpoint.name())).findFirst()
						.isPresent();

				if(logger.isDebugEnabled()) {
					logger.debug("Endpoint {} disabled for user? {} : {} ", endpoint.name(), user, isDisabled);
				}

				
				if (isDisabled) {
					logger.info(
							"User {} with Search Guard Roles {} does not have access to endpoint {}, checking admin TLS certificate now.",
							user, userRoles,
							endpoint.name());
					return "User " + user.getName() + " with Search Guard Roles " + userRoles + " does not have any access to endpoint "
							+ endpoint.name();
				} else {
					logger.debug("User {} has access to endpoint {}.", user, endpoint.name());
					return null;
				}
			} else {
				// no, but maybe the request contains a client certificate. Remember error reason for better response message later on.
				logger.info(
						"User {} with Search Guard roles {} does not have any role privileged for admin access, checking admin TLS certificate now.",
						user, userRoles);
				return "User " + user.getName() + " with Search Guard Roles " + userRoles
						+ " does not have any role privileged for admin access";
			}
		}
		return "Role based access not enabled.";
	}

	private String checkAdminCertBasedAccessPermissions(RestRequest request) throws IOException {
		// Certificate based access, Check if we have an admin TLS certificate
		SSLInfo sslInfo = SSLRequestHelper.getSSLInfo(settings, configPath, request, principalExtractor);

		if (sslInfo == null) {
			// here we log on error level, since authentication finally failed
			logger.warn("No ssl info found in request.");
			return "No ssl info found in request.";
		}

		X509Certificate[] certs = sslInfo.getX509Certs();

		if (certs == null || certs.length == 0) {
			logger.warn("No client TLS certificate found in request");
			return "No client TLS certificate found in request";
		}

		if (!adminDNs.isAdmin(sslInfo.getPrincipal())) {
			logger.warn("SG admin permissions required but {} is not an admin", sslInfo.getPrincipal());
			return "SG admin permissions required but " + sslInfo.getPrincipal() + " is not an admin";
		}
		return null;
	}

	private String constructAccessErrorMessage(String roleBasedAccessFailure, String certBasedAccessFailure) {
		return roleBasedAccessFailure + "; " + certBasedAccessFailure;
	}

}
