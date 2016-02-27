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
	final ACTIVITY_CATEGORIES_PATH = "/activityCategories/"
	final USERS_PATH = "/users/"
	final ANALYSIS_ENGINE_PATH = "/analysisEngine/"
	final BUDDIES_PATH_FRAGMENT = "/buddies/"
	final RELEVANT_SMOOTHWALL_CATEGORIES_PATH_FRAGMENT = "/relevantSmoothwallCategories/"
	final NEW_DEVICE_REQUEST_PATH_FRAGMENT = "/newDeviceRequest"
	final MOBILE_NUMBER_CONFIRMATION_PATH_FRAGMENT = "/confirmMobileNumber"

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

	def deleteGoal(goalURL)
	{
		deleteResource(goalURL)
	}

	def addActivityCategory(jsonString)
	{
		createResource(ACTIVITY_CATEGORIES_PATH, jsonString)
	}

	def getGoal(goalURL)
	{
		getResource(goalURL)
	}

	def addUser(jsonString, password, parameters = [:])
	{
		createResourceWithPassword(USERS_PATH, jsonString, password, parameters)
	}

	def requestOverwriteUser(mobileNumber)
	{
		updateResource(USERS_PATH, """{ }""", [:], ["mobileNumber":mobileNumber])
	}

	def getUser(userURL, boolean includePrivateData, password = null)
	{
		if (includePrivateData)
		{
			getResourceWithPassword(stripQueryString(userURL), password, getQueryParams(userURL) + ["includePrivateData": "true"])
		}
		else
		{
			getResourceWithPassword(userURL, password)
		}
	}

	def updateUser(userURL, jsonString, password)
	{
		updateResourceWithPassword(stripQueryString(userURL), jsonString, password, getQueryParams(userURL))
	}

	def deleteUser(userURL, password, message = "")
	{
		deleteResourceWithPassword(userURL, password, ["message":message])
	}

	def requestBuddy(userPath, jsonString, password)
	{
		createResourceWithPassword(userPath + BUDDIES_PATH_FRAGMENT, jsonString, password)
	}

	def removeBuddy(buddyURL, password, message)
	{
		deleteResourceWithPassword(buddyURL, password, ["message":message])
	}

	def getRelevantSmoothwallCategories()
	{
		getResource(ANALYSIS_ENGINE_PATH + RELEVANT_SMOOTHWALL_CATEGORIES_PATH_FRAGMENT)
	}

	def getAllActivityCategories()
	{
		getResource(ACTIVITY_CATEGORIES_PATH)
	}

	def getBuddies(userPath, password)
	{
		getResourceWithPassword(userPath + BUDDIES_PATH_FRAGMENT, password)
	}

	def setNewDeviceRequest(userPath, password, jsonString)
	{
		updateResourceWithPassword(userPath + NEW_DEVICE_REQUEST_PATH_FRAGMENT, jsonString, password)
	}

	def getNewDeviceRequest(userPath, userSecret = null)
	{
		getResource(userPath + NEW_DEVICE_REQUEST_PATH_FRAGMENT, [:], ["userSecret": userSecret])
	}

	def clearNewDeviceRequest(userPath, password)
	{
		deleteResourceWithPassword(userPath + NEW_DEVICE_REQUEST_PATH_FRAGMENT, password)
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

	def postMessageActionWithPassword(path, jsonString, password)
	{
		postMessageAction(path, jsonString, ["Yona-Password": password])
	}

	def postMessageAction(path, jsonString, headers = [:])
	{
		postJson(path, jsonString, headers)
	}

	def postToAnalysisEngine(jsonString)
	{
		postJson(ANALYSIS_ENGINE_PATH, jsonString)
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
