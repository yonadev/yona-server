/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import static nu.yona.server.test.CommonAssertions.*

import java.time.ZonedDateTime

import groovy.json.*
import nu.yona.server.YonaServer

class AppService extends Service
{
	static final ACTIVITY_CATEGORIES_PATH = "/activityCategories/"
	static final NEW_DEVICE_REQUESTS_PATH = "/newDeviceRequests/"
	static final USERS_PATH = "/users/"
	static final OVERWRITE_USER_REQUEST_PATH = "/admin/requestUserOverwrite/"
	static final LAST_EMAIL_PATH = "/emails/last"

	JsonSlurper jsonSlurper = new JsonSlurper()

	AppService ()
	{
		super("yona.appservice.url", "http://localhost:8082")
	}

	User confirmMobileNumber(Closure asserter, User user)
	{
		def response = confirmMobileNumber(user.mobileNumberConfirmationUrl, """{ "code":"1234" }""", user.password)
		asserter(response)
		return (isSuccess(response)) ? new User(response.responseData) : null
	}

	def addUser(Closure asserter, firstName, lastName, nickname, mobileNumber, parameters = [:])
	{
		def jsonStr = User.makeUserJsonString(firstName, lastName, nickname, mobileNumber)
		def response = addUser(jsonStr, parameters)
		asserter(response)
		return (isSuccess(response)) ? new User(response.responseData) : null
	}

	def addLegacyUser(Closure asserter, firstName, lastName, nickname, mobileNumber, parameters = [:]) // YD-544
	{
		def jsonStr = User.makeLegacyUserJsonString(firstName, lastName, nickname, mobileNumber)
		def response = addUser(jsonStr, parameters)
		asserter(response)
		return (isSuccess(response)) ? new User(response.responseData) : null
	}

	def addUser(Closure asserter, firstName, lastName, nickname, mobileNumber, deviceName, deviceOperatingSystem, deviceAppVersion, deviceAppVersionCode, parameters = [:])
	{
		def jsonStr = User.makeUserJsonString(firstName, lastName, nickname, mobileNumber, deviceName, deviceOperatingSystem, deviceAppVersion, deviceAppVersionCode)
		def response = addUser(jsonStr, parameters)
		asserter(response)
		return (isSuccess(response)) ? new User(response.responseData) : null
	}

	def addUser(jsonString, parameters = [:])
	{
		yonaServer.createResource(USERS_PATH, jsonString, [:], parameters)
	}

	def getUser(Closure asserter, userUrl, boolean includePrivateData, password = null)
	{
		def response
		if (includePrivateData)
		{
			response = yonaServer.getResourceWithPassword(userUrl, password, ["requestingUserId": User.getIdFromUrl(userUrl)])
		}
		else
		{
			response = yonaServer.getResourceWithPassword(userUrl, password)
		}
		asserter(response)
		assertBuddyUsers(response)
		return (isSuccess(response)) ? new User(response.responseData) : null
	}

	def getUser(userUrl, boolean includePrivateData, password = null)
	{
		if (includePrivateData)
		{
			yonaServer.getResourceWithPassword(userUrl, password, ["requestingUserId": User.getIdFromUrl(userUrl)])
		}
		else
		{
			yonaServer.getResourceWithPassword(userUrl, password)
		}
	}

	def getUser(Closure asserter, userUrl, password)
	{
		def response = yonaServer.getResourceWithPassword(userUrl, password)
		asserter(response)
		return (isSuccess(response)) ? new User(response.responseData) : null
	}

	def getUser(Closure asserter, userUrl)
	{
		def response = yonaServer.getResource(userUrl)
		asserter(response)
		return (isSuccess(response)) ? new User(response.responseData) : null
	}

