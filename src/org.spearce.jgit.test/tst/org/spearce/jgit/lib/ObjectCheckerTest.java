/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

import junit.framework.TestCase;

import org.spearce.jgit.errors.CorruptObjectException;

public class ObjectCheckerTest extends TestCase {
	private ObjectChecker checker;

	protected void setUp() throws Exception {
		super.setUp();
		checker = new ObjectChecker();
	}

	public void testInvalidType() {
		try {
			checker.check(Constants.OBJ_BAD, new byte[0]);
			fail("Did not throw CorruptObjectException");
		} catch (CorruptObjectException e) {
			final String m = e.getMessage();
			assertEquals("Invalid object type: " + Constants.OBJ_BAD, m);
		}
	}

	public void testCheckBlob() throws CorruptObjectException {
		// Any blob should pass...
		checker.checkBlob(new byte[0]);
		checker.checkBlob(new byte[1]);

		checker.check(Constants.OBJ_BLOB, new byte[0]);
		checker.check(Constants.OBJ_BLOB, new byte[1]);
	}

	public void testValidCommitNoParent() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <author@localhost> 1 +0000\n");
		b.append("committer A. U. Thor <author@localhost> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkCommit(data);
		checker.check(Constants.OBJ_COMMIT, data);
	}

	public void testValidCommitBlankAuthor() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author <> 0 +0000\n");
		b.append("committer <> 0 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkCommit(data);
		checker.check(Constants.OBJ_COMMIT, data);
	}

	public void testValidCommit1Parent() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("parent ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <author@localhost> 1 +0000\n");
		b.append("committer A. U. Thor <author@localhost> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkCommit(data);
		checker.check(Constants.OBJ_COMMIT, data);
	}

	public void testValidCommit2Parent() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("parent ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("parent ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <author@localhost> 1 +0000\n");
		b.append("committer A. U. Thor <author@localhost> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkCommit(data);
		checker.check(Constants.OBJ_COMMIT, data);
	}

	public void testValidCommit128Parent() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		for (int i = 0; i < 128; i++) {
			b.append("parent ");
			b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
			b.append('\n');
		}

		b.append("author A. U. Thor <author@localhost> 1 +0000\n");
		b.append("committer A. U. Thor <author@localhost> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkCommit(data);
		checker.check(Constants.OBJ_COMMIT, data);
	}

	public void testValidCommitNormalTime() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		final String when = "1222757360 -0730";

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <author@localhost> " + when + "\n");
		b.append("committer A. U. Thor <author@localhost> " + when + "\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkCommit(data);
		checker.check(Constants.OBJ_COMMIT, data);
	}

	public void testInvalidCommitNoTree1() {
		final StringBuilder b = new StringBuilder();

		b.append("parent ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("no tree header", e.getMessage());
		}
	}

	public void testInvalidCommitNoTree2() {
		final StringBuilder b = new StringBuilder();

		b.append("trie ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("no tree header", e.getMessage());
		}
	}

	public void testInvalidCommitNoTree3() {
		final StringBuilder b = new StringBuilder();

		b.append("tree");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("no tree header", e.getMessage());
		}
	}

