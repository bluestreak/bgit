/*
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
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

package org.spearce.jgit.transport;

import junit.framework.TestCase;

import org.spearce.jgit.lib.Ref;

public class RefSpecTestCase extends TestCase {
	public void testMasterMaster() {
		final String sn = "refs/heads/master";
		final RefSpec rs = new RefSpec(sn + ":" + sn);
		assertFalse(rs.isForceUpdate());
		assertFalse(rs.isWildcard());
		assertEquals(sn, rs.getSource());
		assertEquals(sn, rs.getDestination());
		assertEquals(sn + ":" + sn, rs.toString());
		assertEquals(rs, new RefSpec(rs.toString()));

		Ref r = new Ref(Ref.Storage.LOOSE, sn, null);
		assertTrue(rs.matchSource(r));
		assertTrue(rs.matchDestination(r));
		assertSame(rs, rs.expandFromSource(r));

		r = new Ref(Ref.Storage.LOOSE, sn + "-and-more", null);
		assertFalse(rs.matchSource(r));
		assertFalse(rs.matchDestination(r));
	}

	public void testSplitLastColon() {
		final String lhs = ":m:a:i:n:t";
		final String rhs = "refs/heads/maint";
		final RefSpec rs = new RefSpec(lhs + ":" + rhs);
		assertFalse(rs.isForceUpdate());
		assertFalse(rs.isWildcard());
		assertEquals(lhs, rs.getSource());
		assertEquals(rhs, rs.getDestination());
		assertEquals(lhs + ":" + rhs, rs.toString());
		assertEquals(rs, new RefSpec(rs.toString()));
	}

	public void testForceMasterMaster() {
		final String sn = "refs/heads/master";
		final RefSpec rs = new RefSpec("+" + sn + ":" + sn);
		assertTrue(rs.isForceUpdate());
		assertFalse(rs.isWildcard());
		assertEquals(sn, rs.getSource());
		assertEquals(sn, rs.getDestination());
		assertEquals("+" + sn + ":" + sn, rs.toString());
		assertEquals(rs, new RefSpec(rs.toString()));

		Ref r = new Ref(Ref.Storage.LOOSE, sn, null);
		assertTrue(rs.matchSource(r));
		assertTrue(rs.matchDestination(r));
		assertSame(rs, rs.expandFromSource(r));

		r = new Ref(Ref.Storage.LOOSE, sn + "-and-more", null);
		assertFalse(rs.matchSource(r));
		assertFalse(rs.matchDestination(r));
	}

	public void testMaster() {
		final String sn = "refs/heads/master";
		final RefSpec rs = new RefSpec(sn);
		assertFalse(rs.isForceUpdate());
		assertFalse(rs.isWildcard());
		assertEquals(sn, rs.getSource());
		assertNull(rs.getDestination());
		assertEquals(sn, rs.toString());
		assertEquals(rs, new RefSpec(rs.toString()));

		Ref r = new Ref(Ref.Storage.LOOSE, sn, null);
		assertTrue(rs.matchSource(r));
		assertFalse(rs.matchDestination(r));
		assertSame(rs, rs.expandFromSource(r));

		r = new Ref(Ref.Storage.LOOSE, sn + "-and-more", null);
		assertFalse(rs.matchSource(r));
		assertFalse(rs.matchDestination(r));
	}

	public void testForceMaster() {
		final String sn = "refs/heads/master";
		final RefSpec rs = new RefSpec("+" + sn);
		assertTrue(rs.isForceUpdate());
		assertFalse(rs.isWildcard());
		assertEquals(sn, rs.getSource());
		assertNull(rs.getDestination());
		assertEquals("+" + sn, rs.toString());
		assertEquals(rs, new RefSpec(rs.toString()));

		Ref r = new Ref(Ref.Storage.LOOSE, sn, null);
		assertTrue(rs.matchSource(r));
		assertFalse(rs.matchDestination(r));
		assertSame(rs, rs.expandFromSource(r));

		r = new Ref(Ref.Storage.LOOSE, sn + "-and-more", null);
		assertFalse(rs.matchSource(r));
		assertFalse(rs.matchDestination(r));
	}

	public void testDeleteMaster() {
		final String sn = "refs/heads/master";
		final RefSpec rs = new RefSpec(":" + sn);
		assertFalse(rs.isForceUpdate());
		assertFalse(rs.isWildcard());
		assertNull(rs.getSource());
		assertEquals(sn, rs.getDestination());
		assertEquals(":" + sn, rs.toString());
		assertEquals(rs, new RefSpec(rs.toString()));

		Ref r = new Ref(Ref.Storage.LOOSE, sn, null);
		assertFalse(rs.matchSource(r));
		assertTrue(rs.matchDestination(r));
		assertSame(rs, rs.expandFromSource(r));

		r = new Ref(Ref.Storage.LOOSE, sn + "-and-more", null);
		assertFalse(rs.matchSource(r));
		assertFalse(rs.matchDestination(r));
	}

	public void testForceRemotesOrigin() {
		final String srcn = "refs/heads/*";
		final String dstn = "refs/remotes/origin/*";
		final RefSpec rs = new RefSpec("+" + srcn + ":" + dstn);
		assertTrue(rs.isForceUpdate());
		assertTrue(rs.isWildcard());
		assertEquals(srcn, rs.getSource());
		assertEquals(dstn, rs.getDestination());
		assertEquals("+" + srcn + ":" + dstn, rs.toString());
		assertEquals(rs, new RefSpec(rs.toString()));

		Ref r;
		RefSpec expanded;

		r = new Ref(Ref.Storage.LOOSE, "refs/heads/master", null);
		assertTrue(rs.matchSource(r));
		assertFalse(rs.matchDestination(r));
		expanded = rs.expandFromSource(r);
		assertNotSame(rs, expanded);
		assertTrue(expanded.isForceUpdate());
		assertFalse(expanded.isWildcard());
		assertEquals(r.getName(), expanded.getSource());
		assertEquals("refs/remotes/origin/master", expanded.getDestination());

		r = new Ref(Ref.Storage.LOOSE, "refs/remotes/origin/next", null);
		assertFalse(rs.matchSource(r));
		assertTrue(rs.matchDestination(r));

		r = new Ref(Ref.Storage.LOOSE, "refs/tags/v1.0", null);
		assertFalse(rs.matchSource(r));
		assertFalse(rs.matchDestination(r));
	}

	public void testCreateEmpty() {
		final RefSpec rs = new RefSpec();
		assertFalse(rs.isForceUpdate());
		assertFalse(rs.isWildcard());
		assertEquals("HEAD", rs.getSource());
		assertNull(rs.getDestination());
		assertEquals("HEAD", rs.toString());
	}

	public void testSetForceUpdate() {
		final String s = "refs/heads/*:refs/remotes/origin/*";
		final RefSpec a = new RefSpec(s);
		assertFalse(a.isForceUpdate());
		RefSpec b = a.setForceUpdate(true);
		assertNotSame(a, b);
		assertFalse(a.isForceUpdate());
		assertTrue(b.isForceUpdate());
		assertEquals(s, a.toString());
		assertEquals("+" + s, b.toString());
	}

	public void testSetSource() {
		final RefSpec a = new RefSpec();
		final RefSpec b = a.setSource("refs/heads/master");
		assertNotSame(a, b);
		assertEquals("HEAD", a.toString());
		assertEquals("refs/heads/master", b.toString());
	}

	public void testSetDestination() {
		final RefSpec a = new RefSpec();
		final RefSpec b = a.setDestination("refs/heads/master");
		assertNotSame(a, b);
		assertEquals("HEAD", a.toString());
		assertEquals("HEAD:refs/heads/master", b.toString());
	}

	public void testSetDestination_SourceNull() {
		final RefSpec a = new RefSpec();
		RefSpec b;

		b = a.setDestination("refs/heads/master");
		b = b.setSource(null);
		assertNotSame(a, b);
		assertEquals("HEAD", a.toString());
		assertEquals(":refs/heads/master", b.toString());
	}

	public void testSetSourceDestination() {
		final RefSpec a = new RefSpec();
		final RefSpec b;
		b = a.setSourceDestination("refs/heads/*", "refs/remotes/origin/*");
		assertNotSame(a, b);
		assertEquals("HEAD", a.toString());
		assertEquals("refs/heads/*:refs/remotes/origin/*", b.toString());
	}

	public void testExpandFromDestination_NonWildcard() {
		final String src = "refs/heads/master";
		final String dst = "refs/remotes/origin/master";
		final RefSpec a = new RefSpec(src + ":" + dst);
		final RefSpec r = a.expandFromDestination(dst);
		assertSame(a, r);
		assertFalse(r.isWildcard());
		assertEquals(src, r.getSource());
		assertEquals(dst, r.getDestination());
	}

	public void testExpandFromDestination_Wildcard() {
		final String src = "refs/heads/master";
		final String dst = "refs/remotes/origin/master";
		final RefSpec a = new RefSpec("refs/heads/*:refs/remotes/origin/*");
		final RefSpec r = a.expandFromDestination(dst);
		assertNotSame(a, r);
		assertFalse(r.isWildcard());
		assertEquals(src, r.getSource());
		assertEquals(dst, r.getDestination());
	}
}
