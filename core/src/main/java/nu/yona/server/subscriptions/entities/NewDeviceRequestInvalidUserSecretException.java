package nu.yona.server.subscriptions.entities;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Invalid user secret to use new device request. Maybe the request was replaced with a different secret?")
public class NewDeviceRequestInvalidUserSecretException extends RuntimeException
{
	private static final long serialVersionUID = -3433484340573486425L;

	public NewDeviceRequestInvalidUserSecretException()
	{
		super("Invalid user secret to use new device request. Maybe the request was replaced with a different secret?");
	}
}