	public void testInvalidCommitNoTree4() {
		final StringBuilder b = new StringBuilder();

		b.append("tree\t");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("no tree header", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidTree1() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("zzzzfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid tree", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidTree2() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append("z\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid tree", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidTree3() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9b");
		b.append("\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid tree", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidTree4() {
		final StringBuilder b = new StringBuilder();

		b.append("tree  ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid tree", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidParent1() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("parent ");
		b.append("\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid parent", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidParent2() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("parent ");
		b.append("zzzzfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append("\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid parent", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidParent3() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("parent  ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append("\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid parent", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidParent4() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("parent  ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append("z\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid parent", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidParent5() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("parent\t");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append("\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("no author", e.getMessage());
		}
	}

	public void testInvalidCommitNoAuthor() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("committer A. U. Thor <author@localhost> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("no author", e.getMessage());
		}
	}

	public void testInvalidCommitNoCommitter1() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <author@localhost> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("no committer", e.getMessage());
		}
	}

	public void testInvalidCommitNoCommitter2() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <author@localhost> 1 +0000\n");
		b.append("\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("no committer", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidAuthor1() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <foo 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidAuthor2() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor foo> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidAuthor3() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidAuthor4() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author a <b> +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidAuthor5() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author a <b>\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidAuthor6() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author a <b> z");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidAuthor7() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author a <b> 1 z");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidCommitter() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author a <b> 1 +0000\n");
		b.append("committer a <");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid committer", e.getMessage());
		}
	}

	public void testValidTag() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");
		b.append("tag test-tag\n");
		b.append("tagger A. U. Thor <author@localhost> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTag(data);
		checker.check(Constants.OBJ_TAG, data);
	}

	public void testInvalidTagNoObject1() {
		final StringBuilder b = new StringBuilder();

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no object header", e.getMessage());
		}
	}

	public void testInvalidTagNoObject2() {
		final StringBuilder b = new StringBuilder();

		b.append("object\t");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no object header", e.getMessage());
		}
	}

	public void testInvalidTagNoObject3() {
		final StringBuilder b = new StringBuilder();

		b.append("obejct ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no object header", e.getMessage());
		}
	}

