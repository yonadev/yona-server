package nu.yona.server.subscriptions.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("user")
public class OverwriteUserDTO
{
	/*
	 * Only intended for test purposes.
	 */
	private String confirmationCode;

	public OverwriteUserDTO()
	{

	}

	/*
	 * Only intended for test purposes.
	 */
	public void setConfirmationCode(String confirmationCode)
	{
		this.confirmationCode = confirmationCode;
	}

	/*
	 * Only intended for test purposes.
	 */
	@JsonInclude(Include.NON_EMPTY)
	public String getConfirmationCode()
	{
		return confirmationCode;
	}

	public static OverwriteUserDTO createInstance()
	{
		return new OverwriteUserDTO();
	}
}
