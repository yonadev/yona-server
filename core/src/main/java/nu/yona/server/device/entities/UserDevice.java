package nu.yona.server.device.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.Type;

import nu.yona.server.crypto.seckey.DateFieldEncryptor;
import nu.yona.server.crypto.seckey.DateTimeFieldEncryptor;

@Entity
@Table(name = "USER_DEVICES")
public class UserDevice extends DeviceBase
{
	@Type(type = "uuid-char")
	@Column(name = "user_private_id")
	private UUID userPrivateId;

	@Convert(converter = DateTimeFieldEncryptor.class)
	private LocalDateTime registrationTime;

	@Convert(converter = DateFieldEncryptor.class)
	private LocalDate appLastOpenedDate;

	// Default constructor is required for JPA
	public UserDevice()
	{
	}

	public UserDevice(UUID id, UUID userPrivateId, String name)
	{
		super(id, name);
		Objects.requireNonNull(userPrivateId);
		Objects.requireNonNull(name);
		this.userPrivateId = userPrivateId;
		this.registrationTime = LocalDateTime.now();
		this.appLastOpenedDate = LocalDate.now(); // The user registers this device, so the app is open now
	}

	public LocalDateTime getRegistrationTime()
	{
		return registrationTime;
	}

	public void setRegistrationTime(LocalDateTime registrationTime)
	{
		Objects.requireNonNull(registrationTime);
		this.registrationTime = registrationTime;
	}

	public LocalDate getAppLastOpenedDate()
	{
		return appLastOpenedDate;
	}

	public void setAppLastOpenedDate(LocalDate appLastOpenedDate)
	{
		Objects.requireNonNull(appLastOpenedDate);
		this.appLastOpenedDate = appLastOpenedDate;
	}
}
