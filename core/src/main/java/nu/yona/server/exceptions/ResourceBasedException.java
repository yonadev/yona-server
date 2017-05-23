/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

import java.util.Arrays;

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
	private final String[] messageArgs;
	private final String messageId;
	private final HttpStatus statusCode;
	private static Translator translator;

	protected ResourceBasedException(HttpStatus statusCode, String messageId, Object... messageArgs)
	{
		super(messageId);

		this.messageId = messageId;
		this.messageArgs = serializeMessageArgs(messageArgs);
		this.statusCode = statusCode;
	}

	public static void setTranslator(Translator translator)
	{
		ResourceBasedException.translator = translator;
	}

	protected ResourceBasedException(String messageId, Object... messageArgs)
	{
		this(HttpStatus.BAD_REQUEST, messageId, messageArgs);
	}

	protected ResourceBasedException(Throwable cause, String messageId, Object... messageArgs)
	{
		this(HttpStatus.BAD_REQUEST, cause, messageId, messageArgs);
	}

	protected ResourceBasedException(HttpStatus statusCode, Throwable cause, String messageId, Object... messageArgs)
	{
		super(messageId, cause);

		this.messageId = messageId;
		this.messageArgs = serializeMessageArgs(messageArgs);
		this.statusCode = statusCode;
	}

	private String[] serializeMessageArgs(Object... messageArgs)
	{
		return Arrays.stream(messageArgs).map(arg -> arg.toString()).toArray(String[]::new);
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

	public String getMessageId()
	{
		return messageId;
	}

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
			return translator.getLocalizedMessage(messageId, Arrays.copyOf(messageArgs, messageArgs.length, Object[].class));
		}
		catch (Exception e)
		{
			return null;
		}
	}

	private String formAlternativeMessageText()
	{
		StringBuilder sb = new StringBuilder(messageId);
		if ((messageArgs != null) && (messageArgs.length != 0))
		{
			sb.append("; parameters: ");
			appendParametersText(sb);
		}
		return sb.toString();
	}

	private void appendParametersText(StringBuilder sb)
	{
		boolean isFirst = true;
		for (Object parameter : messageArgs)
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
