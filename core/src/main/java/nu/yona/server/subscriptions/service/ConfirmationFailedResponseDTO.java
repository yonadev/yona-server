package nu.yona.server.subscriptions.service;

import nu.yona.server.rest.ErrorResponseDTO;

public class ConfirmationFailedResponseDTO extends ErrorResponseDTO
{
	private final int remainingAttempts;

	public ConfirmationFailedResponseDTO(String code, String message, int remainingAttempts)
	{
		super(code, message);
		this.remainingAttempts = remainingAttempts;
	}

	public int getRemainingAttempts()
	{
		return remainingAttempts;
	}

}
