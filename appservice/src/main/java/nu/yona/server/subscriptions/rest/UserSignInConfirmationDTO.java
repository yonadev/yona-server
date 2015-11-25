package nu.yona.server.subscriptions.rest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("userSignInConfirmation")
public class UserSignInConfirmationDTO
{
	private String code;

	public String getCode()
	{
		return code;
	}

	@JsonCreator
	public UserSignInConfirmationDTO(@JsonProperty("code") String code)
	{
		this.code = code;
	}
}
