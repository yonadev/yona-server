package nu.yona.server.rest;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus.Series;
import org.springframework.stereotype.Component;

@Component
public class ErrorLoggingFilter implements Filter
{
	private static final Logger logger = LoggerFactory.getLogger(ErrorLoggingFilter.class);

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
			throws IOException, ServletException
	{
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;

		chain.doFilter(request, response);

		if (HttpStatus.Series.valueOf(response.getStatus()) == Series.SUCCESSFUL)
		{
			return;
		}

		String queryString = request.getQueryString();
		if (queryString == null)
		{
			logger.warn("Status {} returned from {} on URL {}", response.getStatus(), request.getMethod(),
					request.getRequestURI());
		}
		else
		{
			logger.warn("Status {} returned from {} on URL {} with query string {}", response.getStatus(), request.getMethod(),
					request.getRequestURI(), queryString);
		}
	}

	@Override
	public void destroy()
	{
		// Nothing to do here
	}

	@Override
	public void init(FilterConfig config) throws ServletException
	{
		// Nothing to do here
	}
}