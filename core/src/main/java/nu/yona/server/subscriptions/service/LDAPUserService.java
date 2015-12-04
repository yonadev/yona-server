package nu.yona.server.subscriptions.service;

import java.util.logging.Logger;

import javax.naming.Name;

import org.apache.commons.lang.StringUtils;
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
	private static final Logger LOGGER = Logger.getLogger(LDAPUserService.class.getName());

	private enum Action
	{
		CREATE, DELETE
	};

	@Autowired
	private LdapTemplate ldapTemplate;

	public void createVPNAccount(String vpnLoginID, String vpnPassword)
	{
		if (StringUtils.isBlank(vpnLoginID))
		{
			throw LDAPUserServiceException.emptyVpnLoginId();
		}

		if (StringUtils.isBlank(vpnPassword))
		{
			throw LDAPUserServiceException.emptyVpnPassword();
		}

		doLdapAction(Action.CREATE, new User(vpnLoginID, vpnPassword));
	}

	public void deleteVPNAccount(String vpnLoginID)
	{
		if (StringUtils.isBlank(vpnLoginID))
		{
			throw LDAPUserServiceException.emptyVpnLoginId();
		}

		doLdapAction(Action.DELETE, new User(vpnLoginID, ""));
	}

	private void doLdapAction(Action action, User user)
	{
		if (ldapTemplate == null)
		{
			LOGGER.info("LDAP action " + action + " not performed, as LDAP is not initialized.");
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
	private static class User
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
		private String homeDirectory = "/currently/unused";

		@Attribute
		private String userPassword;

		@SuppressWarnings("unused")
		public User()
		{
			// Default constructor used by Spring
		}

		User(String vpnLoginID, String vpnPassword)
		{
			cn = uid = vpnLoginID;

			userPassword = generateSaltedPassword(vpnPassword);
		}

		private static String generateSaltedPassword(String vpnPassword)
		{
			LdapShaPasswordEncoder ldapShaPasswordEncoder = new LdapShaPasswordEncoder();
			return ldapShaPasswordEncoder.encodePassword(vpnPassword, CryptoUtil.getRandomBytes(64));
		}
	}
}
