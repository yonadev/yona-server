package nu.yona.server.subscriptions.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.GONE, reason = "New device request expired")
public class NewDeviceRequestExpiredException extends RuntimeException
{
	private static final long serialVersionUID = 1764721609029661826L;

	public NewDeviceRequestExpiredException(UUID userID)
	{
		super("New device request expired for user with ID '" + userID + "'");
	}
}
