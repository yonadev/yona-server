/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

public class LocaleContextHelperTest
{
	@Test
	public void inLocaleContext_nullLocale_throwsNPE()
	{
		assertThrows(NullPointerException.class, () -> LocaleContextHelper.inLocaleContext(() -> {
			return;
		}, null));
	}

	@Test
	public void inLocaleContext_nullRunnable_throwsNPE()
	{
		assertThrows(NullPointerException.class, () -> LocaleContextHelper.inLocaleContext(null, Locale.ITALIAN));
	}

	@Test
	public void inLocaleContext_defaultlLocale_localeSet()
	{
		Locale setLocale = Locale.ITALIAN;
		Locale initialLocale = LocaleContextHolder.getLocale();
		LocaleContextHelper.inLocaleContext(() -> assertThat(LocaleContextHolder.getLocale(), equalTo(setLocale)), setLocale);
		assertThat(LocaleContextHolder.getLocale(), equalTo(initialLocale));
	}

	@Test
	public void inLocaleContext_initializedlLocale_localeSet()
	{
		Locale setLocale = Locale.ITALIAN;
		Locale initialLocale = Locale.FRENCH;
		LocaleContextHolder.setLocale(initialLocale);
		LocaleContextHelper.inLocaleContext(() -> assertThat(LocaleContextHolder.getLocale(), equalTo(setLocale)), setLocale);
		assertThat(LocaleContextHolder.getLocale(), equalTo(initialLocale));
	}

	@Test
	public void inLocaleContext_throwFromRunnable_localeRestored()
	{
		Locale setLocale = Locale.ITALIAN;
		Locale initialLocale = Locale.FRENCH;
		LocaleContextHolder.setLocale(initialLocale);
		assertThrows(RuntimeException.class, () -> LocaleContextHelper.inLocaleContext(() -> {
			assertThat(LocaleContextHolder.getLocale(), equalTo(setLocale));
			throw new RuntimeException();
		}, setLocale));
		assertThat(LocaleContextHolder.getLocale(), equalTo(initialLocale));
	}
}
