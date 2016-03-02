/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import groovy.json.*
import nu.yona.server.YonaServer

class AppService extends Service
{
	final ACTIVITY_CATEGORIES_PATH = "/activityCategories/"
	final USERS_PATH = "/users/"
	final GOALS_PATH_FRAGMENT = "/goals/"
	final APP_ACTIVITY_PATH_FRAGMENT = "/appActivity/"

	JsonSlurper jsonSlurper = new JsonSlurper()

	AppService ()
	{
		super("yona.appservice.url", "http://localhost:8082")
	}

	User confirmMobileNumber(Closure asserter, User user)
	{
		def response = confirmMobileNumber(user.mobileNumberConfirmationUrl, """{ "code":"${user.mobileNumberConfirmationCode}" }""", user.password)
		asserter(response)
		return (isSuccess(response)) ? new User(response.responseData, user.password, true) : null
	}

	def addUser(Closure asserter, password, firstName, lastName, nickname, mobileNumber, devices, parameters = [:])
	{
		def jsonStr = User.makeUserJsonString(firstName, lastName, nickname, mobileNumber, devices)
		def response = addUser(jsonStr, password, parameters)
		asserter(response)
		return (isSuccess(response)) ? new User(response.responseData, password) : null
	}

	def addUser(jsonString, password, parameters = [:])
	{
		yonaServer.createResourceWithPassword(USERS_PATH, jsonString, password, parameters)
	}

	def getUser(Closure asserter, userURL, boolean includePrivateData, password = null)
	{
		def response
		if (includePrivateData)
		{
			response = yonaServer.getResourceWithPassword(yonaServer.stripQueryString(userURL), password, yonaServer.getQueryParams(userURL) + ["includePrivateData": "true"])
		}
		else
		{
			response = yonaServer.getResourceWithPassword(userURL, password)
		}
		asserter(response)
		return (isSuccess(response)) ? new User(response.responseData, password, includePrivateData) : null
	}

	def getUser(userURL, boolean includePrivateData, password = null)
	{
		if (includePrivateData)
		{
			yonaServer.getResourceWithPassword(yonaServer.stripQueryString(userURL), password, yonaServer.getQueryParams(userURL) + ["includePrivateData": "true"])
		}
		else
		{
			yonaServer.getResourceWithPassword(userURL, password)
		}
	}

	def updateUser(Closure asserter, User user, url = null)
	{
		def response = updateUser((url) ?: user.url, user.convertToJSON(), user.password)
		asserter(response)
		return (isSuccess(response)) ? new User(response.responseData, user.password) : null
	}

	def updateUser(userURL, jsonString, password)
	{
		yonaServer.updateResourceWithPassword(yonaServer.stripQueryString(userURL), jsonString, password, yonaServer.getQueryParams(userURL))
	}

	def deleteUser(User user, message = "")
	{
		if (!user)
		{
			return null
		}
		def response = yonaServer.deleteResourceWithPassword(user.editURL, user.password, ["message":message])
	}

	def deleteUser(userEditURL, password, message = "")
	{
		yonaServer.deleteResourceWithPassword(userEditURL, password, ["message":message])
	}

	def assertUserCreationResponseDetails(def response)
	{
		assertResponseStatusCreated(response)
		assertUserWithPrivateData(response.responseData)
		assert response.responseData.mobileNumberConfirmationCode
	}

	def assertUserUpdateResponseDetails(def response)
	{
		assert response.status == 200
		assertUserWithPrivateData(response.responseData)
	}

	def assertUserGetResponseDetailsPublicDataAndVpnProfile(def response)
	{
		assertResponseStatusSuccess(response)
		assertUserWithPublicDataAndVpnProfile(response.responseData)
	}

	def assertUserGetResponseDetailsWithPrivateData(def response)
	{
		assertResponseStatusSuccess(response)
		assertUserWithPrivateData(response.responseData)
	}

	def assertUserGetResponseDetailsWithoutPrivateData(def response)
	{
		assertResponseStatusSuccess(response)
		assertUserWithoutPrivateData(response.responseData)
	}

	def assertUserWithPublicDataAndVpnProfile(user)
	{
		assertPublicUserData(user)
		assertVpnProfile(user)
	}

	def assertUserWithPrivateData(user)
	{
		assertPublicUserData(user)
		assertPrivateUserData(user)
	}

	def assertPublicUserData(def user)
	{
		if (user instanceof User)
		{
			assert user.url != null
		}
		else
		{
			assert user._links.self.href != null
		}
		assert user.firstName != null
		assert user.lastName != null
		assert user.mobileNumber ==~/^\+[0-9]+$/
	}

