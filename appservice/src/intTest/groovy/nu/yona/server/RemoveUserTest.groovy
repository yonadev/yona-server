/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

class RemoveUserTest extends AbstractAppServiceIntegrationTest
{
	def 'Delete account'()
	{
		given:
		def richard = addRichard()

		when:
		def response = appService.deleteUser(richard, "Goodbye friends! I deinstalled the Internet")

		then:
		response.status == 200
	}

	def 'Delete and recreate account'()
	{
		given:
		def richard = addRichard()
		appService.deleteUser(richard, "Goodbye friends! I deinstalled the Internet")

		when:
		def newRichard = appService.addUser(appService.&assertUserCreationResponseDetails, richard.password, richard.firstName, richard.lastName,
				richard.nickname, richard.mobileNumber, ["Nokia 6310"])

		then:
		newRichard
	}

	def 'Remove Richard and verify that Bob receives a remove buddy message'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")
		analysisService.postToAnalysisEngine(richard, "news/media", "http://www.refdag.nl")

		when:
		def message = "Goodbye friends! I deinstalled the Internet"
		appService.deleteUser(richard, message)

		then:
		def buddies = appService.getBuddies(bob)
		buddies.size() == 1
		!buddies[0].goals
		def getAnonMessagesResponse = appService.getAnonymousMessages(bob)
		getAnonMessagesResponse.status == 200
		getAnonMessagesResponse.responseData._embedded
		getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0].reason == "USER_ACCOUNT_DELETED"
		getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0].message == message
		getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0].nickname == richard.nickname
		getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0]._links.self.href.startsWith(bob.url + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
		getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0]._links.process.href.startsWith(getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0]._links.self.href)
		getAnonMessagesResponse.responseData._embedded.goalConflictMessages.size == 1
		getAnonMessagesResponse.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
		getAnonMessagesResponse.responseData._embedded.goalConflictMessages[0].activityCategoryName == "gambling"
		getAnonMessagesResponse.responseData._embedded.goalConflictMessages[0].url =~ /poker/
		def getDirectMessagesResponse = appService.getDirectMessages(bob)
		getDirectMessagesResponse.status == 200
		getDirectMessagesResponse.responseData._embedded == null || getDirectMessagesResponse.responseData._embedded.buddyConnectRequestMessages == null

		cleanup:
		appService.deleteUser(bob)
	}

	def 'Remove Richard and verify that buddy data is removed after Bob processed the remove buddy message'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")
		analysisService.postToAnalysisEngine(richard, "news/media", "http://www.refdag.nl")
		def message = "Goodbye friends! I deinstalled the Internet"
		appService.deleteUser(richard, message)
		def getResponse = appService.getAnonymousMessages(bob)
		def disconnectMessage = getResponse.responseData._embedded.buddyDisconnectMessages[0]
		def processURL = disconnectMessage._links.process.href

		when:
		def response = appService.postMessageActionWithPassword(processURL, [:], bob.password)

		then:
		response.status == 200
		response.responseData._embedded.affectedMessages.size() == 1
		response.responseData._embedded.affectedMessages[0]._links.self.href == disconnectMessage._links.self.href
		response.responseData._embedded.affectedMessages[0].processed == true
		response.responseData._embedded.affectedMessages[0]._links.process == null

		def buddies = appService.getBuddies(bob)
		buddies.size() == 0
		def getAnonMessagesResponse = appService.getAnonymousMessages(bob)
		getAnonMessagesResponse.status == 200
		getAnonMessagesResponse.responseData._embedded
		getAnonMessagesResponse.responseData._embedded.goalConflictMessages.size == 1
		getAnonMessagesResponse.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
		getAnonMessagesResponse.responseData._embedded.goalConflictMessages[0].activityCategoryName == "gambling"
		getAnonMessagesResponse.responseData._embedded.goalConflictMessages[0].url =~ /poker/

		cleanup:
		appService.deleteUser(bob)
	}

	def 'Conflicts for Bob are still processed after the unsubscribe of Richard'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		def message = "Goodbye friends! I deinstalled the Internet"
		appService.deleteUser(richard, message)
		def getResponse = appService.getAnonymousMessages(bob)
		def processURL = (getResponse.status == 200) ? getResponse.responseData._embedded.buddyDisconnectMessages[0]._links.process.href : null
		appService.postMessageActionWithPassword(processURL, [:], bob.password)

		when:
		def response = analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")

		then:
		response.status == 200
		def getAnonMessagesResponse = appService.getAnonymousMessages(bob)
		getAnonMessagesResponse.status == 200
		getAnonMessagesResponse.responseData._embedded
		getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0].reason == "USER_ACCOUNT_DELETED"
		getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0].message == message
		getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0].nickname == richard.nickname
		getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0]._links.self.href.startsWith(bob.url + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
		!getAnonMessagesResponse.responseData._embedded.buddyDisconnectMessages[0]._links.process

		cleanup:
		appService.deleteUser(bob)
	}
}
