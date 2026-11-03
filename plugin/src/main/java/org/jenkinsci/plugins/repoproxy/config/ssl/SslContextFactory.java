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

import hudson.model.Describable;
import io.netty.handler.ssl.SslContext;

public abstract class SslContextFactory implements jenkins.netty.SslContextFactory, Describable<SslContextFactory> {

	private static final long serialVersionUID = 1L;

	@Override
	public SslContext createContextForClient() throws IOException {
		return null;
	}

	@Override
	public SslContext createContextForServer() throws IOException {
		return null;
	}
}