	def assertPrivateUserData(def user)
	{
		assertVpnProfile(user)
		assert user.nickname != null
		assert user.devices.size() == 1
		assert user.devices[0]

		if (user instanceof User)
		{
			if (user.mobileNumberConfirmationUrl)
			{
				assert !user.buddiesUrl
				assert !user.messagesUrl
				assert !user.newDeviceRequestUrl
				assert !user.appActivityUrl
			}
			else
			{
				assert user.buddiesUrl
				assert user.messagesUrl
				assert user.newDeviceRequestUrl
				assert user.appActivityUrl
			}
		}
		else
		{
			if (user._links?."yona:confirmMobileNumber"?.href)
			{
				assert !user._embedded?."yona:buddies"?._links?.self?.href
				assert !user._links?."yona:messages"
				assert !user._links?."yona:newDeviceRequest"
				assert !user._links?."yona:appActivity"
			}
			else
			{
				assert user._embedded?."yona:buddies"?._links?.self?.href
				assert user._links?."yona:messages"
				assert user._links?."yona:newDeviceRequest"
				assert user._links?."yona:appActivity"
			}
		}
	}

	def assertVpnProfile(def user)
	{
		assert user.vpnProfile.vpnLoginID ==~ /(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
		assert user.vpnProfile.vpnPassword.length() == 32
		assert user.vpnProfile.openVPNProfile.length() > 10
	}

	def assertUserWithoutPrivateData(def user)
	{
		assertPublicUserData(user)
		assert user.nickname == null
		assert user.devices == null
		assert user.goals == null
		assert user.vpnProfile == null
	}

	def assertResponseStatusCreated(def response)
	{
		assert response.status == 201
	}

	def assertResponseStatusSuccess(def response)
	{
		assert isSuccess(response)
	}

	def isSuccess(def response)
	{
		response.status >= 200 && response.status < 300
	}

	def makeBuddies(requestingUser, respondingUser)
	{
		sendBuddyConnectRequest(requestingUser, respondingUser)
		def acceptURL = fetchBuddyConnectRequestMessage(respondingUser).acceptURL
		def acceptResponse = postMessageActionWithPassword(acceptURL, ["message" : "Yes, great idea!"], respondingUser.password)
		assert acceptResponse.status == 200

		def processURL = fetchBuddyConnectResponseMessage(requestingUser).processURL

		// Have the requesting user process the buddy connect response
		def processResponse = postMessageActionWithPassword(processURL, [ : ], requestingUser.password)
		assert processResponse.status == 200
	}

	def sendBuddyConnectRequest(sendingUser, receivingUser)
	{
		// Send the buddy request
		def response = requestBuddy(sendingUser, """{
			"_embedded":{
				"user":{
					"firstName":"${receivingUser.firstName}",
					"lastName":"${receivingUser.lastName}",
					"mobileNumber":"${receivingUser.mobileNumber}",
					"emailAddress":"not@used.nu"
				}
			},
			"message":"Would you like to be my buddy?",
			"sendingStatus":"REQUESTED",
			"receivingStatus":"REQUESTED"
		}""", sendingUser.password)
		assert response.status == 201
		response
	}

	def fetchBuddyConnectRequestMessage(User user)
	{
		// Have the other user fetch the buddy connect request
		def response = getMessages(user)
		assert response.status == 200
		assert response.responseData._embedded

		def buddyConnectRequestMessages = response.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyConnectRequestMessage"}
		def selfURL = buddyConnectRequestMessages[0]?._links?.self?.href ?: null
		def message = buddyConnectRequestMessages[0]?.message ?: null
		def acceptURL = buddyConnectRequestMessages[0]?._links?."yona:accept"?.href ?: null
		def rejectURL = buddyConnectRequestMessages[0]?._links?."yona:reject"?.href ?: null

		def result = [ : ]
		if (selfURL)
		{
			result.selfURL = selfURL
		}
		if (message)
		{
			result.message = message
		}
		if (acceptURL)
		{
			result.acceptURL = acceptURL
		}
		if (rejectURL)
		{
			result.rejectURL = rejectURL
		}

		return result
	}

	def fetchBuddyConnectResponseMessage(User user)
	{
		// Have the requesting user fetch the buddy connect response
		def response = getMessages(user)
		assert response.status == 200
		assert response.responseData._embedded

		def buddyConnectResponseMessages = response.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyConnectResponseMessage"}
		assert buddyConnectResponseMessages[0]._links."yona:process".href
		def selfURL = buddyConnectResponseMessages[0]?._links?.self?.href
		def message = buddyConnectResponseMessages[0]?.message ?: null
		def status = buddyConnectResponseMessages[0]?.status ?: null
		def processURL = buddyConnectResponseMessages[0]?._links?."yona:process"?.href
		def result = [ : ]
		if (selfURL)
		{
			result.selfURL = selfURL
		}
		if (message)
		{
			result.message = message
		}
		if (status)
		{
			result.status = status
		}
		if (processURL)
		{
			result.processURL = processURL
		}

		return result
	}

	def confirmMobileNumber(mobileNumberConfirmationUrl, jsonString, password)
	{
		yonaServer.createResourceWithPassword(mobileNumberConfirmationUrl, jsonString, password)
	}

	def requestOverwriteUser(mobileNumber)
	{
		yonaServer.updateResource(USERS_PATH, """{ }""", [:], ["mobileNumber":mobileNumber])
	}

	def requestBuddy(User user, jsonString, password)
	{
		yonaServer.createResourceWithPassword(user.buddiesUrl, jsonString, password)
	}

	def removeBuddy(User user, Buddy buddy, message)
	{
		removeBuddy(buddy.editURL, user.password, message)
	}

	def removeBuddy(buddyEditURL, password, message)
	{
		yonaServer.deleteResourceWithPassword(buddyEditURL, password, ["message":message])
	}

	def getAllActivityCategories()
	{
		yonaServer.getResource(ACTIVITY_CATEGORIES_PATH)
	}

	def getBuddies(User user)
	{
		def response = yonaServer.getResourceWithPassword(user.buddiesUrl, user.password)
		assert response.status == 200
		assert response.responseData._links?.self?.href == user.buddiesUrl

		if (!response.responseData._embedded?."yona:buddies")
		{
			return []
		}
		response.responseData._embedded."yona:buddies".collect{new Buddy(it)}
	}

	def getMessages(User user, parameters = [:])
	{
		yonaServer.getResourceWithPassword(user.messagesUrl, user.password, parameters)
	}

	def setNewDeviceRequest(newDeviceRequestUrl, password, userSecret)
	{
		def jsonString = """{ "userSecret": "$userSecret" }"""
		yonaServer.updateResourceWithPassword(newDeviceRequestUrl, jsonString, password)
	}

	def getNewDeviceRequest(newDeviceRequestUrl, userSecret = null)
	{
		yonaServer.getResource(newDeviceRequestUrl, [:], ["userSecret": userSecret])
	}

	def clearNewDeviceRequest(deviceRequestEditURL, password)
	{
		yonaServer.deleteResourceWithPassword(deviceRequestEditURL, password)
	}

	def addBudgetGoal(Closure asserter, User user, BudgetGoal goal, message = "")
	{
		def response = addBudgetGoal(user, goal, message)
		asserter(response)
		return (isSuccess(response)) ? new BudgetGoal(response.responseData) : null
	}

	def addBudgetGoal(User user, BudgetGoal goal, message = "")
	{
		yonaServer.postJson(user.goalsUrl + "budgetGoals/", goal.convertToJsonString(), ["Yona-Password": user.password], ["message": message])
	}

	def removeBudgetGoal(User user, BudgetGoal goal, message = "")
	{
		yonaServer.deleteResourceWithPassword(goal.editURL, user.password, ["message": message])
	}

	def getGoals(User user)
	{
		yonaServer.getResource(user.goalsUrl, ["Yona-Password": user.password])
	}

	def postMessageActionWithPassword(path, properties, password)
	{
		def propertiesString = YonaServer.makeStringMap(properties)
		postMessageActionWithPassword(path, """{
					"properties":{
						$propertiesString
					}
				}""", password)
	}

	def postMessageActionWithPassword(path, String jsonString, password)
	{
		postMessageAction(path, jsonString, ["Yona-Password": password])
	}

	def postMessageAction(path, jsonString, headers = [:])
	{
		yonaServer.postJson(path, jsonString, headers)
	}

	def postAppActivityToAnalysisEngine(User user, def appActivities)
	{
		def json = "["
		boolean isFirst = true
		for (def appActivity : appActivities) {
			if (!isFirst) {
				json += ", "
			}
			isFirst = false
			json += appActivity.getJson()
		}
		json += "]"
		yonaServer.createResourceWithPassword(user.url + APP_ACTIVITY_PATH_FRAGMENT, json, user.password)
	}

	def createResourceWithPassword(path, jsonString, password, parameters = [:])
	{
		yonaServer.createResource(path, jsonString, ["Yona-Password": password], parameters)
	}

	def deleteResourceWithPassword(path, password, parameters = [:])
	{
		yonaServer.deleteResourceWithPassword(path, password, parameters)
	}

	def getResource(path, headers = [:], parameters = [:])
	{
		yonaServer.getResource(path, headers, parameters)
	}

	def updateResource(path, jsonString, headers = [:], parameters = [:])
	{
		yonaServer.updateResource(path, jsonString, headers, parameters)
	}
}
