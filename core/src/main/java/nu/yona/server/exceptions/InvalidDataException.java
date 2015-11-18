package nu.yona.server.exceptions;

/**
 * This exception is to be used in case data is wrong in DTOs. So whenever a field has a wrong value you should throw this
 * exception.
 * 
 * @author pgussow
 */
public class InvalidDataException extends ResourceBasedException
{

    public InvalidDataException(String messageId, Object... parameters)
    {
        super(messageId, parameters);
    }

    public InvalidDataException(Throwable t, String messageId, Object... parameters)
    {
        super(t, messageId, parameters);
    }
}
