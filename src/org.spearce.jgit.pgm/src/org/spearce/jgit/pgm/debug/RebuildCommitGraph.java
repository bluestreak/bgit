/*
 * Copyright (C) 2009, Google Inc.
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

package org.spearce.jgit.pgm.debug;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.spearce.jgit.errors.MissingObjectException;
import org.spearce.jgit.errors.ObjectWritingException;
import org.spearce.jgit.lib.Commit;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.LockFile;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.ObjectWriter;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.lib.ProgressMonitor;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.RefUpdate;
import org.spearce.jgit.lib.RefWriter;
import org.spearce.jgit.lib.TextProgressMonitor;
import org.spearce.jgit.lib.Tree;
import org.spearce.jgit.pgm.TextBuiltin;
import org.spearce.jgit.revwalk.RevWalk;

/**
 * Recreates a repository from another one's commit graph.
 * <p>
 * <b>Do not run this on a repository unless you want to destroy it.</b>
 * <p>
 * To create the input files, in the source repository use:
 *
 * <pre>
 * git for-each-ref &gt;in.refs
 * git log --all '--pretty=format:%H %ct %P' &gt;in.dag
 * </pre>
 * <p>
 * Run the rebuild in either an empty repository, or a clone of the source. Any
 * missing commits (which might be the entire graph) will be created. All refs
 * will be modified to match the input exactly, which means some refs may be
 * deleted from the current repository.
 * <p>
 */
class RebuildCommitGraph extends TextBuiltin {
	private final String REALLY = "--destroy-this-repository";

	@Option(name = REALLY, usage = "approve destruction of repository")
	boolean really;

	@Argument(index = 0, required = true, metaVar = "REFS", usage = "for-each-ref output")
	File refList;

	@Argument(index = 1, required = true, metaVar = "DAG", usage = "log --all '--pretty=format:%H %ct %P' output")
	File graph;

	private final ProgressMonitor pm = new TextProgressMonitor();

	private Map<ObjectId, ObjectId> rewrites = new HashMap<ObjectId, ObjectId>();

	@Override
	protected void run() throws Exception {
		if (!really && !db.getAllRefs().isEmpty()) {
			final StringBuilder m = new StringBuilder();
			m.append("fatal: ");
			m.append("This program will destroy the repository:");
			m.append("\n");
			m.append("fatal:\n");
			m.append("fatal:    ");
			m.append(db.getDirectory().getAbsolutePath());
			m.append("\n");
			m.append("fatal:\n");
			m.append("fatal: ");
			m.append("To continue, add ");
			m.append(REALLY);
			m.append(" to the command line");
			m.append("\n");
			m.append("fatal:");
			System.err.println(m);
			throw die("Need approval to destroy current repository");
		}
		if (!refList.isFile())
			throw die("no such file: " + refList.getPath());
		if (!graph.isFile())
			throw die("no such file: " + graph.getPath());

		recreateCommitGraph();
		detachHead();
		deleteAllRefs();
		recreateRefs();
	}

