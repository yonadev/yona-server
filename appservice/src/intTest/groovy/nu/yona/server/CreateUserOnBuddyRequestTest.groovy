package nu.yona.server

import groovyx.net.http.RESTClient
import spock.lang.Ignore
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import groovy.json.*

import javax.mail.*
import javax.mail.search.*

class CreateUserOnBuddyRequestTest extends Specification {

	def adminServiceBaseURL = System.properties.'yona.adminservice.url'
	def YonaServer adminService = new YonaServer(adminServiceBaseURL)

	def analysisServiceBaseURL = System.properties.'yona.analysisservice.url'
	def YonaServer analysisService = new YonaServer(analysisServiceBaseURL)

	def appServiceBaseURL = System.properties.'yona.appservice.url'
	def YonaServer appService = new YonaServer(appServiceBaseURL)
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
	def bobDunnGmail = "bobdunn325@gmail.com"
	@Shared
	def bobDunnGmailPassword = "bobbydunn"
	@Shared
	def bobDunnInviteURL
	@Shared
	def bobDunnURL
	@Shared
	def bobDunnVPNLoginID
	@Shared
	def bobDunnMobileNumberConfirmationCode
	@Shared
	def richardQuinBobBuddyURL
	@Shared
	def bobDunnRichardBuddyURL
	@Shared
	def bobDunnBuddyMessageAcceptURL
	@Shared
	def bobDunnBuddyMessageProcessURL
	@Shared
	def richardQuinBuddyMessageAcceptURL
	@Shared
	def richardQuinBuddyMessageProcessURL
	@Shared
	def richardQuinMobileNumberConfirmationCode

	def richardQuinCreationJSON = """{
				"firstName":"Richard ${timestamp}",
				"lastName":"Quin ${timestamp}",
				"nickName":"RQ ${timestamp}",
				"mobileNumber":"+${timestamp}11",
				"devices":[
					"Nexus 6"
				],
				"goals":[
					"news"
				]
			}"""

	def 'Add user Richard Quin'(){
		given:

		when:
			def response = appService.addUser(richardQuinCreationJSON, richardQuinPassword)
			if (response.status == 201) {
				richardQuinURL = appService.stripQueryString(response.responseData._links.self.href)
				richardQuinVPNLoginID = response.responseData.vpnProfile.vpnLoginID;
				richardQuinMobileNumberConfirmationCode = response.responseData.confirmationCode;
			}

		then:
			response.status == 201
			richardQuinURL.startsWith(appServiceBaseURL + appService.USERS_PATH)
			richardQuinMobileNumberConfirmationCode != null

		cleanup:
			println "URL Richard: " + richardQuinURL
	}

	def 'Richard cannot create a buddy request before confirming mobile number'(){
		given:

		when:
			def beforeRequestDateTime = new Date()
			def response = appService.requestBuddy(richardQuinURL, """{
				"_embedded":{
					"user":{
						"firstName":"Bob ${timestamp}",
						"lastName":"Dun ${timestamp}",
						"emailAddress":"${bobDunnGmail}",
						"mobileNumber":"+${timestamp}12"
					}
				},
				"message":"Would you like to be my buddy?",
				"sendingStatus":"REQUESTED",
				"receivingStatus":"REQUESTED"
			}""", richardQuinPassword)

		then:
			response.status == 400
	}

	def 'Confirm Richard\'s mobile number'(){
		when:
			def response = appService.confirmMobileNumber(richardQuinURL, """ { "code":"${richardQuinMobileNumberConfirmationCode}" } """, richardQuinPassword)

		then:
			response.status == 200
			response.responseData.mobileNumberConfirmed == true
	}

	def 'Richard requests Bob to become his buddy'(){
		given:

		when:
			def beforeRequestDateTime = new Date()
			def response = appService.requestBuddy(richardQuinURL, """{
				"_embedded":{
					"user":{
						"firstName":"Bob ${timestamp}",
						"lastName":"Dun ${timestamp}",
						"emailAddress":"${bobDunnGmail}",
						"mobileNumber":"+${timestamp}12"
					}
				},
				"message":"Would you like to be my buddy?",
				"sendingStatus":"REQUESTED",
				"receivingStatus":"REQUESTED"
			}""", richardQuinPassword)
			if (response.status == 201) {
				richardQuinBobBuddyURL = response.responseData._links.self.href

				/*
				def bobDunnInviteEmail = getMessageFromGmail(bobDunnGmail, bobDunnGmailPassword, beforeRequestDateTime)
				if(bobDunnInviteEmail) {
					def bobDunnInviteURLMatch = bobDunnInviteEmail.content =~ /$appServiceBaseURL[\w\-\/=\?&+]+/
					if(bobDunnInviteURLMatch) {
						bobDunnInviteURL = bobDunnInviteURLMatch.group()
					}
				}
				*/
				bobDunnInviteURL = response.responseData.userCreatedInviteURL;
				bobDunnMobileNumberConfirmationCode = response.responseData?._embedded?.user?.confirmationCode;
				bobDunnURL = appService.stripQueryString(bobDunnInviteURL)
			}

		then:
			response.status == 201
			response.responseData._embedded.user.firstName == "Bob ${timestamp}"
			richardQuinBobBuddyURL.startsWith(richardQuinURL)
			bobDunnInviteURL
			bobDunnMobileNumberConfirmationCode

		cleanup:
			println "URL buddy Richard: " + richardQuinBobBuddyURL
			println "Invite URL Bob: " + bobDunnInviteURL

	}

