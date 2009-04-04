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

package org.spearce.jgit.treewalk.filter;

import org.spearce.jgit.lib.RepositoryTestCase;
import org.spearce.jgit.treewalk.TreeWalk;

public class TreeFilterTest extends RepositoryTestCase {
	public void testALL_IncludesAnything() throws Exception {
		final TreeWalk tw = new TreeWalk(db);
		assertTrue(TreeFilter.ALL.include(tw));
	}

	public void testALL_ShouldNotBeRecursive() throws Exception {
		assertFalse(TreeFilter.ALL.shouldBeRecursive());
	}

	public void testALL_IdentityClone() throws Exception {
		assertSame(TreeFilter.ALL, TreeFilter.ALL.clone());
	}

	public void testNotALL_IncludesNothing() throws Exception {
		final TreeWalk tw = new TreeWalk(db);
		assertFalse(TreeFilter.ALL.negate().include(tw));
	}

	public void testANY_DIFF_IncludesSingleTreeCase() throws Exception {
		final TreeWalk tw = new TreeWalk(db);
		assertTrue(TreeFilter.ANY_DIFF.include(tw));
	}

	public void testANY_DIFF_ShouldNotBeRecursive() throws Exception {
		assertFalse(TreeFilter.ANY_DIFF.shouldBeRecursive());
	}

	public void testANY_DIFF_IdentityClone() throws Exception {
		assertSame(TreeFilter.ANY_DIFF, TreeFilter.ANY_DIFF.clone());
	}
}
