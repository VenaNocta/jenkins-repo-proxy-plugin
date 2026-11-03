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

import javax.net.ssl.SSLException;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Descriptor;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;

public class OpenSslContextFactory extends SslContextFactory {

	private static final long serialVersionUID = 1L;

	@DataBoundConstructor
	public OpenSslContextFactory() {
	}

	@Override
	public SslContext createContextForClient() throws IOException {
		try {
			return SslContextBuilder.forClient()
					.sslProvider(SslProvider.OPENSSL)
					.build();
		} catch (SSLException e) {
			e.printStackTrace();
			throw e;
		}
	}

	// TODO resolve libnetty_tcnative*.so deployment
	// @Extension(ordinal = 100)
	public static class DescriptorImpl extends Descriptor<SslContextFactory> {

		@Override
		public String getDisplayName() {
			return "OpenSSL-Context (Netty OpenSSL)";
		}
	}
}
