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

package org.spearce.jgit.errors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** An exception detailing multiple reasons for failure. */
public class CompoundException extends Exception {
	private static final long serialVersionUID = 1L;

	private static String format(final Collection<Throwable> causes) {
		final StringBuilder msg = new StringBuilder();
		msg.append("Failure due to one of the following:");
		for (final Throwable c : causes) {
			msg.append("  ");
			msg.append(c.getMessage());
			msg.append("\n");
		}
		return msg.toString();
	}

	private final List<Throwable> causeList;

	/**
	 * Constructs an exception detailing many potential reasons for failure.
	 * 
	 * @param why
	 *            Two or more exceptions that may have been the problem.
	 */
	public CompoundException(final Collection<Throwable> why) {
		super(format(why));
		causeList = Collections.unmodifiableList(new ArrayList<Throwable>(why));
	}

	/**
	 * Get the complete list of reasons why this failure happened.
	 * 
	 * @return unmodifiable collection of all possible reasons.
	 */
	public List<Throwable> getAllCauses() {
		return causeList;
	}
}
