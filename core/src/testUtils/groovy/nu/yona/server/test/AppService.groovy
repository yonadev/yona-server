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
	final NEW_DEVICE_REQUESTS_PATH = "/newDeviceRequests/"
	final USERS_PATH = "/users/"
	final OVERWRITE_USER_REQUEST_PATH = "/admin/requestUserOverwrite/"

	JsonSlurper jsonSlurper = new JsonSlurper()

	AppService ()
	{
		super("yona.appservice.url", "http://localhost:8082")
	}

	User confirmMobileNumber(Closure asserter, User user)
	{
		def response = confirmMobileNumber(user.mobileNumberConfirmationUrl, """{ "code":"1234" }""", user.password)
		asserter(response)
		return (isSuccess(response)) ? new User(response.responseData, user.password, true) : null
	}

	def addUser(Closure asserter, password, firstName, lastName, nickname, mobileNumber, parameters = [:])
	{
		def jsonStr = User.makeUserJsonString(firstName, lastName, nickname, mobileNumber)
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

	def reloadUser(User user)
	{
		def response
		if (user.hasPrivateData)
		{
			response = yonaServer.getResourceWithPassword(yonaServer.stripQueryString(user.url), user.password, yonaServer.getQueryParams(user.url) + ["includePrivateData": "true"])
			assertUserGetResponseDetailsWithPrivateData(response)
		}
		else
		{
			response = yonaServer.getResourceWithPassword(user.url, user.password)
			assertUserGetResponseDetailsWithoutPrivateData(response)
		}
		return (isSuccess(response)) ? new User(response.responseData, user.password, user.hasPrivateData) : null
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
		// assert response.status == 200 Enable this after fixing YD-261
		return response
	}

	def deleteUser(userEditURL, password, message = "")
	{
		yonaServer.deleteResourceWithPassword(userEditURL, password, ["message":message])
	}

	def assertUserCreationResponseDetails(def response)
	{
		assertResponseStatusCreated(response)
		assertUserWithPrivateData(response.responseData)
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

		/*
		 * The below asserts use exclusive or operators. Either there should be a mobile number confirmation URL, or the other URL.
		 * The URLs shouldn't be both missing or both present.
		 */
		if (user instanceof User)
		{
			assert ((boolean) user.mobileNumberConfirmationUrl) ^ ((boolean) user.buddiesUrl)
			assert ((boolean) user.mobileNumberConfirmationUrl) ^ ((boolean) user.messagesUrl)
			assert ((boolean) user.mobileNumberConfirmationUrl) ^ ((boolean) user.newDeviceRequestUrl)
			assert ((boolean) user.mobileNumberConfirmationUrl) ^ ((boolean) user.appActivityUrl)
		}
		else
		{
			assert ((boolean) user._links?."yona:confirmMobileNumber"?.href) ^ ((boolean) user._embedded?."yona:buddies"?._links?.self?.href)
			assert ((boolean) user._links?."yona:confirmMobileNumber"?.href) ^ ((boolean) user._links?."yona:messages")
			assert ((boolean) user._links?."yona:confirmMobileNumber"?.href) ^ ((boolean) user._links?."yona:newDeviceRequest")
			assert ((boolean) user._links?."yona:confirmMobileNumber"?.href) ^ ((boolean) user._links?."yona:appActivity")
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
		assert user.goals == null
		assert user.vpnProfile == null
	}

	def assertResponseStatusCreated(def response)
	{
		assert response.status == 201
	}

	def assertResponseStatusSuccess(def response)
	{
		assert response.status >= 200 && response.status < 300
	}

	def isSuccess(def response)
	{
		response.status >= 200 && response.status < 300
	}

	void makeBuddies(User requestingUser, User respondingUser)
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
				"yona:user":{
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
		yonaServer.postJson(OVERWRITE_USER_REQUEST_PATH, """{ }""", [:], ["mobileNumber":mobileNumber])
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

	List<Buddy> getBuddies(User user)
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

	def getWeekActivityOverviews(User user, parameters = [:])
	{
		yonaServer.getResourceWithPassword(user.weeklyActivityReportsUrl, user.password, parameters)
	}

	def getWeekActivityOverviews(User user, Buddy buddy, parameters = [:])
	{
		yonaServer.getResourceWithPassword(buddy.weeklyActivityReportsUrl, user.password, parameters)
	}

	def getDayActivityOverviews(User user, parameters = [:])
	{
		yonaServer.getResourceWithPassword(user.dailyActivityReportsUrl, user.password, parameters)
	}

	def getDayActivityOverviews(User user, Buddy buddy, parameters = [:])
	{
		yonaServer.getResourceWithPassword(buddy.dailyActivityReportsUrl, user.password, parameters)
	}

	def getDayActivityOverviewsWithBuddies(User user, parameters = [:])
	{
		yonaServer.getResourceWithPassword(user.dailyActivityReportsWithBuddiesUrl, user.password, parameters)
	}

	def getBuddyDayActivityOverviews(User user, int buddyIndex = 0, parameters = [:])
	{
		def userWithBuddies = this.getUser(this.&assertUserGetResponseDetailsWithPrivateData, user.url, true, user.password)
		assert userWithBuddies.buddies != null
		def buddy = userWithBuddies.buddies[buddyIndex]
		assert buddy
		assert buddy.dailyActivityReportsUrl

		yonaServer.getResourceWithPassword(userWithBuddies.buddies[buddyIndex].dailyActivityReportsUrl, user.password, parameters)
	}

	def getBuddyWeekActivityOverviews(User user, int buddyIndex = 0, parameters = [:])
	{
		def userWithBuddies = this.getUser(this.&assertUserGetResponseDetailsWithPrivateData, user.url, true, user.password)
		assert userWithBuddies.buddies != null
		def buddy = userWithBuddies.buddies[buddyIndex]
		assert buddy
		assert buddy.weeklyActivityReportsUrl

		yonaServer.getResourceWithPassword(userWithBuddies.buddies[buddyIndex].weeklyActivityReportsUrl, user.password, parameters)
	}

	def setNewDeviceRequest(mobileNumber, password, newDeviceRequestPassword)
	{
		def jsonString = """{ "newDeviceRequestPassword": "$newDeviceRequestPassword" }"""
		yonaServer.updateResourceWithPassword("$NEW_DEVICE_REQUESTS_PATH$mobileNumber", jsonString, password)
	}

	def getNewDeviceRequest(mobileNumber, newDeviceRequestPassword = null)
	{
		yonaServer.getResource("$NEW_DEVICE_REQUESTS_PATH$mobileNumber", ["Yona-NewDeviceRequestPassword":newDeviceRequestPassword], [:])
	}

	def clearNewDeviceRequest(mobileNumber, password)
	{
		yonaServer.deleteResourceWithPassword("$NEW_DEVICE_REQUESTS_PATH$mobileNumber", password)
	}

	Goal addGoal(Closure asserter, User user, Goal goal, message = "")
	{
		def response = addGoal(user, goal, message)
		asserter(response)
		return (isSuccess(response)) ? Goal.fromJSON(response.responseData) : null
	}

	def addGoal(User user, Goal goal, message = "")
	{
		yonaServer.postJson(user.goalsUrl, goal.convertToJsonString(), ["Yona-Password": user.password], ["message": message])
	}

	def updateGoal(User user, String url, Goal goal, message = "")
	{
		yonaServer.putJson(url, goal.convertToJsonString(), ["Yona-Password": user.password], ["message": message])
	}

	def removeGoal(User user, Goal goal, message = "")
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

	def postAppActivityToAnalysisEngine(User user, def appActivity)
	{
		yonaServer.createResourceWithPassword(user.appActivityUrl, appActivity.getJson(), user.password)
	}

	def createResourceWithPassword(path, jsonString, password, parameters = [:])
	{
		yonaServer.createResource(path, jsonString, ["Yona-Password": password], parameters)
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

	def composeActivityCategoryUrl(def activityCategoryID) {
		"$yonaServer.restClient.uri$ACTIVITY_CATEGORIES_PATH$activityCategoryID"
	}
}
