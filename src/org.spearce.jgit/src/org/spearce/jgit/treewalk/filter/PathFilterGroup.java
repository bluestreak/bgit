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

package org.spearce.jgit.treewalk.filter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

import org.spearce.jgit.errors.StopWalkException;
import org.spearce.jgit.treewalk.TreeWalk;

/**
 * Includes tree entries only if they match one or more configured paths.
 * <p>
 * Operates like {@link PathFilter} but causes the walk to abort as soon as the
 * tree can no longer match any of the paths within the group. This may bypass
 * the boolean logic of a higher level AND or OR group, but does improve
 * performance for the common case of examining one or more modified paths.
 * <p>
 * This filter is effectively an OR group around paths, with the early abort
 * feature described above.
 */
public class PathFilterGroup {
	/**
	 * Create a collection of path filters from Java strings.
	 * <p>
	 * Path strings are relative to the root of the repository. If the user's
	 * input should be assumed relative to a subdirectory of the repository the
	 * caller must prepend the subdirectory's path prior to creating the filter.
	 * <p>
	 * Path strings use '/' to delimit directories on all platforms.
	 * <p>
	 * Paths may appear in any order within the collection. Sorting may be done
	 * internally when the group is constructed if doing so will improve path
	 * matching performance.
	 * 
	 * @param paths
	 *            the paths to test against. Must have at least one entry.
	 * @return a new filter for the list of paths supplied.
	 */
	public static TreeFilter createFromStrings(final Collection<String> paths) {
		if (paths.isEmpty())
			throw new IllegalArgumentException("At least one path is required.");
		final PathFilter[] p = new PathFilter[paths.size()];
		int i = 0;
		for (final String s : paths)
			p[i++] = PathFilter.create(s);
		return create(p);
	}

	/**
	 * Create a collection of path filters.
	 * <p>
	 * Paths may appear in any order within the collection. Sorting may be done
	 * internally when the group is constructed if doing so will improve path
	 * matching performance.
	 * 
	 * @param paths
	 *            the paths to test against. Must have at least one entry.
	 * @return a new filter for the list of paths supplied.
	 */
	public static TreeFilter create(final Collection<PathFilter> paths) {
		if (paths.isEmpty())
			throw new IllegalArgumentException("At least one path is required.");
		final PathFilter[] p = new PathFilter[paths.size()];
		paths.toArray(p);
		return create(p);
	}

	private static TreeFilter create(final PathFilter[] p) {
		if (p.length == 1)
			return new Single(p[0]);
		return new Group(p);
	}

	static class Single extends TreeFilter {
		private final PathFilter path;

		private final byte[] raw;

		private Single(final PathFilter p) {
			path = p;
			raw = path.pathRaw;
		}

		@Override
		public boolean include(final TreeWalk walker) {
			final int cmp = walker.isPathPrefix(raw, raw.length);
			if (cmp > 0)
				throw StopWalkException.INSTANCE;
			return cmp == 0;
		}

		@Override
		public boolean shouldBeRecursive() {
			return path.shouldBeRecursive();
		}

		@Override
		public TreeFilter clone() {
			return this;
		}

		public String toString() {
			return "FAST_" + path.toString();
		}
	}

	static class Group extends TreeFilter {
		private static final Comparator<PathFilter> PATH_SORT = new Comparator<PathFilter>() {
			public int compare(final PathFilter o1, final PathFilter o2) {
				return o1.pathStr.compareTo(o2.pathStr);
			}
		};

		private final PathFilter[] paths;

		private Group(final PathFilter[] p) {
			paths = p;
			Arrays.sort(paths, PATH_SORT);
		}

		@Override
		public boolean include(final TreeWalk walker) {
			final int n = paths.length;
			for (int i = 0;;) {
				final byte[] r = paths[i].pathRaw;
				final int cmp = walker.isPathPrefix(r, r.length);
				if (cmp == 0)
					return true;
				if (++i < n)
					continue;
				if (cmp > 0)
					throw StopWalkException.INSTANCE;
				return false;
			}
		}

		@Override
		public boolean shouldBeRecursive() {
			for (final PathFilter p : paths)
				if (p.shouldBeRecursive())
					return true;
			return false;
		}

		@Override
		public TreeFilter clone() {
			return this;
		}

		public String toString() {
			final StringBuffer r = new StringBuffer();
			r.append("FAST(");
			for (int i = 0; i < paths.length; i++) {
				if (i > 0)
					r.append(" OR ");
				r.append(paths[i].toString());
			}
			r.append(")");
			return r.toString();
		}
	}
}
