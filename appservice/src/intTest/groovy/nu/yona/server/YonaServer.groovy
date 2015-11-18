package nu.yona.server

import groovyx.net.http.RESTClient
import groovy.json.*

import java.text.SimpleDateFormat

class YonaServer {
	final GOALS_PATH = "/goals/"
	final USERS_PATH = "/users/"
	final ANALYSIS_ENGINE_PATH = "/analysisEngine/"
	final BUDDIES_PATH_FRAGMENT = "/buddies/"
	final DIRECT_MESSAGE_PATH_FRAGMENT = "/messages/direct/"
	final ANONYMOUS_MESSAGES_PATH_FRAGMENT = "/messages/anonymous/"
	final RELEVANT_CATEGORIES_PATH_FRAGMENT = "/relevantCategories/"
	final NEW_DEVICE_REQUEST_PATH_FRAGMENT = "/newDeviceRequest"

	JsonSlurper jsonSlurper = new JsonSlurper()
	RESTClient restClient

	YonaServer (baseURL)
	{
		restClient = new RESTClient(baseURL)
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

	def addGoal(jsonString)
	{
		createResource(GOALS_PATH, jsonString)
	}
	
	def getGoal(goalURL)
	{
		getResource(goalURL)
	}

	def addUser(jsonString, password)
	{
		createResourceWithPassword(USERS_PATH, jsonString, password)
	}

	def getUser(userURL, boolean includePrivateData, password = null)
	{
		if (includePrivateData) {
			getResourceWithPassword(userURL, password, ["includePrivateData": "true"])
		} else {
			getResourceWithPassword(userURL, password)
		}
	}

	def deleteUser(userURL, password)
	{
		deleteResourceWithPassword(userURL, password)
	}

	def requestBuddy(userPath, jsonString, password)
	{
		createResourceWithPassword(userPath + BUDDIES_PATH_FRAGMENT, jsonString, password)
	}

	def getRelevantCategories()
	{
		getResource(ANALYSIS_ENGINE_PATH + RELEVANT_CATEGORIES_PATH_FRAGMENT)
	}

	def getAllGoals()
	{
		getResource(GOALS_PATH)
	}

	def getBuddies(userPath, password)
	{
		getResourceWithPassword(userPath + BUDDIES_PATH_FRAGMENT, password)
	}

	def getDirectMessages(userPath, password)
	{
		getResourceWithPassword(userPath + DIRECT_MESSAGE_PATH_FRAGMENT, password)
	}

	def getAnonymousMessages(userPath, password)
	{
		getResourceWithPassword(userPath + ANONYMOUS_MESSAGES_PATH_FRAGMENT, password)
	}

	def setNewDeviceRequest(userPath, password, jsonString)
	{
		updateResourceWithPassword(userPath + NEW_DEVICE_REQUEST_PATH_FRAGMENT, jsonString, password)
	}
	
	def getNewDeviceRequest(userPath, password, userSecret = null)
	{
		getResourceWithPassword(userPath + NEW_DEVICE_REQUEST_PATH_FRAGMENT, password, ["userSecret": userSecret])
	}
	
	def clearNewDeviceRequest(userPath, password)
	{
		deleteResourceWithPassword(userPath + NEW_DEVICE_REQUEST_PATH_FRAGMENT, password)
	}

	def createResourceWithPassword(path, jsonString, password)
	{
		createResource(path, jsonString, ["Yona-Password": password])
	}

	def createResource(path, jsonString, headers = [:])
	{
		postJson(path, jsonString, headers);
	}

	def updateResourceWithPassword(path, jsonString, password)
	{
		updateResource(path, jsonString, ["Yona-Password": password])
	}

	def updateResource(path, jsonString, headers = [:])
	{
		def object = jsonSlurper.parseText(jsonString)
		restClient.put(path: path, 
			body: object, 
			contentType:'application/json',
			headers: headers)
	}

	def deleteResourceWithPassword(path, password)
	{
		deleteResource(path, ["Yona-Password": password])
	}

	def deleteResource(path, headers = [:])
	{
		restClient.delete(path: path, headers: headers)
	}

	def getResourceWithPassword(path, password, parameters = [:])
	{
		getResource(path, password ?  ["Yona-Password": password] : [ : ], parameters)
	}

	def postMessageActionWithPassword(path, jsonString, password)
	{
		postMessageAction(path, jsonString, ["Yona-Password": password])
	}

	def postMessageAction(path, jsonString, headers = [:])
	{
		postJson(path, jsonString, headers);
	}

	def postToAnalysisEngine(jsonString)
	{
		postJson(ANALYSIS_ENGINE_PATH, jsonString);
	}

	def getResource(path, headers = [:], parameters = [:])
	{
		restClient.get(path: path,
			contentType:'application/json',
			headers: headers,
			query: parameters)
	}

	def postJson(path, jsonString, headers = [:])
	{
		def object = jsonSlurper.parseText(jsonString)
		restClient.post(path: path,
			body: object,
			contentType:'application/json',
			headers: headers)
	}

	def stripQueryString(url)
	{
		url - ~/\?.*/
	}

}
