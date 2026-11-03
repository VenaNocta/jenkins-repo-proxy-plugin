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

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.jenkinsci.remoting.ChannelStateException;

import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.nio.NioIoHandler;

public class NettyServerHandle {

	private static final Logger LOGGER = System.getLogger(NettyServerHandle.class.getName());

	private final AtomicReference<VirtualChannel> jenkinsChannel = new AtomicReference<>();
	private final Queue<Callable<?, ?>> pendingCallbacks = new ConcurrentLinkedQueue<>();
	private final AtomicBoolean channelGroupState = new AtomicBoolean(true);

	protected final UUID id;
	protected final String flowNodeId;
	protected final EventLoopGroup mainEventLoopGroup;
	protected final EventLoopGroup workerEventLoopGroup;
	protected final ChannelGroup channelGroup;

	protected NettyServerHandle(final UUID id, final VirtualChannel ch, final String flowNodeId,
			final ChannelGroup group) {
		this.id = id;
		this.flowNodeId = flowNodeId;
		this.mainEventLoopGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
		this.workerEventLoopGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
		this.channelGroup = group;
		this.jenkinsChannel.set(ch);
		// hooks
		this.channelGroup.newCloseFuture()
				.addListener(this::markChannelAsClosed);
	}

	public UUID getId() {
		return this.id;
	}

	public boolean isChannelGroupActive() {
		return this.channelGroupState.get();
	}

	public VirtualChannel getJenkinsChannel() {
		return this.jenkinsChannel.get();
	}

	public String getFlowNodeId() {
		return this.flowNodeId;
	}

	public EventLoopGroup getMainEventLoopGroup() {
		return this.mainEventLoopGroup;
	}

	public EventLoopGroup getWorkerEventLoopGroup() {
		return this.workerEventLoopGroup;
	}

	public ChannelGroup getChannelGroup() {
		return this.channelGroup;
	}

	public void updateJenkinsChannel(final VirtualChannel channel) {
		this.jenkinsChannel.set(Objects.requireNonNull(channel, "channel must not be null"));
		flushCallbacks();
	}

	public void sendCallback(final Callable<?, ?> callback) {
		if (callback == null)
			return;

		final VirtualChannel channel = this.jenkinsChannel.get();
		if (isUnavailable(channel)) {
			LOGGER.log(Level.INFO, "sendCallback | VirtualChannel (" + channel + ") unavailable!");
			this.pendingCallbacks.add(callback);
			return;
		}

		try {
			channel.callAsync(callback);
		} catch (ChannelStateException e) {
			this.pendingCallbacks.add(callback);
			this.jenkinsChannel.compareAndSet(channel, null);
		} catch (IOException e) {
			LOGGER.log(Level.ERROR, "Skipping Callback Delivery! : " + e.getMessage());
			LOGGER.log(Level.ERROR, e.getStackTrace());
		}
	}

	private void flushCallbacks() {
		final VirtualChannel channel = this.jenkinsChannel.get();
		if (isUnavailable(channel))
			return;

		Callable<?, ?> callback;
		while ((callback = this.pendingCallbacks.poll()) != null) {
			try {
				channel.callAsync(callback);
			} catch (ChannelStateException e) {
				this.pendingCallbacks.add(callback);
				this.jenkinsChannel.compareAndSet(channel, null);
				break;
			} catch (IOException e) {
				LOGGER.log(Level.ERROR, "Skipping Callback Delivery! : " + e.getMessage());
				LOGGER.log(Level.ERROR, e.getStackTrace());
				break;
			}
		}
	}

	public ChannelGroupFuture shutdownChannelGroup() {
		return this.channelGroup.close();
	}

	protected void markChannelAsClosed(final Future<?> future) {
		this.channelGroupState.set(false);
	}

	private static boolean isUnavailable(final VirtualChannel channel) {
		if (channel instanceof Channel ch)
			return ch.isClosingOrClosed();
		return false;
	}
}