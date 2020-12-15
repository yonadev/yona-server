/*******************************************************************************
 * Copyright (c) 2016, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import java.text.MessageFormat;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
public class Translator
{
	public static final Locale EN_US_LOCALE = Locale.forLanguageTag("en-US");

	/**
	 * The translator is set upon initialization of the context
	 */
	private static Translator staticReference;

	/**
	 * The source for the messages to use
	 */
	@Autowired
	@Qualifier("messageSource")
	private MessageSource msgSource;

	@EventListener({ ContextRefreshedEvent.class })
	void onContextStarted(ContextRefreshedEvent event)
	{
		Translator.staticReference = this;
	}

	/**
	 * This method returns the translated and formatted message for the passed message ID and parameters based on the given locale
	 *
	 * @return The actual message based on the default locale.
	 */
	public String getLocalizedMessage(String messageId, Object... parameters)
	{
		return getLocalizedMessage(messageId, parameters, null);
	}

	/**
	 * This method returns the translated and formatted message for the passed message ID and parameters based on the given locale
	 *
	 * @param locale The locale to use for getting the message.
	 * @return The actual message based on the given locale.
	 */
	public String getLocalizedMessage(String messageId, Object[] parameters, Locale locale)
	{
		if (locale == null)
		{
			locale = LocaleContextHolder.getLocale();
		}

		return msgSource.getMessage(messageId, parameters, locale);
	}

	public static Translator getInstance()
	{
		return staticReference;
	}

	public static String getStandardLocaleString(Locale locale)
	{
		return locale.toString().replace('_', '-');
	}

	public static String buildLocaleSpecificResourcePath(String format)
	{
		return MessageFormat.format(format, determineLocaleInfix());
	}

	private static Object determineLocaleInfix()
	{
		Locale locale = LocaleContextHolder.getLocale();
		if (locale.equals(EN_US_LOCALE))
		{
			return "";
		}
		return "_" + locale.getLanguage();
	}
}