	private void recreateCommitGraph() throws IOException {
		final RevWalk rw = new RevWalk(db);
		final Map<ObjectId, ToRewrite> toRewrite = new HashMap<ObjectId, ToRewrite>();
		List<ToRewrite> queue = new ArrayList<ToRewrite>();
		final BufferedReader br = new BufferedReader(new InputStreamReader(
				new FileInputStream(graph), Constants.CHARSET));
		try {
			String line;
			while ((line = br.readLine()) != null) {
				final String[] parts = line.split("[ \t]{1,}");
				final ObjectId oldId = ObjectId.fromString(parts[0]);
				try {
					rw.parseCommit(oldId);
					// We have it already. Don't rewrite it.
					continue;
				} catch (MissingObjectException mue) {
					// Fall through and rewrite it.
				}

				final long time = Long.parseLong(parts[1]) * 1000L;
				final ObjectId[] parents = new ObjectId[parts.length - 2];
				for (int i = 0; i < parents.length; i++) {
					parents[i] = ObjectId.fromString(parts[2 + i]);
				}

				final ToRewrite t = new ToRewrite(oldId, time, parents);
				toRewrite.put(oldId, t);
				queue.add(t);
			}
		} finally {
			br.close();
		}

		pm.beginTask("Rewriting commits", queue.size());
		final ObjectWriter ow = new ObjectWriter(db);
		final ObjectId emptyTree = ow.writeTree(new Tree(db));
		final PersonIdent me = new PersonIdent("jgit rebuild-commitgraph",
				"rebuild-commitgraph@localhost");
		while (!queue.isEmpty()) {
			final ListIterator<ToRewrite> itr = queue
					.listIterator(queue.size());
			queue = new ArrayList<ToRewrite>();
			REWRITE: while (itr.hasPrevious()) {
				final ToRewrite t = itr.previous();
				final ObjectId[] newParents = new ObjectId[t.oldParents.length];
				for (int k = 0; k < t.oldParents.length; k++) {
					final ToRewrite p = toRewrite.get(t.oldParents[k]);
					if (p != null) {
						if (p.newId == null) {
							// Must defer until after the parent is rewritten.
							queue.add(t);
							continue REWRITE;
						} else {
							newParents[k] = p.newId;
						}
					} else {
						// We have the old parent object. Use it.
						//
						newParents[k] = t.oldParents[k];
					}
				}

				final Commit newc = new Commit(db);
				newc.setTreeId(emptyTree);
				newc.setAuthor(new PersonIdent(me, new Date(t.commitTime)));
				newc.setCommitter(newc.getAuthor());
				newc.setParentIds(newParents);
				newc.setMessage("ORIGINAL " + t.oldId.name() + "\n");
				t.newId = ow.writeCommit(newc);
				rewrites.put(t.oldId, t.newId);
				pm.update(1);
			}
		}
		pm.endTask();
	}

	private static class ToRewrite {
		final ObjectId oldId;

		final long commitTime;

		final ObjectId[] oldParents;

		ObjectId newId;

		ToRewrite(final ObjectId o, final long t, final ObjectId[] p) {
			oldId = o;
			commitTime = t;
			oldParents = p;
		}
	}

	private void detachHead() throws IOException {
		final String head = db.getFullBranch();
		final ObjectId id = db.resolve(Constants.HEAD);
		if (!ObjectId.isId(head) && id != null) {
			final LockFile lf;
			lf = new LockFile(new File(db.getDirectory(), Constants.HEAD));
			if (!lf.lock())
				throw new IOException("Cannot lock HEAD");
			lf.write(id);
			if (!lf.commit())
				throw new IOException("Cannot deatch HEAD");
		}
	}

	private void deleteAllRefs() throws Exception {
		final RevWalk rw = new RevWalk(db);
		for (final Ref r : db.getAllRefs().values()) {
			if (Constants.HEAD.equals(r.getName()))
				continue;
			final RefUpdate u = db.updateRef(r.getName());
			u.setForceUpdate(true);
			u.delete(rw);
		}
	}

	private void recreateRefs() throws Exception {
		final Map<String, Ref> refs = computeNewRefs();
		new RefWriter(refs.values()) {
			@Override
			protected void writeFile(final String name, final byte[] content)
					throws IOException {
				final File file = new File(db.getDirectory(), name);
				final LockFile lck = new LockFile(file);
				if (!lck.lock())
					throw new ObjectWritingException("Can't write " + file);
				try {
					lck.write(content);
				} catch (IOException ioe) {
					throw new ObjectWritingException("Can't write " + file);
				}
				if (!lck.commit())
					throw new ObjectWritingException("Can't write " + file);
			}
		}.writePackedRefs();
	}

	private Map<String, Ref> computeNewRefs() throws IOException {
		final RevWalk rw = new RevWalk(db);
		final Map<String, Ref> refs = new HashMap<String, Ref>();
		final BufferedReader br = new BufferedReader(new InputStreamReader(
				new FileInputStream(refList), Constants.CHARSET));
		try {
			String line;
			while ((line = br.readLine()) != null) {
				final String[] parts = line.split("[ \t]{1,}");
				final ObjectId origId = ObjectId.fromString(parts[0]);
				final String type = parts[1];
				final String name = parts[2];

				ObjectId id = rewrites.get(origId);
				if (id == null)
					id = origId;
				try {
					rw.parseAny(id);
				} catch (MissingObjectException mue) {
					if (!Constants.TYPE_COMMIT.equals(type)) {
						System.err.println("skipping " + type + " " + name);
						continue;
					}
					throw new MissingObjectException(id, type);
				}
				refs.put(name, new Ref(Ref.Storage.PACKED, name, id));
			}
		} finally {
			br.close();
		}
		return refs;
	}
}
