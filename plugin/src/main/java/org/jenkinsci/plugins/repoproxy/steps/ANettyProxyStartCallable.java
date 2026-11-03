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
package org.jenkinsci.plugins.repoproxy.steps;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.jenkinsci.plugins.repoproxy.config.BomSourceCache;
import org.jenkinsci.plugins.repoproxy.dto.BomCacheData;
import org.jenkinsci.plugins.repoproxy.dto.RemoteInitData;

import jenkins.netty.AgentNettyStartCallable;
import jenkins.netty.NettyServerHandle;

import java.util.Set;
import java.util.UUID;

public abstract class ANettyProxyStartCallable extends AgentNettyStartCallable {

	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = System.getLogger(ANettyProxyStartCallable.class.getName());

	protected final RemoteInitData data;
	protected final Set<InetAddress> addressSet;
	protected final int port;

	public ANettyProxyStartCallable(final String flowNodeId, final RemoteInitData data,
			final Set<InetAddress> addressSet, final int port) {
		super(flowNodeId);
		this.data = data;
		this.addressSet = addressSet;
		this.port = port;
	}

	protected void preloadRemoteCaches(final UUID handleId, final File cacheFolder)
			throws IOException, InterruptedException {
		// ensure the errors are thrown here and not during netty init!
		this.data.initContextForClient();
		this.data.initContextForServer();
	}

	protected void sendCallback(final NettyServerHandle handle, final String refId, final Serializable data) {
		LOGGER.log(Level.DEBUG, "sent ProxyCallbackData {refKey=" + refId + ", data={ " + data + " }}");
		handle.sendCallback(new ProxyCallbackCallable(this.flowNodeId, refId, data));
	}

	protected void killPipeline(final NettyServerHandle handle, final ArtifactValidationInterruption interruption) {
		LOGGER.log(Level.DEBUG, "sent ArtifactValidationInterruption {msg=" + interruption.getMessage() + ", pURL="
				+ interruption.getPURL() + " }");
		handle.sendCallback(new StopStepCallable(this.flowNodeId, interruption));
	}

	protected Map<UUID, Path> resolveBomCacheMap() throws IOException, InterruptedException {
		final Map<UUID, Path> map = new LinkedHashMap<>();
		if (this.data instanceof BomCacheData data)
			for (Entry<UUID, BomSourceCache> entry : data.getBomCache()
					.entrySet()) {
				final UUID uuid = entry.getKey();
				final BomSourceCache cache = entry.getValue();
				if (uuid == null || cache == null)
					continue;
				map.put(uuid, cache.toPath());
			}
		return map;
	}
}
