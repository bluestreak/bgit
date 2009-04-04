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

package org.spearce.jgit.pgm;

import static org.spearce.jgit.lib.Constants.R_HEADS;
import static org.spearce.jgit.lib.Constants.R_REMOTES;
import static org.spearce.jgit.lib.Constants.R_TAGS;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.pgm.opt.CmdLineParser;
import org.spearce.jgit.revwalk.RevWalk;

/**
 * Abstract command which can be invoked from the command line.
 * <p>
 * Commands are configured with a single "current" repository and then the
 * {@link #execute(String[])} method is invoked with the arguments that appear
 * on the command line after the command name.
 * <p>
 * Command constructors should perform as little work as possible as they may be
 * invoked very early during process loading, and the command may not execute
 * even though it was constructed.
 */
public abstract class TextBuiltin {
	private String commandName;

	@Option(name = "--help", usage = "display this help text", aliases = { "-h" })
	private boolean help;

	/** Stream to output to, typically this is standard output. */
	protected PrintWriter out;

	/** Git repository the command was invoked within. */
	protected Repository db;

	/** Directory supplied via --git-dir command line option. */
	protected File gitdir;

	/** RevWalk used during command line parsing, if it was required. */
	protected RevWalk argWalk;

	final void setCommandName(final String name) {
		commandName = name;
	}

	/** @return true if {@link #db}/{@link #getRepository()} is required. */
	protected boolean requiresRepository() {
		return true;
	}

	void init(final Repository repo, final File gd) {
		try {
			final String outputEncoding = repo != null ? repo.getConfig()
					.getString("i18n", null, "logOutputEncoding") : null;
			if (outputEncoding != null)
				out = new PrintWriter(new BufferedWriter(
						new OutputStreamWriter(System.out, outputEncoding)));
			else
				out = new PrintWriter(new BufferedWriter(
						new OutputStreamWriter(System.out)));
		} catch (IOException e) {
			throw die("cannot create output stream");
		}

		if (repo != null) {
			db = repo;
			gitdir = repo.getDirectory();
		} else {
			db = null;
			gitdir = gd;
		}
	}

	/**
	 * Parse arguments and run this command.
	 *
	 * @param args
	 *            command line arguments passed after the command name.
	 * @throws Exception
	 *             an error occurred while processing the command. The main
	 *             framework will catch the exception and print a message on
	 *             standard error.
	 */
	public final void execute(String[] args) throws Exception {
		parseArguments(args);
		run();
	}

	/**
	 * Parses the command line arguments prior to running.
	 * <p>
	 * This method should only be invoked by {@link #execute(String[])}, prior
	 * to calling {@link #run()}. The default implementation parses all
	 * arguments into this object's instance fields.
	 *
	 * @param args
	 *            the arguments supplied on the command line, if any.
	 */
	protected void parseArguments(final String[] args) {
		final CmdLineParser clp = new CmdLineParser(this);
		try {
			clp.parseArgument(args);
		} catch (CmdLineException err) {
			if (!help) {
				System.err.println("fatal: " + err.getMessage());
				System.exit(1);
			}
		}

		if (help) {
			printUsageAndExit(clp);
		}

		argWalk = clp.getRevWalkGently();
	}

	/**
	 * Print the usage line
	 *
	 * @param clp
	 */
	public void printUsageAndExit(final CmdLineParser clp) {
		printUsageAndExit("", clp);
	}

	/**
	 * Print an error message and the usage line
	 *
	 * @param message
	 * @param clp
	 */
	public void printUsageAndExit(final String message, final CmdLineParser clp) {
		System.err.println(message);
		System.err.print("jgit ");
		System.err.print(commandName);
		clp.printSingleLineUsage(System.err);
		System.err.println();

		System.err.println();
		clp.printUsage(System.err);
		System.err.println();

		System.exit(1);
	}

	/**
	 * Perform the actions of this command.
	 * <p>
	 * This method should only be invoked by {@link #execute(String[])}.
	 *
	 * @throws Exception
	 *             an error occurred while processing the command. The main
	 *             framework will catch the exception and print a message on
	 *             standard error.
	 */
	protected abstract void run() throws Exception;

	/**
	 * @return the repository this command accesses.
	 */
	public Repository getRepository() {
		return db;
	}

	ObjectId resolve(final String s) throws IOException {
		final ObjectId r = db.resolve(s);
		if (r == null)
			throw die("Not a revision: " + s);
		return r;
	}

	/**
	 * @param why
	 *            textual explanation
	 * @return a runtime exception the caller is expected to throw
	 */
	protected static Die die(final String why) {
		return new Die(why);
	}

	String abbreviateRef(String dst, boolean abbreviateRemote) {
		if (dst.startsWith(R_HEADS))
			dst = dst.substring(R_HEADS.length());
		else if (dst.startsWith(R_TAGS))
			dst = dst.substring(R_TAGS.length());
		else if (abbreviateRemote && dst.startsWith(R_REMOTES))
			dst = dst.substring(R_REMOTES.length());
		return dst;
	}
}
