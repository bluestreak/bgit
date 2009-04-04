/*
 * Copyright (C) 2006, Robin Rosenberg <robin.rosenberg@dewire.com>
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

package org.spearce.jgit.pgm;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.ExampleMode;
import org.kohsuke.args4j.Option;
import org.spearce.jgit.awtui.AwtAuthenticator;
import org.spearce.jgit.errors.TransportException;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.pgm.opt.CmdLineParser;
import org.spearce.jgit.pgm.opt.SubcommandHandler;
import org.spearce.jgit.util.HttpSupport;

/** Command line entry point. */
public class Main {
	@Option(name = "--help", usage = "display this help text", aliases = { "-h" })
	private boolean help;

	@Option(name = "--show-stack-trace", usage = "display the Java stack trace on exceptions")
	private boolean showStackTrace;

	@Option(name = "--git-dir", metaVar = "GIT_DIR", usage = "set the git repository to operate on")
	private File gitdir;

	@Argument(index = 0, metaVar = "command", required = true, handler = SubcommandHandler.class)
	private TextBuiltin subcommand;

	@Argument(index = 1, metaVar = "ARG")
	private List<String> arguments = new ArrayList<String>();

	/**
	 * Execute the command line.
	 * 
	 * @param argv
	 *            arguments.
	 */
	public static void main(final String[] argv) {
		final Main me = new Main();
		try {
			AwtAuthenticator.install();
			HttpSupport.configureHttpProxy();
			me.execute(argv);
		} catch (Die err) {
			System.err.println("fatal: " + err.getMessage());
			if (me.showStackTrace)
				err.printStackTrace();
			System.exit(128);
		} catch (Exception err) {
			if (!me.showStackTrace && err.getCause() != null
					&& err instanceof TransportException)
				System.err.println("fatal: " + err.getCause().getMessage());

			if (err.getClass().getName().startsWith("org.spearce.jgit.errors.")) {
				System.err.println("fatal: " + err.getMessage());
				if (me.showStackTrace)
					err.printStackTrace();
				System.exit(128);
			}
			err.printStackTrace();
			System.exit(1);
		}
	}

	public void execute(final String[] argv) throws Exception {
		final CmdLineParser clp = new CmdLineParser(this);
        System.out.println("Length:"+argv.length);
		try {
			clp.parseArgument(argv);
		} catch (CmdLineException err) {
			if (argv.length > 0 && !help) {
				System.err.println("fatal: " + err.getMessage());
				System.exit(1);
			}
		}

		if (argv.length == 0 || help) {
			final String ex = clp.printExample(ExampleMode.ALL);
			System.err.println("jgit" + ex + " command [ARG ...]");
			if (help) {
				System.err.println();
				clp.printUsage(System.err);
				System.err.println();
			} else if (subcommand == null) {
				System.err.println();
				System.err.println("The most commonly used commands are:");
				final CommandRef[] common = CommandCatalog.common();
				int width = 0;
				for (final CommandRef c : common)
					width = Math.max(width, c.getName().length());
				width += 2;

				for (final CommandRef c : common) {
					System.err.print(' ');
					System.err.print(c.getName());
					for (int i = c.getName().length(); i < width; i++)
						System.err.print(' ');
					System.err.print(c.getUsage());
					System.err.println();
				}
				System.err.println();
			}
			System.exit(1);
		}

		final TextBuiltin cmd = subcommand;
		if (cmd.requiresRepository()) {
			if (gitdir == null)
				gitdir = findGitDir();
			if (gitdir == null || !gitdir.isDirectory()) {
				System.err.println("error: can't find git directory");
				System.exit(1);
			}
			cmd.init(new Repository(gitdir), gitdir);
		} else {
			cmd.init(null, gitdir);
		}
		try {
			cmd.execute(arguments.toArray(new String[arguments.size()]));
		} finally {
			if (cmd.out != null)
				cmd.out.flush();
		}
	}

	private static File findGitDir() {
		File current = new File(".").getAbsoluteFile();
		while (current != null) {
			final File gitDir = new File(current, ".git");
			if (gitDir.isDirectory())
				return gitDir;
			current = current.getParentFile();
		}
		return null;
	}
}
