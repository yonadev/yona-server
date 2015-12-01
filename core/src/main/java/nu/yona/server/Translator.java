package nu.yona.server;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
public class Translator
{
	/** The source for the messages to use */
	@Autowired
	private MessageSource msgSource;

	/**
	 * This method returns the message for this exception based on the given locale
	 * 
	 * @return The actual message based on the default locale.
	 */
	public String getLocalizedMessage(String messageId, Object... parameters)
	{
		return getLocalizedMessage(messageId, parameters, null);
	}

	/**
	 * This method returns the message for this exception based on the given locale
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
}
