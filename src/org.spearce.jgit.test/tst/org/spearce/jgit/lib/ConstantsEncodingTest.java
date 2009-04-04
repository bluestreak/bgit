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

package org.spearce.jgit.lib;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import junit.framework.TestCase;

public class ConstantsEncodingTest extends TestCase {
	public void testEncodeASCII_SimpleASCII()
			throws UnsupportedEncodingException {
		final String src = "abc";
		final byte[] exp = { 'a', 'b', 'c' };
		final byte[] res = Constants.encodeASCII(src);
		assertTrue(Arrays.equals(exp, res));
		assertEquals(src, new String(res, 0, res.length, "UTF-8"));
	}

	public void testEncodeASCII_FailOnNonASCII() {
		final String src = "Ūnĭcōde̽";
		try {
			Constants.encodeASCII(src);
			fail("Incorrectly accepted a Unicode character");
		} catch (IllegalArgumentException err) {
			assertEquals("Not ASCII string: " + src, err.getMessage());
		}
	}

	public void testEncodeASCII_Number13() {
		final long src = 13;
		final byte[] exp = { '1', '3' };
		final byte[] res = Constants.encodeASCII(src);
		assertTrue(Arrays.equals(exp, res));
	}

	public void testEncode_SimpleASCII() throws UnsupportedEncodingException {
		final String src = "abc";
		final byte[] exp = { 'a', 'b', 'c' };
		final byte[] res = Constants.encode(src);
		assertTrue(Arrays.equals(exp, res));
		assertEquals(src, new String(res, 0, res.length, "UTF-8"));
	}

	public void testEncode_Unicode() throws UnsupportedEncodingException {
		final String src = "Ūnĭcōde̽";
		final byte[] exp = { (byte) 0xC5, (byte) 0xAA, 0x6E, (byte) 0xC4,
				(byte) 0xAD, 0x63, (byte) 0xC5, (byte) 0x8D, 0x64, 0x65,
				(byte) 0xCC, (byte) 0xBD };
		final byte[] res = Constants.encode(src);
		assertTrue(Arrays.equals(exp, res));
		assertEquals(src, new String(res, 0, res.length, "UTF-8"));
	}
}
