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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepository;
import org.jenkinsci.plugins.artifact.lifecycle.config.GlobalRepositoryIndex;
import org.jenkinsci.plugins.repoproxy.config.BomSourceCache;
import org.jenkinsci.plugins.repoproxy.netty.SslContextFactory;

import com.cloudbees.plugins.credentials.common.IdCredentials;

import io.netty.handler.ssl.SslContext;

public class DefaultRemoteInitData implements RemoteInitData, CredentialsData, BomCacheData {

	private static final long serialVersionUID = 1L;

	// RemoteInitData
	private final Map<UUID, ArtifactRepository> repoMap;
	private SslContextFactory sslContextFactory = null;
	private transient SslContext clientSslContext = null;
	private transient SslContext serverSslContext = null;
	// CredentialsData
	private final Map<String, IdCredentials> credMap;
	// BomCacheData
	private final Map<UUID, BomSourceCache> bomCacheMap;

	public DefaultRemoteInitData() {
		this(new LinkedHashMap<>(), null, new LinkedHashMap<>(), new LinkedHashMap<>());
	}

	protected DefaultRemoteInitData(
			// RemoteInitData
			final Map<UUID, ArtifactRepository> repoMap, final SslContextFactory sslContextFactory,
			// CredentialsData
			final Map<String, IdCredentials> credMap,
			// BomCacheData
			final Map<UUID, BomSourceCache> bomCacheMap
	// --
	) {
		// RemoteInitData
		this.repoMap = repoMap;
		this.sslContextFactory = sslContextFactory;
		// CredentialsData
		this.credMap = credMap;
		// BomCacheData
		this.bomCacheMap = bomCacheMap;
	}

	public DefaultRemoteInitData copy() {
		return new DefaultRemoteInitData(
				// RemoteInitData
				new LinkedHashMap<>(this.repoMap), this.sslContextFactory,
				// CredentialsData
				new LinkedHashMap<>(this.credMap),
				// BomCacheData
				new LinkedHashMap<>(this.bomCacheMap)
		// --
		);
	}

	// ---- RemoteInitData

	@Override
	public Map<UUID, ArtifactRepository> getRepositoryMap() {
		return this.repoMap;
	}

	@Override
	public SslContextFactory getSslContextFactory() {
		return this.sslContextFactory;
	}

	public void setSslContextFactory(final SslContextFactory sslContextFactory) {
		this.sslContextFactory = sslContextFactory;
	}

	@Override
	public void retainRepositories(final Predicate<ArtifactRepository> filter) {
		for (Iterator<ArtifactRepository> i = this.repoMap.values()
				.iterator(); i.hasNext();) {
			final ArtifactRepository repo = i.next();
			if (repo == null || !filter.test(repo))
				i.remove();
		}
	}

	@Override
	public <T extends ArtifactRepository> void convertRepositories(final Class<T> type,
			final Predicate<ArtifactRepository> validator) {
		final Function<ArtifactRepository, Stream<ArtifactRepository>> mapper = //
				GlobalRepositoryIndex.adaptToTypeAsStream(type);
		for (Iterator<Entry<UUID, ArtifactRepository>> i = this.repoMap.entrySet()
				.iterator(); i.hasNext();) {
			final Entry<UUID, ArtifactRepository> entry = i.next();
			ArtifactRepository repo = entry.getValue();
			if (repo == null) {
				i.remove();
				continue;
			}
			repo = mapper.apply(repo)
					.filter(validator)
					.findFirst()
					.orElse(null);
			if (repo == null)
				i.remove();
			else
				entry.setValue(repo);
		}
	}

	@Override
	public SslContext getContextForClient() {
		return this.clientSslContext;
	}

	@Override
	public SslContext getContextForServer() {
		return this.serverSslContext;
	}

	@Override
	public void initContextForClient() throws IOException, InterruptedException {
		this.clientSslContext = this.sslContextFactory.createContextForClient();
	}

	@Override
	public void initContextForServer() throws IOException, InterruptedException {
		this.serverSslContext = this.sslContextFactory.createContextForServer();
	}

	// ---- CredentialsData

	@Override
	public Map<String, IdCredentials> getCredentialMap() {
		return this.credMap;
	}

	@Override
	public void retainCredentials(final Predicate<IdCredentials> filter) {
		for (Iterator<IdCredentials> i = this.credMap.values()
				.iterator(); i.hasNext();) {
			final IdCredentials cred = i.next();
			if (cred == null || !filter.test(cred))
				i.remove();
		}
	}

	// ---- BomCacheData

	@Override
	public Map<UUID, BomSourceCache> getBomCache() {
		return this.bomCacheMap;
	}

	@Override
	public void retainCachedBomSources(final Predicate<BomSourceCache> filter) {
		for (Iterator<BomSourceCache> i = this.bomCacheMap.values()
				.iterator(); i.hasNext();) {
			final BomSourceCache source = i.next();
			if (source == null || !filter.test(source))
				i.remove();
		}
	}
}
