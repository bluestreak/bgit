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

package org.spearce.jgit.treewalk;

import org.spearce.jgit.dircache.DirCacheBuilder;
import org.spearce.jgit.errors.CorruptObjectException;
import org.spearce.jgit.lib.FileMode;
import org.spearce.jgit.lib.Repository;

/**
 * Specialized TreeWalk to detect directory-file (D/F) name conflicts.
 * <p>
 * Due to the way a Git tree is organized the standard {@link TreeWalk} won't
 * easily find a D/F conflict when merging two or more trees together. In the
 * standard TreeWalk the file will be returned first, and then much later the
 * directory will be returned. This makes it impossible for the application to
 * efficiently detect and handle the conflict.
 * <p>
 * Using this walk implementation causes the directory to report earlier than
 * usual, at the same time as the non-directory entry. This permits the
 * application to handle the D/F conflict in a single step. The directory is
 * returned only once, so it does not get returned later in the iteration.
 * <p>
 * When a D/F conflict is detected {@link TreeWalk#isSubtree()} will return true
 * and {@link TreeWalk#enterSubtree()} will recurse into the subtree, no matter
 * which iterator originally supplied the subtree.
 * <p>
 * Because conflicted directories report early, using this walk implementation
 * to populate a {@link DirCacheBuilder} may cause the automatic resorting to
 * run and fix the entry ordering.
 * <p>
 * This walk implementation requires more CPU to implement a look-ahead and a
 * look-behind to merge a D/F pair together, or to skip a previously reported
 * directory. In typical Git repositories the look-ahead cost is 0 and the
 * look-behind doesn't trigger, as users tend not to create trees which contain
 * both "foo" as a directory and "foo.c" as a file.
 * <p>
 * In the worst-case however several thousand look-ahead steps per walk step may
 * be necessary, making the overhead quite significant. Since this worst-case
 * should never happen this walk implementation has made the time/space tradeoff
 * in favor of more-time/less-space, as that better suits the typical case.
 */
public class NameConflictTreeWalk extends TreeWalk {
	private static final int TREE_MODE = FileMode.TREE.getBits();

	private boolean fastMinHasMatch;

	/**
	 * Create a new tree walker for a given repository.
	 *
	 * @param repo
	 *            the repository the walker will obtain data from.
	 */
	public NameConflictTreeWalk(final Repository repo) {
		super(repo);
	}

	@Override
	AbstractTreeIterator min() throws CorruptObjectException {
		for (;;) {
			final AbstractTreeIterator minRef = fastMin();
			if (fastMinHasMatch)
				return minRef;

			if (isTree(minRef)) {
				if (skipEntry(minRef)) {
					for (final AbstractTreeIterator t : trees) {
						if (t.matches == minRef) {
							t.next(1);
							t.matches = null;
						}
					}
					continue;
				}
				return minRef;
			}

			return combineDF(minRef);
		}
	}

	private AbstractTreeIterator fastMin() {
		fastMinHasMatch = true;

		int i = 0;
		AbstractTreeIterator minRef = trees[i];
		while (minRef.eof() && ++i < trees.length)
			minRef = trees[i];
		if (minRef.eof())
			return minRef;

		minRef.matches = minRef;
		while (++i < trees.length) {
			final AbstractTreeIterator t = trees[i];
			if (t.eof())
				continue;

			final int cmp = t.pathCompare(minRef);
			if (cmp < 0) {
				if (fastMinHasMatch && isTree(minRef) && !isTree(t)
						&& nameEqual(minRef, t)) {
					// We used to be at a tree, but now we are at a file
					// with the same name. Allow the file to match the
					// tree anyway.
					//
					t.matches = minRef;
				} else {
					fastMinHasMatch = false;
					t.matches = t;
					minRef = t;
				}
			} else if (cmp == 0) {
				// Exact name/mode match is best.
				//
				t.matches = minRef;
			} else if (fastMinHasMatch && isTree(t) && !isTree(minRef)
					&& nameEqual(t, minRef)) {
				// The minimum is a file (non-tree) but the next entry
				// of this iterator is a tree whose name matches our file.
				// This is a classic D/F conflict and commonly occurs like
				// this, with no gaps in between the file and directory.
				//
				// Use the tree as the minimum instead (see combineDF).
				//

				for (int k = 0; k < i; k++) {
					final AbstractTreeIterator p = trees[k];
					if (p.matches == minRef)
						p.matches = t;
				}
				t.matches = t;
				minRef = t;
			} else
				fastMinHasMatch = false;
		}

		return minRef;
	}

