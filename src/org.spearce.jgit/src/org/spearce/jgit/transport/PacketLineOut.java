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
import java.io.OutputStream;

import org.spearce.jgit.lib.Constants;

class PacketLineOut {
	private final OutputStream out;

	private final byte[] lenbuffer;

	PacketLineOut(final OutputStream i) {
		out = i;
		lenbuffer = new byte[5];
	}

	void writeString(final String s) throws IOException {
		writePacket(Constants.encode(s));
	}

	void writePacket(final byte[] packet) throws IOException {
		formatLength(packet.length + 4);
		out.write(lenbuffer, 0, 4);
		out.write(packet);
	}

	void writeChannelPacket(final int channel, final byte[] buf, int off,
			int len) throws IOException {
		formatLength(len + 5);
		lenbuffer[4] = (byte) channel;
		out.write(lenbuffer, 0, 5);
		out.write(buf, off, len);
	}

	void end() throws IOException {
		formatLength(0);
		out.write(lenbuffer, 0, 4);
		flush();
	}

	void flush() throws IOException {
		out.flush();
	}

	private static final byte[] hexchar = { '0', '1', '2', '3', '4', '5', '6',
			'7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	private void formatLength(int w) {
		int o = 3;
		while (o >= 0 && w != 0) {
			lenbuffer[o--] = hexchar[w & 0xf];
			w >>>= 4;
		}
		while (o >= 0)
			lenbuffer[o--] = '0';
	}
}
