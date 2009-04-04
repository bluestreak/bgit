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

package org.spearce.jgit.pgm;

import java.io.BufferedInputStream;
import java.io.File;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.spearce.jgit.lib.TextProgressMonitor;

class IndexPack extends TextBuiltin {
	@Option(name = "--fix-thin", usage = "fix a thin pack to be complete")
	private boolean fixThin;

	@Option(name = "--index-version", usage = "index file format to create")
	private int indexVersion = -1;

	@Argument(index = 0, required = true, metaVar = "base")
	private File base;

	@Override
	protected void run() throws Exception {
		if (indexVersion == -1)
			indexVersion = db.getConfig().getCore().getPackIndexVersion();
		final BufferedInputStream in;
		final org.spearce.jgit.transport.IndexPack ip;
		in = new BufferedInputStream(System.in);
		ip = new org.spearce.jgit.transport.IndexPack(db, in, base);
		ip.setFixThin(fixThin);
		ip.setIndexVersion(indexVersion);
		ip.index(new TextProgressMonitor());
	}
}