	def reloadUser(User user, Closure asserter = null)
	{
		def response
		if (user.hasPrivateData)
		{
			response = yonaServer.getResourceWithPassword(user.url, user.password, ["requestingUserId": user.getId()])
			if (asserter)
			{
				asserter(response)
			}
			else
			{
				assertUserGetResponseDetailsWithPrivateData(response)
			}
			assertBuddyUsers(response)
		}
		else
		{
			response = yonaServer.getResourceWithPassword(user.url, user.password)
			if (asserter)
			{
				asserter(response)
			}
			else
			{
				assertUserGetResponseDetailsWithoutPrivateData(response)
			}
		}
		return (isSuccess(response)) ? new User(response.responseData) : null
	}

	def updateUser(Closure asserter, User user)
	{
		def response = updateUser(user.url, user.convertToJson(), user.password)
		asserter(response)
		assertBuddyUsers(response)
		return (isSuccess(response)) ? new User(response.responseData) : null
	}

	def updateUserCreatedOnBuddyRequest(Closure asserter, User user, inviteUrl)
	{
		def response = updateUser(inviteUrl, user.convertToJson())
		asserter(response)
		return (isSuccess(response)) ? new User(response.responseData) : null
	}

	def updateUser(userUrl, jsonString, password)
	{
		yonaServer.updateResourceWithPassword(userUrl, jsonString, password, [:])
	}

	def updateUser(userUrl, jsonString)
	{
		yonaServer.updateResource(userUrl, jsonString, [:], [:])
	}

	def deleteUser(User user, message = "")
	{
		if (!user)
		{
			return null
		}
		def response = yonaServer.deleteResourceWithPassword(user.editUrl, user.password, ["message":message])
		assertResponseStatusOk(response)
		return response
	}

	def deleteUser(userEditUrl, password, message = "")
	{
		yonaServer.deleteResourceWithPassword(userEditUrl, password, ["message":message])
	}

