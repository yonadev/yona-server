package nu.yona.server.util;

import org.springframework.context.i18n.LocaleContextHolder;
import org.thymeleaf.context.Context;

public class ThymeleafUtil
{
	private ThymeleafUtil()
	{
		// No instances;
	}

	public static Context createContext()
	{
		Context ctx = new Context();
		ctx.setLocale(LocaleContextHolder.getLocale());
		return ctx;
	}
}
