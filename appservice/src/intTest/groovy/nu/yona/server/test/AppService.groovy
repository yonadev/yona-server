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

	def addUser(Closure asserter, password, firstName, lastName, nickname, mobileNumber, devices, goals)
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
		def response = addUser(jsonStr, password)
		asserter(response)
		return new User(response.responseData, password)
	}

	def assertUserCreationResponseDetails(def response)
	{
		assertResponseStatusCreated(response)
		assert response.responseData._links.self.href
		assert response.responseData.vpnProfile.vpnLoginID
		assert response.responseData.confirmationCode
	}

	def assertResponseStatusCreated(def response)
	{
		assert response.status == 201
	}

	def assertResponseStatusSuccess(def response)
	{
		assert response.status >= 200 && response.status < 300
	}

	def makeBuddies(user1, user2)
	{
		// Send the buddy request
		def addBuddyResponse = requestBuddy(user1.url, """{
			"_embedded":{
				"user":{
					"firstName":"${user2.firstName}",
					"lastName":"${user2.lastName}",
					"mobileNumber":"${user2.mobileNumber}",
					"emailAddress":"not@used.nu"
				}
			},
			"message":"Would you like to be my buddy?",
			"sendingStatus":"REQUESTED",
			"receivingStatus":"REQUESTED"
		}""", user1.password)
		assert addBuddyResponse.status == 201

		// Have the other user fetch the buddy connect request
		def user2DirectMessagesResponse = getDirectMessages(user2.url, user2.password)
		assert user2DirectMessagesResponse.status == 200
		assert user2DirectMessagesResponse.responseData._embedded.buddyConnectRequestMessages[0]._links.accept.href

		// Accept the buddy request
		def user2BuddyConnectRequestMessageAcceptURL = user2DirectMessagesResponse.responseData._embedded.buddyConnectRequestMessages[0]._links.accept.href
		def acceptResponse = postMessageActionWithPassword(user2BuddyConnectRequestMessageAcceptURL, ["message" : "Yes, great idea!"], user2.password)
		assert acceptResponse.status == 200

		// Have the requesting user fetch the buddy connect response
		def user1AnonymousMessagesResponse = getAnonymousMessages(user1.url, user1.password)
		assert user1AnonymousMessagesResponse.status == 200
		assert user1AnonymousMessagesResponse.responseData._embedded.buddyConnectResponseMessages[0]._links.process.href

		// Have the requesting user process the buddy connect response
		def user1BuddyConnectResponseMessageProcessURL = user1AnonymousMessagesResponse.responseData._embedded.buddyConnectResponseMessages[0]._links.process.href
		def processResponse = postMessageActionWithPassword(user1BuddyConnectResponseMessageProcessURL, [ : ], user1.password)
		assert processResponse.status == 200
	}

	def addUser(jsonString, password, parameters = [:]) {
		yonaServer.createResourceWithPassword(USERS_PATH, jsonString, password, parameters)
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

	def removeBuddy(buddyURL, password, message) {
		yonaServer.deleteResourceWithPassword(buddyURL, password, ["message":message])
	}

	def getAllGoals() {
		yonaServer.getResource(GOALS_PATH)
	}

	def getBuddies(User user) {
		def response = yonaServer.getResourceWithPassword(user.url + BUDDIES_PATH_FRAGMENT, user.password)
		assert response.status == 200

		if (!response.responseData._embedded || !!response.responseData._embedded.buddies)
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
