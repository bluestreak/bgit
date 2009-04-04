/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
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

package org.spearce.jgit.lib;

import java.io.File;
import java.io.IOException;

import org.spearce.jgit.util.JGitTestUtil;

public class T0004_PackReader extends RepositoryTestCase {
	private static final String PACK_NAME = "pack-34be9032ac282b11fa9babdc2b2a93ca996c9c2f";
	private static final File TEST_PACK = JGitTestUtil.getTestResourceFile(PACK_NAME + ".pack");
	private static final File TEST_IDX = JGitTestUtil.getTestResourceFile(PACK_NAME + ".idx");

	public void test003_lookupCompressedObject() throws IOException {
		final PackFile pr;
		final ObjectId id;
		final PackedObjectLoader or;

		id = ObjectId.fromString("902d5476fa249b7abc9d84c611577a81381f0327");
		pr = new PackFile(TEST_IDX, TEST_PACK);
		or = pr.get(new WindowCursor(), id);
		assertNotNull(or);
		assertEquals(Constants.OBJ_TREE, or.getType());
		assertEquals(35, or.getSize());
		assertEquals(7738, or.getDataOffset());
		pr.close();
	}

	public void test004_lookupDeltifiedObject() throws IOException {
		final ObjectId id;
		final ObjectLoader or;

		id = ObjectId.fromString("5b6e7c66c276e7610d4a73c70ec1a1f7c1003259");
		or = db.openObject(id);
		assertNotNull(or);
		assertTrue(or instanceof PackedObjectLoader);
		assertEquals(Constants.OBJ_BLOB, or.getType());
		assertEquals(18009, or.getSize());
		assertEquals(537, ((PackedObjectLoader) or).getDataOffset());
	}

	public void test005_todopack() throws IOException {
		final File todopack = JGitTestUtil.getTestResourceFile("todopack");
		if (!todopack.isDirectory()) {
			System.err.println("Skipping " + getName() + ": no " + todopack);
			return;
		}

		final File packDir = new File(db.getObjectsDirectory(), "pack");
		final String packname = "pack-2e71952edc41f3ce7921c5e5dd1b64f48204cf35";
		copyFile(new File(todopack, packname + ".pack"), new File(packDir,
				packname + ".pack"));
		copyFile(new File(todopack, packname + ".idx"), new File(packDir,
				packname + ".idx"));
		db.scanForPacks();
		Tree t;

		t = db
				.mapTree(ObjectId.fromString(
						"aac9df07f653dd18b935298deb813e02c32d2e6f"));
		assertNotNull(t);
		t.memberCount();

		t = db
				.mapTree(ObjectId.fromString(
						"6b9ffbebe7b83ac6a61c9477ab941d999f5d0c96"));
		assertNotNull(t);
		t.memberCount();
	}
}
