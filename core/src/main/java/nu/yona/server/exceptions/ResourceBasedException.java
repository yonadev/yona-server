/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import nu.yona.server.Translator;

/**
 * This exception class is used as a base for all exceptions that use an error message which is defined in the message properties
 */
public abstract class ResourceBasedException extends RuntimeException
{
	@Component
	private static class TranslatorInjector
	{
		@Autowired
		private Translator translator;

		@EventListener({ ContextRefreshedEvent.class })
		void onContextStarted(ContextRefreshedEvent event)
		{
			ResourceBasedException.setTranslator(translator);
		}
	}

	private static final long serialVersionUID = 8031973645020363969L;
	/** Holds the parameters for the exception message. */
	private final Object[] parameters;
	/** Holds the message id. */
	private final String messageId;
	/** Holds the HTTP response code to be used. */
	private final HttpStatus statusCode;
	private static Translator translator;

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

	public static void setTranslator(Translator translator)
	{
		ResourceBasedException.translator = translator;
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

	@Override
	public String getMessage()
	{
		return getLocalizedMessage();
	}

	@Override
	public String getLocalizedMessage()
	{
		String localizedMessage = tryTranslateMessage();
		if (localizedMessage == null)
		{
			localizedMessage = formAlternativeMessageText();
		}
		return localizedMessage;
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
	 * This method gets the parameters for the exception message.
	 * 
	 * @return The parameters for the exception message.
	 */
	public Object[] getParameters()
	{
		return parameters;
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

	private String tryTranslateMessage()
	{
		if (translator == null)
		{
			return null;
		}
		try
		{
			return translator.getLocalizedMessage(messageId, parameters);
		}
		catch (Exception e)
		{
			return null;
		}
	}

	private String formAlternativeMessageText()
	{
		StringBuffer sb = new StringBuffer(messageId);
		if ((parameters != null) && (parameters.length != 0))
		{
			sb.append("; parameters: ");
			appendParametersText(sb);
		}
		return sb.toString();
	}

	private void appendParametersText(StringBuffer sb)
	{
		boolean isFirst = true;
		for (Object parameter : parameters)
		{
			if (!isFirst)
			{
				sb.append(", ");
			}
			isFirst = false;
			sb.append('"');
			sb.append((parameter == null) ? "null" : parameter.toString());
			sb.append('"');
		}
	}
}
