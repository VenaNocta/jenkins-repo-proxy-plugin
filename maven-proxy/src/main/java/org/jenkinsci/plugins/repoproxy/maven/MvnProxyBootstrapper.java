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
package org.jenkinsci.plugins.repoproxy.maven;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jenkinsci.plugins.artifact.lifecycle.maven.config.Maven2Repository;
import org.jenkinsci.plugins.isolationctx.IsolationContext.NetLink;
import org.jenkinsci.plugins.isolationctx.dto.TrafficMap;
import org.jenkinsci.plugins.isolationctx.dto.TrafficMap.Protocol;
import org.jenkinsci.plugins.repoproxy.AProxyBootstrapper;
import org.jenkinsci.plugins.repoproxy.dto.RemoteInitData;
import org.jenkinsci.plugins.repoproxy.maven.config.MvnProxyConfig;
import org.jenkinsci.plugins.repoproxy.steps.TrackedArtifactsAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import hudson.model.Action;
import hudson.model.TaskListener;
import jenkins.agents.ControllerToAgentFileCallable;
import net.runeduniverse.lib.utils.net.api.IpAddress;

public class MvnProxyBootstrapper extends AProxyBootstrapper implements Action {

	protected final Map<String, MvnProxyConfig> instConfigs;
	protected final Set<IpAddress<?>> ipSet;
	protected final int port;
	// tracking data
	protected final Map<String, Set<String>> purls = new LinkedHashMap<>();

	public MvnProxyBootstrapper(final Set<IpAddress<?>> ipSet, final int port) {
		this.instConfigs = new LinkedHashMap<>();
		this.ipSet = ipSet;
		this.port = port;
	}

	protected MvnProxyBootstrapper(final Map<String, MvnProxyConfig> instanceConfigs, final Set<IpAddress<?>> ipSet,
			final int port) {
		this.instConfigs = instanceConfigs;
		this.ipSet = ipSet;
		this.port = port;
	}

	public boolean canSupport(final String refId, final Set<IpAddress<?>> ipSet, final int port) {
		if (this.instConfigs.containsKey(refId))
			return false;
		if (this.ipSet.size() != ipSet.size() || !this.ipSet.containsAll(ipSet))
			return false;
		if (this.port != port)
			return false;
		return true;
	}

	public void addRepoInstance(final String refId, final MvnProxyConfig config) {
		this.instConfigs.put(refId, config);
	}

	@Override
	public Set<UUID> getRequiredBoms() {
		final Set<UUID> ids = new LinkedHashSet<>();
		this.instConfigs.values()
				.stream()
				.map(MvnProxyConfig::getRequiredBoms)
				.forEach(ids::addAll);
		return ids;
	}

	@Override
	public Set<UUID> getRequiredRepos() {
		final Set<UUID> ids = new LinkedHashSet<>();
		this.instConfigs.values()
				.stream()
				.map(MvnProxyConfig::getRequiredRepos)
				.forEach(ids::addAll);
		return ids;
	}

	@Override
	public Set<String> getRequiredCredentials() {
		final Set<String> creds = new LinkedHashSet<>();
		this.instConfigs.values()
				.stream()
				.map(MvnProxyConfig::getRequiredCredentials)
				.forEach(creds::addAll);
		return creds;
	}

	@Override
	public void reduceRepositoryData(final RemoteInitData data) {
		final Set<UUID> ids = getRequiredRepos();
		data.convertRepositories(Maven2Repository.class,
				e -> ids.contains(e.getUuid()) && Maven2Repository.isSupported(e));
	}

	@Override
	public ControllerToAgentFileCallable<UUID> serverStartCallable(final NetLink link, //
			final StepContext context, final Map<String, String> proxyEnv, final RemoteInitData data,
			final TaskListener listener) throws IOException, InterruptedException {

		final Set<InetAddress> addressSet = this.ipSet.stream()
				.map(ip -> ip.toInetAddress(null))
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());

		final FlowNode node = context.get(FlowNode.class);
		final MvnProxyStartCallable callable = new MvnProxyStartCallable(//
				node.getId(), this.instConfigs, data, addressSet, this.port);

		updateEnv(this.instConfigs.keySet(), this.ipSet, this.port, proxyEnv, "MVN");

		for (IpAddress<?> ip : this.ipSet) {
			// the TrafficMap only allows ip pairs with the same family
			// -> all others are ignored
			link.firewall()
					.firewallInputOpen(
							new TrafficMap().add(ip, link.getContextAddressV4(), Protocol.TCP, Set.of(this.port))
									.add(ip, link.getContextAddressV6(), Protocol.TCP, Set.of(this.port)),
							listener);
		}

		return callable;
	}

	protected void trackArtifact(final String refKey, final String purl) {
		synchronized (this.purls) {
			this.purls.computeIfAbsent(refKey, k -> new LinkedHashSet<>())
					.add(purl);
		}
	}

	protected void trackArtifacts(final String refKey, final Collection<String> purls) {
		synchronized (this.purls) {
			this.purls.computeIfAbsent(refKey, k -> new LinkedHashSet<>())
					.addAll(purls);
		}
	}

	@Override
	public void receiveCallback(final String refKey, final Serializable data) {
		if (data instanceof ArtifactRequestTracker.Data artifact) {
			trackArtifact(refKey, artifact.getPurl());
		}
	}

	@Override
	public Set<Action> results() {
		final Set<Action> set = new LinkedHashSet<>();
		final TrackedArtifactsAction tracker = new TrackedArtifactsAction();
		synchronized (this.purls) {
			this.purls.forEach(tracker::addArtifacts);
		}
		set.add(tracker);
		return set;
	}

	@Override
	public final String getIconFileName() {
		return null;
	}

	@Override
	public final String getDisplayName() {
		return null;
	}

	@Override
	public String getUrlName() {
		return null;
	}
}
