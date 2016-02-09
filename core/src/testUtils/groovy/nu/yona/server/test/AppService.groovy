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
	final BUDDIES_PATH_FRAGMENT = "/buddies/"
	final DIRECT_MESSAGES_PATH_FRAGMENT = "/messages/direct/"
	final ANONYMOUS_MESSAGES_PATH_FRAGMENT = "/messages/anonymous/"
	final ALL_MESSAGES_PATH_FRAGMENT = "/messages/all/"
	final NEW_DEVICE_REQUEST_PATH_FRAGMENT = "/newDeviceRequest"
	final MOBILE_NUMBER_CONFIRMATION_PATH_FRAGMENT = "/confirmMobileNumber"
	final GOALS_PATH_FRAGMENT = "/goals/"

	JsonSlurper jsonSlurper = new JsonSlurper()

	AppService ()
	{
		super("yona.appservice.url", "http://localhost:8082")
	}

	void confirmMobileNumber(Closure asserter, User user)
	{
		def response = confirmMobileNumber(user.mobileNumberConfirmationUrl, """{ "code":"${user.mobileNumberConfirmationCode}" }""", user.password)
		asserter(response)
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
		def response = requestBuddy(sendingUser.url, """{
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
		def response = getAnonymousMessages(user)
		assert response.status == 200
		assert response.responseData._embedded

		def selfURL = response.responseData._embedded?.buddyConnectRequestMessages[0]?._links?.self?.href ?: null
		def message = response.responseData._embedded?.buddyConnectRequestMessages[0]?.message ?: null
		def acceptURL = response.responseData._embedded?.buddyConnectRequestMessages[0]?._links?.accept?.href ?: null
		def rejectURL = response.responseData._embedded?.buddyConnectRequestMessages[0]?._links?.reject?.href ?: null

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
		def response = getAnonymousMessages(user)
		assert response.status == 200
		assert response.responseData._embedded
		assert response.responseData._embedded.buddyConnectResponseMessages[0]._links.process.href

		def selfURL = response.responseData._embedded?.buddyConnectResponseMessages[0]?._links?.self?.href
		def message = response.responseData._embedded?.buddyConnectResponseMessages[0]?.message ?: null
		def status = response.responseData._embedded?.buddyConnectResponseMessages[0]?.status ?: null
		def processURL = response.responseData._embedded?.buddyConnectResponseMessages[0]?._links?.process?.href
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
		def response = yonaServer.deleteResourceWithPassword(user.url, user.password, ["message":message])
	}

	def deleteUser(userURL, password, message = "")
	{
		yonaServer.deleteResourceWithPassword(userURL, password, ["message":message])
	}

	def requestBuddy(userPath, jsonString, password)
	{
		yonaServer.createResourceWithPassword(userPath + BUDDIES_PATH_FRAGMENT, jsonString, password)
	}

	def removeBuddy(User user, Buddy buddy, message)
	{
		removeBuddy(buddy.url, user.password, message)
	}

	def removeBuddy(buddyURL, password, message)
	{
		yonaServer.deleteResourceWithPassword(buddyURL, password, ["message":message])
	}

	def getAllActivityCategories()
	{
		yonaServer.getResource(ACTIVITY_CATEGORIES_PATH)
	}

	def getBuddies(User user)
	{
		def response = yonaServer.getResourceWithPassword(user.url + BUDDIES_PATH_FRAGMENT, user.password)
		assert response.status == 200
		assert response.responseData._links?.self?.href == user.url + BUDDIES_PATH_FRAGMENT

		if (!response.responseData._embedded?.buddies)
		{
			return []
		}
		response.responseData._embedded.buddies.collect{new Buddy(it)}
	}

	def getBuddies(userPath, password)
	{
		yonaServer.getResourceWithPassword(userPath + BUDDIES_PATH_FRAGMENT, password)
	}

	def getAnonymousMessages(User user, parameters = [:])
	{
		getAnonymousMessages(user.url, user.password, parameters)
	}

	def getAnonymousMessages(userPath, password, parameters = [:])
	{
		yonaServer.getResourceWithPassword(userPath + ANONYMOUS_MESSAGES_PATH_FRAGMENT, password, parameters)
	}

	def setNewDeviceRequest(userPath, password, userSecret)
	{
		def jsonString = """{ "userSecret": "$userSecret" }"""
		yonaServer.updateResourceWithPassword(userPath + NEW_DEVICE_REQUEST_PATH_FRAGMENT, jsonString, password)
	}

	def getNewDeviceRequest(userPath, userSecret = null)
	{
		yonaServer.getResource(userPath + NEW_DEVICE_REQUEST_PATH_FRAGMENT, [:], ["userSecret": userSecret])
	}

	def clearNewDeviceRequest(userPath, password)
	{
		yonaServer.deleteResourceWithPassword(userPath + NEW_DEVICE_REQUEST_PATH_FRAGMENT, password)
	}

	def addBudgetGoal(Closure asserter, User user, BudgetGoal goal)
	{
		def response = addBudgetGoal(user, goal)
		asserter(response)
		return (isSuccess(response)) ? new BudgetGoal(response.responseData) : null
	}

	def addBudgetGoal(User user, BudgetGoal goal)
	{
		yonaServer.postJson(user.url + GOALS_PATH_FRAGMENT + "budgetGoals/", goal.convertToJsonString(), ["Yona-Password": user.password])
	}

	def removeBudgetGoal(User user, BudgetGoal goal)
	{
		yonaServer.deleteResourceWithPassword(goal.url, user.password)
	}

	def getGoals(User user)
	{
		yonaServer.getResource(user.url + GOALS_PATH_FRAGMENT, ["Yona-Password": user.password])
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
