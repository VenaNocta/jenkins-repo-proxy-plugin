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
package org.jenkinsci.plugins.repoproxy.dto;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepository;
import org.jenkinsci.plugins.repoproxy.netty.SslContextFactory;

import io.netty.handler.ssl.SslContext;

public interface RemoteInitData extends Serializable {

	public Map<UUID, ArtifactRepository> getRepositoryMap();

	public SslContextFactory getSslContextFactory();

	public SslContext getContextForClient();

	public SslContext getContextForServer();

	public void initContextForClient() throws IOException, InterruptedException;

	public void initContextForServer() throws IOException, InterruptedException;

	public void retainRepositories(Predicate<ArtifactRepository> filter);

	public <T extends ArtifactRepository> void convertRepositories(Class<T> type,
			Predicate<ArtifactRepository> validator);

}
