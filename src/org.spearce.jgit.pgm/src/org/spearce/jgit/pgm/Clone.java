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

package org.spearce.jgit.pgm;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.spearce.jgit.errors.NotSupportedException;
import org.spearce.jgit.errors.TransportException;
import org.spearce.jgit.lib.Commit;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.GitIndex;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.RefComparator;
import org.spearce.jgit.lib.RefUpdate;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.lib.TextProgressMonitor;
import org.spearce.jgit.lib.Tree;
import org.spearce.jgit.lib.WorkDirCheckout;
import org.spearce.jgit.transport.FetchResult;
import org.spearce.jgit.transport.RefSpec;
import org.spearce.jgit.transport.RemoteConfig;
import org.spearce.jgit.transport.Transport;
import org.spearce.jgit.transport.URIish;

@Command(common = true, usage = "Clone a repository into a new directory")
class Clone extends AbstractFetchCommand {
	@Option(name = "--origin", aliases = { "-o" }, metaVar = "name", usage = "use <name> instead of 'origin' to track upstream")
	private String remoteName = "origin";

	@Argument(index = 0, required = true, metaVar = "uri-ish")
	private String sourceUri;

	@Argument(index = 1, metaVar = "directory")
	private String localName;

	@Override
	protected final boolean requiresRepository() {
		return false;
	}

	@Override
	protected void run() throws Exception {
		if (localName != null && gitdir != null)
			throw die("conflicting usage of --git-dir and arguments");

		final URIish uri = new URIish(sourceUri);
		if (localName == null) {
			String p = uri.getPath();
			while (p.endsWith("/"))
				p = p.substring(0, p.length() - 1);
			final int s = p.lastIndexOf('/');
			if (s < 0)
				throw die("cannot guess local name from " + sourceUri);
			localName = p.substring(s + 1);
			if (localName.endsWith(".git"))
				localName = localName.substring(0, localName.length() - 4);
		}
		if (gitdir == null)
			gitdir = new File(localName, ".git");

		db = new Repository(gitdir);
		db.create();
		db.getConfig().setBoolean("core", null, "bare", false);
		db.getConfig().save();

		out.println("Initialized empty Git repository in "
				+ gitdir.getAbsolutePath());
		out.flush();

		saveRemote(uri);
		final FetchResult r = runFetch();
		final Ref branch = guessHEAD(r);
		doCheckout(branch);
	}

	private void saveRemote(final URIish uri) throws URISyntaxException,
			IOException {
		final RemoteConfig rc = new RemoteConfig(db.getConfig(), remoteName);
		rc.addURI(uri);
		rc.addFetchRefSpec(new RefSpec().setForceUpdate(true)
				.setSourceDestination(Constants.R_HEADS + "*",
						Constants.R_REMOTES + remoteName + "/*"));
		rc.update(db.getConfig());
		db.getConfig().save();
	}

	private FetchResult runFetch() throws NotSupportedException,
			URISyntaxException, TransportException {
		final Transport tn = Transport.open(db, remoteName);
		final FetchResult r;
		try {
			r = tn.fetch(new TextProgressMonitor(), null);
		} finally {
			tn.close();
		}
		showFetchResult(tn, r);
		return r;
	}

	private Ref guessHEAD(final FetchResult result) {
		final Ref idHEAD = result.getAdvertisedRef(Constants.HEAD);
		final List<Ref> availableRefs = new ArrayList<Ref>();
		Ref head = null;
		for (final Ref r : result.getAdvertisedRefs()) {
			final String n = r.getName();
			if (!n.startsWith(Constants.R_HEADS))
				continue;
			availableRefs.add(r);
			if (idHEAD == null || head != null)
				continue;
			if (r.getObjectId().equals(idHEAD.getObjectId()))
				head = r;
		}
		Collections.sort(availableRefs, RefComparator.INSTANCE);
		if (idHEAD != null && head == null)
			head = idHEAD;
		return head;
	}

	private void doCheckout(final Ref branch) throws IOException {
		if (branch == null)
			throw die("cannot checkout; no HEAD advertised by remote");
		if (!Constants.HEAD.equals(branch.getName()))
			db.writeSymref(Constants.HEAD, branch.getName());

		final Commit commit = db.mapCommit(branch.getObjectId());
		final RefUpdate u = db.updateRef(Constants.HEAD);
		u.setNewObjectId(commit.getCommitId());
		u.forceUpdate();

		final GitIndex index = new GitIndex(db);
		final Tree tree = commit.getTree();
		final WorkDirCheckout co;

		co = new WorkDirCheckout(db, db.getWorkDir(), index, tree);
		co.checkout();
		index.write();
	}
}
