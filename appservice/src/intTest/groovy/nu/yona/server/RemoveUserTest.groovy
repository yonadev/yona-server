package nu.yona.server

import groovy.json.*

class RemoveUserTest extends AbstractAppServiceIntegrationTest
{
	def 'Delete account'()
	{
		given:
			def richard = addRichard()

		when:
			def response = newAppService.deleteUser(richard, "Goodbye friends! I deinstalled the Internet")

		then:
			response.status == 200
	}

	def 'Delete and recreate account'()
	{
		given:
			def richard = addRichard()
			newAppService.deleteUser(richard, "Goodbye friends! I deinstalled the Internet")

		when:
			def newRichard = newAppService.addUser(newAppService.&assertUserCreationResponseDetails, richard.password, richard.firstName, richard.lastName,
				richard.nickname, richard.mobileNumber, [ "Nokia 6310" ], [])

		then:
			newRichard
	}

	def 'Remove Richard and verify that Bob receives a remove buddy message'()
	{
		given:
			def richardAndBob = addRichardAndBobAsBuddies()
			def richard = richardAndBob.richard
			def bob = richardAndBob.bob
			newAnalysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")
			newAnalysisService.postToAnalysisEngine(richard, "news/media", "http://www.refdag.nl")

		when:
			def message = "Goodbye friends! I deinstalled the Internet"
			newAppService.deleteUser(richard, message)

		then:
			// TODO: when are the buddies actually removed? Not on "process" of the message?
			def buddies = newAppService.getBuddies(bob)
			buddies.size() == 1
			def getAnonMessagesResponse = newAppService.getAnonymousMessages(bob)
			getAnonMessagesResponse.status == 200
			getAnonMessagesResponse.responseData._embedded
			getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0].reason == "USER_ACCOUNT_DELETED"
			getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0].message == message
			getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0].nickname == richard.nickname
			getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0]._links.self.href.startsWith(bob.url + newAppService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
			getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0]._links.process.href.startsWith(getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0]._links.self.href)
			getAnonMessagesResponse.responseData._embedded.goalConflictMessages.size == 1
			getAnonMessagesResponse.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
			getAnonMessagesResponse.responseData._embedded.goalConflictMessages[0].goalName == "gambling"
			getAnonMessagesResponse.responseData._embedded.goalConflictMessages[0].url =~ /poker/
			def getDirectMessagesResponse = newAppService.getDirectMessages(bob)
			getDirectMessagesResponse.status == 200
			getDirectMessagesResponse.responseData._embedded == null || response.responseData._embedded.buddyConnectRequestMessages == null

		cleanup:
			newAppService.deleteUser(bob)
	}

	def 'Remove Richard and verify that buddy data is removed after Bob processed the remove buddy message'()
	{
		given:
			def richardAndBob = addRichardAndBobAsBuddies()
			def richard = richardAndBob.richard
			def bob = richardAndBob.bob
			newAnalysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")
			newAnalysisService.postToAnalysisEngine(richard, "news/media", "http://www.refdag.nl")
			def message = "Goodbye friends! I deinstalled the Internet"
			newAppService.deleteUser(richard, message)
			def getResponse = newAppService.getAnonymousMessages(bob)
			def processURL = (getResponse.status == 200) ? getResponse.responseData._embedded.buddyDisconnectMessages[0]._links.process.href : null

		when:
			def response = newAppService.postMessageActionWithPassword(processURL, [:], bob.password)

		then:
			response.status == 200
			def buddies = newAppService.getBuddies(bob)
			buddies.size() == 0
			def getAnonMessagesResponse = newAppService.getAnonymousMessages(bob)
			getAnonMessagesResponse.status == 200
			getAnonMessagesResponse.responseData._embedded
			getAnonMessagesResponse.responseData._embedded.goalConflictMessages.size == 1
			getAnonMessagesResponse.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
			getAnonMessagesResponse.responseData._embedded.goalConflictMessages[0].goalName == "gambling"
			getAnonMessagesResponse.responseData._embedded.goalConflictMessages[0].url =~ /poker/

		cleanup:
			newAppService.deleteUser(bob)
	}

	def 'Conflicts for Bob are still processed after the unsubscribe of Bob'()
	{
		given:
			def richardAndBob = addRichardAndBobAsBuddies()
			def richard = richardAndBob.richard
			def bob = richardAndBob.bob
			def message = "Goodbye friends! I deinstalled the Internet"
			newAppService.deleteUser(richard, message)
			def getResponse = newAppService.getAnonymousMessages(bob)
			def processURL = (getResponse.status == 200) ? getResponse.responseData._embedded.buddyDisconnectMessages[0]._links.process.href : null
			newAppService.postMessageActionWithPassword(processURL, [:], bob.password)

		when:
			def response = newAnalysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")

		then:
			response.status == 200
			def getAnonMessagesResponse = newAppService.getAnonymousMessages(bob)
			getAnonMessagesResponse.status == 200
			getAnonMessagesResponse.responseData._embedded
			getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0].reason == "USER_ACCOUNT_DELETED"
			getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0].message == message
			getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0].nickname == richard.nickname
			getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0]._links.self.href.startsWith(bob.url + newAppService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
			!getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0]._links.process

		cleanup:
			newAppService.deleteUser(bob)
	}
}
