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

import java.io.IOException;

/**
 * A tree entry representing a symbolic link.
 *
 * Note. Java cannot really handle these as file system objects.
 */
public class SymlinkTreeEntry extends TreeEntry {
	private static final long serialVersionUID = 1L;

	/**
	 * Construct a {@link SymlinkTreeEntry} with the specified name and SHA-1 in
	 * the specified parent
	 *
	 * @param parent
	 * @param id
	 * @param nameUTF8
	 */
	public SymlinkTreeEntry(final Tree parent, final ObjectId id,
			final byte[] nameUTF8) {
		super(parent, id, nameUTF8);
	}

	public FileMode getMode() {
		return FileMode.SYMLINK;
	}

	public void accept(final TreeVisitor tv, final int flags)
			throws IOException {
		if ((MODIFIED_ONLY & flags) == MODIFIED_ONLY && !isModified()) {
			return;
		}

		tv.visitSymlink(this);
	}

	public String toString() {
		final StringBuffer r = new StringBuffer();
		r.append(ObjectId.toString(getId()));
		r.append(" S ");
		r.append(getFullName());
		return r.toString();
	}
}