	public void testInvalidTagNoObject4() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("zz9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("invalid object", e.getMessage());
		}
	}

	public void testInvalidTagNoObject5() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append(" \n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("invalid object", e.getMessage());
		}
	}

	public void testInvalidTagNoObject6() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("invalid object", e.getMessage());
		}
	}

	public void testInvalidTagNoType1() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no type header", e.getMessage());
		}
	}

	public void testInvalidTagNoType2() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type\tcommit\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no type header", e.getMessage());
		}
	}

	public void testInvalidTagNoType3() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("tpye commit\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no type header", e.getMessage());
		}
	}

	public void testInvalidTagNoType4() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no tag header", e.getMessage());
		}
	}

	public void testInvalidTagNoTagHeader1() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no tag header", e.getMessage());
		}
	}

	public void testInvalidTagNoTagHeader2() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");
		b.append("tag\tfoo\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no tag header", e.getMessage());
		}
	}

	public void testInvalidTagNoTagHeader3() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");
		b.append("tga foo\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no tag header", e.getMessage());
		}
	}

	public void testInvalidTagNoTagHeader4() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");
		b.append("tag foo");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no tagger header", e.getMessage());
		}
	}

	public void testInvalidTagNoTaggerHeader1() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");
		b.append("tag foo\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no tagger header", e.getMessage());
		}
	}

	public void testInvalidTagInvalidTaggerHeader1() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");
		b.append("tag foo\n");
		b.append("tagger \n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("invalid tagger", e.getMessage());
		}
	}

	public void testInvalidTagInvalidTaggerHeader3() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");
		b.append("tag foo\n");
		b.append("tagger a < 1 +000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("invalid tagger", e.getMessage());
		}
	}

	public void testValidEmptyTree() throws CorruptObjectException {
		checker.checkTree(new byte[0]);
		checker.check(Constants.OBJ_TREE, new byte[0]);
	}

	public void testValidTree1() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 regular-file");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	public void testValidTree2() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100755 executable");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	public void testValidTree3() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "40000 tree");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	public void testValidTree4() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "120000 symlink");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	public void testValidTree5() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "160000 git link");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	public void testValidTree6() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 .a");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	public void testValidTreeSorting1() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 fooaaa");
		entry(b, "100755 foobar");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	public void testValidTreeSorting2() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100755 fooaaa");
		entry(b, "100644 foobar");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	public void testValidTreeSorting3() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "40000 a");
		entry(b, "100644 b");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	public void testValidTreeSorting4() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "40000 b");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	public void testValidTreeSorting5() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a.c");
		entry(b, "40000 a");
		entry(b, "100644 a0c");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	public void testValidTreeSorting6() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "40000 a");
		entry(b, "100644 apple");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	public void testValidTreeSorting7() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "40000 an orang");
		entry(b, "40000 an orange");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	public void testValidTreeSorting8() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "100644 a0c");
		entry(b, "100644 b");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	public void testInvalidTreeModeStartsWithZero1() {
		final StringBuilder b = new StringBuilder();
		entry(b, "0 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("mode starts with '0'", e.getMessage());
		}
	}

	public void testInvalidTreeModeStartsWithZero2() {
		final StringBuilder b = new StringBuilder();
		entry(b, "0100644 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("mode starts with '0'", e.getMessage());
		}
	}

	public void testInvalidTreeModeStartsWithZero3() {
		final StringBuilder b = new StringBuilder();
		entry(b, "040000 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("mode starts with '0'", e.getMessage());
		}
	}

	public void testInvalidTreeModeNotOctal1() {
		final StringBuilder b = new StringBuilder();
		entry(b, "8 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid mode character", e.getMessage());
		}
	}

	public void testInvalidTreeModeNotOctal2() {
		final StringBuilder b = new StringBuilder();
		entry(b, "Z a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid mode character", e.getMessage());
		}
	}

	public void testInvalidTreeModeNotSupportedMode1() {
		final StringBuilder b = new StringBuilder();
		entry(b, "1 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid mode 1", e.getMessage());
		}
	}

	public void testInvalidTreeModeNotSupportedMode2() {
		final StringBuilder b = new StringBuilder();
		entry(b, "170000 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid mode " + 0170000, e.getMessage());
		}
	}

	public void testInvalidTreeModeMissingName() {
		final StringBuilder b = new StringBuilder();
		b.append("100644");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("truncated in mode", e.getMessage());
		}
	}

	public void testInvalidTreeNameContainsSlash() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a/b");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("name contains '/'", e.getMessage());
		}
	}

	public void testInvalidTreeNameIsEmpty() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 ");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("zero length name", e.getMessage());
		}
	}

	public void testInvalidTreeNameIsDot() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 .");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid name '.'", e.getMessage());
		}
	}

	public void testInvalidTreeNameIsDotDot() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 ..");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid name '..'", e.getMessage());
		}
	}

	public void testInvalidTreeTruncatedInName() {
		final StringBuilder b = new StringBuilder();
		b.append("100644 b");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("truncated in name", e.getMessage());
		}
	}

	public void testInvalidTreeTruncatedInObjectId() {
		final StringBuilder b = new StringBuilder();
		b.append("100644 b\0\1\2");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("truncated in object id", e.getMessage());
		}
	}

	public void testInvalidTreeBadSorting1() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 foobar");
		entry(b, "100644 fooaaa");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("incorrectly sorted", e.getMessage());
		}
	}

	public void testInvalidTreeBadSorting2() {
		final StringBuilder b = new StringBuilder();
		entry(b, "40000 a");
		entry(b, "100644 a.c");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("incorrectly sorted", e.getMessage());
		}
	}

	public void testInvalidTreeBadSorting3() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a0c");
		entry(b, "40000 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("incorrectly sorted", e.getMessage());
		}
	}

	public void testInvalidTreeDuplicateNames1() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "100644 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("duplicate entry names", e.getMessage());
		}
	}

	public void testInvalidTreeDuplicateNames2() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "100755 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("duplicate entry names", e.getMessage());
		}
	}

	public void testInvalidTreeDuplicateNames3() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "40000 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("duplicate entry names", e.getMessage());
		}
	}

	public void testInvalidTreeDuplicateNames4() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "100644 a.c");
		entry(b, "100644 a.d");
		entry(b, "100644 a.e");
		entry(b, "40000 a");
		entry(b, "100644 zoo");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("duplicate entry names", e.getMessage());
		}
	}

	private static void entry(final StringBuilder b, final String modeName) {
		b.append(modeName);
		b.append('\0');
		for (int i = 0; i < Constants.OBJECT_ID_LENGTH; i++)
			b.append((char) i);
	}
}