	void makeBuddies(User requestingUser, User respondingUser)
	{
		sendBuddyConnectRequest(requestingUser, respondingUser)
		def acceptUrl = fetchBuddyConnectRequestMessage(respondingUser).acceptUrl
		def acceptResponse = postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], respondingUser.password)
		assertResponseStatusOk(acceptResponse)

		def processUrl = fetchBuddyConnectResponseMessage(requestingUser).processUrl
		assert processUrl == null // Processing happens automatically these days
	}

	def sendBuddyConnectRequest(sendingUser, receivingUser, assertSuccess = true)
	{
		// Send the buddy request
		def response = requestBuddy(sendingUser, """{
			"_embedded":{
				"yona:user":{
					"firstName":"${receivingUser.firstName}",
					"lastName":"${receivingUser.lastName}",
					"mobileNumber":"${receivingUser.mobileNumber}",
					"emailAddress":"${receivingUser.emailAddress}"
				}
			},
			"message":"Would you like to be my buddy?",
			"sendingStatus":"REQUESTED",
			"receivingStatus":"REQUESTED"
		}""", sendingUser.password)
		if (assertSuccess) {
			assertResponseStatusCreated(response)
		}
		response
	}

	def fetchBuddyConnectRequestMessage(User user)
	{
		// Have the other user fetch the buddy connect request
		def response = getMessages(user)
		assertResponseStatusOk(response)
		assert response.responseData._embedded

		def buddyConnectRequestMessages = response.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyConnectRequestMessage"}
		def selfUrl = buddyConnectRequestMessages[0]?._links?.self?.href ?: null
		def message = buddyConnectRequestMessages[0]?.message ?: null
		def acceptUrl = buddyConnectRequestMessages[0]?._links?."yona:accept"?.href ?: null
		def rejectUrl = buddyConnectRequestMessages[0]?._links?."yona:reject"?.href ?: null

		def result = [ : ]
		if (selfUrl)
		{
			result.selfUrl = selfUrl
		}
		if (message)
		{
			result.message = message
		}
		if (acceptUrl)
		{
			result.acceptUrl = acceptUrl
		}
		if (rejectUrl)
		{
			result.rejectUrl = rejectUrl
		}

		return result
	}

	def fetchBuddyConnectResponseMessage(User user)
	{
		// Have the requesting user fetch the buddy connect response
		def response = getMessages(user)
		assertResponseStatusOk(response)
		assert response.responseData._embedded

		def buddyConnectResponseMessages = response.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyConnectResponseMessage"}
		assert buddyConnectResponseMessages[0]._links."yona:process" == null // Processing happens automatically these days
		def selfUrl = buddyConnectResponseMessages[0]?._links?.self?.href
		def message = buddyConnectResponseMessages[0]?.message ?: null
		def status = buddyConnectResponseMessages[0]?.status ?: null
		def processUrl = buddyConnectResponseMessages[0]?._links?."yona:process"?.href
		def buddyUrl = buddyConnectResponseMessages[0]?._links?."yona:buddy"?.href

		def result = [ : ]
		if (selfUrl)
		{
			result.selfUrl = selfUrl
		}
		if (message)
		{
			result.message = message
		}
		if (status)
		{
			result.status = status
		}
		if (processUrl)
		{
			result.processUrl = processUrl
		}
		if (buddyUrl)
		{
			result.buddyUrl = buddyUrl
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
		removeBuddy(buddy.editUrl, user.password, message)
	}

	def removeBuddy(buddyEditUrl, password, message)
	{
		yonaServer.deleteResourceWithPassword(buddyEditUrl, password, ["message":message])
	}

	def getAllActivityCategories()
	{
		yonaServer.getResource(ACTIVITY_CATEGORIES_PATH)
	}

	List<Buddy> getBuddies(User user)
	{
		def response = yonaServer.getResourceWithPassword(user.buddiesUrl, user.password)
		assertResponseStatusOk(response)
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

	def getDayActivityDetails(User user, Goal goal, int weeksBack, String shortDay)
	{
		def responseDayOverviewsAll = getDayActivityOverviews(user, ["size": (weeksBack+1)*7])
		assertResponseStatusOk(responseDayOverviewsAll)
		getDayDetailsFromOverview(responseDayOverviewsAll, user, goal, weeksBack, shortDay)
	}

	def getDayActivityDetails(User user, Buddy buddy, Goal goal, int weeksBack, String shortDay)
	{
		def responseDayOverviewsAll = getDayActivityOverviews(user, buddy, ["size": (weeksBack+1)*7])
		assertResponseStatusOk(responseDayOverviewsAll)
		getDayDetailsFromOverview(responseDayOverviewsAll, user, goal, weeksBack, shortDay)
	}

	def getDayDetailsFromOverview(responseDayOverviewsAll, User user, Goal goal, int weeksBack, String shortDay) {
		def dayOffset = YonaServer.relativeDateStringToDaysOffset(weeksBack, shortDay)
		def dayActivityOverview = responseDayOverviewsAll.responseData._embedded."yona:dayActivityOverviews"[dayOffset]
		def dayActivityForGoal = dayActivityOverview.dayActivities.find{ it._links."yona:goal".href == goal.url}
		return getDayDetailsForDayFromOverviewItem(user, dayActivityForGoal)
	}

	def getDayDetails(User user, String activityCategoryUrl, ZonedDateTime date) {
		Goal goal = user.findActiveGoal(activityCategoryUrl)
		def url = YonaServer.stripQueryString(user.url) + "/activity/days/" + YonaServer.toIsoDateString(date) + "/details/" + goal.getId()
		getResourceWithPassword(url, user.password)
	}

	def getWeekDetailsFromOverview(responseWeekOverviewsAll, User user, Goal goal, int weeksBack) {
		def weekActivityOverview = responseWeekOverviewsAll.responseData._embedded."yona:weekActivityOverviews"[weeksBack]
		def weekActivityForGoal = weekActivityOverview.weekActivities.find{ it._links."yona:goal".href == goal.url}
		return getWeekDetailsForWeekFromOverviewItem(user, weekActivityForGoal)
	}

	def getDayDetailsForDayFromOverviewItem(User user, dayActivityForGoal) {
		assert dayActivityForGoal?._links?."yona:dayDetails"?.href
		def dayActivityDetailUrl =  dayActivityForGoal?._links?."yona:dayDetails"?.href
		def response = getResourceWithPassword(dayActivityDetailUrl, user.password)
		assertResponseStatusOk(response)
		return response
	}

	def getWeekDetailsForWeekFromOverviewItem(User user, weekActivityForGoal) {
		assert weekActivityForGoal?._links?."yona:weekDetails"?.href
		def weekActivityDetailUrl =  weekActivityForGoal?._links?."yona:weekDetails"?.href
		def response = getResourceWithPassword(weekActivityDetailUrl, user.password)
		assertResponseStatusOk(response)
		return response
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

	def registerNewDevice(url, newDeviceRequestPassword, name, operatingSystem, appVersion = Device.SOME_APP_VERSION, appVersionCode = Device.SUPPORTED_APP_VERSION_CODE, firebaseInstanceId = null)
	{
		def firebaseInstanceIdString = firebaseInstanceId ? "\"$firebaseInstanceId\"" : "null"
		def json = """{
				"name": "$name",
				"operatingSystem": "$operatingSystem",
				"appVersion": "$appVersion",
				"appVersionCode": "$appVersionCode",
				"firebaseInstanceId": $firebaseInstanceIdString
				}"""
		yonaServer.postJson(url, json, ["Yona-NewDeviceRequestPassword":newDeviceRequestPassword], [:])
	}

	def clearNewDeviceRequest(mobileNumber, password)
	{
		yonaServer.deleteResourceWithPassword("$NEW_DEVICE_REQUESTS_PATH$mobileNumber", password)
	}

	User addDevice(User user, name, operatingSystem, appVersion = Device.SOME_APP_VERSION, appVersionCode = Device.SUPPORTED_APP_VERSION_CODE)
	{
		def newDeviceRequestPassword = "Zomaar"
		assertResponseStatusSuccess(setNewDeviceRequest(user.mobileNumber, user.password, newDeviceRequestPassword))

		def getResponse = getNewDeviceRequest(user.mobileNumber, newDeviceRequestPassword)
		assertResponseStatusSuccess(getResponse)

		def registerResponse = registerNewDevice(getResponse.responseData._links."yona:registerDevice".href, newDeviceRequestPassword, name, operatingSystem, appVersion, appVersionCode)
		assertResponseStatusSuccess(registerResponse)

		new User(registerResponse.responseData)
	}

	Goal addGoal(Closure asserter, User user, Goal goal, message = "")
	{
		def response = addGoal(user, goal, message)
		asserter(response)
		return (isSuccess(response)) ? Goal.fromJson(response.responseData) : null
	}

	def addGoal(User user, Goal goal, message = "")
	{
		yonaServer.postJson(user.goalsUrl, goal.convertToJsonString(), ["Yona-Password": user.password], ["message": message])
	}

	Goal updateGoal(Closure asserter, User user, String url, Goal goal, message = "")
	{
		def response = updateGoal(user, url, goal, message)
		asserter(response)
		return (isSuccess(response)) ? Goal.fromJson(response.responseData) : null
	}

	def updateGoal(User user, String url, Goal goal, message = "")
	{
		yonaServer.putJson(url, goal.convertToJsonString(), ["Yona-Password": user.password], ["message": message])
	}

	def removeGoal(User user, Goal goal, message = "")
	{
		yonaServer.deleteResourceWithPassword(goal.editUrl, user.password, ["message": message])
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
		yonaServer.createResourceWithPassword(user.devices.find{ it.requestingDevice }.appActivityUrl, appActivity.getJson(), user.password)
	}

	def composeActivityCategoryUrl(def activityCategoryId) {
		"$yonaServer.restClient.uri$ACTIVITY_CATEGORIES_PATH$activityCategoryId"
	}

	def getLastEmail()
	{
		getResource(LAST_EMAIL_PATH)
	}
}
