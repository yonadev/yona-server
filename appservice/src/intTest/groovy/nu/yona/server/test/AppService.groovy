package nu.yona.server.test

import groovy.json.*
import groovyx.net.http.RESTClient
import groovyx.net.http.URIBuilder

import java.text.SimpleDateFormat

class AppService  extends Service {
	final GOALS_PATH = "/goals/"
	final USERS_PATH = "/users/"
	final BUDDIES_PATH_FRAGMENT = "/buddies/"
	final DIRECT_MESSAGES_PATH_FRAGMENT = "/messages/direct/"
	final ANONYMOUS_MESSAGES_PATH_FRAGMENT = "/messages/anonymous/"
	final ALL_MESSAGES_PATH_FRAGMENT = "/messages/all/"
	final RELEVANT_CATEGORIES_PATH_FRAGMENT = "/relevantCategories/"
	final NEW_DEVICE_REQUEST_PATH_FRAGMENT = "/newDeviceRequest"
	final MOBILE_NUMBER_CONFIRMATION_PATH_FRAGMENT = "/confirmMobileNumber"

	JsonSlurper jsonSlurper = new JsonSlurper()

	AppService () {
		super("yona.appservice.url", "http://localhost:8082")
	}

	void confirmMobileNumber(Closure asserter, User user)
	{
		def response = confirmMobileNumber(user.url, """{ "code":"${user.mobileNumberConfirmationCode}" }""", user.password)
		asserter(response)
	}

	def addUser(Closure asserter, password, firstName, lastName, nickname, mobileNumber, devices, goals, parameters = [:])
	{
		def devicesString = makeStringList(devices)
		def goalsString = makeStringList(goals)
		def jsonStr = """{
				"firstName":"${firstName}",
				"lastName":"${lastName}",
				"nickname":"${nickname}",
				"mobileNumber":"${mobileNumber}",
				"devices":[
					${devicesString}
				],
				"goals":[
					${goalsString}
				]
			}"""
		def response = addUser(jsonStr, password, parameters)
		asserter(response)
		return (isSuccess(response)) ? new User(response.responseData, password) : null
	}

	def addUser(jsonString, password, parameters = [:]) {
		yonaServer.createResourceWithPassword(USERS_PATH, jsonString, password, parameters)
	}


	def assertUserCreationResponseDetails(def response)
	{
		assertUserOverwriteResponseDetails(response)
		assert response.responseData.confirmationCode
	}

	def assertUserOverwriteResponseDetails(def response)
	{
		assertResponseStatusCreated(response)
		assert response.responseData._links.self.href
		assert response.responseData.vpnProfile.vpnLoginID
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
		def response = getDirectMessages(user)
		assert response.status == 200
		assert response.responseData._embedded

		def message = response.responseData._embedded?.buddyConnectRequestMessages[0]?.message ?: null
		def acceptURL = response.responseData._embedded?.buddyConnectRequestMessages[0]?._links?.accept?.href ?: null
		def rejectURL = response.responseData._embedded?.buddyConnectRequestMessages[0]?._links?.reject?.href ?: null

		def result = [ : ]
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

		def message = response.responseData._embedded?.buddyConnectResponseMessages[0]?.message ?: null
		def processURL = response.responseData._embedded?.buddyConnectResponseMessages[0]?._links?.process?.href
		def result = [ : ]
		if (message)
		{
			result.message = message
		}
		if (processURL)
		{
			result.processURL = processURL
		}

		return result
	}

	def confirmMobileNumber(userURL, jsonString, password) {
		yonaServer.createResourceWithPassword(yonaServer.stripQueryString(userURL) + MOBILE_NUMBER_CONFIRMATION_PATH_FRAGMENT, jsonString, password)
	}

	def requestOverwriteUser(mobileNumber) {
		yonaServer.updateResource(USERS_PATH, """ { } """, [:], ["mobileNumber":mobileNumber])
	}

	def getUser(userURL, boolean includePrivateData, password = null) {
		if (includePrivateData) {
			yonaServer.getResourceWithPassword(yonaServer.stripQueryString(userURL), password, yonaServer.getQueryParams(userURL) + ["includePrivateData": "true"])
		} else {
			yonaServer.getResourceWithPassword(userURL, password)
		}
	}

	def updateUser(userURL, jsonString, password) {
		yonaServer.updateResourceWithPassword(yonaServer.stripQueryString(userURL), jsonString, password, yonaServer.getQueryParams(userURL))
	}

	def deleteUser(User user, message = "") {
		def response = yonaServer.deleteResourceWithPassword(user.url, user.password, ["message":message])
	}

	def deleteUser(userURL, password, message = "") {
		yonaServer.deleteResourceWithPassword(userURL, password, ["message":message])
	}

	def requestBuddy(userPath, jsonString, password) {
		yonaServer.createResourceWithPassword(userPath + BUDDIES_PATH_FRAGMENT, jsonString, password)
	}

	def removeBuddy(User user, Buddy buddy, message) {
		removeBuddy(buddy.url, user.password, message)
	}

	def removeBuddy(buddyURL, password, message) {
		yonaServer.deleteResourceWithPassword(buddyURL, password, ["message":message])
	}

	def getAllGoals() {
		yonaServer.getResource(GOALS_PATH)
	}

	def getBuddies(User user)
	{
		def response = yonaServer.getResourceWithPassword(user.url + BUDDIES_PATH_FRAGMENT, user.password)
		assert response.status == 200

		if (!response.responseData._embedded?.buddies)
		{
			return []
		}
		response.responseData._embedded.buddies.collect{new Buddy(it)}
	}

	def getBuddies(userPath, password) {
		yonaServer.getResourceWithPassword(userPath + BUDDIES_PATH_FRAGMENT, password)
	}

	def getDirectMessages(User user, parameters = [:])
	{
		getDirectMessages(user.url, user.password, parameters)
	}

	def getDirectMessages(userPath, password, parameters = [:]) {
		yonaServer.getResourceWithPassword(userPath + DIRECT_MESSAGES_PATH_FRAGMENT, password, parameters)
	}

	def getAnonymousMessages(User user, parameters = [:])
	{
		getAnonymousMessages(user.url, user.password, parameters)
	}

	def getAnonymousMessages(userPath, password, parameters = [:]) {
		yonaServer.getResourceWithPassword(userPath + ANONYMOUS_MESSAGES_PATH_FRAGMENT, password, parameters)
	}

	def setNewDeviceRequest(userPath, password, userSecret) {
		def jsonString = """{ "userSecret": "$userSecret" }"""
		yonaServer.updateResourceWithPassword(userPath + NEW_DEVICE_REQUEST_PATH_FRAGMENT, jsonString, password)
	}

	def getNewDeviceRequest(userPath, userSecret = null) {
		yonaServer.getResource(userPath + NEW_DEVICE_REQUEST_PATH_FRAGMENT, [:], ["userSecret": userSecret])
	}

	def clearNewDeviceRequest(userPath, password) {
		yonaServer.deleteResourceWithPassword(userPath + NEW_DEVICE_REQUEST_PATH_FRAGMENT, password)
	}

	def postMessageActionWithPassword(path, properties, password)
	{
		def propertiesString = makeStringMap(properties)
		postMessageActionWithPassword(path, """{
					"properties":{
						$propertiesString
					}
				}""", password)
	}

	def postMessageActionWithPassword(path, String jsonString, password) {
		postMessageAction(path, jsonString, ["Yona-Password": password])
	}

	def postMessageAction(path, jsonString, headers = [:]) {
		yonaServer.postJson(path, jsonString, headers);
	}
}
