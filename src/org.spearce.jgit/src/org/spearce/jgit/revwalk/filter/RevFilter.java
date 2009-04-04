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

package org.spearce.jgit.revwalk.filter;

import java.io.IOException;

import org.spearce.jgit.errors.IncorrectObjectTypeException;
import org.spearce.jgit.errors.MissingObjectException;
import org.spearce.jgit.errors.StopWalkException;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevWalk;

/**
 * Selects interesting revisions during walking.
 * <p>
 * This is an abstract interface. Applications may implement a subclass, or use
 * one of the predefined implementations already available within this package.
 * Filters may be chained together using <code>AndRevFilter</code> and
 * <code>OrRevFilter</code> to create complex boolean expressions.
 * <p>
 * Applications should install the filter on a RevWalk by
 * {@link RevWalk#setRevFilter(RevFilter)} prior to starting traversal.
 * <p>
 * Unless specifically noted otherwise a RevFilter implementation is not thread
 * safe and may not be shared by different RevWalk instances at the same time.
 * This restriction allows RevFilter implementations to cache state within their
 * instances during {@link #include(RevWalk, RevCommit)} if it is beneficial to
 * their implementation. Deep clones created by {@link #clone()} may be used to
 * construct a thread-safe copy of an existing filter.
 *
 * <p>
 * <b>Message filters:</b>
 * <ul>
 * <li>Author name/email: {@link AuthorRevFilter}</li>
 * <li>Committer name/email: {@link CommitterRevFilter}</li>
 * <li>Message body: {@link MessageRevFilter}</li>
 * </ul>
 *
 * <p>
 * <b>Merge filters:</b>
 * <ul>
 * <li>Skip all merges: {@link #NO_MERGES}.</li>
 * </ul>
 *
 * <p>
 * <b>Boolean modifiers:</b>
 * <ul>
 * <li>AND: {@link AndRevFilter}</li>
 * <li>OR: {@link OrRevFilter}</li>
 * <li>NOT: {@link NotRevFilter}</li>
 * </ul>
 */
public abstract class RevFilter {
	/** Default filter that always returns true (thread safe). */
	public static final RevFilter ALL = new RevFilter() {
		@Override
		public boolean include(final RevWalk walker, final RevCommit c) {
			return true;
		}

		@Override
		public RevFilter clone() {
			return this;
		}

		@Override
		public String toString() {
			return "ALL";
		}
	};

	/** Default filter that always returns false (thread safe). */
	public static final RevFilter NONE = new RevFilter() {
		@Override
		public boolean include(final RevWalk walker, final RevCommit c) {
			return false;
		}

		@Override
		public RevFilter clone() {
			return this;
		}

		@Override
		public String toString() {
			return "NONE";
		}
	};

	/** Excludes commits with more than one parent (thread safe). */
	public static final RevFilter NO_MERGES = new RevFilter() {
		@Override
		public boolean include(final RevWalk walker, final RevCommit c) {
			return c.getParentCount() < 2;
		}

		@Override
		public RevFilter clone() {
			return this;
		}

		@Override
		public String toString() {
			return "NO_MERGES";
		}
	};

	/**
	 * Selects only merge bases of the starting points (thread safe).
	 * <p>
	 * This is a special case filter that cannot be combined with any other
	 * filter. Its include method always throws an exception as context
	 * information beyond the arguments is necessary to determine if the
	 * supplied commit is a merge base.
	 */
	public static final RevFilter MERGE_BASE = new RevFilter() {
		@Override
		public boolean include(final RevWalk walker, final RevCommit c) {
			throw new UnsupportedOperationException("Cannot be combined.");
		}

		@Override
		public RevFilter clone() {
			return this;
		}

		@Override
		public String toString() {
			return "MERGE_BASE";
		}
	};

	/**
	 * Create a new filter that does the opposite of this filter.
	 *
	 * @return a new filter that includes commits this filter rejects.
	 */
	public RevFilter negate() {
		return NotRevFilter.create(this);
	}

	/**
	 * Determine if the supplied commit should be included in results.
	 *
	 * @param walker
	 *            the active walker this filter is being invoked from within.
	 * @param cmit
	 *            the commit currently being tested. The commit has been parsed
	 *            and its body is available for inspection.
	 * @return true to include this commit in the results; false to have this
	 *         commit be omitted entirely from the results.
	 * @throws StopWalkException
	 *             the filter knows for certain that no additional commits can
	 *             ever match, and the current commit doesn't match either. The
	 *             walk is halted and no more results are provided.
	 * @throws MissingObjectException
	 *             an object the filter needs to consult to determine its answer
	 *             does not exist in the Git repository the walker is operating
	 *             on. Filtering this commit is impossible without the object.
	 * @throws IncorrectObjectTypeException
	 *             an object the filter needed to consult was not of the
	 *             expected object type. This usually indicates a corrupt
	 *             repository, as an object link is referencing the wrong type.
	 * @throws IOException
	 *             a loose object or pack file could not be read to obtain data
	 *             necessary for the filter to make its decision.
	 */
	public abstract boolean include(RevWalk walker, RevCommit cmit)
			throws StopWalkException, MissingObjectException,
			IncorrectObjectTypeException, IOException;

	/**
	 * Clone this revision filter, including its parameters.
	 * <p>
	 * This is a deep clone. If this filter embeds objects or other filters it
	 * must also clone those, to ensure the instances do not share mutable data.
	 *
	 * @return another copy of this filter, suitable for another thread.
	 */
	public abstract RevFilter clone();

	@Override
	public String toString() {
		String n = getClass().getName();
		int lastDot = n.lastIndexOf('.');
		if (lastDot >= 0) {
			n = n.substring(lastDot + 1);
		}
		return n.replace('$', '.');
	}
}
