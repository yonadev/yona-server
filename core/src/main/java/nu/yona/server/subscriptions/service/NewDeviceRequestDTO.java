package nu.yona.server.subscriptions.service;

import java.time.LocalDateTime;

import nu.yona.server.subscriptions.entities.NewDeviceRequest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("newDeviceRequest")
public class NewDeviceRequestDTO {
	private LocalDateTime expirationDateTime;
	private String userPassword;
	
	public NewDeviceRequestDTO(LocalDateTime expirationDateTime, String userPassword) {
		this.expirationDateTime = expirationDateTime;
		this.userPassword = userPassword;
	}
	
	@JsonIgnore
	public LocalDateTime getExpirationDateTime() {
		return expirationDateTime;
	}
	
	@JsonIgnore
	public String getUserPassword() {
		return userPassword;
	}

	public static NewDeviceRequestDTO createInstance(NewDeviceRequest newDeviceRequestEntity) {
		if(newDeviceRequestEntity == null) {
			return new NewDeviceRequestDTO(null, null);
		}
		return new NewDeviceRequestDTO(newDeviceRequestEntity.getExpirationTime(), null);
	}
	
	public static NewDeviceRequestDTO createInstanceWithPassword(NewDeviceRequest newDeviceRequestEntity) {
		if(newDeviceRequestEntity == null) {
			return new NewDeviceRequestDTO(null, null);
		}
		return new NewDeviceRequestDTO(newDeviceRequestEntity.getExpirationTime(), newDeviceRequestEntity.getUserPassword());
	}
}
