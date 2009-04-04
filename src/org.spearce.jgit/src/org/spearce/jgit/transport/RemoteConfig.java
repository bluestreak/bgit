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

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.spearce.jgit.lib.RepositoryConfig;

/**
 * A remembered remote repository, including URLs and RefSpecs.
 * <p>
 * A remote configuration remembers one or more URLs for a frequently accessed
 * remote repository as well as zero or more fetch and push specifications
 * describing how refs should be transferred between this repository and the
 * remote repository.
 */
public class RemoteConfig {
	private static final String SECTION = "remote";

	private static final String KEY_URL = "url";

	private static final String KEY_FETCH = "fetch";

	private static final String KEY_PUSH = "push";

	private static final String KEY_UPLOADPACK = "uploadpack";

	private static final String KEY_RECEIVEPACK = "receivepack";

	private static final String KEY_TAGOPT = "tagopt";

	private static final String KEY_MIRROR = "mirror";

	private static final boolean DEFAULT_MIRROR = false;

	/** Default value for {@link #getUploadPack()} if not specified. */
	public static final String DEFAULT_UPLOAD_PACK = "git-upload-pack";

	/** Default value for {@link #getReceivePack()} if not specified. */
	public static final String DEFAULT_RECEIVE_PACK = "git-receive-pack";

	/**
	 * Parse all remote blocks in an existing configuration file, looking for
	 * remotes configuration.
	 *
	 * @param rc
	 *            the existing configuration to get the remote settings from.
	 *            The configuration must already be loaded into memory.
	 * @return all remotes configurations existing in provided repository
	 *         configuration. Returned configurations are ordered
	 *         lexicographically by names.
	 * @throws URISyntaxException
	 *             one of the URIs within the remote's configuration is invalid.
	 */
	public static List<RemoteConfig> getAllRemoteConfigs(
			final RepositoryConfig rc) throws URISyntaxException {
		final List<String> names = new ArrayList<String>(rc
				.getSubsections(SECTION));
		Collections.sort(names);

		final List<RemoteConfig> result = new ArrayList<RemoteConfig>(names
				.size());
		for (final String name : names)
			result.add(new RemoteConfig(rc, name));
		return result;
	}

	private String name;

	private List<URIish> uris;

	private List<RefSpec> fetch;

	private List<RefSpec> push;

	private String uploadpack;

	private String receivepack;

	private TagOpt tagopt;

	private boolean mirror;

	/**
	 * Parse a remote block from an existing configuration file.
	 * <p>
	 * This constructor succeeds even if the requested remote is not defined
	 * within the supplied configuration file. If that occurs then there will be
	 * no URIs and no ref specifications known to the new instance.
	 * 
	 * @param rc
	 *            the existing configuration to get the remote settings from.
	 *            The configuration must already be loaded into memory.
	 * @param remoteName
	 *            subsection key indicating the name of this remote.
	 * @throws URISyntaxException
	 *             one of the URIs within the remote's configuration is invalid.
	 */
	public RemoteConfig(final RepositoryConfig rc, final String remoteName)
			throws URISyntaxException {
		name = remoteName;

		String[] vlst;
		String val;

		vlst = rc.getStringList(SECTION, name, KEY_URL);
		uris = new ArrayList<URIish>(vlst.length);
		for (final String s : vlst)
			uris.add(new URIish(s));

		vlst = rc.getStringList(SECTION, name, KEY_FETCH);
		fetch = new ArrayList<RefSpec>(vlst.length);
		for (final String s : vlst)
			fetch.add(new RefSpec(s));

		vlst = rc.getStringList(SECTION, name, KEY_PUSH);
		push = new ArrayList<RefSpec>(vlst.length);
		for (final String s : vlst)
			push.add(new RefSpec(s));

		val = rc.getString(SECTION, name, KEY_UPLOADPACK);
		if (val == null)
			val = DEFAULT_UPLOAD_PACK;
		uploadpack = val;

		val = rc.getString(SECTION, name, KEY_RECEIVEPACK);
		if (val == null)
			val = DEFAULT_RECEIVE_PACK;
		receivepack = val;

		val = rc.getString(SECTION, name, KEY_TAGOPT);
		tagopt = TagOpt.fromOption(val);
		mirror = rc.getBoolean(SECTION, name, KEY_MIRROR, DEFAULT_MIRROR);
	}

	/**
	 * Update this remote's definition within the configuration.
	 * 
	 * @param rc
	 *            the configuration file to store ourselves into.
	 */
	public void update(final RepositoryConfig rc) {
		final List<String> vlst = new ArrayList<String>();

		vlst.clear();
		for (final URIish u : getURIs())
			vlst.add(u.toPrivateString());
		rc.setStringList(SECTION, getName(), KEY_URL, vlst);

		vlst.clear();
		for (final RefSpec u : getFetchRefSpecs())
			vlst.add(u.toString());
		rc.setStringList(SECTION, getName(), KEY_FETCH, vlst);

		vlst.clear();
		for (final RefSpec u : getPushRefSpecs())
			vlst.add(u.toString());
		rc.setStringList(SECTION, getName(), KEY_PUSH, vlst);

		set(rc, KEY_UPLOADPACK, getUploadPack(), DEFAULT_UPLOAD_PACK);
		set(rc, KEY_RECEIVEPACK, getReceivePack(), DEFAULT_RECEIVE_PACK);
		set(rc, KEY_TAGOPT, getTagOpt().option(), TagOpt.AUTO_FOLLOW.option());
		set(rc, KEY_MIRROR, mirror, DEFAULT_MIRROR);
	}

