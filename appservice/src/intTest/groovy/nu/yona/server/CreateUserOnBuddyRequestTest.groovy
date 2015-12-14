package nu.yona.server

import groovy.json.*

import nu.yona.server.test.User
import nu.yona.server.test.User

class CreateUserOnBuddyRequestTest extends AbstractAppServiceIntegrationTest
{
	def 'Richard cannot create a buddy request before confirming his own mobile number'()
	{
		given:
			def richard = newAppService.addUser(newAppService.&assertUserCreationResponseDetails, "R i c h a r d", "Richard", "Quinn", "RQ",
				"+$timestamp", [ "Nexus 6" ], [ "news", "gambling" ])

		when:
			def response = sendBuddyRequestForBob(richard, "+$timestamp")

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
			def response = sendBuddyRequestForBob(richard, "+$timestamp")

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
			def mobileNumberBob = "+$timestamp"
			def inviteURL = sendBuddyRequestForBob(richard, mobileNumberBob).responseData.userCreatedInviteURL

		when:
			def bob = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBoddyRequest, inviteURL, true, null)

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
			def mobileNumberBob = "+$timestamp"
			def inviteURL = sendBuddyRequestForBob(richard, mobileNumberBob).responseData.userCreatedInviteURL
			def bob = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBoddyRequest, inviteURL, true, null)

		when:
			def newNickname = "Bobby"
			def newPassword = "B o b"
			def updatedBobJson = bob.convertToJSON()
			updatedBobJson.nickname = newNickname
			updatedBobJson.devices = [ "iPhone 6" ]
			updatedBobJson.goals = [ "gambling" ]
			def response = newAppService.updateUser(inviteURL, updatedBobJson, newPassword);

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

			def getUserResponse = newAppService.getUser(inviteURL, true, null)
			getUserResponse.status == 400
			getUserResponse.responseData.code == "error.decrypting.data"

			def bobFromGetAfterUpdate = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBoddyRequest, bob.url, true, newPassword)
			bobFromGetAfterUpdate.firstName == bob.firstName
			bobFromGetAfterUpdate.lastName == bob.lastName
			bobFromGetAfterUpdate.mobileNumber == bob.mobileNumber
			bobFromGetAfterUpdate.nickname == newNickname
			bobFromGetAfterUpdate.devices.size() == 1
			bobFromGetAfterUpdate.devices[0] == "iPhone 6"
			bobFromGetAfterUpdate.goals.size() == 0 //TODO: updating of goals is not yet supported
			bobFromGetAfterUpdate.url

			def getDirectMessagesResponse = newAppService.getDirectMessages(bobFromGetAfterUpdate)
			getDirectMessagesResponse.status == 400
			getDirectMessagesResponse.responseData.code == "error.mobile.number.not.confirmed"

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob.url, newPassword)
	}

	def 'Bob updates his user information saved with the device password'()
	{
		given:
			def richard = addRichard()
			def mobileNumberBob = "+$timestamp"
			def inviteURL = sendBuddyRequestForBob(richard, mobileNumberBob).responseData.userCreatedInviteURL
			def bob = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBoddyRequest, inviteURL, true, null)
			def newNickname = "Bobby"
			def newPassword = "B o b"
			def updatedBobJson = bob.convertToJSON()
			updatedBobJson.nickname = newNickname
			updatedBobJson.devices = [ "iPhone 6" ]
			updatedBobJson.goals = [ "gambling" ]
			newAppService.updateUser(inviteURL, updatedBobJson, newPassword);
			def bobFromGetAfterUpdate = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBoddyRequest, bob.url, true, newPassword)

		when:
			def againChangedNickname = "Robert"
			def againUpdatedBobJson = bobFromGetAfterUpdate.convertToJSON()
			againUpdatedBobJson.nickname = againChangedNickname
			def againUpdatedBob = newAppService.updateUser(newAppService.&assertUserUpdateResponseDetails, new User(againUpdatedBobJson, newPassword));

		then:
			againUpdatedBob.nickname == againChangedNickname

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob.url, newPassword)
	}

	def 'Bob receives confirmation SMS and enters the confirmation code in app'()
	{
		given:
			def richard = addRichard()
			def mobileNumberBob = "+$timestamp"
			def inviteURL = sendBuddyRequestForBob(richard, mobileNumberBob).responseData.userCreatedInviteURL
			def bob = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBoddyRequest, inviteURL, true, null)
			def newNickname = "Bobby"
			def newPassword = "B o b"
			def updatedBobJson = bob.convertToJSON()
			updatedBobJson.nickname = newNickname
			updatedBobJson.devices = [ "iPhone 6" ]
			updatedBobJson.goals = [ "gambling" ]
			def updatedBob = newAppService.updateUser(newAppService.&assertUserUpdateResponseDetails, new User(updatedBobJson, newPassword), inviteURL);

		when:
			newAppService.confirmMobileNumber(newAppService.&assertResponseStatusSuccess, updatedBob)

		then:
			def getDirectMessagesResponse = newAppService.getDirectMessages(updatedBob)
			getDirectMessagesResponse.status == 200

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(updatedBob)
	}

	def 'Bob accepts Richard\'s buddy request'()
	{
		given:
			def richard = addRichard()
			def mobileNumberBob = "+$timestamp"
			def inviteURL = sendBuddyRequestForBob(richard, mobileNumberBob).responseData.userCreatedInviteURL
			def bob = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBoddyRequest, inviteURL, true, null)
			def newNickname = "Bobby"
			def newPassword = "B o b"
			def updatedBobJson = bob.convertToJSON()
			updatedBobJson.nickname = newNickname
			updatedBobJson.devices = [ "iPhone 6" ]
			updatedBobJson.goals = [ "gambling" ]
			def updatedBob = newAppService.updateUser(newAppService.&assertUserUpdateResponseDetails, new User(updatedBobJson, newPassword), inviteURL);
			newAppService.confirmMobileNumber(newAppService.&assertResponseStatusSuccess, updatedBob)
			def acceptURL = newAppService.fetchBuddyConnectRequestMessage(updatedBob).acceptURL

		when:
			def response = newAppService.postMessageActionWithPassword(acceptURL, ["message" : "Yes, great idea!"], newPassword)

		then:
			response.status == 200
			def bobWithBuddy = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBoddyRequest, updatedBob.url, true, updatedBob.password)
			bobWithBuddy.buddies != null
			bobWithBuddy.buddies.size() == 1
			bobWithBuddy.buddies[0].user.firstName == "Richard"
			bobWithBuddy.buddies[0].sendingStatus == "ACCEPTED"
			bobWithBuddy.buddies[0].receivingStatus == "ACCEPTED"

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(updatedBob)
	}

	def 'Richard processes Bob\'s buddy acceptance'()
	{
		given:
			def richard = addRichard()
			def mobileNumberBob = "+$timestamp"
			def inviteURL = sendBuddyRequestForBob(richard, mobileNumberBob).responseData.userCreatedInviteURL
			def bob = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBoddyRequest, inviteURL, true, null)
			def newNickname = "Bobby"
			def newPassword = "B o b"
			def updatedBobJson = bob.convertToJSON()
			updatedBobJson.nickname = newNickname
			updatedBobJson.devices = [ "iPhone 6" ]
			updatedBobJson.goals = [ "gambling" ]
			def updatedBob = newAppService.updateUser(newAppService.&assertUserUpdateResponseDetails, new User(updatedBobJson, newPassword), inviteURL);
			newAppService.confirmMobileNumber(newAppService.&assertResponseStatusSuccess, updatedBob)
			def acceptURL = newAppService.fetchBuddyConnectRequestMessage(updatedBob).acceptURL
			newAppService.postMessageActionWithPassword(acceptURL, ["message" : "Yes, great idea!"], newPassword)

		when:
			def processURL = newAppService.fetchBuddyConnectResponseMessage(richard).processURL
			def response = newAppService.postMessageActionWithPassword(processURL, [ : ], richard.password)

		then:
			response.status == 200
			response.responseData.properties.status == "done"

			def richardWithBuddy = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBoddyRequest, richard.url, true, richard.password)
			richardWithBuddy.buddies != null
			richardWithBuddy.buddies.size() == 1
			richardWithBuddy.buddies[0].user.firstName == "Bob"
			richardWithBuddy.buddies[0].sendingStatus == "ACCEPTED"
			richardWithBuddy.buddies[0].receivingStatus == "ACCEPTED"

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(updatedBob)
	}

	def 'Goal conflict of Richard is reported to Richard and Bob'()
	{
		given:
			def richard = addRichard()
			def mobileNumberBob = "+$timestamp"
			def inviteURL = sendBuddyRequestForBob(richard, mobileNumberBob).responseData.userCreatedInviteURL
			def bob = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBoddyRequest, inviteURL, true, null)
			def newNickname = "Bobby"
			def newPassword = "B o b"
			def updatedBobJson = bob.convertToJSON()
			updatedBobJson.nickname = newNickname
			updatedBobJson.devices = [ "iPhone 6" ]
			updatedBobJson.goals = [ "gambling" ]
			def updatedBob = newAppService.updateUser(newAppService.&assertUserUpdateResponseDetails, new User(updatedBobJson, newPassword), inviteURL);
			newAppService.confirmMobileNumber(newAppService.&assertResponseStatusSuccess, updatedBob)
			def acceptURL = newAppService.fetchBuddyConnectRequestMessage(updatedBob).acceptURL
			newAppService.postMessageActionWithPassword(acceptURL, ["message" : "Yes, great idea!"], newPassword)
			def processURL = newAppService.fetchBuddyConnectResponseMessage(richard).processURL
			newAppService.postMessageActionWithPassword(processURL, [ : ], richard.password)

		when:
			newAnalysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")

		then:
			def getAnonMessagesRichardResponse = newAppService.getAnonymousMessages(richard)
			getAnonMessagesRichardResponse.status == 200
			getAnonMessagesRichardResponse.responseData._embedded.goalConflictMessages.size() == 1
			getAnonMessagesRichardResponse.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
			getAnonMessagesRichardResponse.responseData._embedded.goalConflictMessages[0].goalName == "news"
			getAnonMessagesRichardResponse.responseData._embedded.goalConflictMessages[0].url == "http://www.refdag.nl"

			def getAnonMessagesBobResponse = newAppService.getAnonymousMessages(updatedBob)
			getAnonMessagesBobResponse.status == 200
			getAnonMessagesBobResponse.responseData._embedded.goalConflictMessages.size() == 1
			getAnonMessagesBobResponse.responseData._embedded.goalConflictMessages[0].nickname == richard.nickname
			getAnonMessagesBobResponse.responseData._embedded.goalConflictMessages[0].goalName == "news"
			getAnonMessagesBobResponse.responseData._embedded.goalConflictMessages[0].url == null

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(updatedBob)
	}

	def 'Attempt to add another user with the same mobile number'()
	{
		given:
			def richard = addRichard()
			def mobileNumberBob = "+$timestamp"
			sendBuddyRequestForBob(richard, mobileNumberBob)

		when:
			def john = newAppService.addUser(this.&assertUserCreationFailedBecauseOfDuplicate, "J o h n", "John", "Doe", "JD",
				mobileNumberBob, [ "Nokia 6310" ], [])

		then:
			john == null

		cleanup:
			newAppService.deleteUser(richard)
			// TODO: How to delete the invited user?
	}

	def 'Hacking attempt: Try to get Bob with an invalid temp password'()
	{
		given:
			def richard = addRichard()
			def inviteURL = sendBuddyRequestForBob(richard, "+$timestamp")

		when:
			def response = newAppService.getResource(inviteURL, [:], ["tempPassword": "hack", "includePrivateData": "true"])

		then:
			response.status == 400
			// response.responseData.code == "TODO" // Why do we not get a response code here?

		cleanup:
			newAppService.deleteUser(richard)
			// TODO: How to delete the invited user?
	}

	def 'Hacking attempt: Try to update Bob Dunn with an invalid temp password'()
	{
		given:
			def richard = addRichard()
			def inviteURL = sendBuddyRequestForBob(richard, "+$timestamp")

		when:
			def response = newAppService.updateResource(richard.url, """{
				"firstName":"Richard",
				"lastName":"Quin",
				"nickname":"RQ",
				"mobileNumber":"+$timestamp",
				"devices":[
					"Nexus 6"
				],
				"goals":[
					"news"
				]
			}""", ["Yona-Password": "New password"], ["tempPassword": "hack"])

		then:
			response.status == 400
			// response.responseData.code == "TODO" // Why do we not get a response code here?

		cleanup:
			newAppService.deleteUser(richard)
			// TODO: How to delete the invited user?
	}

	def 'Hacking attempt: Try to get a normal user with a temp password'()
	{
		given:
			def richard = addRichard()

		when:
			def response = newAppService.getResource(richard.url, [:], ["tempPassword": "hack", "includePrivateData": "true"])

		then:
			response.status == 400
			// response.responseData.code == "TODO" // Why do we not get a response code here?

		cleanup:
			newAppService.deleteUser(richard)
	}

	def 'Hacking attempt: Try to update a normal user with a temp password'()
	{
		given:
			def richard = addRichard()

		when:
			def response = newAppService.updateResource(richard.url, """{
				"firstName":"Richard",
				"lastName":"Quin",
				"nickname":"RQ",
				"mobileNumber":"+$timestamp",
				"devices":[
					"Nexus 6"
				],
				"goals":[
					"news"
				]
			}""", ["Yona-Password": "New password"], ["tempPassword": "hack"])

		then:
			response.status == 400
			// response.responseData.code == "TODO" // Why do we not get a response code here?

		cleanup:
			newAppService.deleteUser(richard)
	}

	def assertUserCreationFailedBecauseOfDuplicate(response)
	{
		response.status == 400
		response.responseData.code == "error.user.exists.created.on.buddy.request"
	}

	def sendBuddyRequestForBob(User user, String mobileNumber)
	{
		newAppService.requestBuddy(user.url, """{
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
