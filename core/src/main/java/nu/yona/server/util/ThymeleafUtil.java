/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.util;

import org.springframework.context.i18n.LocaleContextHolder;
import org.thymeleaf.context.Context;

public class ThymeleafUtil
{
	private ThymeleafUtil()
	{
		// No instances
	}

	public static Context createContext()
	{
		Context ctx = new Context();
		ctx.setLocale(LocaleContextHolder.getLocale());
		return ctx;
	}
}
