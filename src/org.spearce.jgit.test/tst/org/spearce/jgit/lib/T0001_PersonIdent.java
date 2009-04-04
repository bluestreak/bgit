/*
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

import java.util.Date;
import java.util.TimeZone;

import junit.framework.TestCase;

public class T0001_PersonIdent extends TestCase {
	public void test001_NewIdent() {
		final PersonIdent p = new PersonIdent("A U Thor", "author@example.com",
				new Date(1142878501000L), TimeZone.getTimeZone("EST"));
		assertEquals("A U Thor", p.getName());
		assertEquals("author@example.com", p.getEmailAddress());
		assertEquals(1142878501000L, p.getWhen().getTime());
		assertEquals("A U Thor <author@example.com> 1142878501 -0500", p
				.toExternalString());
	}

	public void test002_ParseIdent() {
		final String i = "A U Thor <author@example.com> 1142878501 -0500";
		final PersonIdent p = new PersonIdent(i);
		assertEquals(i, p.toExternalString());
		assertEquals("A U Thor", p.getName());
		assertEquals("author@example.com", p.getEmailAddress());
		assertEquals(1142878501000L, p.getWhen().getTime());
	}

	public void test003_ParseIdent() {
		final String i = "A U Thor <author@example.com> 1142878501 +0230";
		final PersonIdent p = new PersonIdent(i);
		assertEquals(i, p.toExternalString());
		assertEquals("A U Thor", p.getName());
		assertEquals("author@example.com", p.getEmailAddress());
		assertEquals(1142878501000L, p.getWhen().getTime());
	}

	public void test004_ParseIdent() {
		final String i = "A U Thor<author@example.com> 1142878501 +0230";
		final PersonIdent p = new PersonIdent(i);
		assertEquals("A U Thor", p.getName());
		assertEquals("author@example.com", p.getEmailAddress());
		assertEquals(1142878501000L, p.getWhen().getTime());
	}

	public void test005_ParseIdent() {
		final String i = "A U Thor<author@example.com>1142878501 +0230";
		final PersonIdent p = new PersonIdent(i);
		assertEquals("A U Thor", p.getName());
		assertEquals("author@example.com", p.getEmailAddress());
		assertEquals(1142878501000L, p.getWhen().getTime());
	}

	public void test006_ParseIdent() {
		final String i = "A U Thor   <author@example.com>1142878501 +0230";
		final PersonIdent p = new PersonIdent(i);
		assertEquals("A U Thor", p.getName());
		assertEquals("author@example.com", p.getEmailAddress());
		assertEquals(1142878501000L, p.getWhen().getTime());
	}

	public void test007_ParseIdent() {
		final String i = "A U Thor<author@example.com>1142878501 +0230 ";
		final PersonIdent p = new PersonIdent(i);
		assertEquals("A U Thor", p.getName());
		assertEquals("author@example.com", p.getEmailAddress());
		assertEquals(1142878501000L, p.getWhen().getTime());
	}
}
