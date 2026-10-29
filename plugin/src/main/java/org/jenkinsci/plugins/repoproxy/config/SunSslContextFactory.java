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
package org.jenkinsci.plugins.repoproxy.config;

import java.io.IOException;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;

import org.jenkinsci.plugins.repoproxy.netty.SslContextFactory;

import io.netty.handler.ssl.DelegatingSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;

public class SunSslContextFactory implements SslContextFactory {

	private static final long serialVersionUID = 1L;

	@Override
	public SslContext createContextForClient() throws IOException {
		try {
			final SslContext base = SslContextBuilder.forClient()
					.sslProvider(SslProvider.JDK)
					.build();
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

	@Override
	public SslContext createContextForServer() throws IOException {
		return null;
	}
}
