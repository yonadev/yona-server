/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import static nu.yona.server.test.CommonAssertions.*

import nu.yona.server.YonaServer

abstract class Service
{
	final String url
	final YonaServer yonaServer

	protected Service(String urlPropertyName, String defaultUrl)
	{
		this.url = getProperty(urlPropertyName, defaultUrl)
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
		String retVal = System.properties.getProperty(propertyName, defaultValue)

		if (!retVal?.trim())
		{
			throw new RuntimeException("Missing property: " + propertyName)
		}

		return retVal
	}

	void setEnableStatistics(def enable)
	{
		def response = yonaServer.createResource("/hibernateStatistics/enable/", "{}", [:], ["enable" : enable])
		assert response.status == 200 : "Ensure the server stats are enabled (run with -Dyona.enableHibernateStatsAllowed=true)"
	}

	void resetStatistics()
	{
		def response = getResource("/hibernateStatistics/", [:], ["reset" : "true"])
		assertResponseStatusOk(response)
	}

	void clearCaches()
	{
		def response = yonaServer.createResource("/hibernateStatistics/clearCaches/", "{}", [:], [:])
		assertResponseStatusOk(response)
	}

	def getStatistics()
	{
		def response = getResource("/hibernateStatistics/", [:], ["reset" : "false"])
		assertResponseStatusOk(response)
		response.responseData
	}

	def isSuccess(def response)
	{
		response.status >= 200 && response.status < 300
	}

	def createResourceWithPassword(path, jsonString, password, parameters = [:])
	{
		yonaServer.createResource(path, jsonString, ["Yona-Password": password], parameters)
	}

	def updateResourceWithPassword(path, jsonString, password, parameters = [:])
	{
		yonaServer.updateResource(path, jsonString, ["Yona-Password": password], parameters)
	}

	def deleteResourceWithPassword(path, password, parameters = [:])
	{
		yonaServer.deleteResourceWithPassword(path, password, parameters)
	}

	def getResourceWithPassword(path, password, parameters = [:])
	{
		yonaServer.getResourceWithPassword(path, password, parameters)
	}

	def getResource(path, headers = [:], parameters = [:])
	{
		yonaServer.getResource(path, headers, parameters)
	}

	def updateResource(path, jsonString, headers = [:], parameters = [:])
	{
		yonaServer.updateResource(path, jsonString, headers, parameters)
	}

	def deleteResource(path, headers = [:], parameters = [:])
	{
		yonaServer.deleteResource(path, headers, parameters)
	}
}
