package nu.yona.server.subscriptions.service;

import java.util.Date;

import nu.yona.server.subscriptions.entities.NewDeviceRequest;

import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("newDeviceRequest")
public class NewDeviceRequestDTO
{
	private Date expirationDateTime;
	private String userPassword;

	public NewDeviceRequestDTO(Date expirationDateTime, String userPassword)
	{
		this.expirationDateTime = expirationDateTime;
		this.userPassword = userPassword;
	}

	public Date getExpirationDateTime()
	{
		return expirationDateTime;
	}

	public String getUserPassword()
	{
		return userPassword;
	}

	public static NewDeviceRequestDTO createInstance(NewDeviceRequest newDeviceRequestEntity)
	{
		return new NewDeviceRequestDTO(newDeviceRequestEntity.getExpirationTime(), null);
	}

	public static NewDeviceRequestDTO createInstanceWithPassword(NewDeviceRequest newDeviceRequestEntity)
	{
		return new NewDeviceRequestDTO(newDeviceRequestEntity.getExpirationTime(), newDeviceRequestEntity.getUserPassword());
	}
}
