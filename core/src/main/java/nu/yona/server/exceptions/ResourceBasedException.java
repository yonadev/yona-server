package nu.yona.server.exceptions;

import org.springframework.http.HttpStatus;

/**
 * This exception class is used as a base for all exceptions that use an error message which is defined in the message properties
 * 
 * @author pgussow
 */
public abstract class ResourceBasedException extends RuntimeException
{
	private static final long serialVersionUID = 8031973645020363969L;
	/** Holds the parameters for the exception message. */
	private Object[] parameters;
	/** Holds the message id. */
	private String messageId;
	/** Holds the http response code to be used. */
	private HttpStatus statusCode;

	/**
	 * Constructor.
	 * 
	 * @param statusCode The status code of the exception.
	 * @param messageId The ID of the exception in the resource bundle
	 * @param parameters The parameters for the message
	 */
	protected ResourceBasedException(HttpStatus statusCode, String messageId, Object... parameters)
	{
		super(messageId);

		this.messageId = messageId;
		this.parameters = parameters;
		this.statusCode = statusCode;
	}
	
	/**
	 * Constructor.
	 * 
	 * @param messageId The ID of the exception in the resource bundle
	 * @param parameters The parameters for the message
	 */
	protected ResourceBasedException(String messageId, Object... parameters)
	{
		this(HttpStatus.BAD_REQUEST, messageId, parameters);
	}

	/**
	 * Constructor.
	 * 
	 * @param t The cause exception
	 * @param messageId The ID of the exception in the resource bundle
	 * @param parameters The parameters for the message
	 */
	protected ResourceBasedException(Throwable t, String messageId, Object... parameters)
	{
		this(HttpStatus.BAD_REQUEST, t, messageId, parameters);
	}
	
	/**
	 * Constructor.
	 * 
	 * @param statusCode The status code of the exception.
	 * @param t The cause exception
	 * @param messageId The ID of the exception in the resource bundle
	 * @param parameters The parameters for the message
	 */
	protected ResourceBasedException(HttpStatus statusCode, Throwable t, String messageId, Object... parameters)
	{
		super(messageId, t);

		this.messageId = messageId;
		this.parameters = parameters;
		this.statusCode = statusCode;
	}

	/**
	 * This method gets the message id.
	 * 
	 * @return The message id.
	 */
	public String getMessageId()
	{
		return messageId;
	}

	/**
	 * This method sets the message id.
	 * 
	 * @param messageId The message id.
	 */
	public void setMessageId(String messageId)
	{
		this.messageId = messageId;
	}

	/**
	 * This method gets the parameters for the exception message.
	 * 
	 * @return The parameters for the exception message.
	 */
	public Object[] getParameters()
	{
		return parameters;
	}

	/**
	 * This method sets the parameters for the exception message.
	 * 
	 * @param parameters The parameters for the exception message.
	 */
	public void setParameters(Object[] parameters)
	{
		this.parameters = parameters;
	}
	
	/**
	 * This method gets the http response code to be used.
	 * 
	 * @return The http response code to be used.
	 */
	public HttpStatus getStatusCode()
	{
		return statusCode;
	}

	/**
	 * This method sets the http response code to be used.
	 * 
	 * @param statusCode The http response code to be used.
	 */
	public void setStatusCode(HttpStatus statusCode)
	{
		this.statusCode = statusCode;
	}
}
