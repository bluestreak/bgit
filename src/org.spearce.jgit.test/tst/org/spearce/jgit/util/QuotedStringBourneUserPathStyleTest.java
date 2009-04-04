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

package org.spearce.jgit.util;

import static org.spearce.jgit.util.QuotedString.BOURNE_USER_PATH;
import junit.framework.TestCase;

import org.spearce.jgit.lib.Constants;

public class QuotedStringBourneUserPathStyleTest extends TestCase {
	private static void assertQuote(final String in, final String exp) {
		final String r = BOURNE_USER_PATH.quote(in);
		assertNotSame(in, r);
		assertFalse(in.equals(r));
		assertEquals('\'' + exp + '\'', r);
	}

	private static void assertDequote(final String exp, final String in) {
		final byte[] b = Constants.encode('\'' + in + '\'');
		final String r = BOURNE_USER_PATH.dequote(b, 0, b.length);
		assertEquals(exp, r);
	}

	public void testQuote_Empty() {
		assertEquals("''", BOURNE_USER_PATH.quote(""));
	}

	public void testDequote_Empty1() {
		assertEquals("", BOURNE_USER_PATH.dequote(new byte[0], 0, 0));
	}

	public void testDequote_Empty2() {
		assertEquals("", BOURNE_USER_PATH.dequote(new byte[] { '\'', '\'' }, 0,
				2));
	}

	public void testDequote_SoleSq() {
		assertEquals("", BOURNE_USER_PATH.dequote(new byte[] { '\'' }, 0, 1));
	}

	public void testQuote_BareA() {
		assertQuote("a", "a");
	}

	public void testDequote_BareA() {
		final String in = "a";
		final byte[] b = Constants.encode(in);
		assertEquals(in, BOURNE_USER_PATH.dequote(b, 0, b.length));
	}

	public void testDequote_BareABCZ_OnlyBC() {
		final String in = "abcz";
		final byte[] b = Constants.encode(in);
		final int p = in.indexOf('b');
		assertEquals("bc", BOURNE_USER_PATH.dequote(b, p, p + 2));
	}

	public void testDequote_LoneBackslash() {
		assertDequote("\\", "\\");
	}

	public void testQuote_NamedEscapes() {
		assertQuote("'", "'\\''");
		assertQuote("!", "'\\!'");

		assertQuote("a'b", "a'\\''b");
		assertQuote("a!b", "a'\\!'b");
	}

	public void testDequote_NamedEscapes() {
		assertDequote("'", "'\\''");
		assertDequote("!", "'\\!'");

		assertDequote("a'b", "a'\\''b");
		assertDequote("a!b", "a'\\!'b");
	}

	public void testQuote_User() {
		assertEquals("~foo/", BOURNE_USER_PATH.quote("~foo"));
		assertEquals("~foo/", BOURNE_USER_PATH.quote("~foo/"));
		assertEquals("~/", BOURNE_USER_PATH.quote("~/"));

		assertEquals("~foo/'a'", BOURNE_USER_PATH.quote("~foo/a"));
		assertEquals("~/'a'", BOURNE_USER_PATH.quote("~/a"));
	}

	public void testDequote_User() {
		assertEquals("~foo", BOURNE_USER_PATH.dequote("~foo"));
		assertEquals("~foo/", BOURNE_USER_PATH.dequote("~foo/"));
		assertEquals("~/", BOURNE_USER_PATH.dequote("~/"));

		assertEquals("~foo/a", BOURNE_USER_PATH.dequote("~foo/'a'"));
		assertEquals("~/a", BOURNE_USER_PATH.dequote("~/'a'"));
	}
}
