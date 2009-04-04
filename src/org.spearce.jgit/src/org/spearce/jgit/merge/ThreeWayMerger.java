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

package org.spearce.jgit.merge;

import java.io.IOException;

import org.spearce.jgit.errors.IncorrectObjectTypeException;
import org.spearce.jgit.errors.MissingObjectException;
import org.spearce.jgit.lib.AnyObjectId;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.revwalk.RevTree;
import org.spearce.jgit.treewalk.AbstractTreeIterator;

/** A merge of 2 trees, using a common base ancestor tree. */
public abstract class ThreeWayMerger extends Merger {
	private RevTree baseTree;

	/**
	 * Create a new merge instance for a repository.
	 *
	 * @param local
	 *            the repository this merger will read and write data on.
	 */
	protected ThreeWayMerger(final Repository local) {
		super(local);
	}

	/**
	 * Set the common ancestor tree.
	 *
	 * @param id
	 *            common base treeish; null to automatically compute the common
	 *            base from the input commits during
	 *            {@link #merge(AnyObjectId, AnyObjectId)}.
	 * @throws IncorrectObjectTypeException
	 *             the object is not a treeish.
	 * @throws MissingObjectException
	 *             the object does not exist.
	 * @throws IOException
	 *             the object could not be read.
	 */
	public void setBase(final AnyObjectId id) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		if (id != null) {
			baseTree = walk.parseTree(id);
		} else {
			baseTree = null;
		}
	}

	/**
	 * Merge together two tree-ish objects.
	 * <p>
	 * Any tree-ish may be supplied as inputs. Commits and/or tags pointing at
	 * trees or commits may be passed as input objects.
	 *
	 * @param a
	 *            source tree to be combined together.
	 * @param b
	 *            source tree to be combined together.
	 * @return true if the merge was completed without conflicts; false if the
	 *         merge strategy cannot handle this merge or there were conflicts
	 *         preventing it from automatically resolving all paths.
	 * @throws IncorrectObjectTypeException
	 *             one of the input objects is not a commit, but the strategy
	 *             requires it to be a commit.
	 * @throws IOException
	 *             one or more sources could not be read, or outputs could not
	 *             be written to the Repository.
	 */
	public boolean merge(final AnyObjectId a, final AnyObjectId b)
			throws IOException {
		return merge(new AnyObjectId[] { a, b });
	}

	@Override
	public boolean merge(final AnyObjectId[] tips) throws IOException {
		if (tips.length != 2)
			return false;
		return super.merge(tips);
	}

	/**
	 * Create an iterator to walk the merge base.
	 *
	 * @return an iterator over the caller-specified merge base, or the natural
	 *         merge base of the two input commits.
	 * @throws IOException
	 */
	protected AbstractTreeIterator mergeBase() throws IOException {
		if (baseTree != null)
			return openTree(baseTree);
		return mergeBase(0, 1);
	}
}
