/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Git Development Community nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.spearce.jgit.lib;

/** A progress reporting interface. */
public interface ProgressMonitor {
	/** Constant indicating the total work units cannot be predicted. */
	public static final int UNKNOWN = 0;

	/**
	 * Advise the monitor of the total number of subtasks.
	 * <p>
	 * This should be invoked at most once per progress monitor interface.
	 * 
	 * @param totalTasks
	 *            the total number of tasks the caller will need to complete
	 *            their processing.
	 */
	void start(int totalTasks);

	/**
	 * Begin processing a single task.
	 * 
	 * @param title
	 *            title to describe the task. Callers should publish these as
	 *            stable string constants that implementations could match
	 *            against for translation support.
	 * @param totalWork
	 *            total number of work units the application will perform;
	 *            {@link #UNKNOWN} if it cannot be predicted in advance.
	 */
	void beginTask(String title, int totalWork);

	/**
	 * Denote that some work units have been completed.
	 * <p>
	 * This is an incremental update; if invoked once per work unit the correct
	 * value for our argument is <code>1</code>, to indicate a single unit of
	 * work has been finished by the caller.
	 * 
	 * @param completed
	 *            the number of work units completed since the last call.
	 */
	void update(int completed);

	/** Finish the current task, so the next can begin. */
	void endTask();

	/**
	 * Check for user task cancellation.
	 * 
	 * @return true if the user asked the process to stop working.
	 */
	boolean isCancelled();
}
