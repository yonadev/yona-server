/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*
import groovyx.net.http.RESTClient
import groovyx.net.http.URIBuilder

import java.text.SimpleDateFormat

class YonaServer
{
	JsonSlurper jsonSlurper = new JsonSlurper()
	RESTClient restClient

	YonaServer (baseURL)
	{
		restClient = new RESTClient(baseURL)

		restClient.handler.failure = restClient.handler.success
	}

	def static getTimeStamp()
	{
		def formatter = new SimpleDateFormat("yyyyMMddhhmmss")
		formatter.format(new Date())
	}

	def createResourceWithPassword(path, jsonString, password, parameters = [:])
	{
		createResource(path, jsonString, ["Yona-Password": password], parameters)
	}

	def createResource(path, jsonString, headers = [:], parameters = [:])
	{
		postJson(path, jsonString, headers, parameters)
	}

	def updateResourceWithPassword(path, jsonString, password, parameters = [:])
	{
		updateResource(path, jsonString, ["Yona-Password": password], parameters)
	}

	def updateResource(path, jsonString, headers = [:], parameters = [:])
	{
		putJson(path, jsonString, headers, parameters)
	}

	def deleteResourceWithPassword(path, password, parameters = [:])
	{
		deleteResource(path, ["Yona-Password": password], parameters)
	}

	def deleteResource(path, headers = [:], parameters = [:])
	{
		restClient.delete(path: path, headers: headers, query:parameters)
	}

	def getResourceWithPassword(path, password, parameters = [:])
	{
		getResource(path, password ? ["Yona-Password": password] : [ : ], parameters)
	}

	def getResource(path, headers = [:], parameters = [:])
	{
		restClient.get(path: path,
		contentType:'application/json',
		headers: headers,
		query: parameters)
	}

	def postJson(path, jsonString, headers = [:], parameters = [:])
	{
		def object = null
		if (jsonString instanceof Map)
		{
			object = jsonString
		}
		else
		{
			object = jsonSlurper.parseText(jsonString)
		}

		restClient.post(path: path,
		body: object,
		contentType:'application/json',
		headers: headers,
		query: parameters)
	}

	def putJson(path, jsonString, headers = [:], parameters = [:])
	{
		def object = null
		if (jsonString instanceof Map)
		{
			object = jsonString
		}
		else
		{
			object = jsonSlurper.parseText(jsonString)
		}

		restClient.put(path: path,
		body: object,
		contentType:'application/json',
		headers: headers,
		query: parameters)
	}

	def getQueryParams(url)
	{
		def uriBuilder = new URIBuilder(url)
		if(uriBuilder.query)
		{
			return uriBuilder.query
		}
		else
		{
			return [ : ]
		}
	}

	static def stripQueryString(url)
	{
		url - ~/\?.*/
	}

	static String makeStringList(def strings)
	{
		def stringList = ""
		strings.each(
				{
					stringList += (stringList) ? ", " : ""
					stringList += '\"' + it + '\"'
				})
		return stringList
	}

	static String makeList(def itemsJson)
	{
		def list = ""
		itemsJson.each(
				{
					list += (list) ? ", " : ""
					list += it
				})
		return list
	}

	static String makeStringMap(def strings)
	{
		def stringList = ""
		strings.keySet().each(
				{
					stringList += (stringList) ? ", " : ""
					stringList += '\"' + it + '\" : \"' + strings[it] + '\"'
				})
		return stringList
	}
}
