package nu.yona.server.subscriptions.service;

import java.util.UUID;

import nu.yona.server.exceptions.YonaException;

public class DeviceRequestException extends YonaException
{
	private static final long serialVersionUID = -7070143633618007280L;

	private DeviceRequestException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	public static DeviceRequestException noDeviceRequestPresent(UUID userID)
	{
		return new DeviceRequestException("error.no.device.request.present", userID);
	}

	public static DeviceRequestException deviceRequestExpired(UUID userID)
	{
		return new DeviceRequestException("error.device.request.expired", userID);
	}

	public static DeviceRequestException invalidSecret()
	{
		return new DeviceRequestException("error.device.request.invalid.secret");
	}
}
