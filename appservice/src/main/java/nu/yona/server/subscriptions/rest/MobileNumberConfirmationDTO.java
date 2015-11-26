package nu.yona.server.subscriptions.rest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("mobileNumberConfirmation")
public class MobileNumberConfirmationDTO
{
	private String code;

	public String getCode()
	{
		return code;
	}

	@JsonCreator
	public MobileNumberConfirmationDTO(@JsonProperty("code") String code)
	{
		this.code = code;
	}
}
