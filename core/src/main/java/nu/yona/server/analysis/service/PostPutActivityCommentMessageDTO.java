package nu.yona.server.analysis.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("postPutActivityCommentMessage")
public class PostPutActivityCommentMessageDTO
{
	private final String message;

	@JsonCreator
	public PostPutActivityCommentMessageDTO(@JsonProperty(value = "message", required = true) String message)
	{
		this.message = message;
	}

	public String getMessage()
	{
		return message;
	}
}
