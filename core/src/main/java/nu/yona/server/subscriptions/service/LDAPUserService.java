package nu.yona.server.subscriptions.service;

import javax.naming.Name;

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
	@Autowired
	private LdapTemplate ldapTemplate;

	public void createVPNAccount(String loginID, String password)
	{
		create(new User(loginID, password));
	}

	public void deleteVPNAccount(String loginID)
	{
		delete(new User(loginID, ""));
	}

	private User create(User user)
	{
		ldapTemplate.create(user);
		return user;
	}

	private void delete(User user)
	{
		ldapTemplate.delete(user);
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

		User(String loginID, String password)
		{
			cn = uid = loginID;

			userPassword = generateSaltedPassword(password);
		}

		private static String generateSaltedPassword(String password)
		{
			LdapShaPasswordEncoder ldapShaPasswordEncoder = new LdapShaPasswordEncoder();
			return ldapShaPasswordEncoder.encodePassword(password, CryptoUtil.getRandomBytes(64));
		}
	}
}
