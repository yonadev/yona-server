package nu.yona.server.rest;

/**
 * This DTO is used to communicate error messages or other type of messages to the client.
 * 
 * @author pgussow
 */
public class ResponseMessageDTO
{
    /** Holds the type of message */
    private EResponseMessageType type;
    /** Holds the actual message */
    private String message;
    /** Holds the detailed message. */
    /** Holds the message code. */
    private String code;

    /**
     * Default constructor
     */
    public ResponseMessageDTO()
    {
    }

    /**
     * Creates a new DTO based on the given message.
     * 
     * @param type The type of message.
     * @param message The actual message to display.
     */
    public ResponseMessageDTO(EResponseMessageType type, String code, String message)
    {
        this.type = type;
        this.code = code;
        this.message = message;
    }
    
    /**
     * This method gets the message code.
     * 
     * @return The message code.
     */
    public String getCode()
    {
        return code;
    }

    /**
     * This method sets the message code.
     * 
     * @param code The message code.
     */
    public void setCode(String code)
    {
        this.code = code;
    }
    
    /**
     * This method gets the type of the message.
     * 
     * @return The type of the message.
     */
    public EResponseMessageType getType()
    {
        return type;
    }

    /**
     * This method sets the type of the message.
     * 
     * @param type The type of the message.
     */
    public void setType(EResponseMessageType type)
    {
        this.type = type;
    }
    
    /**
     * This method gets the actual message.
     * 
     * @return The actual message.
     */
    public String getMessage()
    {
        return message;
    }

    /**
     * This method sets the actual message.
     * 
     * @param message The actual message.
     */
    public void setMessage(String message)
    {
        this.message = message;
    }
}
