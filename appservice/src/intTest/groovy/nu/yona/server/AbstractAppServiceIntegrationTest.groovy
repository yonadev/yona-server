package nu.yona.server

import groovy.json.*
import nu.yona.server.test.AbstractYonaIntegrationTest
import spock.lang.Shared

abstract class AbstractAppServiceIntegrationTest extends AbstractYonaIntegrationTest {
	@Shared
	def timestamp = YonaServer.getTimeStamp()
	@Shared
	def userNumber = 0;

	def addUser(firstName, lastName) {
		userNumber++
		def password = "PASSWORD USER " + userNumber;
		def mobileNumber = "+${timestamp}${userNumber}"
		def nickname = "${firstName} ${lastName} @${timestamp}"
		def response = appService.addUser("""{
				"firstName":"${firstName} @${timestamp}",
				"lastName":"${lastName} @${timestamp}",
				"nickname":"${nickname}",
				"mobileNumber":"${mobileNumber}",
				"devices":[
					"Nexus 6"
				],
				"goals":[
					"news", "gambling"
				]
			}""", password)
		assert response.status == 201
		assert response.responseData._links.self.href
		def userURL = appService.stripQueryString(response.responseData._links.self.href)
		assert response.responseData.vpnProfile.vpnLoginID
		def vpnLoginID = response.responseData.vpnProfile.vpnLoginID
		assert response.responseData.confirmationCode
		def mobileNumberConfirmationCode = response.responseData.confirmationCode
		def confirmResponse = appService.confirmMobileNumber(userURL, """ { "code":"${mobileNumberConfirmationCode}" } """, password)
		assert confirmResponse.status == 200
		return [
			"url": userURL,
			"password": password,
			"vpnLoginID": vpnLoginID,
			"nickname": nickname,
			"mobileNumber": mobileNumber
		]
	}

	def makeBuddies(user1Object, user2Object) {
		def addBuddyResponse = appService.requestBuddy(user1Object.url, """{
			"_embedded":{
				"user":{
					"mobileNumber":"${user2Object.mobileNumber}"
				}
			},
			"message":"Would you like to be my buddy?",
			"sendingStatus":"REQUESTED",
			"receivingStatus":"REQUESTED"
		}""", user1Object.password)
		assert addBuddyResponse.status == 201
		def user2DirectMessagesResponse = appService.getDirectMessages(user2Object.url, user2Object.password)
		assert user2DirectMessagesResponse.status == 200
		assert user2DirectMessagesResponse.responseData._embedded.buddyConnectRequestMessages[0]._links.accept.href
		def user2BuddyConnectRequestMessageAcceptURL = user2DirectMessagesResponse.responseData._embedded.buddyConnectRequestMessages[0]._links.accept.href
		def acceptResponse = appService.postMessageActionWithPassword(user2BuddyConnectRequestMessageAcceptURL, """{
			"properties":{
				"message":"Yes, great idea!"
			}
		}""", user2Object.password)
		assert acceptResponse.status == 200
		def user1AnonymousMessagesResponse = appService.getAnonymousMessages(user1Object.url, user1Object.password)
		assert user1AnonymousMessagesResponse.status == 200
		assert user1AnonymousMessagesResponse.responseData._embedded.buddyConnectResponseMessages[0]._links.process.href
		def user1BuddyConnectResponseMessageProcessURL = user1AnonymousMessagesResponse.responseData._embedded.buddyConnectResponseMessages[0]._links.process.href
		def processResponse = appService.postMessageActionWithPassword(user1BuddyConnectResponseMessageProcessURL, """{
			"properties":{
			}
		}""", user1Object.password)
		assert processResponse.status == 200
	}
}