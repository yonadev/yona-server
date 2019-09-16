/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import java.util.Locale;
import java.util.Objects;

import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;

public class LocaleContextHelper
{
	private LocaleContextHelper()
	{
		// No instances
	}

	public static void inLocaleContext(Runnable runnable, Locale locale)
	{
		Objects.requireNonNull(runnable);
		Objects.requireNonNull(locale);
		try (AutoRestoringLocaleContext localeContextSetter = putLocaleInContext(locale))
		{
			runnable.run();
		}
	}

	private static AutoRestoringLocaleContext putLocaleInContext(Locale locale)
	{
		AutoRestoringLocaleContext localeContextSetter = new AutoRestoringLocaleContext(LocaleContextHolder.getLocaleContext(),
				locale);
		LocaleContextHolder.setLocaleContext(localeContextSetter);
		return localeContextSetter;
	}

	private static class AutoRestoringLocaleContext implements AutoCloseable, LocaleContext
	{

		private final LocaleContext localeContext;
		private final Locale locale;

		public AutoRestoringLocaleContext(LocaleContext localeContext, Locale locale)
		{
			this.localeContext = localeContext;
			this.locale = locale;
		}

		@Override
		public Locale getLocale()
		{
			return locale;
		}

		@Override
		public void close()
		{
			LocaleContextHolder.setLocaleContext(localeContext);
		}
	}
}