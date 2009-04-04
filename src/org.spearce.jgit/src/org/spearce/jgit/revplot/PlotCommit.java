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

package org.spearce.jgit.revplot;

import org.spearce.jgit.lib.AnyObjectId;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.lib.Ref;

/**
 * A commit reference to a commit in the DAG.
 * 
 * @param <L>
 *            type of lane being used by the plotter.
 * @see PlotCommitList
 */
public class PlotCommit<L extends PlotLane> extends RevCommit {
	static final PlotCommit[] NO_CHILDREN = {};

	static final PlotLane[] NO_LANES = {};

	PlotLane[] passingLanes;

	PlotLane lane;

	PlotCommit[] children;

	final Ref[] refs;

	/**
	 * Create a new commit.
	 * 
	 * @param id
	 *            the identity of this commit.
	 * @param tags
	 *            the tags associated with this commit, null for no tags
	 */
	protected PlotCommit(final AnyObjectId id, final Ref[] tags) {
		super(id);
		this.refs = tags;
		passingLanes = NO_LANES;
		children = NO_CHILDREN;
	}

	void addPassingLane(final PlotLane c) {
		final int cnt = passingLanes.length;
		if (cnt == 0)
			passingLanes = new PlotLane[] { c };
		else if (cnt == 1)
			passingLanes = new PlotLane[] { passingLanes[0], c };
		else {
			final PlotLane[] n = new PlotLane[cnt + 1];
			System.arraycopy(passingLanes, 0, n, 0, cnt);
			n[cnt] = c;
			passingLanes = n;
		}
	}

	void addChild(final PlotCommit c) {
		final int cnt = children.length;
		if (cnt == 0)
			children = new PlotCommit[] { c };
		else if (cnt == 1)
			children = new PlotCommit[] { children[0], c };
		else {
			final PlotCommit[] n = new PlotCommit[cnt + 1];
			System.arraycopy(children, 0, n, 0, cnt);
			n[cnt] = c;
			children = n;
		}
	}

	/**
	 * Get the number of child commits listed in this commit.
	 * 
	 * @return number of children; always a positive value but can be 0.
	 */
	public final int getChildCount() {
		return children.length;
	}

	/**
	 * Get the nth child from this commit's child list.
	 * 
	 * @param nth
	 *            child index to obtain. Must be in the range 0 through
	 *            {@link #getChildCount()}-1.
	 * @return the specified child.
	 * @throws ArrayIndexOutOfBoundsException
	 *             an invalid child index was specified.
	 */
	public final PlotCommit getChild(final int nth) {
		return children[nth];
	}

	/**
	 * Determine if the given commit is a child (descendant) of this commit.
	 * 
	 * @param c
	 *            the commit to test.
	 * @return true if the given commit built on top of this commit.
	 */
	public final boolean isChild(final PlotCommit c) {
		for (final PlotCommit a : children)
			if (a == c)
				return true;
		return false;
	}

	/**
	 * Obtain the lane this commit has been plotted into.
	 * 
	 * @return the assigned lane for this commit.
	 */
	public final L getLane() {
		return (L) lane;
	}

	@Override
	public void reset() {
		passingLanes = NO_LANES;
		children = NO_CHILDREN;
		lane = null;
		super.reset();
	}
}
