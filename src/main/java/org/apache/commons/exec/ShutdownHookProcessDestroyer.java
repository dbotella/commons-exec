/* 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.commons.exec;

import java.util.Enumeration;
import java.util.Vector;

/**
 * Destroys all registered <code>Process</code>es when the VM exits.
 */
public class ShutdownHookProcessDestroyer implements ProcessDestroyer, Runnable {

	private final Vector processes = new Vector();

	private ProcessDestroyerImpl destroyProcessThread = null;

    /**
     * Whether or not this ProcessDestroyer has been registered as a shutdown hook
     */
	private boolean added = false;

    /**
     * Whether or not this ProcessDestroyer is currently running as shutdown hook
     */
	private boolean running = false;

	private class ProcessDestroyerImpl extends Thread {
		private boolean shouldDestroy = true;

		public ProcessDestroyerImpl() {
			super("ProcessDestroyer Shutdown Hook");
		}

		public void run() {
			if (shouldDestroy) {
				ShutdownHookProcessDestroyer.this.run();
			}
		}

		public void setShouldDestroy(final boolean shouldDestroy) {
			this.shouldDestroy = shouldDestroy;
		}
	}

	/**
	 * Constructs a <code>ProcessDestroyer</code> and obtains
	 * <code>Runtime.addShutdownHook()</code> and
	 * <code>Runtime.removeShutdownHook()</code> through reflection. The
	 * ProcessDestroyer manages a list of processes to be destroyed when the VM
	 * exits. If a process is added when the list is empty, this
	 * <code>ProcessDestroyer</code> is registered as a shutdown hook. If
	 * removing a process results in an empty list, the
	 * <code>ProcessDestroyer</code> is removed as a shutdown hook.
	 */
	public ShutdownHookProcessDestroyer() {
	}

	/**
	 * Registers this <code>ProcessDestroyer</code> as a shutdown hook, uses
	 * reflection to ensure pre-JDK 1.3 compatibility.
	 */
	private void addShutdownHook() {
		if (!running) {
			destroyProcessThread = new ProcessDestroyerImpl();
			Runtime.getRuntime().addShutdownHook(destroyProcessThread);
			added = true;
		}
	}

	/**
	 * Removes this <code>ProcessDestroyer</code> as a shutdown hook, uses
	 * reflection to ensure pre-JDK 1.3 compatibility
	 */
	private void removeShutdownHook() {
		if (added && !running) {
			boolean removed = Runtime.getRuntime().removeShutdownHook(
					destroyProcessThread);
			if (!removed) {
				// System.err.println("Could not remove shutdown hook");
				// TODO should we just ignore?
			}
			/*
			 * start the hook thread, a unstarted thread may not be eligible for
			 * garbage collection Cf.: http://developer.java.sun.com/developer/
			 * bugParade/bugs/4533087.html
			 */

			destroyProcessThread.setShouldDestroy(false);
			destroyProcessThread.start();
			// this should return quickly, since it basically is a NO-OP.
			try {
				destroyProcessThread.join(20000);
			} catch (InterruptedException ie) {
				// the thread didn't die in time
				// it should not kill any processes unexpectedly
			}
			destroyProcessThread = null;
			added = false;
		}
	}

	/**
	 * Returns whether or not the ProcessDestroyer is registered as as shutdown
	 * hook
	 * 
	 * @return true if this is currently added as shutdown hook
	 */
	public boolean isAddedAsShutdownHook() {
		return added;
	}

	/**
	 * Returns <code>true</code> if the specified <code>Process</code> was
	 * successfully added to the list of processes to destroy upon VM exit.
	 * 
	 * @param process
	 *            the process to add
	 * @return <code>true</code> if the specified <code>Process</code> was
	 *         successfully added
	 */
	public boolean add(final Process process) {
		synchronized (processes) {
			// if this list is empty, register the shutdown hook
			if (processes.size() == 0) {
				addShutdownHook();
			}
			processes.addElement(process);
			return processes.contains(process);
		}
	}

	/**
	 * Returns <code>true</code> if the specified <code>Process</code> was
	 * successfully removed from the list of processes to destroy upon VM exit.
	 * 
	 * @param process
	 *            the process to remove
	 * @return <code>true</code> if the specified <code>Process</code> was
	 *         successfully removed
	 */
	public boolean remove(final Process process) {
		synchronized (processes) {
			boolean processRemoved = processes.removeElement(process);
			if (processRemoved && processes.size() == 0) {
				removeShutdownHook();
			}
			return processRemoved;
		}
	}

	/**
	 * Invoked by the VM when it is exiting.
	 */
	public void run() {
		synchronized (processes) {
			running = true;
			Enumeration e = processes.elements();
			while (e.hasMoreElements()) {
				((Process) e.nextElement()).destroy();
			}
		}
	}
}
