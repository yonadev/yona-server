package nu.yona.server.subscriptions.service;

import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;

import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserService.UserEncryptedEntitySet;

import org.springframework.stereotype.Service;

/*
 * Triggers the use of new subtransactions. See
 * http://stackoverflow.com/questions/15795985/spring-transaction-propagation-required-requires-new
 */
@Service
class UserServiceTempEncryptionContextExecutor
{
	// use a separate transaction to commit within the crypto session
	@Transactional(value = TxType.REQUIRES_NEW)
	public User addUserCreatedOnBuddyRequestFlush(UserDTO buddyUserResource)
	{
		return User.getRepository().save(
				User.createInstanceOnBuddyRequest(buddyUserResource.getFirstName(), buddyUserResource.getLastName(),
						buddyUserResource.getPrivateData().getNickName(), buddyUserResource.getMobileNumber()));
	}

	// use a separate transaction to read within the crypto session
	@Transactional(value = TxType.REQUIRES_NEW)
	public UserEncryptedEntitySet retrieveUserEncryptedDataFlush(User originalUserEntity)
	{
		UserAnonymized userAnonymizedEntity = originalUserEntity.getAnonymized();
		MessageSource namedMessageSource = originalUserEntity.getNamedMessageSource();
		MessageSource anonymousMessageSource = originalUserEntity.getAnonymousMessageSource();
		UserEncryptedEntitySet userEncryptedData = new UserEncryptedEntitySet(originalUserEntity, userAnonymizedEntity,
				namedMessageSource, anonymousMessageSource);
		userEncryptedData.loadLazyEncryptedData();
		return userEncryptedData;
	}
}