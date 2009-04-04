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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.spearce.jgit.errors.PackProtocolException;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.MutableObjectId;
import org.spearce.jgit.lib.ProgressMonitor;
import org.spearce.jgit.util.NB;
import org.spearce.jgit.util.RawParseUtils;

class PacketLineIn {
	private static final byte fromhex[];

	static {
		fromhex = new byte['f' + 1];
		Arrays.fill(fromhex, (byte) -1);
		for (char i = '0'; i <= '9'; i++)
			fromhex[i] = (byte) (i - '0');
		for (char i = 'a'; i <= 'f'; i++)
			fromhex[i] = (byte) ((i - 'a') + 10);
	}

	static enum AckNackResult {
		/** NAK */
		NAK,
		/** ACK */
		ACK,
		/** ACK + continue */
		ACK_CONTINUE
	}

	private final InputStream in;

	private final byte[] lenbuffer;

	PacketLineIn(final InputStream i) {
		in = i;
		lenbuffer = new byte[4];
	}

	InputStream sideband(final ProgressMonitor pm) {
		return new SideBandInputStream(this, in, pm);
	}

	AckNackResult readACK(final MutableObjectId returnedId) throws IOException {
		final String line = readString();
		if (line.length() == 0)
			throw new PackProtocolException("Expected ACK/NAK, found EOF");
		if ("NAK".equals(line))
			return AckNackResult.NAK;
		if (line.startsWith("ACK ")) {
			returnedId.fromString(line.substring(4, 44));
			if (line.indexOf("continue", 44) != -1)
				return AckNackResult.ACK_CONTINUE;
			return AckNackResult.ACK;
		}
		throw new PackProtocolException("Expected ACK/NAK, got: " + line);
	}

	String readString() throws IOException {
		int len = readLength();
		if (len == 0)
			return "";

		len -= 5; // length header (4 bytes) and trailing LF.

		final byte[] raw = new byte[len];
		NB.readFully(in, raw, 0, len);
		readLF();
		return RawParseUtils.decode(Constants.CHARSET, raw, 0, len);
	}

	String readStringNoLF() throws IOException {
		int len = readLength();
		if (len == 0)
			return "";

		len -= 4; // length header (4 bytes)

		final byte[] raw = new byte[len];
		NB.readFully(in, raw, 0, len);
		return RawParseUtils.decode(Constants.CHARSET, raw, 0, len);
	}

	private void readLF() throws IOException {
		if (in.read() != '\n')
			throw new IOException("Protocol error: expected LF");
	}

	int readLength() throws IOException {
		NB.readFully(in, lenbuffer, 0, 4);
		try {
			int r = fromhex[lenbuffer[0]] << 4;

			r |= fromhex[lenbuffer[1]];
			r <<= 4;

			r |= fromhex[lenbuffer[2]];
			r <<= 4;

			r |= fromhex[lenbuffer[3]];
			if (r < 0)
				throw new ArrayIndexOutOfBoundsException();
			return r;
		} catch (ArrayIndexOutOfBoundsException err) {
			throw new IOException("Invalid packet line header: "
					+ (char) lenbuffer[0] + (char) lenbuffer[1]
					+ (char) lenbuffer[2] + (char) lenbuffer[3]);
		}
	}
}