	def 'Hacking attempt: Try to get Bob Dunn with an invalid temp password'(){
		given:

		when:
			def response = appService.getResource(bobDunnURL, [:], ["tempPassword": "hack", "includePrivateData": "true"])

		then:
			response.status == 400
	}

	def 'Hacking attempt: Try to update Bob Dunn with an invalid temp password'(){
		given:

		when:
			def response = appService.updateResource(bobDunnURL, """{
				"firstName":"Richard ${timestamp}",
				"lastName":"Quin ${timestamp}",
				"nickName":"RQ ${timestamp}",
				"mobileNumber":"+${timestamp}11",
				"devices":[
					"Nexus 6"
				],
				"goals":[
					"news"
				]
			}""", ["Yona-Password": "New password"], ["tempPassword": "hack"])

		then:
			response.status == 400
	}

	def 'Hacking attempt: Try to get a normal user with a temp password'(){
		given:

		when:
			def response = appService.getResource(richardQuinURL, [:], ["tempPassword": "hack", "includePrivateData": "true"])

		then:
			response.status == 400
	}

	def 'Hacking attempt: Try to update a normal user with a temp password'(){
		given:

		when:
			def response = appService.updateResource(richardQuinURL, richardQuinCreationJSON, ["Yona-Password": "New password"], ["tempPassword": "hack"])

		then:
			response.status == 500
	}

