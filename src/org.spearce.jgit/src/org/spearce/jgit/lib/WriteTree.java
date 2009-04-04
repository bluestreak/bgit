/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2007, Shawn O. Pearce <spearce@spearce.org>
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

import org.spearce.jgit.errors.SymlinksNotSupportedException;

/**
 * A tree visitor for writing a directory tree to the git object database. Blob
 * data is fetched from the files, not the cached blobs.
 */
public class WriteTree extends TreeVisitorWithCurrentDirectory {
	private final ObjectWriter ow;

	/**
	 * Construct a WriteTree for a given directory
	 *
	 * @param sourceDirectory
	 * @param db
	 */
	public WriteTree(final File sourceDirectory, final Repository db) {
		super(sourceDirectory);
		ow = new ObjectWriter(db);
	}

	public void visitFile(final FileTreeEntry f) throws IOException {
		f.setId(ow.writeBlob(new File(getCurrentDirectory(), f.getName())));
	}

	public void visitSymlink(final SymlinkTreeEntry s) throws IOException {
		if (s.isModified()) {
			throw new SymlinksNotSupportedException("Symlink \""
					+ s.getFullName()
					+ "\" cannot be written as the link target"
					+ " cannot be read from within Java.");
		}
	}

	public void endVisitTree(final Tree t) throws IOException {
		super.endVisitTree(t);
		t.setId(ow.writeTree(t));
	}
}
