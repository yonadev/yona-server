/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

class MessagingTest extends AbstractAppServiceIntegrationTest
{
	def 'Richard pages through his messages'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
		analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker'com")
		analysisService.postToAnalysisEngine(bob, ["news/media"], "http://www.refdag.nl")

		when:
		def allMessagesResponse = appService.getMessages(richard)
		def firstPageMessagesResponse = appService.getMessages(richard, [
			"page": 0,
			"size": 2,
			"sort": "creationTime"])
		def secondPageMessagesResponse = appService.getMessages(richard, [
			"page": 1,
			"size": 2,
			"sort": "creationTime"])

		then:
		allMessagesResponse.status == 200
		allMessagesResponse.responseData._links.self.href == richard.url + appService.MESSAGES_PATH_FRAGMENT
		allMessagesResponse.responseData._embedded.messages.findAll{ it."@type" == "BuddyConnectResponseMessage"}.size() == 1
		allMessagesResponse.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}.size() == 3
		allMessagesResponse.responseData._embedded.messages.size() == 4

		firstPageMessagesResponse.status == 200
		firstPageMessagesResponse.responseData._links.self.href == richard.url + appService.MESSAGES_PATH_FRAGMENT + "?page=0&size=2&sort=creationTime"
		!firstPageMessagesResponse.responseData._links.prev
		firstPageMessagesResponse.responseData._links.next
		firstPageMessagesResponse.responseData._embedded.messages.findAll{ it."@type" == "BuddyConnectResponseMessage"}.size() == 0
		firstPageMessagesResponse.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}.size() == 2
		firstPageMessagesResponse.responseData.page.totalElements == 4

		secondPageMessagesResponse.status == 200
		secondPageMessagesResponse.responseData._links.self.href == richard.url + appService.MESSAGES_PATH_FRAGMENT + "?page=1&size=2&sort=creationTime"
		secondPageMessagesResponse.responseData._links.prev
		!secondPageMessagesResponse.responseData._links.next
		secondPageMessagesResponse.responseData._embedded.messages.findAll{ it."@type" == "BuddyConnectResponseMessage"}.size() == 1
		secondPageMessagesResponse.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}.size() == 1
		secondPageMessagesResponse.responseData.page.totalElements == 4
	}

	def 'Bob tries to delete Richard\'s buddy request before it is processed'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		appService.sendBuddyConnectRequest(richard, bob)
		def messageURL = appService.getMessages(bob).responseData._embedded.messages.findAll{ it."@type" == "BuddyConnectRequestMessage"}[0]._links.self.href

		when:
		def response = appService.deleteResourceWithPassword(messageURL, bob.password)

		then:
		response.status == 400
		response.responseData?.code == "error.cannot.delete.unprocessed.message"
		def buddyConnectRequestMessages = appService.getMessages(bob).responseData._embedded.messages.findAll{ it."@type" == "BuddyConnectRequestMessage"}
		buddyConnectRequestMessages.size() == 1
		!buddyConnectRequestMessages[0]._links.edit
	}

	def 'Bob deletes Richard\'s buddy request after it is processed'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		appService.sendBuddyConnectRequest(richard, bob)
		def acceptURL = appService.fetchBuddyConnectRequestMessage(bob).acceptURL
		appService.postMessageActionWithPassword(acceptURL, ["message" : "Yes, great idea!"], bob.password)
		def messageDeleteURL = appService.getMessages(bob).responseData._embedded.messages.findAll{ it."@type" == "BuddyConnectRequestMessage"}[0]._links.edit.href

		when:
		def response = appService.deleteResourceWithPassword(messageDeleteURL, bob.password)

		then:
		response.status == 200
		!appService.getMessages(bob).responseData._embedded?.messages?.size()
	}

	def 'Richard tries to delete Bob\'s buddy acceptance before it is processed'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		appService.sendBuddyConnectRequest(richard, bob)
		def acceptURL = appService.fetchBuddyConnectRequestMessage(bob).acceptURL
		appService.postMessageActionWithPassword(acceptURL, ["message" : "Yes, great idea!"], bob.password)
		def messageURL = appService.getMessages(richard).responseData._embedded.messages.findAll{ it."@type" == "BuddyConnectResponseMessage"}[0]._links.self.href

		when:
		def response = appService.deleteResourceWithPassword(messageURL, richard.password)

		then:
		response.status == 400
		response.responseData?.code == "error.cannot.delete.unprocessed.message"
		def buddyConnectResponseMessages = appService.getMessages(richard).responseData._embedded.messages.findAll{ it."@type" == "BuddyConnectResponseMessage"}
		buddyConnectResponseMessages.size() == 1
		!buddyConnectResponseMessages[0]._links.edit
	}

	def 'Richard deletes Bob\'s buddy acceptance after it is processed'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		appService.sendBuddyConnectRequest(richard, bob)
		def acceptURL = appService.fetchBuddyConnectRequestMessage(bob).acceptURL
		appService.postMessageActionWithPassword(acceptURL, ["message" : "Yes, great idea!"], bob.password)
		def processURL = appService.fetchBuddyConnectResponseMessage(richard).processURL
		def processResponse = appService.postMessageActionWithPassword(processURL, [ : ], richard.password)
		def messageDeleteURL = appService.getMessages(richard).responseData._embedded.messages.findAll{ it."@type" == "BuddyConnectResponseMessage"}[0]._links.edit.href

		when:
		def response = appService.deleteResourceWithPassword(messageDeleteURL, richard.password)

		then:
		response.status == 200
		!appService.getMessages(richard).responseData._embedded?.messages?.size()
	}

	def 'Richard deletes a goal conflict message. After that, Bob still has it'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
		def messageDeleteURL = appService.getMessages(richard).responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}[0]._links.edit.href

		when:
		def response = appService.deleteResourceWithPassword(messageDeleteURL, richard.password)

		then:
		response.status == 200
		appService.getMessages(richard).responseData._embedded?.messages?.findAll{ it."@type" == "GoalConflictMessage"}.size() == 0

		appService.getMessages(bob).responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}.size() == 1
	}

	def 'Bob deletes a goal conflict message. After that, Richard still has it'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
		def messageDeleteURL = appService.getMessages(bob).responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}[0]._links.edit.href

		when:
		def response = appService.deleteResourceWithPassword(messageDeleteURL, bob.password)

		then:
		response.status == 200
		appService.getMessages(richard).responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}.size() == 1

		appService.getMessages(bob).responseData._embedded?.messages?.findAll{ it."@type" == "GoalConflictMessage"}.size() == 0
	}
}
