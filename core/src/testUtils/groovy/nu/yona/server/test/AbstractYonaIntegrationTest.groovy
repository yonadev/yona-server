package nu.yona.server.test

import nu.yona.server.YonaServer
import spock.lang.Specification

abstract class AbstractYonaIntegrationTest extends Specification
{
	def adminServiceBaseURL = getParameter('yona.adminservice.url', "http://localhost:8080")
	def YonaServer adminService = new YonaServer(adminServiceBaseURL)

	def analysisServiceBaseURL = getParameter('yona.analysisservice.url', "http://localhost:8081")
	def YonaServer analysisService = new YonaServer(analysisServiceBaseURL)

	def appServiceBaseURL = getParameter('yona.appservice.url', "http://localhost:8082")
	def YonaServer appService = new YonaServer(appServiceBaseURL)
	
	/**
	 * This method checks whether or not the given system property is avialble.
	 * 
	 * @param parameterName The name of the system property to retrieve.
	 * @return The value. If the value is empty an exception is thrown.
	 */
	def String getParameter(parameterName, defaultValue)
	{
		def retVal = System.properties.getProperty(parameterName, defaultValue);
		
		if (!retVal?.trim())
		{
			throw new RuntimeException("Missing test parameter: " + parameterName);
		}
		
		return retVal;
	}
}
