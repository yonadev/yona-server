package nu.yona.server

import groovy.json.*
import nu.yona.server.test.AbstractYonaIntegrationTest
import spock.lang.Shared

class RejectBuddyTest extends AbstractYonaIntegrationTest {

	@Shared
	def timestamp = YonaServer.getTimeStamp()

	@Shared
	def richardQuinPassword = "R i c h a r d"
	def bobDunnPassword = "B o b"
	@Shared
	def richardQuinURL
	@Shared
	def richardQuinVPNLoginID
	@Shared
	def bobDunnURL
	@Shared
	def bobDunnVPNLoginID
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

	def 'Add user Richard Quin'(){
		given:

		when:
		def response = appService.addUser("""{
				"firstName":"Richard ${timestamp}",
				"lastName":"Quin ${timestamp}",
				"nickname":"RQ ${timestamp}",
				"mobileNumber":"+${timestamp}1",
				"devices":[
					"Nexus 6"
				],
				"goals":[
					"news"
				]
			}""", richardQuinPassword)
		if (response.status == 201) {
			richardQuinURL = appService.stripQueryString(response.responseData._links.self.href)
			richardQuinVPNLoginID = response.responseData.vpnProfile.vpnLoginID;

			def confirmationCode = response.responseData.confirmationCode;
			appService.confirmMobileNumber(richardQuinURL, """ { "code":"${confirmationCode}" } """, richardQuinPassword)
		}

		then:
		response.status == 201
		richardQuinURL.startsWith(appServiceBaseURL + appService.USERS_PATH)

		cleanup:
		println "URL Richard: " + richardQuinURL
	}

	def 'Add user Bob Dunn'(){
		given:

		when:
		def response = appService.addUser("""{
				"firstName":"Bob ${timestamp}",
				"lastName":"Dunn ${timestamp}",
				"nickname":"BD ${timestamp}",
				"mobileNumber":"+${timestamp}2",
				"devices":[
					"iPhone 6"
				],
				"goals":[
					"gambling"
				]
			}""", bobDunnPassword)
		if (response.status == 201) {
			bobDunnURL = appService.stripQueryString(response.responseData._links.self.href)
			bobDunnVPNLoginID = response.responseData.vpnProfile.vpnLoginID;

			def confirmationCode = response.responseData.confirmationCode;
			appService.confirmMobileNumber(bobDunnURL, """ { "code":"${confirmationCode}" } """, bobDunnPassword)
		}

		then:
		response.status == 201
		bobDunnURL.startsWith(appServiceBaseURL + appService.USERS_PATH)

		cleanup:
		println "URL Bob: " + bobDunnURL
	}

	def 'Richard requests Bob to become his buddy'(){
		given:

		when:
		def response = appService.requestBuddy(richardQuinURL, """{
				"_embedded":{
					"user":{
						"firstName":"Bob ${timestamp}",
						"lastName":"Dun ${timestamp}n",
						"mobileNumber":"+${timestamp}2"
					}
				},
				"message":"Would you like to be my buddy?",
				"sendingStatus":"REQUESTED",
				"receivingStatus":"REQUESTED"
			}""", richardQuinPassword)
		if (response.status == 201) {
			richardQuinBobBuddyURL = response.responseData._links.self.href
		}

		then:
		response.status == 201
		response.responseData._embedded.user.firstName == "Bob ${timestamp}"
		richardQuinBobBuddyURL.startsWith(richardQuinURL)

		cleanup:
		println "URL buddy Richard: " + richardQuinBobBuddyURL
	}

	def 'Bob checks his direct messages'(){
		given:

		when:
		def response = appService.getDirectMessages(bobDunnURL, bobDunnPassword)
		if (response.status == 200) {
			bobDunnBuddyMessageRejectURL = response.responseData._embedded?.buddyConnectRequestMessages[0]?._links?.reject?.href
		}

		then:
		response.status == 200
		response.responseData._embedded.buddyConnectRequestMessages[0].user.firstName == "Richard ${timestamp}"
		response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href.startsWith(bobDunnURL + appService.DIRECT_MESSAGES_PATH_FRAGMENT)
		bobDunnBuddyMessageRejectURL.startsWith(response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href)
	}

	def 'Bob rejects Richard\'s buddy request'(){
		given:

		when:
		def response = appService.postMessageActionWithPassword(bobDunnBuddyMessageRejectURL, """{
				"properties":{
					"message":"No, thanks."
				}
			}""", bobDunnPassword)

		then:
		response.status == 200
		response.responseData?.properties?.status == "done"
	}

	def 'Richard checks his anonymous messages'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuinURL, richardQuinPassword)
		if (response.status == 200) {
			richardQuinBuddyMessageProcessURL = response.responseData._embedded?.buddyConnectResponseMessages[0]?._links?.process?.href
		}

		then:
		response.status == 200
		response.responseData._embedded.buddyConnectResponseMessages[0].user.firstName == "Bob ${timestamp}"
		response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href.startsWith(richardQuinURL + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
		richardQuinBuddyMessageProcessURL.startsWith(response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href)
	}

	def 'Richard processes Bob\'s buddy rejection'(){
		given:

		when:
		def response = appService.postMessageActionWithPassword(richardQuinBuddyMessageProcessURL, """{
				"properties":{
				}
			}""", richardQuinPassword)

		then:
		response.status == 200
		response.responseData?.properties?.status == "done"
	}

	def 'Richard checks his buddy list and Bob is not there'(){
		given:

		when:
		def response = appService.getBuddies(richardQuinURL, richardQuinPassword);

		then:
		response.status == 200
		response.responseData?._embedded?.buddies == null
	}

	def 'Bob checks his buddy list and Richard is not there'(){
		given:

		when:
		def response = appService.getBuddies(bobDunnURL, bobDunnPassword);

		then:
		response.status == 200
		response.responseData?._embedded?.buddies == null
	}
}
