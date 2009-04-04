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

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StopOptionHandler;
import org.spearce.jgit.dircache.DirCache;
import org.spearce.jgit.dircache.DirCacheBuildIterator;
import org.spearce.jgit.dircache.DirCacheBuilder;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.FileMode;
import org.spearce.jgit.pgm.opt.PathTreeFilterHandler;
import org.spearce.jgit.treewalk.TreeWalk;
import org.spearce.jgit.treewalk.filter.TreeFilter;

@Command(usage = "Stop tracking a file", common = true)
class Rm extends TextBuiltin {
	@Argument(metaVar = "path", usage = "path", multiValued = true, required = true, handler = PathTreeFilterHandler.class)
	@Option(name = "--", handler = StopOptionHandler.class)
	private TreeFilter paths;

	private File root;

	@Override
	protected void run() throws Exception {
		root = db.getWorkDir();

		final DirCache dirc = DirCache.lock(db);
		final DirCacheBuilder edit = dirc.builder();

		final TreeWalk walk = new TreeWalk(db);
		walk.reset(); // drop the first empty tree, which we do not need here
		walk.setRecursive(true);
		walk.setFilter(paths);
		walk.addTree(new DirCacheBuildIterator(edit));

		while (walk.next()) {
			final File path = new File(root, walk.getPathString());
			final FileMode mode = walk.getFileMode(0);
			if (mode.getObjectType() == Constants.OBJ_BLOB) {
				// Deleting a blob is simply a matter of removing
				// the file or symlink named by the tree entry.
				delete(path);
			}
		}

		edit.commit();
	}

	private void delete(File p) {
		while (p != null && !p.equals(root) && p.delete())
			p = p.getParentFile();
	}
}
