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

package org.spearce.jgit.revwalk;

import java.io.IOException;
import java.nio.charset.Charset;

import org.spearce.jgit.errors.CorruptObjectException;
import org.spearce.jgit.errors.IncorrectObjectTypeException;
import org.spearce.jgit.errors.MissingObjectException;
import org.spearce.jgit.lib.AnyObjectId;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.ObjectLoader;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.lib.Tag;
import org.spearce.jgit.util.MutableInteger;
import org.spearce.jgit.util.RawParseUtils;

/** An annotated tag. */
public class RevTag extends RevObject {
	private RevObject object;

	private byte[] buffer;

	private String name;

	/**
	 * Create a new tag reference.
	 * 
	 * @param id
	 *            object name for the tag.
	 */
	protected RevTag(final AnyObjectId id) {
		super(id);
	}

	@Override
	void parse(final RevWalk walk) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		final ObjectLoader ldr = walk.db.openObject(walk.curs, this);
		if (ldr == null)
			throw new MissingObjectException(this, Constants.TYPE_TAG);
		final byte[] data = ldr.getCachedBytes();
		if (Constants.OBJ_TAG != ldr.getType())
			throw new IncorrectObjectTypeException(this, Constants.TYPE_TAG);
		parseCanonical(walk, data);
	}

	void parseCanonical(final RevWalk walk, final byte[] rawTag)
			throws CorruptObjectException {
		final MutableInteger pos = new MutableInteger();
		final int oType;

		pos.value = 53; // "object $sha1\ntype "
		oType = Constants.decodeTypeString(this, rawTag, (byte) '\n', pos);
		walk.idBuffer.fromString(rawTag, 7);
		object = walk.lookupAny(walk.idBuffer, oType);

		int p = pos.value += 4; // "tag "
		final int nameEnd = RawParseUtils.nextLF(rawTag, p) - 1;
		name = RawParseUtils.decode(Constants.CHARSET, rawTag, p, nameEnd);
		buffer = rawTag;
		flags |= PARSED;
	}

	@Override
	public final int getType() {
		return Constants.OBJ_TAG;
	}

	/**
	 * Parse the tagger identity from the raw buffer.
	 * <p>
	 * This method parses and returns the content of the tagger line, after
	 * taking the tag's character set into account and decoding the tagger
	 * name and email address. This method is fairly expensive and produces a
	 * new PersonIdent instance on each invocation. Callers should invoke this
	 * method only if they are certain they will be outputting the result, and
	 * should cache the return value for as long as necessary to use all
	 * information from it.
	 *
	 * @return identity of the tagger (name, email) and the time the tag
	 *         was made by the tagger; null if no tagger line was found.
	 */
	public final PersonIdent getTaggerIdent() {
		final byte[] raw = buffer;
		final int nameB = RawParseUtils.tagger(raw, 0);
		if (nameB < 0)
			return null;
		return RawParseUtils.parsePersonIdent(raw, nameB);
	}

	/**
	 * Parse the complete tag message and decode it to a string.
	 * <p>
	 * This method parses and returns the message portion of the tag buffer,
	 * after taking the tag's character set into account and decoding the buffer
	 * using that character set. This method is a fairly expensive operation and
	 * produces a new string on each invocation.
	 *
	 * @return decoded tag message as a string. Never null.
	 */
	public final String getFullMessage() {
		final byte[] raw = buffer;
		final int msgB = RawParseUtils.tagMessage(raw, 0);
		if (msgB < 0)
			return "";
		final Charset enc = RawParseUtils.parseEncoding(raw);
		return RawParseUtils.decode(enc, raw, msgB, raw.length);
	}

	/**
	 * Parse the tag message and return the first "line" of it.
	 * <p>
	 * The first line is everything up to the first pair of LFs. This is the
	 * "oneline" format, suitable for output in a single line display.
	 * <p>
	 * This method parses and returns the message portion of the tag buffer,
	 * after taking the tag's character set into account and decoding the buffer
	 * using that character set. This method is a fairly expensive operation and
	 * produces a new string on each invocation.
	 *
	 * @return decoded tag message as a string. Never null. The returned string
	 *         does not contain any LFs, even if the first paragraph spanned
	 *         multiple lines. Embedded LFs are converted to spaces.
	 */
	public final String getShortMessage() {
		final byte[] raw = buffer;
		final int msgB = RawParseUtils.tagMessage(raw, 0);
		if (msgB < 0)
			return "";

		final Charset enc = RawParseUtils.parseEncoding(raw);
		final int msgE = RawParseUtils.endOfParagraph(raw, msgB);
		String str = RawParseUtils.decode(enc, raw, msgB, msgE);
		if (RevCommit.hasLF(raw, msgB, msgE))
			str = str.replace('\n', ' ');
		return str;
	}

	/**
	 * Parse this tag buffer for display.
	 * 
	 * @param walk
	 *            revision walker owning this reference.
	 * @return parsed tag.
	 */
	public Tag asTag(final RevWalk walk) {
		return new Tag(walk.db, this, name, buffer);
	}

	/**
	 * Get a reference to the object this tag was placed on.
	 * 
	 * @return object this tag refers to.
	 */
	public RevObject getObject() {
		return object;
	}

	/**
	 * Get the name of this tag, from the tag header.
	 * 
	 * @return name of the tag, according to the tag header.
	 */
	public String getName() {
		return name;
	}

	public void dispose() {
		flags &= ~PARSED;
		buffer = null;
	}
}
