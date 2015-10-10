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

	JsonSlurper jsonSlurper = new JsonSlurper()
	RESTClient restClient

	YonaServer (baseURL)
	{
		restClient = new RESTClient(baseURL)
	}

	def getTimeStamp()
	{
		def formatter = new SimpleDateFormat("yyyyMMddhhmmss")
		formatter.format(new Date())
	}

	def addGoal(jsonString)
	{
		createResource(GOALS_PATH, jsonString)
	}

	def addUser(jsonString, password)
	{
		createResourceWithPassword(USERS_PATH, jsonString, password)
	}

	def requestBuddy(userPath, jsonString, password)
	{
		createResourceWithPassword(userPath + BUDDIES_PATH_FRAGMENT, jsonString, password)
	}

	def getAllGoals()
	{
		def responseData = getResource(GOALS_PATH)
		responseData
	}

	def getDirectMessages(userPath, password)
	{
		def responseData = getResourceWithPassword(userPath + DIRECT_MESSAGE_PATH_FRAGMENT, password)
		responseData
	}

	def getAnonymousMessages(userPath, password)
	{
		def responseData = getResourceWithPassword(userPath + ANONYMOUS_MESSAGES_PATH_FRAGMENT, password)
		responseData
	}

	def createResourceWithPassword(path, jsonString, password)
	{
		createResource(path, jsonString, ["Yona-Password": password])
	}

	def createResource(path, jsonString, headers = [:])
	{
		postJson(path, jsonString, headers);
	}

	def getResourceWithPassword(path, password)
	{
		getResource(path, ["Yona-Password": password])
	}

	def postMessageActionWithPassword(path, jsonString, password)
	{
		postMessageAction(path, jsonString, ["Yona-Password": password])
	}

	def postMessageAction(path, jsonString, headers = [:])
	{
		def response = postJson(path, jsonString, headers);
		assert response.status == 200
		response.responseData
	}

	def postToAnalysisEngine(jsonString)
	{
		postJson(ANALYSIS_ENGINE_PATH, jsonString);
	}
	def getResource(path, headers = [:])
	{
		def response = restClient.get(path: path,
			contentType:'application/json',
			headers: headers)
		assert response.status == 200
		response.responseData
	}

	def postJson(path, jsonString, headers = [:])
	{
		def object = jsonSlurper.parseText(jsonString)
		def response = restClient.post(path: path,
			body: object,
			contentType:'application/json',
			headers: headers)
		response
	}

	def stripQueryString(url)
	{
		url - ~/\?.*/
	}

}
