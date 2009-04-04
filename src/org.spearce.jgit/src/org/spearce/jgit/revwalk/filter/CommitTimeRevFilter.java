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
import java.util.Date;

import org.spearce.jgit.errors.IncorrectObjectTypeException;
import org.spearce.jgit.errors.MissingObjectException;
import org.spearce.jgit.errors.StopWalkException;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevWalk;

/** Selects commits based upon the commit time field. */
public abstract class CommitTimeRevFilter extends RevFilter {
	/**
	 * Create a new filter to select commits before a given date/time.
	 * 
	 * @param ts
	 *            the point in time to cut on.
	 * @return a new filter to select commits on or before <code>ts</code>.
	 */
	public static final RevFilter before(final Date ts) {
		return new Before(ts.getTime());
	}

	/**
	 * Create a new filter to select commits after a given date/time.
	 * 
	 * @param ts
	 *            the point in time to cut on.
	 * @return a new filter to select commits on or after <code>ts</code>.
	 */
	public static final RevFilter after(final Date ts) {
		return new After(ts.getTime());
	}

	final int when;

	CommitTimeRevFilter(final long ts) {
		when = (int) (ts / 1000);
	}

	@Override
	public RevFilter clone() {
		return this;
	}

	@Override
	public String toString() {
		return super.toString() + "(" + new Date(when * 1000) + ")";
	}

	private static class Before extends CommitTimeRevFilter {
		Before(final long ts) {
			super(ts);
		}

		@Override
		public boolean include(final RevWalk walker, final RevCommit cmit)
				throws StopWalkException, MissingObjectException,
				IncorrectObjectTypeException, IOException {
			return cmit.getCommitTime() <= when;
		}
	}

	private static class After extends CommitTimeRevFilter {
		After(final long ts) {
			super(ts);
		}

		@Override
		public boolean include(final RevWalk walker, final RevCommit cmit)
				throws StopWalkException, MissingObjectException,
				IncorrectObjectTypeException, IOException {
			// Since the walker sorts commits by commit time we can be
			// reasonably certain there is nothing remaining worth our
			// scanning if this commit is before the point in question.
			//
			if (cmit.getCommitTime() < when)
				throw StopWalkException.INSTANCE;
			return true;
		}
	}
}
