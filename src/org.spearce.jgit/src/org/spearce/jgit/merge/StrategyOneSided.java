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

package org.spearce.jgit.merge;

import java.io.IOException;

import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.Repository;

/**
 * Trivial merge strategy to make the resulting tree exactly match an input.
 * <p>
 * This strategy can be used to cauterize an entire side branch of history, by
 * setting the output tree to one of the inputs, and ignoring any of the paths
 * of the other inputs.
 */
public class StrategyOneSided extends MergeStrategy {
	private final String strategyName;

	private final int treeIndex;

	/**
	 * Create a new merge strategy to select a specific input tree.
	 *
	 * @param name
	 *            name of this strategy.
	 * @param index
	 *            the position of the input tree to accept as the result.
	 */
	protected StrategyOneSided(final String name, final int index) {
		strategyName = name;
		treeIndex = index;
	}

	@Override
	public String getName() {
		return strategyName;
	}

	@Override
	public Merger newMerger(final Repository db) {
		return new OneSide(db, treeIndex);
	}

	static class OneSide extends Merger {
		private final int treeIndex;

		protected OneSide(final Repository local, final int index) {
			super(local);
			treeIndex = index;
		}

		@Override
		protected boolean mergeImpl() throws IOException {
			return treeIndex < sourceTrees.length;
		}

		@Override
		public ObjectId getResultTreeId() {
			return sourceTrees[treeIndex];
		}
	}
}
