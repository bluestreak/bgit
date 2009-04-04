/*
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

import java.io.IOException;
import java.io.OutputStream;

import org.spearce.jgit.transport.PackedObjectInfo;
import org.spearce.jgit.util.NB;

/**
 * Creates the version 2 pack table of contents files.
 *
 * @see PackIndexWriter
 * @see PackIndexV2
 */
class PackIndexWriterV2 extends PackIndexWriter {
	PackIndexWriterV2(final OutputStream dst) {
		super(dst);
	}

	@Override
	protected void writeImpl() throws IOException {
		writeTOC(2);
		writeFanOutTable();
		writeObjectNames();
		writeCRCs();
		writeOffset32();
		writeOffset64();
		writeChecksumFooter();
	}

	private void writeObjectNames() throws IOException {
		for (final PackedObjectInfo oe : entries)
			oe.copyRawTo(out);
	}

	private void writeCRCs() throws IOException {
		for (final PackedObjectInfo oe : entries) {
			NB.encodeInt32(tmp, 0, oe.getCRC());
			out.write(tmp, 0, 4);
		}
	}

	private void writeOffset32() throws IOException {
		int o64 = 0;
		for (final PackedObjectInfo oe : entries) {
			final long o = oe.getOffset();
			if (o < Integer.MAX_VALUE)
				NB.encodeInt32(tmp, 0, (int) o);
			else
				NB.encodeInt32(tmp, 0, (1 << 31) | o64++);
			out.write(tmp, 0, 4);
		}
	}

	private void writeOffset64() throws IOException {
		for (final PackedObjectInfo oe : entries) {
			final long o = oe.getOffset();
			if (o > Integer.MAX_VALUE) {
				NB.encodeInt64(tmp, 0, o);
				out.write(tmp, 0, 8);
			}
		}
	}
}
