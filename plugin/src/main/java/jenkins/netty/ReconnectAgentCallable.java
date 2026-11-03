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

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.UUID;

import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import jenkins.agents.ControllerToAgentFileCallable;

public final class ReconnectAgentCallable implements ControllerToAgentFileCallable<Void> {

	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = System.getLogger(ReconnectAgentCallable.class.getName());

	private final UUID handleId;

	public ReconnectAgentCallable(final UUID handleId) {
		this.handleId = handleId;
	}

	@Override
	public Void invoke(final File ignored, final VirtualChannel channel) throws IOException {
		// NOTE: The channel may be null, usually on remote agents!
		final VirtualChannel callbackChannel = channel != null ? channel : Channel.current();
		if (callbackChannel == null)
			throw new IOException("No current Remoting channel");

		final NettyServerHandle handle = AgentNettyRegistry.get(this.handleId);
		if (handle == null)
			throw new NettyServerHandleNotFoundException(this.handleId);

		LOGGER.log(Level.INFO, "ReconnectAgentCallable: updating channel for handle (ID: " + this.handleId + ") to "
				+ callbackChannel);
		handle.updateJenkinsChannel(callbackChannel);
		return null;
	}
}