	def 'Bob Dunn downloads the app and opens the link sent in the email with the app; app retrieves data to prefill'(){
		given:

		when:
			def response = appService.getUser(bobDunnInviteURL, true, null)

		then:
			response.status == 200
			response.responseData.firstName == "Bob ${timestamp}"
			response.responseData.lastName == "Dun ${timestamp}"
			response.responseData.mobileNumber == "+${timestamp}12"
			response.responseData?.mobileNumberConfirmed == false
			response.responseData.vpnProfile.vpnLoginID ==~ /(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
			response.responseData.vpnProfile.vpnPassword.length() == 32
			response.responseData.vpnProfile.openVPNProfile.length() > 10

	}

	def 'Bob Dunn adjusts data and submits; app saves with new password'(){
		given:

		when:
			def response = appService.updateUser(bobDunnInviteURL, """{
				"firstName":"Bob ${timestamp}",
				"lastName":"Dun ${timestamp}",
				"nickName":"BD ${timestamp}",
				"mobileNumber":"+${timestamp}12",
				"devices":[
					"iPhone 6"
				],
				"goals":[
					"gambling"
				]
			}""", bobDunnPassword)
			if(response.status == 200)
			{
				bobDunnURL = appService.stripQueryString(response.responseData._links.self.href)
			}

		then:
			response.status == 200
			response.responseData.firstName == "Bob ${timestamp}"
			response.responseData.lastName == "Dun ${timestamp}"
			response.responseData.mobileNumber == "+${timestamp}12"
			response.responseData.nickName == "BD ${timestamp}"
			response.responseData.devices.size() == 1
			response.responseData.devices[0] == "iPhone 6"
			//TODO: updating of goals is not yet supported
			response.responseData.goals.size() == 0
	}

	def 'Bob cannot read direct messages before confirming mobile number'(){
		given:

		when:
			def response = appService.getDirectMessages(bobDunnURL, bobDunnPassword)

		then:
			response.status == 400
	}

	def 'Bob Dunn receives confirmation SMS and enters the confirmation code in app'(){
		given:

		when:
			def response = appService.confirmMobileNumber(bobDunnInviteURL, """ { "code":"${bobDunnMobileNumberConfirmationCode}" } """, bobDunnPassword)

		then:
			response.status == 200
			response.responseData.mobileNumberConfirmed == true
	}

	def 'Check if user is now retrievable with new password'(){
		given:

		when:
			def response = appService.getUser(bobDunnURL, true, bobDunnPassword)

		then:
			response.status == 200
			response.responseData.firstName == "Bob ${timestamp}"
			response.responseData.lastName == "Dun ${timestamp}"
			response.responseData.mobileNumber == "+${timestamp}12"
			response.responseData.nickName == "BD ${timestamp}"
			response.responseData.devices.size() == 1
			response.responseData.devices[0] == "iPhone 6"
			response.responseData.goals.size() == 0
	}

	def 'Check if user is now modifiable with new password'(){
		given:

		when:
			def response = appService.updateUser(bobDunnURL, """{
				"firstName":"Bob ${timestamp}",
				"lastName":"Dunn ${timestamp}",
				"nickName":"BD ${timestamp}",
				"mobileNumber":"+${timestamp}12",
				"devices":[
					"iPhone 6"
				],
				"goals":[
					"gambling"
				]
			}""", bobDunnPassword)

		then:
			response.status == 200
			response.responseData.firstName == "Bob ${timestamp}"
			response.responseData.lastName == "Dunn ${timestamp}"
			response.responseData.mobileNumber == "+${timestamp}12"
			response.responseData.nickName == "BD ${timestamp}"
			response.responseData.devices.size() == 1
			response.responseData.devices[0] == "iPhone 6"
			response.responseData.goals.size() == 0
	}


	def 'User should no longer be accessible by temp password'(){
		given:

		when:
			def response = appService.getUser(bobDunnInviteURL, true, null)

		then:
			response.status == 400
	}

	def 'Bob checks his direct messages'(){
		given:

		when:
			def response = appService.getDirectMessages(bobDunnURL, bobDunnPassword)
			if (response.responseData._embedded && response.responseData._embedded.buddyConnectRequestMessages) {
				bobDunnBuddyMessageAcceptURL = response.responseData._embedded.buddyConnectRequestMessages[0]._links.accept.href
			}

		then:
			response.status == 200
			response.responseData._links.self.href == bobDunnURL + appService.DIRECT_MESSAGE_PATH_FRAGMENT
			response.responseData._embedded.buddyConnectRequestMessages[0].user.firstName == "Richard ${timestamp}"
			response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href.startsWith(response.responseData._links.self.href)
			bobDunnBuddyMessageAcceptURL.startsWith(response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href)
	}

	def 'Bob accepts Richard\'s buddy request'(){
		given:

		when:
			def response = appService.postMessageActionWithPassword(bobDunnBuddyMessageAcceptURL, """{
				"properties":{
					"message":"Yes, great idea!"
				}
			}""", bobDunnPassword)

		then:
			response.status == 200
			response.responseData.properties.status == "done"
	}

	def 'Richard checks his direct messages'(){
		given:

		when:
			def response = appService.getDirectMessages(richardQuinURL, richardQuinPassword)
			if (response.responseData._embedded && response.responseData._embedded.buddyConnectResponseMessages) {
				richardQuinBuddyMessageProcessURL = response.responseData._embedded.buddyConnectResponseMessages[0]._links.process.href
			}

		then:
			response.status == 200
			response.responseData._links.self.href == richardQuinURL + appService.DIRECT_MESSAGE_PATH_FRAGMENT
			response.responseData._embedded.buddyConnectResponseMessages[0].user.firstName == "Bob ${timestamp}"
			response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href.startsWith(response.responseData._links.self.href)
			richardQuinBuddyMessageProcessURL.startsWith(response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href)
	}

	def 'Richard processes Bob\'s buddy acceptance'(){
		given:

		when:
			def response = appService.postMessageActionWithPassword(richardQuinBuddyMessageProcessURL, """{
				"properties":{
				}
			}""", richardQuinPassword)

		then:
			response.status == 200
			response.responseData.properties.status == "done"
	}

	def 'Bob\'s user data will contain Richard as buddy'(){
		given:

		when:
			def response = appService.getUser(bobDunnURL, true, bobDunnPassword)

		then:
			response.status == 200
			response.responseData._embedded.buddies != null
			response.responseData._embedded.buddies.size() == 1
			response.responseData._embedded.buddies[0]._embedded.user.firstName == "Richard ${timestamp}"
			response.responseData._embedded.buddies[0].sendingStatus == "ACCEPTED"
			response.responseData._embedded.buddies[0].receivingStatus == "ACCEPTED"
	}

	def 'Delete users'(){
		given:

		when:
			def responseRichard = appService.deleteUser(richardQuinURL, richardQuinPassword)
			def responseBob = appService.deleteUser(bobDunnURL, bobDunnPassword)

		then:
			responseRichard.status == 200
			responseBob.status == 200
	}
}
