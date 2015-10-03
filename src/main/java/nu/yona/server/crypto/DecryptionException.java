package nu.yona.server.crypto;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.exceptions.YonaException;

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Decryption failed")
public class DecryptionException extends YonaException {
	private static final long serialVersionUID = -8007474934276158682L;

	public DecryptionException(Throwable cause) {
		super("Decryption failed", cause);
	}

	public DecryptionException() {
		this(null);
	}
}
