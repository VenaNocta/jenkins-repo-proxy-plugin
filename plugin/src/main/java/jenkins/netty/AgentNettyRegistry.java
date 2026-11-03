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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import hudson.remoting.VirtualChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupException;
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
		final Map<UUID, ShutdownProcedure> rundown = new LinkedHashMap<>();
		// signal all servers to shutdown
		for (UUID uuid : uuids) {
			final NettyServerHandle handle = SERVERS.remove(uuid);
			if (handle == null)
				continue;
			rundown.put(uuid, new ShutdownProcedure(handle).shutdownChannelGroup());
		}
		// wait for them to do so
		final long deadline = System.nanoTime() + unit.toNanos(timeout);
		for (Map.Entry<UUID, ShutdownProcedure> entry : rundown.entrySet()) {
			final ShutdownProcedure sm = entry.getValue();
			final ChannelGroupFuture future = sm.getCloseFuture();

			final long remaining = deadline - System.nanoTime();
			if (future.await(remaining, TimeUnit.NANOSECONDS) == false) {
				LOGGER.log(Level.WARNING,
						"NettyServerHandle(" + entry.getKey() + ") | Timed out waiting for channel-group close");
			}
		}
		// check success state & ensure event-loops are terminated
		boolean allOk = true;
		for (Map.Entry<UUID, ShutdownProcedure> entry : rundown.entrySet()) {
			final ShutdownProcedure sm = entry.getValue();
			final ChannelGroupFuture future = sm.getCloseFuture();
			if (future.isSuccess() == false) {
				allOk = false;
				final ChannelGroupException cause = future.cause();
				LOGGER.log(Level.ERROR, "NettyServerHandle(" + entry.getKey() + ") | Channel-group close failed: "
						+ cause.getMessage());
				LOGGER.log(Level.TRACE, cause.getStackTrace());
			}
			try {
				sm.shutdownMainEventLoop()
						.get();
			} catch (ExecutionException e) {
				final Throwable cause = e.getCause();
				LOGGER.log(Level.ERROR, "NettyServerHandle(" + entry.getKey() + ") | Main-EventLoop Shutdown failed: "
						+ cause.getMessage());
				LOGGER.log(Level.TRACE, cause.getStackTrace());
			}
			try {
				sm.shutdownWorkerEventLoop()
						.get();
			} catch (ExecutionException e) {
				final Throwable cause = e.getCause();
				LOGGER.log(Level.ERROR, "NettyServerHandle(" + entry.getKey() + ") | Worker-EventLoop Shutdown failed: "
						+ cause.getMessage());
				LOGGER.log(Level.TRACE, cause.getStackTrace());
			}
		}

		return allOk;
	}

	public static Stream<NettyServerHandle> stream() {
		return SERVERS.values()
				.stream();
	}

	public static class ShutdownProcedure {

		private final NettyServerHandle handle;

		private ChannelGroupFuture closeFuture;
		private Future<?> mainEventLoopShutdownFuture;
		private Future<?> workerEventLoopShutdownFuture;

		public ShutdownProcedure(final NettyServerHandle handle) {
			this.handle = handle;
		}

		public ChannelGroupFuture getCloseFuture() {
			return this.closeFuture;
		}

		public Future<?> getMainEventLoopShutdownFuture() {
			return this.mainEventLoopShutdownFuture;
		}

		public Future<?> getWorkerEventLoopShutdownFuture() {
			return this.workerEventLoopShutdownFuture;
		}

		public synchronized ShutdownProcedure shutdownChannelGroup() {
			if (this.closeFuture == null) {
				this.closeFuture = this.handle.shutdownChannelGroup()
						.addListener(v -> {
							this.shutdownMainEventLoop();
							this.shutdownWorkerEventLoop();
						});
			}
			return this;
		}

		public synchronized Future<?> shutdownMainEventLoop() {
			if (this.mainEventLoopShutdownFuture == null) {
				this.mainEventLoopShutdownFuture = this.handle.getMainEventLoopGroup()
						.shutdownGracefully();
			}
			return this.mainEventLoopShutdownFuture;
		}

		public synchronized Future<?> shutdownWorkerEventLoop() {
			if (this.workerEventLoopShutdownFuture == null) {
				this.workerEventLoopShutdownFuture = this.handle.getWorkerEventLoopGroup()
						.shutdownGracefully();
			}
			return this.workerEventLoopShutdownFuture;
		}
	}
}
