package nu.yona.server

import groovy.json.*

import nu.yona.server.test.AbstractYonaIntegrationTest
import nu.yona.server.test.User
import spock.lang.Shared

class CreateUserOnBuddyRequestTest extends AbstractAppServiceIntegrationTest
{

	@Shared
	def ts = YonaServer.getTimeStamp()

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

	def 'Richard cannot create a buddy request before confirming his own mobile number'()
	{
		given:
			def richard = newAppService.addUser(newAppService.&assertUserCreationResponseDetails, "R i c h a r d", "Richard", "Quinn", "RQ",
				"+$timestampNew", [ "Nexus 6" ], [ "news", "gambling" ])

		when:
			def response = sendBuddyRequestForBob(richard, "+$timestampNew")

		then:
			response.status == 400
			response.responseData.code == "error.mobile.number.not.confirmed"

		cleanup:
			newAppService.deleteUser(richard)
	}

	def 'Richard successfully creates a buddy request after confirming his own mobile number'()
	{
		given:
			def richard = addRichard()

		when:
			def response = sendBuddyRequestForBob(richard, "+$timestampNew")

		then:
			response.status == 201
			response.responseData._embedded.user.firstName == "Bob"
			response.responseData._links.self.href.startsWith(richard.url)
			response.responseData.userCreatedInviteURL

		cleanup:
			newAppService.deleteUser(richard)
			// TODO: How to delete the invited user?
	}

	def 'Bob downloads the app and uses it to open the link sent in the e-mail and load the prefilled data provided by Richard'()
	{
		given:
			def richard = addRichard()
			def mobileNumberBob = "+$timestampNew"
			def inviteURL = sendBuddyRequestForBob(richard, mobileNumberBob).responseData.userCreatedInviteURL

		when:
			def bob = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBoddyReqeuest, inviteURL, true, null)

