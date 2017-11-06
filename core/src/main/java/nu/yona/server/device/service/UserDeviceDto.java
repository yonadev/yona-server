package nu.yona.server.device.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.device.entities.UserDeviceRepository;
import nu.yona.server.entities.RepositoryProvider;

@JsonRootName("device")
public class UserDeviceDto extends DeviceBaseDto
{
	private final LocalDateTime registrationTime;

	private final LocalDate appLastOpenedDate;

	private UserDeviceDto(UUID id, String name, boolean isVpnConnected, LocalDateTime registrationTime,
			LocalDate appLastOpenedDate)
	{
		super(id, name, isVpnConnected);
		this.registrationTime = registrationTime;
		this.appLastOpenedDate = appLastOpenedDate;
	}

	@JsonCreator
	public UserDeviceDto(@JsonProperty("name") String name)
	{
		this(null, name, true, LocalDateTime.now(), LocalDate.now());
	}

	public static UserDeviceRepository getRepository()
	{
		return (UserDeviceRepository) RepositoryProvider.getRepository(UserDevice.class, UUID.class);
	}

	public LocalDateTime getRegistrationTime()
	{
		return registrationTime;
	}

	public LocalDate getAppLastOpenedDate()
	{
		return appLastOpenedDate;
	}

	public static UserDeviceDto createInstance(UserDevice deviceEntity)
	{
		return new UserDeviceDto(deviceEntity.getId(), deviceEntity.getName(), deviceEntity.isVpnConnected(),
				deviceEntity.getRegistrationTime(), deviceEntity.getAppLastOpenedDate());
	}
}
