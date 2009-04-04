/*
 * Copyright (C) 2008, Google Inc.
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

package org.spearce.jgit.transport;

import java.io.IOException;

import org.spearce.jgit.lib.Repository;

/** A service exposed by {@link Daemon} over anonymous <code>git://</code>. */
public abstract class DaemonService {
	private final String command;

	private final String config;

	private boolean enabled;

	private boolean overridable;

	DaemonService(final String cmdName, final String cfgName) {
		command = cmdName.startsWith("git-") ? cmdName : "git-" + cmdName;
		config = cfgName;
		overridable = true;
	}

	/** @return is this service enabled for invocation? */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * @param on
	 *            true to allow this service to be used; false to deny it.
	 */
	public void setEnabled(final boolean on) {
		enabled = on;
	}

	/** @return can this service be configured in the repository config file? */
	public boolean isOverridable() {
		return overridable;
	}

	/**
	 * @param on
	 *            true to permit repositories to override this service's enabled
	 *            state with the <code>daemon.servicename</code> config setting.
	 */
	public void setOverridable(final boolean on) {
		overridable = on;
	}

	/** @return name of the command requested by clients. */
	public String getCommandName() {
		return command;
	}

	/**
	 * Determine if this service can handle the requested command.
	 *
	 * @param commandLine
	 *            input line from the client.
	 * @return true if this command can accept the given command line.
	 */
	public boolean handles(final String commandLine) {
		return command.length() + 1 < commandLine.length()
				&& commandLine.charAt(command.length()) == ' '
				&& commandLine.startsWith(command);
	}

	void execute(final DaemonClient client, final String commandLine)
			throws IOException {
		final String name = commandLine.substring(command.length() + 1);
		final Repository db = client.getDaemon().openRepository(name);
		if (db == null)
			return;
		boolean on = isEnabled();
		if (isOverridable())
			on = db.getConfig().getBoolean("daemon", config, on);
		if (on)
			execute(client, db);
	}

	abstract void execute(DaemonClient client, Repository db)
			throws IOException;
}
