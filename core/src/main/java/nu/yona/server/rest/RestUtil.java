package nu.yona.server.rest;

import org.springframework.http.HttpStatus;

public class RestUtil
{
	public static boolean isError(HttpStatus status)
	{
		HttpStatus.Series series = status.series();
		return (series == HttpStatus.Series.CLIENT_ERROR || series == HttpStatus.Series.SERVER_ERROR);
	}
}
