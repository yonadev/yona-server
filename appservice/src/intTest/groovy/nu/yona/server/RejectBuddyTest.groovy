package nu.yona.server

import groovy.json.*
import spock.lang.Shared

class RejectBuddyTest extends AbstractAppServiceIntegrationTest {

	@Shared
	def richardQuin
	@Shared
	def bobDunn
	@Shared
	def richardQuinBobBuddyURL
	@Shared
	def bobDunnRichardBuddyURL
	@Shared
	def bobDunnBuddyMessageRejectURL
	@Shared
	def bobDunnBuddyMessageProcessURL
	@Shared
	def richardQuinBuddyMessageRejectURL
	@Shared
	def richardQuinBuddyMessageProcessURL

	def 'Add user Richard'(){
		given:

		when:
		richardQuin = addUser("Richard", "Quin")

		then:
		richardQuin
	}

	def 'Add user Bob'(){
		given:

		when:
		bobDunn = addUser("Bob", "Dunn")

		then:
		bobDunn
	}

	def 'Richard requests Bob to become his buddy'(){
		given:

		when:
		def response = appService.requestBuddy(richardQuin.url, """{
				"_embedded":{
					"user":{
						"mobileNumber":"${bobDunn.mobileNumber}"
					}
				},
				"message":"Would you like to be my buddy?",
				"sendingStatus":"REQUESTED",
				"receivingStatus":"REQUESTED"
			}""", richardQuin.password)
		if (response.status == 201) {
			richardQuinBobBuddyURL = response.responseData._links.self.href
		}

		then:
		response.status == 201
		response.responseData._embedded.user.firstName == "Bob"
		richardQuinBobBuddyURL.startsWith(richardQuin.url)

		cleanup:
		println "URL buddy Richard: " + richardQuinBobBuddyURL
	}

	def 'Bob checks his direct messages'(){
		given:

		when:
		def response = appService.getDirectMessages(bobDunn.url, bobDunn.password)
		if (response.status == 200) {
			bobDunnBuddyMessageRejectURL = response.responseData._embedded?.buddyConnectRequestMessages[0]?._links?.reject?.href
		}

		then:
		response.status == 200
		response.responseData._embedded.buddyConnectRequestMessages[0].user.firstName == "Richard"
		response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href.startsWith(bobDunn.url + appService.DIRECT_MESSAGES_PATH_FRAGMENT)
		bobDunnBuddyMessageRejectURL.startsWith(response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href)
	}

	def 'Bob rejects Richard\'s buddy request'(){
		given:

		when:
		def response = appService.postMessageActionWithPassword(bobDunnBuddyMessageRejectURL, """{
				"properties":{
					"message":"No, thanks."
				}
			}""", bobDunn.password)

		then:
		response.status == 200
		response.responseData?.properties?.status == "done"
	}

	def 'Richard checks his anonymous messages'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuin.url, richardQuin.password)
		if (response.status == 200) {
			richardQuinBuddyMessageProcessURL = response.responseData._embedded?.buddyConnectResponseMessages[0]?._links?.process?.href
		}

		then:
		response.status == 200
		response.responseData._embedded.buddyConnectResponseMessages[0].status == "REJECTED"
		response.responseData._embedded.buddyConnectResponseMessages[0].user.firstName == "Bob"
		response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href.startsWith(richardQuin.url + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
		richardQuinBuddyMessageProcessURL.startsWith(response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href)
	}

	def 'Richard processes Bob\'s buddy rejection'(){
		given:

		when:
		def response = appService.postMessageActionWithPassword(richardQuinBuddyMessageProcessURL, """{
				"properties":{
				}
			}""", richardQuin.password)

		then:
		response.status == 200
		response.responseData?.properties?.status == "done"
	}

	def 'Richard checks his buddy list and Bob is not there'(){
		given:

		when:
		def response = appService.getBuddies(richardQuin.url, richardQuin.password);

		then:
		response.status == 200
		response.responseData?._embedded?.buddies == null
	}

	def 'Bob checks his buddy list and Richard is not there'(){
		given:

		when:
		def response = appService.getBuddies(bobDunn.url, bobDunn.password);

		then:
		response.status == 200
		response.responseData?._embedded?.buddies == null
	}
}
