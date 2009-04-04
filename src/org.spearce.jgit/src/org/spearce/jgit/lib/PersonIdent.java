/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
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

import java.util.Date;
import java.util.TimeZone;

/**
 * A combination of a person identity and time in Git.
 * 
 * Git combines Name + email + time + time zone to specify who wrote or
 * committed something.
 */
public class PersonIdent {
	private final String name;

	private final String emailAddress;

	private final long when;

	private final int tzOffset;

	/**
	 * Creates new PersonIdent from config info in repository, with current time.
	 * This new PersonIdent gets the info from the default committer as available
	 * from the configuration.
	 * 
	 * @param repo
	 */
	public PersonIdent(final Repository repo) {
		final RepositoryConfig config = repo.getConfig();
		name = config.getCommitterName();
		emailAddress = config.getCommitterEmail();
		when = System.currentTimeMillis();
		tzOffset = TimeZone.getDefault().getOffset(when) / (60 * 1000);
	}

	/**
	 * Copy a {@link PersonIdent}.
	 * 
	 * @param pi
	 *            Original {@link PersonIdent}
	 */
	public PersonIdent(final PersonIdent pi) {
		this(pi.getName(), pi.getEmailAddress());
	}

	/**
	 * Construct a new {@link PersonIdent} with current time.
	 * 
	 * @param aName
	 * @param aEmailAddress
	 */
	public PersonIdent(final String aName, final String aEmailAddress) {
		this(aName, aEmailAddress, new Date(), TimeZone.getDefault());
	}

	/**
	 * Copy a PersonIdent, but alter the clone's time stamp
	 * 
	 * @param pi
	 *            original {@link PersonIdent}
	 * @param when
	 *            local time
	 * @param tz
	 *            time zone
	 */
	public PersonIdent(final PersonIdent pi, final Date when, final TimeZone tz) {
		this(pi.getName(), pi.getEmailAddress(), when, tz);
	}

	/**
	 * Copy a {@link PersonIdent}, but alter the clone's time stamp
	 * 
	 * @param pi
	 *            original {@link PersonIdent}
	 * @param aWhen
	 *            local time
	 */
	public PersonIdent(final PersonIdent pi, final Date aWhen) {
		name = pi.getName();
		emailAddress = pi.getEmailAddress();
		when = aWhen.getTime();
		tzOffset = pi.tzOffset;
	}

	/**
	 * Construct a PersonIdent from simple data
	 * 
	 * @param aName
	 * @param aEmailAddress
	 * @param aWhen
	 *            local time stamp
	 * @param aTZ
	 *            time zone
	 */
	public PersonIdent(final String aName, final String aEmailAddress,
			final Date aWhen, final TimeZone aTZ) {
		name = aName;
		emailAddress = aEmailAddress;
		when = aWhen.getTime();
		tzOffset = aTZ.getOffset(when) / (60 * 1000);
	}

	/**
	 * Construct a {@link PersonIdent}
	 * 
	 * @param aName
	 * @param aEmailAddress
	 * @param aWhen
	 *            local time stamp
	 * @param aTZ
	 *            time zone
	 */
	public PersonIdent(final String aName, final String aEmailAddress,
			final long aWhen, final int aTZ) {
		name = aName;
		emailAddress = aEmailAddress;
		when = aWhen;
		tzOffset = aTZ;
	}

	/**
	 * Copy a PersonIdent, but alter the clone's time stamp
	 * 
	 * @param pi
	 *            original {@link PersonIdent}
	 * @param aWhen
	 *            local time stamp
	 * @param aTZ
	 *            time zone
	 */
	public PersonIdent(final PersonIdent pi, final long aWhen, final int aTZ) {
		name = pi.getName();
		emailAddress = pi.getEmailAddress();
		when = aWhen;
		tzOffset = aTZ;
	}

