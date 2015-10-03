package nu.yona.server.crypto;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.exceptions.YonaException;
import nu.yona.server.rest.Constants;

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Header " + Constants.PASSWORD_HEADER + " not provided")
public class MissingPasswordException extends YonaException {

	private static final long serialVersionUID = 1989876591001478378L;

	public MissingPasswordException() {
		super("Password not provided");
	}
}
