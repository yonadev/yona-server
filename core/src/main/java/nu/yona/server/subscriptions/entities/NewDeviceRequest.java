package nu.yona.server.subscriptions.entities;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.crypto.StringFieldEncrypter;
import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.RepositoryProvider;

/* 
 * A request to add another device for an existing user. 
 * The data cannot be encrypted with the user 'password' or auto-generated key because that is stored on the device and cannot be entered by the user.
 * Therefore we have to transfer the user 'password' to the new device and obviously encrypt it (with a secret entered by the user, e.g. her PIN or a one time password) while transferring.
 */
@Entity
@Table(name = "NEW_DEVICE_REQUESTS")
public class NewDeviceRequest extends EntityWithID {
	public static NewDeviceRequestRepository getRepository() {
		return (NewDeviceRequestRepository) RepositoryProvider.getRepository(NewDeviceRequest.class, UUID.class);
	}
	
	private Date expirationDateTime;
	
	@Transient
	private String userPassword;
	private String userPasswordCipherText;

	private byte[] initializationVector;
	
	public Date getExpirationTime() {
		return expirationDateTime;
	}

	public String getUserPassword() {
		return userPassword;
	}
	
	// Default constructor is required for JPA
	public NewDeviceRequest() {
		super(null);
	}
	
	private NewDeviceRequest(UUID id, String userPassword, Date expirationDateTime) {
		super(id);
		this.userPassword = userPassword;
		this.expirationDateTime = expirationDateTime;
	}
	
	public static NewDeviceRequest createInstance(String userPassword, Date expirationDateTime) {
		return new NewDeviceRequest(UUID.randomUUID(), userPassword, expirationDateTime);
	}
	
	public void encryptUserPassword(String userSecret) {
		this.userPasswordCipherText = CryptoSession.execute(Optional.of(userSecret), null,
				() -> {
					this.initializationVector = CryptoSession.getCurrent().generateInitializationVector();
					CryptoSession.getCurrent().setInitializationVector(this.initializationVector);
					return new StringFieldEncrypter().convertToDatabaseColumn(this.userPassword);
				});
	}
	
	public void decryptUserPassword(String userSecret) {
		this.userPassword = CryptoSession.execute(Optional.of(userSecret), null,
				() -> {
					CryptoSession.getCurrent().setInitializationVector(this.initializationVector);
					return new StringFieldEncrypter().convertToEntityAttribute(this.userPasswordCipherText);
				});
	}
}