		then:
			bob.firstName == "Bob"
			bob.lastName == "Dunn"
			bob.mobileNumber == mobileNumberBob
			bob.mobileNumberConfirmed == false

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}

	def 'Bob adjusts data and submits; app saves with the device password'()
	{
		given:
			def richard = addRichard()
			def mobileNumberBob = "+$timestampNew"
			def inviteURL = sendBuddyRequestForBob(richard, mobileNumberBob).responseData.userCreatedInviteURL
			def bob = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBoddyReqeuest, inviteURL, true, null)

		when:
			def newNickname = "Bobby"
			def newPassword = "B o b"
			def updatedBob = bob.convertToJSON()
			updatedBob.nickname = newNickname
			updatedBob.devices = [ "iPhone 6" ]
			updatedBob.goals = [ "gambling" ]
			def response = newAppService.updateUser(inviteURL, updatedBob, newPassword);

		then:
			response.status == 200
			response.responseData.firstName == bob.firstName
			response.responseData.lastName == bob.lastName
			response.responseData.mobileNumber == bob.mobileNumber
			response.responseData.nickname == newNickname
			response.responseData.devices.size() == 1
			response.responseData.devices[0] == "iPhone 6"
			response.responseData.goals.size() == 0 //TODO: updating of goals is not yet supported
			!(response.responseData._links.self.href ==~ /tempPassword/)
			response.responseData.confirmationCode;

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob.url, newPassword)
	}

	def 'Bob fetches his user information saved with the device password'()
	{
		given:
			def richard = addRichard()
			def mobileNumberBob = "+$timestampNew"
			def inviteURL = sendBuddyRequestForBob(richard, mobileNumberBob).responseData.userCreatedInviteURL
			def bob = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBoddyReqeuest, inviteURL, true, null)
			def newNickname = "Bobby"
			def newPassword = "B o b"
			def updatedBob = bob.convertToJSON()
			updatedBob.nickname = newNickname
			updatedBob.devices = [ "iPhone 6" ]
			updatedBob.goals = [ "gambling" ]
			newAppService.updateUser(inviteURL, updatedBob, newPassword);

		when:
			def bobFromGetAfterUpdate = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBoddyReqeuest, bob.url, true, newPassword)

		then:
			bobFromGetAfterUpdate.firstName == bob.firstName
			bobFromGetAfterUpdate.lastName == bob.lastName
			bobFromGetAfterUpdate.mobileNumber == bob.mobileNumber
			bobFromGetAfterUpdate.nickname == newNickname
			bobFromGetAfterUpdate.devices.size() == 1
			bobFromGetAfterUpdate.devices[0] == "iPhone 6"
			bobFromGetAfterUpdate.goals.size() == 0 //TODO: updating of goals is not yet supported
			bobFromGetAfterUpdate.url

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob.url, newPassword)
	}

	def 'Bob cannot read direct messages before confirming his mobile number'()
	{
		given:
			def richard = addRichard()
			def mobileNumberBob = "+$timestampNew"
			def inviteURL = sendBuddyRequestForBob(richard, mobileNumberBob).responseData.userCreatedInviteURL
			def bob = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBoddyReqeuest, inviteURL, true, null)
			def newNickname = "Bobby"
			def newPassword = "B o b"
			def updatedBob = bob.convertToJSON()
			updatedBob.nickname = newNickname
			updatedBob.devices = [ "iPhone 6" ]
			updatedBob.goals = [ "gambling" ]
			def bobUpdated = newAppService.updateUser(inviteURL, updatedBob, newPassword);
			def bobFromGetAfterUpdate = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBoddyReqeuest, bob.url, true, "B o b")

		when:
			def response = newAppService.getDirectMessages(bobFromGetAfterUpdate)

		then:
			response.status == 400
			response.responseData.code == "error.mobile.number.not.confirmed"

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob.url, newPassword)
	}

	def richardQuinCreationJSON = """{
				"firstName":"Richard ${ts}",
				"lastName":"Quin ${ts}",
				"nickname":"RQ ${ts}",
				"mobileNumber":"+${ts}11",
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
			def response = appService.requestBuddy(richardQuinURL, """{
				"_embedded":{
					"user":{
						"firstName":"Bob ${ts}",
						"lastName":"Dunn ${ts}",
						"emailAddress":"bobdunn325@gmail.com",
						"mobileNumber":"+${ts}12"
					}
				},
				"message":"Would you like to be my buddy?",
				"sendingStatus":"REQUESTED",
				"receivingStatus":"REQUESTED"
			}""", richardQuinPassword)
			if (response.status == 201) {
				richardQuinBobBuddyURL = response.responseData._links.self.href

				bobDunnInviteURL = response.responseData.userCreatedInviteURL;
				bobDunnURL = appService.stripQueryString(bobDunnInviteURL)
			}

		then:
			response.status == 201
			response.responseData._embedded.user.firstName == "Bob ${ts}"
			richardQuinBobBuddyURL.startsWith(richardQuinURL)
			bobDunnInviteURL

		cleanup:
			println "URL buddy Richard: " + richardQuinBobBuddyURL
			println "Invite URL Bob: " + bobDunnInviteURL

	}
	
	def 'Attempt to add another user with the same mobile number'(){
		when:
			def response = appService.addUser("""{
					"firstName":"Bobo ${ts}",
					"lastName":"Duno ${ts}",
					"nickname":"BDo ${ts}",
					"mobileNumber":"+${ts}12",
					"devices":[
						"Nexus 6"
					],
					"goals":[
						"news"
					]
				}""", "Foo")

		then:
			response.status == 400
			response.responseData.code == "error.user.exists.created.on.buddy.request"
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
				"firstName":"Richard ${ts}",
				"lastName":"Quin ${ts}",
				"nickname":"RQ ${ts}",
				"mobileNumber":"+${ts}12",
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
			response.status == 400
	}

	def 'Bob Dunn downloads the app and opens the link sent in the email with the app; app retrieves data to prefill'(){
		given:

		when:
			def response = appService.getUser(bobDunnInviteURL, true, null)

		then:
			response.status == 200
			response.responseData.firstName == "Bob ${ts}"
			response.responseData.lastName == "Dunn ${ts}"
			response.responseData.mobileNumber == "+${ts}12"
			response.responseData?.mobileNumberConfirmed == false
			response.responseData.vpnProfile.vpnLoginID ==~ /(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
			response.responseData.vpnProfile.vpnPassword.length() == 32
			response.responseData.vpnProfile.openVPNProfile.length() > 10

	}

	def 'Bob Dunn adjusts data and submits; app saves with new password'(){
		given:

		when:
			def response = appService.updateUser(bobDunnInviteURL, """{
				"firstName":"Bob ${ts}",
				"lastName":"Dunn ${ts}",
				"nickname":"BD ${ts}",
				"mobileNumber":"+${ts}13",
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
				bobDunnMobileNumberConfirmationCode = response.responseData.confirmationCode;
			}

		then:
			response.status == 200
			bobDunnMobileNumberConfirmationCode
			response.responseData.firstName == "Bob ${ts}"
			response.responseData.lastName == "Dunn ${ts}"
			response.responseData.mobileNumber == "+${ts}13"
			response.responseData.nickname == "BD ${ts}"
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
			response.responseData.firstName == "Bob ${ts}"
			response.responseData.lastName == "Dunn ${ts}"
			response.responseData.mobileNumber == "+${ts}13"
			response.responseData.nickname == "BD ${ts}"
			response.responseData.devices.size() == 1
			response.responseData.devices[0] == "iPhone 6"
			response.responseData.goals.size() == 0
	}

	def 'Check if user is now modifiable with new password'(){
		given:

		when:
			def response = appService.updateUser(bobDunnURL, """{
				"firstName":"Bob ${ts}",
				"lastName":"Dunn ${ts}",
				"nickname":"BD ${ts}",
				"mobileNumber":"+${ts}13",
				"devices":[
					"iPhone 6"
				],
				"goals":[
					"gambling"
				]
			}""", bobDunnPassword)

		then:
			response.status == 200
			response.responseData.firstName == "Bob ${ts}"
			response.responseData.lastName == "Dunn ${ts}"
			response.responseData.mobileNumber == "+${ts}13"
			response.responseData.nickname == "BD ${ts}"
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
			response.responseData._embedded.buddyConnectRequestMessages[0].user.firstName == "Richard ${ts}"
			response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href.startsWith(bobDunnURL + appService.DIRECT_MESSAGES_PATH_FRAGMENT)
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

	def 'Richard checks his anonymous messages'(){
		given:

		when:
			def response = appService.getAnonymousMessages(richardQuinURL, richardQuinPassword)
			if (response.responseData._embedded && response.responseData._embedded.buddyConnectResponseMessages) {
				richardQuinBuddyMessageProcessURL = response.responseData._embedded.buddyConnectResponseMessages[0]._links.process.href
			}

		then:
			response.status == 200
			response.responseData._embedded.buddyConnectResponseMessages[0].user.firstName == "Bob ${ts}"
			response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href.startsWith(richardQuinURL + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
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
			response.responseData._embedded.buddies[0]._embedded.user.firstName == "Richard ${ts}"
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
	def sendBuddyRequestForBob(User user, String mobileNumber)
	{
		appService.requestBuddy(user.url, """{
			"_embedded":{
				"user":{
					"firstName":"Bob",
					"lastName":"Dunn",
					"emailAddress":"bobdunn325@gmail.com",
					"mobileNumber":"$mobileNumber"
				}
			},
			"message":"Would you like to be my buddy?",
			"sendingStatus":"REQUESTED",
			"receivingStatus":"REQUESTED"
		}""", user.password)
	}
}
