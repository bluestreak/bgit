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

package org.spearce.jgit.awtui;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** Basic network prompt for username/password when using AWT. */
public class AwtAuthenticator extends Authenticator {
	private static final AwtAuthenticator me = new AwtAuthenticator();

	/** Install this authenticator implementation into the JVM. */
	public static void install() {
		setDefault(me);
	}

	/**
	 * Add a cached authentication for future use.
	 * 
	 * @param ca
	 *            the information we should remember.
	 */
	public static void add(final CachedAuthentication ca) {
		synchronized (me) {
			me.cached.add(ca);
		}
	}

	private final Collection<CachedAuthentication> cached = new ArrayList<CachedAuthentication>();

	@Override
	protected PasswordAuthentication getPasswordAuthentication() {
		for (final CachedAuthentication ca : cached) {
			if (ca.host.equals(getRequestingHost())
					&& ca.port == getRequestingPort())
				return ca.toPasswordAuthentication();
		}

		final GridBagConstraints gbc = new GridBagConstraints(0, 0, 1, 1, 1, 1,
				GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0);
		final Container panel = new JPanel();
		panel.setLayout(new GridBagLayout());

		final StringBuilder instruction = new StringBuilder();
		instruction.append("Enter username and password for ");
		if (getRequestorType() == RequestorType.PROXY) {
			instruction.append(getRequestorType());
			instruction.append(" ");
			instruction.append(getRequestingHost());
			if (getRequestingPort() > 0) {
				instruction.append(":");
				instruction.append(getRequestingPort());
			}
		} else {
			instruction.append(getRequestingURL());
		}

		gbc.weightx = 1.0;
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		gbc.gridx = 0;
		panel.add(new JLabel(instruction.toString()), gbc);
		gbc.gridy++;

		gbc.gridwidth = GridBagConstraints.RELATIVE;

		// Username
		//
		final JTextField username;
		gbc.fill = GridBagConstraints.NONE;
		gbc.gridx = 0;
		gbc.weightx = 1;
		panel.add(new JLabel("Username:"), gbc);

		gbc.gridx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weighty = 1;
		username = new JTextField(20);
		panel.add(username, gbc);
		gbc.gridy++;

		// Password
		//
		final JPasswordField password;
		gbc.fill = GridBagConstraints.NONE;
		gbc.gridx = 0;
		gbc.weightx = 1;
		panel.add(new JLabel("Password:"), gbc);

		gbc.gridx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weighty = 1;
		password = new JPasswordField(20);
		panel.add(password, gbc);
		gbc.gridy++;

		if (JOptionPane.showConfirmDialog(null, panel,
				"Authentication Required", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION) {
			final CachedAuthentication ca = new CachedAuthentication(
					getRequestingHost(), getRequestingPort(), username
							.getText(), new String(password.getPassword()));
			cached.add(ca);
			return ca.toPasswordAuthentication();
		}

		return null; // cancel
	}

	/** Authentication data to remember and reuse. */
	public static class CachedAuthentication {
		final String host;

		final int port;

		final String user;

		final String pass;

		/**
		 * Create a new cached authentication.
		 * 
		 * @param aHost
		 *            system this is for.
		 * @param aPort
		 *            port number of the service.
		 * @param aUser
		 *            username at the service.
		 * @param aPass
		 *            password at the service.
		 */
		public CachedAuthentication(final String aHost, final int aPort,
				final String aUser, final String aPass) {
			host = aHost;
			port = aPort;
			user = aUser;
			pass = aPass;
		}

		PasswordAuthentication toPasswordAuthentication() {
			return new PasswordAuthentication(user, pass.toCharArray());
		}
	}
}
