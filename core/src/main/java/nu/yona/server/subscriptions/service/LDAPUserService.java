/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
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
import org.springframework.security.authentication.encoding.LdapShaPasswordEncoder;
import org.springframework.stereotype.Service;

import nu.yona.server.crypto.CryptoUtil;

@Service
public class LDAPUserService
{
	private static final Logger logger = LoggerFactory.getLogger(LDAPUserService.class);

	private enum Action
	{
		CREATE, DELETE
	}

	@Autowired
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
			case CREATE:
				ldapTemplate.create(user);
				break;
			case DELETE:
				ldapTemplate.delete(user);
				break;
			default:
				throw LDAPUserServiceException.actionUnknown(action.name());

		}
	}

	@Entry(objectClasses = { "top", "account", "shadowAccount", "posixAccount" }, base = "ou=SSL")
	private static final class User
	{
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
			LdapShaPasswordEncoder ldapShaPasswordEncoder = new LdapShaPasswordEncoder();
			return ldapShaPasswordEncoder.encodePassword(vpnPassword, CryptoUtil.getRandomBytes(64));
		}
	}
}