	/**
	 * Construct a PersonIdent from a string with full name, email, time time
	 * zone string. The input string must be valid.
	 * 
	 * @param in
	 *            a Git internal format author/committer string.
	 */
	public PersonIdent(final String in) {
		final int lt = in.indexOf('<');
		if (lt == -1) {
			throw new IllegalArgumentException("Malformed PersonIdent string"
					+ " (no < was found): " + in);
		}
		final int gt = in.indexOf('>', lt);
		if (gt == -1) {
			throw new IllegalArgumentException("Malformed PersonIdent string"
					+ " (no > was found): " + in);
		}
		final int sp = in.indexOf(' ', gt + 2);
		if (sp == -1) {
			when = 0;
			tzOffset = -1;
		} else {
			final String tzHoursStr = in.substring(sp + 1, sp + 4).trim();
			final int tzHours;
			if (tzHoursStr.charAt(0) == '+') {
				tzHours = Integer.parseInt(tzHoursStr.substring(1));
			} else {
				tzHours = Integer.parseInt(tzHoursStr);
			}
			final int tzMins = Integer.parseInt(in.substring(sp + 4).trim());
			when = Long.parseLong(in.substring(gt + 1, sp).trim()) * 1000;
			tzOffset = tzHours * 60 + tzMins;
		}

		name = in.substring(0, lt).trim();
		emailAddress = in.substring(lt + 1, gt).trim();
	}

	/**
	 * @return Name of person
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return email address of person
	 */
	public String getEmailAddress() {
		return emailAddress;
	}

	/**
	 * @return timestamp
	 */
	public Date getWhen() {
		return new Date(when);
	}

	/**
	 * @return this person's preferred time zone; null if time zone is unknown.
	 */
	public TimeZone getTimeZone() {
		final String[] ids = TimeZone.getAvailableIDs(tzOffset * 60 * 1000);
		if (ids.length == 0)
			return null;
		return TimeZone.getTimeZone(ids[0]);
	}

	/**
	 * @return this person's preferred time zone as minutes east of UTC. If the
	 *         timezone is to the west of UTC it is negative.
	 */
	public int getTimeZoneOffset() {
		return tzOffset;
	}

	public int hashCode() {
		return getEmailAddress().hashCode() ^ (int) when;
	}

	public boolean equals(final Object o) {
		if (o instanceof PersonIdent) {
			final PersonIdent p = (PersonIdent) o;
			return getName().equals(p.getName())
					&& getEmailAddress().equals(p.getEmailAddress())
					&& when == p.when;
		}
		return false;
	}

	/**
	 * Format for Git storage.
	 * 
	 * @return a string in the git author format
	 */
	public String toExternalString() {
		final StringBuffer r = new StringBuffer();
		int offset = tzOffset;
		final char sign;
		final int offsetHours;
		final int offsetMins;

		if (offset < 0) {
			sign = '-';
			offset = -offset;
		} else {
			sign = '+';
		}

		offsetHours = offset / 60;
		offsetMins = offset % 60;

		r.append(getName());
		r.append(" <");
		r.append(getEmailAddress());
		r.append("> ");
		r.append(when / 1000);
		r.append(' ');
		r.append(sign);
		if (offsetHours < 10) {
			r.append('0');
		}
		r.append(offsetHours);
		if (offsetMins < 10) {
			r.append('0');
		}
		r.append(offsetMins);
		return r.toString();
	}

	public String toString() {
		final StringBuffer r = new StringBuffer();
		int minutes;

		minutes = tzOffset < 0 ? -tzOffset : tzOffset;
		minutes = (minutes / 100) * 60 + (minutes % 100);
		minutes = tzOffset < 0 ? -minutes : minutes;

		r.append("PersonIdent[");
		r.append(getName());
		r.append(", ");
		r.append(getEmailAddress());
		r.append(", ");
		r.append(new Date(when + minutes * 60));
		r.append("]");

		return r.toString();
	}
}
