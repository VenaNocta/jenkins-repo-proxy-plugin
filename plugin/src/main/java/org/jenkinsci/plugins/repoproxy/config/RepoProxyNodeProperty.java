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

import org.jenkinsci.plugins.repoproxy.config.ssl.PatchedSunSslContextFactory;
import org.jenkinsci.plugins.repoproxy.config.ssl.SslContextFactory;
import org.jenkinsci.plugins.repoproxy.config.ssl.SunSslContextFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;

public class RepoProxyNodeProperty extends NodeProperty<Node> {

	private SslContextFactory sslContextFactory = null;

	@DataBoundConstructor
	public RepoProxyNodeProperty() {
	}

	public SslContextFactory getSslContextFactory() {
		return this.sslContextFactory;
	}

	@DataBoundSetter
	public void setSslContextFactory(final SslContextFactory sslContextFactory) {
		this.sslContextFactory = sslContextFactory;
	}

	public static SslContextFactory getSslContextFactory(final Node node) {
		if (node == null)
			return null;
		final RepoProxyNodeProperty prop = node.getNodeProperty(RepoProxyNodeProperty.class);
		if (prop != null) {
			final SslContextFactory value = prop.getSslContextFactory();
			if (value != null)
				return value;
		}
		if (node instanceof Slave) {
			// NOTE: Remote Agents are affected by the Private-Key NPE regression
			// caused by bouncycastle-api
			return new PatchedSunSslContextFactory();
		}
		return new SunSslContextFactory();
	}

	@Extension
	public static class DescriptorImpl extends NodePropertyDescriptor {
		@Override
		public String getDisplayName() {
			return "Repo-Proxy Presets";
		}
	}
}
