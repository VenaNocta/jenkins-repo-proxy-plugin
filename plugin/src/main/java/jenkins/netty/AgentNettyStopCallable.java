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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import hudson.remoting.VirtualChannel;
import jenkins.agents.ControllerToAgentFileCallable;

public class AgentNettyStopCallable implements ControllerToAgentFileCallable<Boolean> {

	private static final long serialVersionUID = 1L;

	protected final Collection<UUID> uuids;
	protected final long timeout;
	protected final TimeUnit unit;

	public AgentNettyStopCallable(final Collection<UUID> uuids) {
		this(uuids, 60, TimeUnit.SECONDS);
	}

	public AgentNettyStopCallable(final Collection<UUID> uuids, final long timeout, final TimeUnit unit) {
		this.uuids = uuids instanceof Serializable ? uuids : new ArrayList<>(uuids);
		this.timeout = timeout;
		this.unit = unit;
	}

	@Override
	public Boolean invoke(final File f, final VirtualChannel channel) throws IOException, InterruptedException {
		return AgentNettyRegistry.stopServers(this.uuids, this.timeout, this.unit);
	}
}
