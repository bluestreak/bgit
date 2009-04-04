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

package org.spearce.jgit.util;

import junit.framework.TestCase;

import org.spearce.jgit.lib.Constants;

public class RawParseUtils_MatchTest extends TestCase {
	public void testMatch_Equal() {
		final byte[] src = Constants.encodeASCII(" differ\n");
		final byte[] dst = Constants.encodeASCII("foo differ\n");
		assertTrue(RawParseUtils.match(dst, 3, src) == 3 + src.length);
	}

	public void testMatch_NotEqual() {
		final byte[] src = Constants.encodeASCII(" differ\n");
		final byte[] dst = Constants.encodeASCII("a differ\n");
		assertTrue(RawParseUtils.match(dst, 2, src) < 0);
	}

	public void testMatch_Prefix() {
		final byte[] src = Constants.encodeASCII("author ");
		final byte[] dst = Constants.encodeASCII("author A. U. Thor");
		assertTrue(RawParseUtils.match(dst, 0, src) == src.length);
		assertTrue(RawParseUtils.match(dst, 1, src) < 0);
	}

	public void testMatch_TooSmall() {
		final byte[] src = Constants.encodeASCII("author ");
		final byte[] dst = Constants.encodeASCII("author autho");
		assertTrue(RawParseUtils.match(dst, src.length + 1, src) < 0);
	}
}
