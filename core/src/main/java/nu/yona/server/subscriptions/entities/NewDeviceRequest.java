package nu.yona.server.subscriptions.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Table;

import nu.yona.server.crypto.StringFieldEncrypter;
import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.RepositoryProvider;

/* 
 * A request to add another device for an existing user. 
 * The data cannot be encrypted with the user 'password' or auto-generated key because that is stored on the device and cannot be entered by the user.
 * Therefore we have to transfer the user 'password' to the new device and obviously encrypt it (with a secret entered by the user, e.g. her PIN or a one time password) while transferring.
 */
@Entity
@Table(name = "ADD_DEVICE_REQUESTS")
public class NewDeviceRequest extends EntityWithID {
	public static NewDeviceRequestRepository getRepository() {
		return (NewDeviceRequestRepository) RepositoryProvider.getRepository(NewDeviceRequest.class, UUID.class);
	}
	
	private LocalDateTime expirationDateTime;
	
	/* 
	 * The user 'password' (auto-generated key that is passed with every request),
	 * encrypted with a user secret (e.g. her PIN or a one time password).
	 * Notice a different CryptoSession will be used in this case.
	 */
	@Convert(converter = StringFieldEncrypter.class)
	private String userPassword;

	public LocalDateTime getExpirationTime() {
		return expirationDateTime;
	}

	public String getUserPassword() {
		return userPassword;
	}
	
	// Default constructor is required for JPA
	public NewDeviceRequest() {
		super(null);
	}
	
	private NewDeviceRequest(UUID id, String userPassword, LocalDateTime expirationDateTime) {
		super(id);
		this.userPassword = userPassword;
		this.expirationDateTime = expirationDateTime;
	}
	
	public static NewDeviceRequest createInstance(String userPassword, LocalDateTime expirationDateTime) {
		return new NewDeviceRequest(UUID.randomUUID(), userPassword, expirationDateTime);
	}
}
