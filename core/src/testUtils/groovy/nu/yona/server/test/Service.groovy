package nu.yona.server.test

import nu.yona.server.YonaServer

abstract class Service
{
	final String url
	final YonaServer yonaServer

	protected Service(String urlPropertyName, String defaultURL)
	{
		this.url = getProperty(urlPropertyName, defaultURL)
		this.yonaServer = new YonaServer(url)
	}

	/**
	 * This method returns the requested property if it is available. If it is not available and no default value is provided,
	 * it throws an exception.
	 * 
	 * @param propertyName The name of the system property to retrieve.
	 * @param defaultValue The default property value
	 * @return The value.
	 */
	static def String getProperty(propertyName, defaultValue)
	{
		String retVal = System.properties.getProperty(propertyName, defaultValue);
		
		if (!retVal?.trim())
		{
			throw new RuntimeException("Missing property: " + propertyName);
		}
		
		return retVal;
	}
}
