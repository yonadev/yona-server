package nu.yona.server.subscriptions.service;

import java.util.Collections;

import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.UserService.EncryptedUserData;

/*
 * Triggers the use of new subtransactions. See
 * http://stackoverflow.com/questions/15795985/spring-transaction-propagation-required-requires-new
 */
@Service
class UserServiceTempEncryptionContextExecutor
{
	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private UserService userService;

	@Autowired
	private LDAPUserService ldapUserService;

	// use a separate transaction to commit within the crypto session
	@Transactional(value = TxType.REQUIRES_NEW)
	public User addUserCreatedOnBuddyRequest(UserDTO buddyUserResource)
	{
		User newUser = User.createInstance(buddyUserResource.getFirstName(), buddyUserResource.getLastName(),
				buddyUserResource.getPrivateData().getNickName(), buddyUserResource.getMobileNumber(),
				CryptoUtil.getRandomString(yonaProperties.getSecurity().getPasswordLength()), Collections.emptySet(),
				Collections.emptySet());
		newUser.setIsCreatedOnBuddyRequest();
		userService.setUserUnconfirmedWithNewConfirmationCode(newUser);
		User savedUser = User.getRepository().save(newUser);
		ldapUserService.createVPNAccount(savedUser.getVPNLoginID().toString(), savedUser.getVPNPassword());
		return savedUser;
	}

	// use a separate transaction to read within the crypto session
	@Transactional(value = TxType.REQUIRES_NEW)
	public EncryptedUserData retrieveUserEncryptedData(User originalUserEntity)
	{
		MessageSource namedMessageSource = originalUserEntity.getNamedMessageSource();
		MessageSource anonymousMessageSource = originalUserEntity.getAnonymousMessageSource();
		EncryptedUserData userEncryptedData = new EncryptedUserData(originalUserEntity, namedMessageSource,
				anonymousMessageSource);
		userEncryptedData.loadLazyEncryptedData();
		return userEncryptedData;
	}
}
