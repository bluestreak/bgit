/*
 * Copyright (C) 2008, Imran M Yousuf <imyousuf@smartitengineering.com>
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

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

public abstract class JGitTestUtil {
	public static final String CLASSPATH_TO_RESOURCES = "org/spearce/jgit/test/resources/";

	private JGitTestUtil() {
		throw new UnsupportedOperationException();
	}

	public static File getTestResourceFile(final String fileName) {
		if (fileName == null || fileName.length() <= 0) {
			return null;
		}
		final URL url = cl().getResource(CLASSPATH_TO_RESOURCES + fileName);
		if (url == null) {
			// If URL is null then try to load it as it was being
			// loaded previously
			return new File("tst", fileName);
		}
		try {
			return new File(url.toURI());
		} catch(URISyntaxException e) {
			return new File(url.getPath());
		}
	}

	private static ClassLoader cl() {
		return JGitTestUtil.class.getClassLoader();
	}
}
