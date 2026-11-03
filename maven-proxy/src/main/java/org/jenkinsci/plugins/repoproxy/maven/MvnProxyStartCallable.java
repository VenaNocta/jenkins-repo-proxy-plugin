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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;

import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepository;
import org.jenkinsci.plugins.artifact.lifecycle.maven.config.Maven2Repository;
import org.jenkinsci.plugins.repoproxy.dto.RemoteInitData;
import org.jenkinsci.plugins.repoproxy.maven.config.MvnProxyConfig;
import org.jenkinsci.plugins.repoproxy.steps.ANettyProxyStartCallable;
import org.jenkinsci.plugins.repoproxy.steps.ArtifactValidationInterruption;
import org.jenkinsci.plugins.repoproxy.steps.ProxyCallbackData;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.netty.channel.group.ChannelGroup;
import jenkins.netty.AgentNettyRegistry;
import jenkins.netty.NettyServerHandle;
import net.runeduniverse.lib.repo.maven.data.DefaultFileTypeIndex;
import net.runeduniverse.lib.repo.maven.error.ArtifactException;
import net.runeduniverse.lib.repo.maven.proxy.DefaultCache;
import net.runeduniverse.lib.repo.maven.proxy.DefaultRepositoryProxyInstance;
import net.runeduniverse.lib.repo.maven.proxy.ProxyServer;
import net.runeduniverse.lib.repo.maven.server.http.HttpRepoServerInitializer;

public class MvnProxyStartCallable extends ANettyProxyStartCallable {

	private static final long serialVersionUID = 1L;

	protected final Map<String, MvnProxyConfig> instanceConfigs;

	public MvnProxyStartCallable(final String flowNodeId, final Map<String, MvnProxyConfig> instanceConfigs,
			final RemoteInitData data, final Set<InetAddress> addressSet, final int port) {
		super(flowNodeId, data, addressSet, port);
		this.instanceConfigs = instanceConfigs;
	}

	@Override
	protected void attachServerChannel(final UUID handleId, final File cacheFolder, final ChannelGroup channelGroup)
			throws IOException, InterruptedException {
		final NettyServerHandle handle = AgentNettyRegistry.get(handleId);
		final ProxyServer server = new ProxyServer(handle.getMainEventLoopGroup(), handle.getWorkerEventLoopGroup(),
				new DefaultFileTypeIndex(), HttpRepoServerInitializer::new);
		final Path cachePath = cacheFolder.toPath();

		configure(server, cachePath, data -> {
			String refId = null;

			if (data instanceof ProxyCallbackData pData) {
				refId = pData.getRefKey();
				data = pData.getData();
			}

			sendCallback(handle, refId, data);
			if (data instanceof ArtifactException ex) {
				killPipeline(handle, ex);
			}
		});

		for (InetAddress address : this.addressSet) {
			channelGroup.add(server.bindChannel(address, this.port)
					.sync()
					.channel());
		}
	}

	protected void killPipeline(final NettyServerHandle handle, final ArtifactException ex) {
		killPipeline(handle, new ArtifactValidationInterruption(ex.getMessage()).setPURL(ex.getPURL()));
	}

	protected void configure(final ProxyServer server, final Path cachePath, final Consumer<Serializable> callback)
			throws IOException, InterruptedException {
		final Map<UUID, Path> bomCacheMap = resolveBomCacheMap();
		final Map<UUID, Maven2Repository> repoMap = resolveRepoMap();
		for (Entry<String, MvnProxyConfig> entry : this.instanceConfigs.entrySet()) {
			final String refId = entry.getKey();
			final MvnProxyConfig config = entry.getValue();

			final DefaultRepositoryProxyInstance instance = new DefaultRepositoryProxyInstance(entry.getKey(),
					DefaultCache::new);

			final Path repoPath = cachePath.resolve(entry.getKey());
			instance.sources()
					.putAll(config.buildSourceMap(repoPath, bomCacheMap, repoMap, this.data, data -> {
						if (data instanceof ProxyCallbackData)
							callback.accept(data);
						else
							callback.accept(new ProxyCallbackData(refId, data));
					}));

			server.addInstance(instance);
		}
	}

	protected Map<UUID, Maven2Repository> resolveRepoMap() throws IOException, InterruptedException {
		return this.data.getRepositoryMap()
				.values()
				.stream()
				.filter(Maven2Repository::isSupported)
				.collect(Collectors.toMap(ArtifactRepository::getUuid, r -> (Maven2Repository) r));
	}
}
