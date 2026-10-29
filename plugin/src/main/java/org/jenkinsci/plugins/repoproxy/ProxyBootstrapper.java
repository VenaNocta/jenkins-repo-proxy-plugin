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
package org.jenkinsci.plugins.repoproxy;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jenkinsci.plugins.isolationctx.IsolationContext.NetLink;
import org.jenkinsci.plugins.repoproxy.dto.BomCacheData;
import org.jenkinsci.plugins.repoproxy.dto.CredentialsData;
import org.jenkinsci.plugins.repoproxy.dto.RemoteInitData;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import hudson.model.Action;
import hudson.model.TaskListener;
import jenkins.agents.ControllerToAgentFileCallable;

public interface ProxyBootstrapper {

	public default Set<UUID> getRequiredRepos() {
		return Set.of();
	}

	public default Set<String> getRequiredCredentials() {
		return Set.of();
	}

	public default Set<UUID> getRequiredBoms() {
		return Set.of();
	}

	public ControllerToAgentFileCallable<UUID> serverStartCallable(NetLink link, //
			StepContext context, Map<String, String> proxyEnv, RemoteInitData remoteInitData, //
			TaskListener listener) throws IOException, InterruptedException;

	public void receiveCallback(String refKey, Serializable data);

	public Set<Action> results();

	// for the sake of minimizing the amount of data transferred
	// - exclude all entries incompatible with this Proxy
	public default void reduceData(final RemoteInitData data) {
		reduceRepositoryData(data);
		if (data instanceof CredentialsData data0) {
			reduceCredentialData(data0);
		}
		if (data instanceof BomCacheData data0) {
			reduceCachedBomSources(data0);
		}
	}

	public default void reduceRepositoryData(final RemoteInitData data) {
		final Set<UUID> ids = getRequiredRepos();
		data.retainRepositories(e -> ids.contains(e.getUuid()));
	}

	public default void reduceCredentialData(final CredentialsData data) {
		final Set<String> ids = getRequiredCredentials();
		data.retainCredentials(e -> ids.contains(e.getId()));
	}

	public default void reduceCachedBomSources(final BomCacheData data) {
		final Set<UUID> ids = getRequiredBoms();
		data.retainCachedBomSources(e -> ids.contains(e.sourceId()));
	}
}
