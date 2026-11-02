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

import java.io.Serializable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jenkinsci.plugins.repoproxy.AgentToControllerCallable;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

public class ProxyCallbackCallable implements AgentToControllerCallable<Void, RuntimeException> {

	private static final long serialVersionUID = 1L;

	protected final String flowNodeId;
	protected final String refKey;
	protected final Serializable data;
	protected final long timeout;
	protected final TimeUnit unit;

	public ProxyCallbackCallable(final String flowNodeId, final String refKey, final Serializable data) {
		this(flowNodeId, refKey, data, 10, TimeUnit.SECONDS);
	}

	public ProxyCallbackCallable(final String flowNodeId, final String refKey, final Serializable data,
			final long timeout, final TimeUnit unit) {
		this.flowNodeId = flowNodeId;
		this.refKey = refKey;
		this.data = data;
		this.timeout = timeout;
		this.unit = unit;
	}

	@Override
	public Void call() throws RuntimeException {
		// DEBUG: System.err.println("delivering ProxyCallbackData
		// {refKey=" + refKey + ", data={ " + data + " }}");
		final AtomicBoolean delivered = new AtomicBoolean(false);
		final Future<?> search = StepExecution.acceptAll(exec -> {
			if (exec instanceof RepoProxyStepExecution proxyExec) {
				try {
					final String id = proxyExec.getContext()
							.get(FlowNode.class)
							.getId();
					if (ProxyCallbackCallable.this.flowNodeId.equals(id)) {
						proxyExec.receiveCallback(ProxyCallbackCallable.this.refKey, ProxyCallbackCallable.this.data);
						delivered.set(true);
					}
				} catch (Exception e) {
					System.err.println("WARNING : ProxyCallbackCallable >>>");
					e.printStackTrace(System.err);
					if (e instanceof RuntimeException ex)
						throw ex;
					throw new RuntimeException(e);
				}
			}
		});
		// validate the search
		try {
			search.get(this.timeout, this.unit);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			System.err.println("WARNING : ProxyCallbackCallable >>>");
			e.printStackTrace(System.err);
			final Throwable t = e instanceof ExecutionException ex ? ex.getCause() : e;
			throw new RuntimeException("Failed to locate StepExecution of FlowNode=" + this.flowNodeId, t);
		}
		// validate the lookup result
		if (delivered.get() == false) {
			System.err.println(
					"WARNING : ProxyCallbackCallable >>> Failed to deliver Callback to StepExecution of FlowNode="
							+ this.flowNodeId);
			throw new IllegalStateException(
					"Failed to deliver Callback to StepExecution of FlowNode=" + this.flowNodeId);
		}
		return null;
	}
}
