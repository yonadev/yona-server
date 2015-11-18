package nu.yona.server.subscriptions.rest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("createNewDeviceRequest")
public class CreateNewDeviceRequestDTO {
	private String userSecret;
	
	public String getUserSecret() {
		return userSecret;
	}
	
	@JsonCreator
	public CreateNewDeviceRequestDTO(@JsonProperty("userSecret") String userSecret) {
		this.userSecret = userSecret;
	}
}