	private void set(final RepositoryConfig rc, final String key,
			final String currentValue, final String defaultValue) {
		if (defaultValue.equals(currentValue))
			unset(rc, key);
		else
			rc.setString(SECTION, getName(), key, currentValue);
	}

	private void set(final RepositoryConfig rc, final String key,
			final boolean currentValue, final boolean defaultValue) {
		if (defaultValue == currentValue)
			unset(rc, key);
		else
			rc.setBoolean(SECTION, getName(), key, currentValue);
	}

	private void unset(final RepositoryConfig rc, final String key) {
		rc.unsetString(SECTION, getName(), key);
	}

	/**
	 * Get the local name this remote configuration is recognized as.
	 * 
	 * @return name assigned by the user to this configuration block.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get all configured URIs under this remote.
	 * 
	 * @return the set of URIs known to this remote.
	 */
	public List<URIish> getURIs() {
		return Collections.unmodifiableList(uris);
	}

	/**
	 * Add a new URI to the end of the list of URIs.
	 * 
	 * @param toAdd
	 *            the new URI to add to this remote.
	 * @return true if the URI was added; false if it already exists.
	 */
	public boolean addURI(final URIish toAdd) {
		if (uris.contains(toAdd))
			return false;
		return uris.add(toAdd);
	}

	/**
	 * Remove a URI from the list of URIs.
	 * 
	 * @param toRemove
	 *            the URI to remove from this remote.
	 * @return true if the URI was added; false if it already exists.
	 */
	public boolean removeURI(final URIish toRemove) {
		return uris.remove(toRemove);
	}

	/**
	 * Remembered specifications for fetching from a repository.
	 * 
	 * @return set of specs used by default when fetching.
	 */
	public List<RefSpec> getFetchRefSpecs() {
		return Collections.unmodifiableList(fetch);
	}

	/**
	 * Add a new fetch RefSpec to this remote.
	 * 
	 * @param s
	 *            the new specification to add.
	 * @return true if the specification was added; false if it already exists.
	 */
	public boolean addFetchRefSpec(final RefSpec s) {
		if (fetch.contains(s))
			return false;
		return fetch.add(s);
	}

	/**
	 * Override existing fetch specifications with new ones.
	 *
	 * @param specs
	 *            list of fetch specifications to set. List is copied, it can be
	 *            modified after this call.
	 */
	public void setFetchRefSpecs(final List<RefSpec> specs) {
		fetch.clear();
		fetch.addAll(specs);
	}

	/**
	 * Override existing push specifications with new ones.
	 *
	 * @param specs
	 *            list of push specifications to set. List is copied, it can be
	 *            modified after this call.
	 */
	public void setPushRefSpecs(final List<RefSpec> specs) {
		push.clear();
		push.addAll(specs);
	}

	/**
	 * Remove a fetch RefSpec from this remote.
	 * 
	 * @param s
	 *            the specification to remove.
	 * @return true if the specification existed and was removed.
	 */
	public boolean removeFetchRefSpec(final RefSpec s) {
		return fetch.remove(s);
	}

	/**
	 * Remembered specifications for pushing to a repository.
	 * 
	 * @return set of specs used by default when pushing.
	 */
	public List<RefSpec> getPushRefSpecs() {
		return Collections.unmodifiableList(push);
	}

	/**
	 * Add a new push RefSpec to this remote.
	 * 
	 * @param s
	 *            the new specification to add.
	 * @return true if the specification was added; false if it already exists.
	 */
	public boolean addPushRefSpec(final RefSpec s) {
		if (push.contains(s))
			return false;
		return push.add(s);
	}

	/**
	 * Remove a push RefSpec from this remote.
	 * 
	 * @param s
	 *            the specification to remove.
	 * @return true if the specification existed and was removed.
	 */
	public boolean removePushRefSpec(final RefSpec s) {
		return push.remove(s);
	}

	/**
	 * Override for the location of 'git-upload-pack' on the remote system.
	 * <p>
	 * This value is only useful for an SSH style connection, where Git is
	 * asking the remote system to execute a program that provides the necessary
	 * network protocol.
	 * 
	 * @return location of 'git-upload-pack' on the remote system. If no
	 *         location has been configured the default of 'git-upload-pack' is
	 *         returned instead.
	 */
	public String getUploadPack() {
		return uploadpack;
	}

	/**
	 * Override for the location of 'git-receive-pack' on the remote system.
	 * <p>
	 * This value is only useful for an SSH style connection, where Git is
	 * asking the remote system to execute a program that provides the necessary
	 * network protocol.
	 * 
	 * @return location of 'git-receive-pack' on the remote system. If no
	 *         location has been configured the default of 'git-receive-pack' is
	 *         returned instead.
	 */
	public String getReceivePack() {
		return receivepack;
	}

	/**
	 * Get the description of how annotated tags should be treated during fetch.
	 * 
	 * @return option indicating the behavior of annotated tags in fetch.
	 */
	public TagOpt getTagOpt() {
		return tagopt;
	}

	/**
	 * Set the description of how annotated tags should be treated on fetch.
	 * 
	 * @param option
	 *            method to use when handling annotated tags.
	 */
	public void setTagOpt(final TagOpt option) {
		tagopt = option != null ? option : TagOpt.AUTO_FOLLOW;
	}

	/**
	 * @return true if pushing to the remote automatically deletes remote refs
	 *         which don't exist on the source side.
	 */
	public boolean isMirror() {
		return mirror;
	}

	/**
	 * Set the mirror flag to automatically delete remote refs.
	 *
	 * @param m
	 *            true to automatically delete remote refs during push.
	 */
	public void setMirror(final boolean m) {
		mirror = m;
	}
}
