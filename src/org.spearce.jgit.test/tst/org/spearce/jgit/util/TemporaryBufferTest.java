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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.TestCase;

public class TemporaryBufferTest extends TestCase {
	public void testEmpty() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer();
		try {
			b.close();
			assertEquals(0, b.length());
			final byte[] r = b.toByteArray();
			assertNotNull(r);
			assertEquals(0, r.length);
		} finally {
			b.destroy();
		}
	}

	public void testOneByte() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer();
		final byte test = (byte) new TestRng(getName()).nextInt();
		try {
			b.write(test);
			b.close();
			assertEquals(1, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(1, r.length);
				assertEquals(test, r[0]);
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(1, r.length);
				assertEquals(test, r[0]);
			}
		} finally {
			b.destroy();
		}
	}

	public void testOneBlock_BulkWrite() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer();
		final byte[] test = new TestRng(getName())
				.nextBytes(TemporaryBuffer.Block.SZ);
		try {
			b.write(test, 0, 2);
			b.write(test, 2, 4);
			b.write(test, 6, test.length - 6 - 2);
			b.write(test, test.length - 2, 2);
			b.close();
			assertEquals(test.length, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(test.length, r.length);
				assertTrue(Arrays.equals(test, r));
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(test.length, r.length);
				assertTrue(Arrays.equals(test, r));
			}
		} finally {
			b.destroy();
		}
	}

	public void testOneBlockAndHalf_BulkWrite() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer();
		final byte[] test = new TestRng(getName())
				.nextBytes(TemporaryBuffer.Block.SZ * 3 / 2);
		try {
			b.write(test, 0, 2);
			b.write(test, 2, 4);
			b.write(test, 6, test.length - 6 - 2);
			b.write(test, test.length - 2, 2);
			b.close();
			assertEquals(test.length, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(test.length, r.length);
				assertTrue(Arrays.equals(test, r));
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(test.length, r.length);
				assertTrue(Arrays.equals(test, r));
			}
		} finally {
			b.destroy();
		}
	}

	public void testOneBlockAndHalf_SingleWrite() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer();
		final byte[] test = new TestRng(getName())
				.nextBytes(TemporaryBuffer.Block.SZ * 3 / 2);
		try {
			for (int i = 0; i < test.length; i++)
				b.write(test[i]);
			b.close();
			assertEquals(test.length, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(test.length, r.length);
				assertTrue(Arrays.equals(test, r));
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(test.length, r.length);
				assertTrue(Arrays.equals(test, r));
			}
		} finally {
			b.destroy();
		}
	}

	public void testOneBlockAndHalf_Copy() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer();
		final byte[] test = new TestRng(getName())
				.nextBytes(TemporaryBuffer.Block.SZ * 3 / 2);
		try {
			final ByteArrayInputStream in = new ByteArrayInputStream(test);
			b.write(in.read());
			b.copy(in);
			b.close();
			assertEquals(test.length, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(test.length, r.length);
				assertTrue(Arrays.equals(test, r));
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(test.length, r.length);
				assertTrue(Arrays.equals(test, r));
			}
		} finally {
			b.destroy();
		}
	}

	public void testLarge_SingleWrite() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer();
		final byte[] test = new TestRng(getName())
				.nextBytes(TemporaryBuffer.DEFAULT_IN_CORE_LIMIT * 3);
		try {
			b.write(test);
			b.close();
			assertEquals(test.length, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(test.length, r.length);
				assertTrue(Arrays.equals(test, r));
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(test.length, r.length);
				assertTrue(Arrays.equals(test, r));
			}
		} finally {
			b.destroy();
		}
	}

	public void testInCoreLimit_SwitchOnAppendByte() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer();
		final byte[] test = new TestRng(getName())
				.nextBytes(TemporaryBuffer.DEFAULT_IN_CORE_LIMIT + 1);
		try {
			b.write(test, 0, test.length - 1);
			b.write(test[test.length - 1]);
			b.close();
			assertEquals(test.length, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(test.length, r.length);
				assertTrue(Arrays.equals(test, r));
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(test.length, r.length);
				assertTrue(Arrays.equals(test, r));
			}
		} finally {
			b.destroy();
		}
	}

	public void testInCoreLimit_SwitchBeforeAppendByte() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer();
		final byte[] test = new TestRng(getName())
				.nextBytes(TemporaryBuffer.DEFAULT_IN_CORE_LIMIT * 3);
		try {
			b.write(test, 0, test.length - 1);
			b.write(test[test.length - 1]);
			b.close();
			assertEquals(test.length, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(test.length, r.length);
				assertTrue(Arrays.equals(test, r));
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(test.length, r.length);
				assertTrue(Arrays.equals(test, r));
			}
		} finally {
			b.destroy();
		}
	}

	public void testInCoreLimit_SwitchOnCopy() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer();
		final byte[] test = new TestRng(getName())
				.nextBytes(TemporaryBuffer.DEFAULT_IN_CORE_LIMIT * 2);
		try {
			final ByteArrayInputStream in = new ByteArrayInputStream(test,
					TemporaryBuffer.DEFAULT_IN_CORE_LIMIT, test.length
							- TemporaryBuffer.DEFAULT_IN_CORE_LIMIT);
			b.write(test, 0, TemporaryBuffer.DEFAULT_IN_CORE_LIMIT);
			b.copy(in);
			b.close();
			assertEquals(test.length, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(test.length, r.length);
				assertTrue(Arrays.equals(test, r));
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(test.length, r.length);
				assertTrue(Arrays.equals(test, r));
			}
		} finally {
			b.destroy();
		}
	}

	public void testDestroyWhileOpen() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer();
		try {
			b.write(new TestRng(getName())
					.nextBytes(TemporaryBuffer.DEFAULT_IN_CORE_LIMIT * 2));
		} finally {
			b.destroy();
		}
	}

	public void testRandomWrites() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer();
		final TestRng rng = new TestRng(getName());
		final int max = TemporaryBuffer.DEFAULT_IN_CORE_LIMIT * 2;
		final byte[] expect = new byte[max];
		try {
			int written = 0;
			boolean onebyte = true;
			while (written < max) {
				if (onebyte) {
					final byte v = (byte) rng.nextInt();
					b.write(v);
					expect[written++] = v;
				} else {
					final int len = Math
							.min(rng.nextInt() & 127, max - written);
					final byte[] tmp = rng.nextBytes(len);
					b.write(tmp, 0, len);
					System.arraycopy(tmp, 0, expect, written, len);
					written += len;
				}
				onebyte = !onebyte;
			}
			assertEquals(expect.length, written);
			b.close();

			assertEquals(expect.length, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(expect.length, r.length);
				assertTrue(Arrays.equals(expect, r));
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(expect.length, r.length);
				assertTrue(Arrays.equals(expect, r));
			}
		} finally {
			b.destroy();
		}
	}

}
