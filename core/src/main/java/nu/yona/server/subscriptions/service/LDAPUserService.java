/*******************************************************************************
 * Copyright (c) 2016, 2021 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import javax.naming.Name;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.odm.annotations.Attribute;
import org.springframework.ldap.odm.annotations.DnAttribute;
import org.springframework.ldap.odm.annotations.Entry;
import org.springframework.ldap.odm.annotations.Id;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.password.LdapShaPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LDAPUserService
{
	private static final Logger logger = LoggerFactory.getLogger(LDAPUserService.class);

	private enum Action
	{
		CREATE, DELETE
	}

	@Autowired(required = false)
	private LdapTemplate ldapTemplate;

	public void createVpnAccount(String vpnLoginId, String vpnPassword)
	{
		if (StringUtils.isBlank(vpnLoginId))
		{
			throw LDAPUserServiceException.emptyVpnLoginId();
		}

		if (StringUtils.isBlank(vpnPassword))
		{
			throw LDAPUserServiceException.emptyVpnPassword();
		}

		doLdapAction(Action.CREATE, new User(vpnLoginId, vpnPassword));
	}

	public void deleteVpnAccount(String vpnLoginId)
	{
		if (StringUtils.isBlank(vpnLoginId))
		{
			throw LDAPUserServiceException.emptyVpnLoginId();
		}

		doLdapAction(Action.DELETE, new User(vpnLoginId, ""));
	}

	private void doLdapAction(Action action, User user)
	{
		if (ldapTemplate == null)
		{
			logger.info("LDAP action {} not performed, as LDAP is not initialized.", action);
			return;
		}
		switch (action)
		{
			case CREATE -> ldapTemplate.create(user);
			case DELETE -> ldapTemplate.delete(user);
			default -> throw LDAPUserServiceException.actionUnknown(action.name());
		}
	}

	@Entry(objectClasses = { "top", "account", "shadowAccount", "posixAccount" }, base = "ou=SSL")
	private static final class User
	{
		/**
		 * Encoder for the password.
		 * SHA1 is not considered secure anymore, so Spring deprecated it, with no intend to remove it. We continue to use it
		 * because of our use case: secure the VPN to Smoothwall. If our LDAP gets hacked and our passwords are cracked, the only
		 * thing an attacker could do is ingest bad traffic to a random innocent user. The VPN accounts are anonymous, so it is
		 * not possible to ingest the traffic to a known user.
		 * Considering this, it's not worth it to update the LDAP configuration with a more secure password hashing module.
		 */
		@SuppressWarnings("deprecation")
		private static final LdapShaPasswordEncoder ldapShaPasswordEncoder = new LdapShaPasswordEncoder(
				KeyGenerators.secureRandom());

		@Id
		private Name dn;

		@Attribute
		private String cn;

		@Attribute
		@DnAttribute(value = "cn", index = 1)
		private String uid;

		@Attribute
		private int uidNumber;

		@Attribute
		private int gidNumber;

		@Attribute
		private final String homeDirectory = "/currently/unused";

		@Attribute
		private String userPassword;

		@SuppressWarnings("unused")
		public User()
		{
			// Default constructor used by Spring
		}

		User(String vpnLoginId, String vpnPassword)
		{
			cn = uid = vpnLoginId;

			userPassword = generateSaltedPassword(vpnPassword);
		}

		private static String generateSaltedPassword(String vpnPassword)
		{
			return ldapShaPasswordEncoder.encode(vpnPassword);
		}
	}
}
