package nu.yona.server.subscriptions.service;

import java.util.Date;

import nu.yona.server.subscriptions.entities.NewDeviceRequest;

import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("newDeviceRequest")
public class NewDeviceRequestDTO
{
	private Date expirationDateTime;
	private String userPassword;
	private boolean isUpdatingExistingRequest;

	public NewDeviceRequestDTO(Date expirationDateTime, String userPassword, boolean isUpdatingExistingRequest)
	{
		this.expirationDateTime = expirationDateTime;
		this.userPassword = userPassword;
		this.isUpdatingExistingRequest = isUpdatingExistingRequest;
	}

	public Date getExpirationDateTime()
	{
		return expirationDateTime;
	}

	public String getUserPassword()
	{
		return userPassword;
	}

	public boolean getIsUpdatingExistingRequest()
	{
		return isUpdatingExistingRequest;
	}

	public static NewDeviceRequestDTO createInstance(NewDeviceRequest newDeviceRequestEntity, boolean isUpdatingExistingRequest)
	{
		return new NewDeviceRequestDTO(newDeviceRequestEntity.getExpirationTime(), null, isUpdatingExistingRequest);
	}

	public static NewDeviceRequestDTO createInstance(NewDeviceRequest newDeviceRequestEntity)
	{
		return new NewDeviceRequestDTO(newDeviceRequestEntity.getExpirationTime(), null, false);
	}

	public static NewDeviceRequestDTO createInstanceWithPassword(NewDeviceRequest newDeviceRequestEntity)
	{
		return new NewDeviceRequestDTO(newDeviceRequestEntity.getExpirationTime(), newDeviceRequestEntity.getUserPassword(),
				false);
	}
}
