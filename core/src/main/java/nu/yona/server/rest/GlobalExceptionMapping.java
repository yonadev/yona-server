package nu.yona.server.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.Translator;
import nu.yona.server.exceptions.YonaException;

/**
 * This class contains the mapping for the different exceptions and how they should be mapped to an http response
 * 
 * @author pgussow
 */
@ControllerAdvice
public class GlobalExceptionMapping
{
	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionMapping.class);

	/** The source that contains the actual messages for the codes */
	@Autowired
	Translator translator;

	/**
	 * This method generically handles the illegal argument exceptions. They are translated into nice ResponseMessage objects so
	 * the response data is properly organized and JSON parseable.
	 * 
	 * @param exception The exception.
	 * @return The response object to return.
	 */
	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ResponseBody
	public ResponseMessageDTO handleOtherException(Exception exception)
	{
		logger.error("Unhandled exception", exception);

		return new ResponseMessageDTO(ResponseMessageType.ERROR, null, exception.getMessage());
	}

	/**
	 * This method generically handles the Yona exceptions. They are translated into nice ResponseMessage objects so the response
	 * data is properly organized and JSON parseable.
	 * 
	 * @param exception The exception.
	 * @return The response object to return.
	 */
	@ExceptionHandler(YonaException.class)
	public ResponseEntity<ResponseMessageDTO> handleYonaException(YonaException exception)
	{
		logger.error("Unhandled exception", exception);

		ResponseMessageDTO responseMessage = new ResponseMessageDTO(ResponseMessageType.ERROR, exception.getMessageId(),
				translator.getLocalizedMessage(exception.getMessageId(), exception.getParameters()));

		return new ResponseEntity<ResponseMessageDTO>(responseMessage, exception.getStatusCode());
	}
}
