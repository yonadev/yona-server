package nu.yona.server.subscriptions.service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.NewDeviceRequest;
import nu.yona.server.subscriptions.entities.User;

@Service
public class NewDeviceRequestService
{
	private static final Logger logger = LoggerFactory.getLogger(NewDeviceRequestService.class);

	@Autowired
	private UserService userService;

	@Autowired
	private YonaProperties yonaProperties;

	@Transactional
	public NewDeviceRequestDTO setNewDeviceRequestForUser(UUID userID, String yonaPassword, String newDeviceRequestPassword)
	{
		User userEntity = userService.getValidatedUserbyID(userID);

		NewDeviceRequest newDeviceRequestEntity = NewDeviceRequest.createInstance(yonaPassword);
		newDeviceRequestEntity.encryptYonaPassword(newDeviceRequestPassword);

		userEntity.setNewDeviceRequest(newDeviceRequestEntity);

		logger.info("User with mobile number '{}' and ID '{}' set a new device request", userEntity.getMobileNumber(),
				userEntity.getID());
		return NewDeviceRequestDTO.createInstance(User.getRepository().save(userEntity).getNewDeviceRequest());
	}

	@Transactional
	public NewDeviceRequestDTO getNewDeviceRequestForUser(UUID userID, Optional<String> newDeviceRequestPassword)
	{
		User userEntity = userService.getValidatedUserbyID(userID);
		NewDeviceRequest newDeviceRequestEntity = userEntity.getNewDeviceRequest();

		if (newDeviceRequestEntity == null)
		{
			throw DeviceRequestException.noDeviceRequestPresent(userEntity.getMobileNumber());
		}

		if (isExpired(newDeviceRequestEntity))
		{
			throw DeviceRequestException.deviceRequestExpired(userEntity.getMobileNumber());
		}

		if (newDeviceRequestPassword.isPresent())
		{
			newDeviceRequestEntity.decryptYonaPassword(newDeviceRequestPassword.get(), userEntity.getMobileNumber());
			logger.info("User with mobile number '{}' and ID '{}' fetched the new device request", userEntity.getMobileNumber(),
					userEntity.getID());
			return NewDeviceRequestDTO.createInstanceWithPassword(newDeviceRequestEntity);
		}
		else
		{
			logger.info("User with mobile number '{}' and ID '{}' verified the existence of new device request",
					userEntity.getMobileNumber(), userEntity.getID());
			return NewDeviceRequestDTO.createInstance(userEntity.getNewDeviceRequest());
		}
	}

	@Transactional
	public void clearNewDeviceRequestForUser(UUID userID)
	{
		User userEntity = userService.getValidatedUserbyID(userID);

		NewDeviceRequest existingNewDeviceRequestEntity = userEntity.getNewDeviceRequest();
		if (existingNewDeviceRequestEntity != null)
		{
			logger.info("User with mobile number '{}' and ID '{}' cleared the new device request", userEntity.getMobileNumber(),
					userEntity.getID());
			userEntity.setNewDeviceRequest(null);
			User.getRepository().save(userEntity);
		}
	}

	private boolean isExpired(NewDeviceRequest newDeviceRequestEntity)
	{
		ZonedDateTime creationTime = newDeviceRequestEntity.getCreationTime();
		return creationTime.plus(Duration.ofMillis(getExpirationIntervalMillis())).isBefore(ZonedDateTime.now());
	}

	private long getExpirationIntervalMillis()
	{
		return yonaProperties.getSecurity().getNewDeviceRequestExpirationDays() * 24 * 60 * 60 * 1000;
	}
}
