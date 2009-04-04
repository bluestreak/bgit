/*
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

package org.spearce.jgit.dircache;

import java.io.IOException;

import org.spearce.jgit.errors.CorruptObjectException;
import org.spearce.jgit.errors.IncorrectObjectTypeException;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.treewalk.AbstractTreeIterator;

/**
 * Iterate and update a {@link DirCache} as part of a <code>TreeWalk</code>.
 * <p>
 * Like {@link DirCacheIterator} this iterator allows a DirCache to be used in
 * parallel with other sorts of iterators in a TreeWalk. However any entry which
 * appears in the source DirCache and which is skipped by the TreeFilter is
 * automatically copied into {@link DirCacheBuilder}, thus retaining it in the
 * newly updated index.
 * <p>
 * This iterator is suitable for update processes, or even a simple delete
 * algorithm. For example deleting a path:
 *
 * <pre>
 * final DirCache dirc = DirCache.lock(db);
 * final DirCacheBuilder edit = dirc.builder();
 *
 * final TreeWalk walk = new TreeWalk(db);
 * walk.reset();
 * walk.setRecursive(true);
 * walk.setFilter(PathFilter.create(&quot;name/to/remove&quot;));
 * walk.addTree(new DirCacheBuildIterator(edit));
 *
 * while (walk.next())
 * 	; // do nothing on a match as we want to remove matches
 * edit.commit();
 * </pre>
 */
public class DirCacheBuildIterator extends DirCacheIterator {
	private final DirCacheBuilder builder;

	/**
	 * Create a new iterator for an already loaded DirCache instance.
	 * <p>
	 * The iterator implementation may copy part of the cache's data during
	 * construction, so the cache must be read in prior to creating the
	 * iterator.
	 *
	 * @param dcb
	 *            the cache builder for the cache to walk. The cache must be
	 *            already loaded into memory.
	 */
	public DirCacheBuildIterator(final DirCacheBuilder dcb) {
		super(dcb.getDirCache());
		builder = dcb;
	}

	DirCacheBuildIterator(final DirCacheBuildIterator p,
			final DirCacheTree dct) {
		super(p, dct);
		builder = p.builder;
	}

	@Override
	public AbstractTreeIterator createSubtreeIterator(final Repository repo)
			throws IncorrectObjectTypeException, IOException {
		if (currentSubtree == null)
			throw new IncorrectObjectTypeException(getEntryObjectId(),
					Constants.TYPE_TREE);
		return new DirCacheBuildIterator(this, currentSubtree);
	}

	@Override
	public void skip() throws CorruptObjectException {
		if (currentSubtree != null)
			builder.keep(ptr, currentSubtree.getEntrySpan());
		else
			builder.add(currentEntry);
		next(1);
	}

	@Override
	public void stopWalk() {
		final int cur = ptr;
		final int cnt = cache.getEntryCount();
		if (cur < cnt)
			builder.keep(cur, cnt - cur);
	}
}
