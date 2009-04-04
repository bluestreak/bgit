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

import java.io.UnsupportedEncodingException;

import junit.framework.TestCase;

public class RawParseUtils_LineMapTest extends TestCase {
	public void testEmpty() {
		final IntList map = RawParseUtils.lineMap(new byte[] {}, 0, 0);
		assertNotNull(map);
		assertEquals(2, map.size());
		assertEquals(Integer.MIN_VALUE, map.get(0));
		assertEquals(0, map.get(1));
	}

	public void testOneBlankLine() {
		final IntList map = RawParseUtils.lineMap(new byte[] { '\n' }, 0, 1);
		assertEquals(3, map.size());
		assertEquals(Integer.MIN_VALUE, map.get(0));
		assertEquals(0, map.get(1));
		assertEquals(1, map.get(2));
	}

	public void testTwoLineFooBar() throws UnsupportedEncodingException {
		final byte[] buf = "foo\nbar\n".getBytes("ISO-8859-1");
		final IntList map = RawParseUtils.lineMap(buf, 0, buf.length);
		assertEquals(4, map.size());
		assertEquals(Integer.MIN_VALUE, map.get(0));
		assertEquals(0, map.get(1));
		assertEquals(4, map.get(2));
		assertEquals(buf.length, map.get(3));
	}

	public void testTwoLineNoLF() throws UnsupportedEncodingException {
		final byte[] buf = "foo\nbar".getBytes("ISO-8859-1");
		final IntList map = RawParseUtils.lineMap(buf, 0, buf.length);
		assertEquals(4, map.size());
		assertEquals(Integer.MIN_VALUE, map.get(0));
		assertEquals(0, map.get(1));
		assertEquals(4, map.get(2));
		assertEquals(buf.length, map.get(3));
	}

	public void testFourLineBlanks() throws UnsupportedEncodingException {
		final byte[] buf = "foo\n\n\nbar\n".getBytes("ISO-8859-1");
		final IntList map = RawParseUtils.lineMap(buf, 0, buf.length);
		assertEquals(6, map.size());
		assertEquals(Integer.MIN_VALUE, map.get(0));
		assertEquals(0, map.get(1));
		assertEquals(4, map.get(2));
		assertEquals(5, map.get(3));
		assertEquals(6, map.get(4));
		assertEquals(buf.length, map.get(5));
	}
}
