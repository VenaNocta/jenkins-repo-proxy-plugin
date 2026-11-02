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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jenkinsci.plugins.repoproxy.AgentToControllerCallable;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import hudson.model.Result;
import jenkins.model.CauseOfInterruption;

public class StopStepCallable implements AgentToControllerCallable<Void, RuntimeException> {

	private static final long serialVersionUID = 1L;

	protected final String flowNodeId;
	protected final Result result;
	protected final CauseOfInterruption cause;

	public StopStepCallable(final String flowNodeId, final CauseOfInterruption cause) {
		this(flowNodeId, Result.FAILURE, cause);
	}

	public StopStepCallable(final String flowNodeId, final Result result, final CauseOfInterruption cause) {
		this.flowNodeId = flowNodeId;
		this.result = result;
		this.cause = cause;
	}

	@Override
	public Void call() throws RuntimeException {
		final AtomicBoolean delivered = new AtomicBoolean(false);
		final Future<?> search = StepExecution.acceptAll(exec -> {
			try {
				final String id = exec.getContext()
						.get(FlowNode.class)
						.getId();

				if (StopStepCallable.this.flowNodeId.equals(id)) {
					exec.stop(new FlowInterruptedException(//
							StopStepCallable.this.result, true, StopStepCallable.this.cause));
					delivered.set(true);
				}
			} catch (Exception e) {
				System.err.println("WARNING : StopStepCallable >>>");
				e.printStackTrace(System.err);
				if (e instanceof RuntimeException ex)
					throw ex;
				throw new RuntimeException(e);
			}
		});
		// validate the search
		try {
			search.get(10, TimeUnit.SECONDS);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			System.err.println("WARNING : StopStepCallable >>>");
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
