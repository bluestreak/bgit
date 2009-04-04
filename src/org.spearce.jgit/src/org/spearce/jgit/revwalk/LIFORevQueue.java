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

package org.spearce.jgit.revwalk;

import java.io.IOException;

import org.spearce.jgit.errors.IncorrectObjectTypeException;
import org.spearce.jgit.errors.MissingObjectException;

/** A queue of commits in LIFO order. */
public class LIFORevQueue extends BlockRevQueue {
	private Block head;

	/** Create an empty LIFO queue. */
	public LIFORevQueue() {
		super();
	}

	LIFORevQueue(final Generator s) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		super(s);
	}

	public void add(final RevCommit c) {
		Block b = head;
		if (b == null || !b.canUnpop()) {
			b = free.newBlock();
			b.resetToEnd();
			b.next = head;
			head = b;
		}
		b.unpop(c);
	}

	public RevCommit next() {
		final Block b = head;
		if (b == null)
			return null;

		final RevCommit c = b.pop();
		if (b.isEmpty()) {
			head = b.next;
			free.freeBlock(b);
		}
		return c;
	}

	public void clear() {
		head = null;
		free.clear();
	}

	boolean everbodyHasFlag(final int f) {
		for (Block b = head; b != null; b = b.next) {
			for (int i = b.headIndex; i < b.tailIndex; i++)
				if ((b.commits[i].flags & f) == 0)
					return false;
		}
		return true;
	}

	boolean anybodyHasFlag(final int f) {
		for (Block b = head; b != null; b = b.next) {
			for (int i = b.headIndex; i < b.tailIndex; i++)
				if ((b.commits[i].flags & f) != 0)
					return true;
		}
		return false;
	}

	public String toString() {
		final StringBuilder s = new StringBuilder();
		for (Block q = head; q != null; q = q.next) {
			for (int i = q.headIndex; i < q.tailIndex; i++)
				describe(s, q.commits[i]);
		}
		return s.toString();
	}
}
