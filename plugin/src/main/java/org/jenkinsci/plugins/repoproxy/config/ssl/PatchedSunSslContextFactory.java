/*
 * Copyright © 2026 VenaNocta (venanocta@gmail.com)
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
 */
package org.jenkinsci.plugins.repoproxy.config.ssl;

import java.io.IOException;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Descriptor;
import io.netty.handler.ssl.DelegatingSslContext;
import io.netty.handler.ssl.SslContext;

public class PatchedSunSslContextFactory extends SunSslContextFactory {

	private static final long serialVersionUID = 1L;

	@DataBoundConstructor
	public PatchedSunSslContextFactory() {
	}

	@Override
	public SslContext createContextForClient() throws IOException {
		try {
			final SslContext base = super.createContextForClient();
			return new DelegatingSslContext(base) {
				@Override
				protected void initEngine(final SSLEngine engine) {
					final SSLParameters parameters = engine.getSSLParameters();
					parameters.setNamedGroups(new String[] { "secp256r1", "secp384r1" });
					engine.setSSLParameters(parameters);
				}
			};
		} catch (SSLException e) {
			e.printStackTrace();
			throw e;
		}
	}

	@Extension(ordinal = 1001)
	public static class DescriptorImpl extends Descriptor<SslContextFactory> {

		@Override
		public String getDisplayName() {
			return "Sun SSL-Context (with regression patch)";
		}
	}
}
