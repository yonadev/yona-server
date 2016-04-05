package nu.yona.server.appresources;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import nu.yona.server.Translator;
import nu.yona.server.properties.YonaProperties;

@Component
/**
 * This servlet filter redirects requests for app resources to a locale specific path and it adds the Content-Language response
 * header. The requested locale is taken from the Accept-Language header (an earlier filter converts that into the Locale of the
 * request). That locale is matched against the set of supported locales. Of the requested locale is not supported, the default
 * locale is used. <br/>
 * If the request targets a localizable resource of the mobile app, then then request path is updated, to insert the locale into
 * it. E.g. A request for /resources/android/messages.properties with the Dutch locale is rewired to
 * /resources/android/nl-NL/messages.properties. <br/>
 * The Content-Language header with the actual locale is added response headers.
 */
public class LocalizationFilter implements Filter
{
	public static class LocalizationRequestWrapper extends HttpServletRequestWrapper
	{
		private final Locale locale;
		private YonaProperties properties;

		public LocalizationRequestWrapper(YonaProperties properties, HttpServletRequest request)
		{
			super(request);
			this.properties = properties;
			this.locale = determineLocale(request.getLocale());
		}

		@Override
		public Locale getLocale()
		{
			return locale;
		}

		private Locale determineLocale(Locale requestLocale)
		{
			if (properties.getSupportedLocales().contains(requestLocale))
			{
				return requestLocale;
			}
			return properties.getDefaultLocale();
		}
	}

	private static final Pattern resourcesPathPattern = Pattern.compile("(/resources/\\w+/)(.+)");

	private static final String IS_REDIRECTED_TO_LOCALIZED_RESOURCES = LocalizationFilter.class.getName()
			+ ".isRedirectedToLocalizedResources";

	@Autowired
	private YonaProperties properties;

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
			throws IOException, ServletException
	{
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;

		request = new LocalizationRequestWrapper(properties, request);
		response.setHeader(HttpHeaders.CONTENT_LANGUAGE, Translator.getStandardLocaleString(request.getLocale()));

		String requestURI = request.getRequestURI();
		Matcher matcher = resourcesPathPattern.matcher(requestURI);
		if (matcher.matches() && !isRedirectedAlready(request))
		{
			redirectToLocalizedResources(request, response, requestURI, matcher);
		}
		else
		{
			chain.doFilter(request, response);
		}
	}

	private boolean isRedirectedAlready(HttpServletRequest request)
	{
		return request.getAttribute(IS_REDIRECTED_TO_LOCALIZED_RESOURCES) != null;
	}

	private void redirectToLocalizedResources(HttpServletRequest request, HttpServletResponse response, String originalRequestURI,
			Matcher matcher) throws ServletException, IOException
	{
		String redirectedRequestURI = insertLocaleIntoRequestURI(request.getLocale(), originalRequestURI, matcher);
		markAsRedirected(request);
		request.getRequestDispatcher(redirectedRequestURI).forward(request, response);
	}

	private void markAsRedirected(HttpServletRequest request)
	{
		request.setAttribute(IS_REDIRECTED_TO_LOCALIZED_RESOURCES, Boolean.TRUE);
	}

	private String insertLocaleIntoRequestURI(Locale locale, String requestURI, Matcher matcher)
	{
		return matcher.group(1) + Translator.getStandardLocaleString(locale) + "/" + matcher.group(2);
	}

	@Override
	public void destroy()
	{
	}

	@Override
	public void init(FilterConfig config) throws ServletException
	{
	}
}