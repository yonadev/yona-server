package nu.yona.server.subscriptions.service;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.NewDeviceRequest;
import nu.yona.server.subscriptions.entities.User;

@Service
public class NewDeviceRequestService
{
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
			return NewDeviceRequestDTO.createInstanceWithPassword(newDeviceRequestEntity);
		}
		else
		{
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
			userEntity.setNewDeviceRequest(null);
			User.getRepository().save(userEntity);
		}
	}

	private boolean isExpired(NewDeviceRequest newDeviceRequestEntity)
	{
		Date creationTime = newDeviceRequestEntity.getCreationTime();
		return (creationTime.getTime() + getExpirationIntervalMillis() < System.currentTimeMillis());
	}

	private long getExpirationIntervalMillis()
	{
		return yonaProperties.getSecurity().getNewDeviceRequestExpirationDays() * 24 * 60 * 60 * 1000;
	}
}
