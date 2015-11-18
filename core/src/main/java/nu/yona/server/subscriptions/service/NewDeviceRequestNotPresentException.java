package nu.yona.server.subscriptions.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No new device request present")
public class NewDeviceRequestNotPresentException extends RuntimeException {
	public NewDeviceRequestNotPresentException(UUID id) {
		super("No new device request present for user with ID '" + id + "'");
	}
}