	private static boolean nameEqual(final AbstractTreeIterator a,
			final AbstractTreeIterator b) {
		return a.pathCompare(b, TREE_MODE) == 0;
	}

	private static boolean isTree(final AbstractTreeIterator p) {
		return FileMode.TREE.equals(p.mode);
	}

	private boolean skipEntry(final AbstractTreeIterator minRef)
			throws CorruptObjectException {
		// A tree D/F may have been handled earlier. We need to
		// not report this path if it has already been reported.
		//
		for (final AbstractTreeIterator t : trees) {
			if (t.matches == minRef || t.first())
				continue;

			int stepsBack = 0;
			for (;;) {
				stepsBack++;
				t.back(1);

				final int cmp = t.pathCompare(minRef, 0);
				if (cmp == 0) {
					// We have already seen this "$path" before. Skip it.
					//
					t.next(stepsBack);
					return true;
				} else if (cmp < 0 || t.first()) {
					// We cannot find "$path" in t; it will never appear.
					//
					t.next(stepsBack);
					break;
				}
			}
		}

		// We have never seen the current path before.
		//
		return false;
	}

	private AbstractTreeIterator combineDF(final AbstractTreeIterator minRef)
			throws CorruptObjectException {
		// Look for a possible D/F conflict forward in the tree(s)
		// as there may be a "$path/" which matches "$path". Make
		// such entries match this entry.
		//
		AbstractTreeIterator treeMatch = null;
		for (final AbstractTreeIterator t : trees) {
			if (t.matches == minRef || t.eof())
				continue;

			for (;;) {
				final int cmp = t.pathCompare(minRef, TREE_MODE);
				if (cmp < 0) {
					// The "$path/" may still appear later.
					//
					t.matchShift++;
					t.next(1);
					if (t.eof()) {
						t.back(t.matchShift);
						t.matchShift = 0;
						break;
					}
				} else if (cmp == 0) {
					// We have a conflict match here.
					//
					t.matches = minRef;
					treeMatch = t;
					break;
				} else {
					// A conflict match is not possible.
					//
					if (t.matchShift != 0) {
						t.back(t.matchShift);
						t.matchShift = 0;
					}
					break;
				}
			}
		}

		if (treeMatch != null) {
			// If we do have a conflict use one of the directory
			// matching iterators instead of the file iterator.
			// This way isSubtree is true and isRecursive works.
			//
			for (final AbstractTreeIterator t : trees)
				if (t.matches == minRef)
					t.matches = treeMatch;
			return treeMatch;
		}

		return minRef;
	}

	@Override
	void popEntriesEqual() throws CorruptObjectException {
		final AbstractTreeIterator ch = currentHead;
		for (int i = 0; i < trees.length; i++) {
			final AbstractTreeIterator t = trees[i];
			if (t.matches == ch) {
				if (t.matchShift == 0)
					t.next(1);
				else {
					t.back(t.matchShift);
					t.matchShift = 0;
				}
				t.matches = null;
			}
		}
	}

	@Override
	void skipEntriesEqual() throws CorruptObjectException {
		final AbstractTreeIterator ch = currentHead;
		for (int i = 0; i < trees.length; i++) {
			final AbstractTreeIterator t = trees[i];
			if (t.matches == ch) {
				if (t.matchShift == 0)
					t.skip();
				else {
					t.back(t.matchShift);
					t.matchShift = 0;
				}
				t.matches = null;
			}
		}
	}
}
