/*
 * Copyright 2015 floragunn UG (haftungsbeschränkt)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package test;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import javax.xml.bind.DatatypeConverter;

import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestWatcher;

import io.netty.handler.ssl.OpenSsl;
import test.helper.cluster.ClusterHelper;
import test.helper.rules.SGTestWatcher;

public abstract class AbstractSGUnitTest {

	static {

		System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch") + " "
				+ System.getProperty("os.version"));
		System.out.println(
				"Java Version: " + System.getProperty("java.version") + " " + System.getProperty("java.vendor"));
		System.out.println("JVM Impl.: " + System.getProperty("java.vm.version") + " "
				+ System.getProperty("java.vm.vendor") + " " + System.getProperty("java.vm.name"));
		System.out.println("Open SSL available: " + OpenSsl.isAvailable());
		System.out.println("Open SSL version: " + OpenSsl.versionString());
	}
	
	protected ClusterHelper clusterHelper = new ClusterHelper();

	protected final ESLogger log = Loggers.getLogger(this.getClass());
	
	@Rule
	public TestName name = new TestName();

	@Rule
	public final TestWatcher testWatcher = new SGTestWatcher();

	@After
	public void tearDown() throws Exception {
		clusterHelper.stopCluster();
	}

	public static String encodeBasicHeader(final String username, final String password) {
		return new String(DatatypeConverter.printBase64Binary(
				(username + ":" + Objects.requireNonNull(password)).getBytes(StandardCharsets.UTF_8)));
	}
}
