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
package jenkins.netty;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import hudson.remoting.VirtualChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;

public class AgentNettyRegistry {

	private static final Logger LOGGER = System.getLogger(AgentNettyRegistry.class.getName());

	protected static final Map<UUID, NettyServerHandle> SERVERS = new ConcurrentHashMap<>();

	public static synchronized NettyServerHandle register(final VirtualChannel ch, final String flowNodeId,
			final ChannelGroup group) {
		UUID id;
		do {
			id = UUID.randomUUID();
		} while (SERVERS.containsKey(id));

		final NettyServerHandle handle = new NettyServerHandle(id, ch, flowNodeId, group);
		SERVERS.put(id, handle);
		return handle;
	}

	public static NettyServerHandle get(final UUID uuid) {
		return SERVERS.get(uuid);
	}

	public static boolean stopServers(final Collection<UUID> uuids) throws InterruptedException {
		return stopServers(uuids, 60, TimeUnit.SECONDS);
	}

	public static boolean stopServers(final Collection<UUID> uuids, final long timeout, final TimeUnit unit)
			throws InterruptedException {
		final Map<UUID, ChannelGroupFuture> futures = new LinkedHashMap<>();
		// signal all servers to shutdown
		for (UUID uuid : uuids) {
			final NettyServerHandle handle = SERVERS.remove(uuid);
			if (handle == null)
				continue;
			futures.put(uuid, handle.getChannelGroup()
					.close());
		}
		// wait for them to do so
		final long deadline = System.nanoTime() + unit.toNanos(timeout);
		for (Map.Entry<UUID, ChannelGroupFuture> entry : futures.entrySet()) {
			final ChannelGroupFuture future = entry.getValue();

			final long remaining = deadline - System.nanoTime();
			if (future.await(remaining, TimeUnit.NANOSECONDS) == false) {
				LOGGER.log(Level.WARNING,
						"NettyServerHandle(" + entry.getKey() + ") | Timed out waiting for channel-group close");
				continue;
			}
		}
		boolean allOk = true;
		for (Map.Entry<UUID, ChannelGroupFuture> entry : futures.entrySet()) {
			final ChannelGroupFuture future = entry.getValue();
			if (future.isSuccess() == false) {
				allOk = false;
				LOGGER.log(Level.ERROR,
						"NettyServerHandle(" + entry.getKey() + ") | Channel-group close failed: " + future.cause());
			}
		}
		return allOk;
	}

	public static Stream<NettyServerHandle> stream() {
		return SERVERS.values()
				.stream();
	}
}
