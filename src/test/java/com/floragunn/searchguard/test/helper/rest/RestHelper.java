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

package com.floragunn.searchguard.test.helper.rest;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.net.ssl.SSLContext;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.floragunn.searchguard.test.helper.cluster.ClusterInfo;
import com.floragunn.searchguard.test.helper.file.FileHelper;
import com.google.common.base.Strings;

public class RestHelper {

	protected final Logger log = LogManager.getLogger(RestHelper.class);
	
	public boolean enableHTTPClientSSL = true;
	public boolean enableHTTPClientSSLv3Only = false;
	public boolean sendHTTPClientCertificate = false;
	public boolean trustHTTPServerCertificate = true;
	public String keystore = "kirk-keystore.jks";
	public String truststore = "truststore.jks";
	private ClusterInfo clusterInfo;
	
	public RestHelper(ClusterInfo clusterInfo) {
		this.clusterInfo = clusterInfo;
	}
	
	public RestHelper(ClusterInfo clusterInfo, boolean enableHTTPClientSSL, boolean trustHTTPServerCertificate) {
		this.clusterInfo = clusterInfo;
		this.enableHTTPClientSSL = enableHTTPClientSSL;
		this.trustHTTPServerCertificate = trustHTTPServerCertificate;
	}
	public String executeSimpleRequest(final String request) throws Exception {

		CloseableHttpClient httpClient = null;
		CloseableHttpResponse response = null;
		try {
			httpClient = getHTTPClient();
			response = httpClient.execute(new HttpGet(getHttpServerUri() + "/" + request));

			if (response.getStatusLine().getStatusCode() >= 300) {
				throw new Exception("Statuscode " + response.getStatusLine().getStatusCode());
			}

			return IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
		} finally {

			if (response != null) {
				response.close();
			}

			if (httpClient != null) {
				httpClient.close();
			}
		}
	}

	public HttpResponse executeGetRequest(final String request, Header... header) throws Exception {
	    return executeRequest(new HttpGet(getHttpServerUri() + "/" + request), header);
	}

	public HttpResponse executePutRequest(final String request, String body, Header... header) throws Exception {
		HttpPut uriRequest = new HttpPut(getHttpServerUri() + "/" + request);
		if (!Strings.isNullOrEmpty(body)) {
			uriRequest.setEntity(new StringEntity(body));
		}
		return executeRequest(uriRequest, header);
	}

	public HttpResponse executeDeleteRequest(final String request, Header... header) throws Exception {
		return executeRequest(new HttpDelete(getHttpServerUri() + "/" + request), header);
	}

	public HttpResponse executePostRequest(final String request, String body, Header... header) throws Exception {
		HttpPost uriRequest = new HttpPost(getHttpServerUri() + "/" + request);
		if (!Strings.isNullOrEmpty(body)) {
			uriRequest.setEntity(new StringEntity(body));
		}

		return executeRequest(uriRequest, header);
	}
	
	protected HttpResponse executeRequest(HttpUriRequest uriRequest, Header... header) throws Exception {

		CloseableHttpClient httpClient = null;
		try {

			httpClient = getHTTPClient();

			if (header != null && header.length > 0) {
				for (int i = 0; i < header.length; i++) {
					Header h = header[i];
					uriRequest.addHeader(h);
				}
			}
			
			uriRequest.addHeader(new BasicHeader("content-type", "application/json"));

			HttpResponse res = new HttpResponse(httpClient.execute(uriRequest));
			log.debug(res.getBody());
			return res;
		} finally {

			if (httpClient != null) {
				httpClient.close();
			}
		}
	}
	
	protected final String getHttpServerUri() {
		final String address = "http" + (enableHTTPClientSSL ? "s" : "") + "://" + clusterInfo.httpHost + ":" + clusterInfo.httpPort;
		log.debug("Connect to {}", address);
		return address;
	}
	
	protected final CloseableHttpClient getHTTPClient() throws Exception {

		final HttpClientBuilder hcb = HttpClients.custom();

		if (enableHTTPClientSSL) {

			log.debug("Configure HTTP client with SSL");

			final KeyStore myTrustStore = KeyStore.getInstance("JKS");
			myTrustStore.load(new FileInputStream(FileHelper.getAbsoluteFilePathFromClassPath(truststore)),
					"changeit".toCharArray());

			final KeyStore keyStore = KeyStore.getInstance("JKS");
			keyStore.load(new FileInputStream(FileHelper.getAbsoluteFilePathFromClassPath(keystore)), "changeit".toCharArray());

			final SSLContextBuilder sslContextbBuilder = SSLContexts.custom().useTLS();

			if (trustHTTPServerCertificate) {
				sslContextbBuilder.loadTrustMaterial(myTrustStore);
			}

			if (sendHTTPClientCertificate) {
				sslContextbBuilder.loadKeyMaterial(keyStore, "changeit".toCharArray());
			}

			final SSLContext sslContext = sslContextbBuilder.build();

			String[] protocols = null;

			if (enableHTTPClientSSLv3Only) {
				protocols = new String[] { "SSLv3" };
			} else {
				protocols = new String[] { "TLSv1", "TLSv1.1", "TLSv1.2" };
			}

			final SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, protocols, null,
					SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

			hcb.setSSLSocketFactory(sslsf);
		}

		hcb.setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(60 * 1000).build());

		return hcb.build();
	}

	
	public class HttpResponse {
		private final CloseableHttpResponse inner;
		private final String body;
		private final Header[] header;
		private final int statusCode;
		private final String statusReason;

		public HttpResponse(CloseableHttpResponse inner) throws IllegalStateException, IOException {
			super();
			this.inner = inner;
			this.body = IOUtils.toString(inner.getEntity().getContent(), StandardCharsets.UTF_8);
			this.header = inner.getAllHeaders();
			this.statusCode = inner.getStatusLine().getStatusCode();
			this.statusReason = inner.getStatusLine().getReasonPhrase();
			inner.close();
		}

		public CloseableHttpResponse getInner() {
			return inner;
		}

		public String getBody() {
			return body;
		}

		public Header[] getHeader() {
			return header;
		}

		public int getStatusCode() {
			return statusCode;
		}

		public String getStatusReason() {
			return statusReason;
		}

	}

}
