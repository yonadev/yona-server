package nu.yona.server.test

import nu.yona.server.YonaServer
import spock.lang.Specification

abstract class AbstractYonaIntegrationTest extends Specification
{
	def adminServiceBaseURL = getProperty('yona.adminservice.url', "http://localhost:8080")
	def YonaServer adminService = new YonaServer(adminServiceBaseURL)

	def analysisServiceBaseURL = getProperty('yona.analysisservice.url', "http://localhost:8081")
	def YonaServer analysisService = new YonaServer(analysisServiceBaseURL)

	def appServiceBaseURL = getProperty('yona.appservice.url', "http://localhost:8082")
	def YonaServer appService = new YonaServer(appServiceBaseURL)
	
	/**
	 * This method checks whether or not the given system property is available.
	 * 
	 * @param propertyName The name of the system property to retrieve.
	 * @return The value. If the value is empty an exception is thrown.
	 */
	def String getProperty(propertyName, defaultValue)
	{
		def retVal = System.properties.getProperty(propertyName, defaultValue);
		
		if (!retVal?.trim())
		{
			throw new RuntimeException("Missing property: " + propertyName);
		}
		
		return retVal;
	}
}
