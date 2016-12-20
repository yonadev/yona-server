/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*
import nu.yona.server.test.User

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
		allMessagesResponse.responseData._links.self.href == richard.messagesUrl
		allMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectResponseMessage"}.size() == 1
		allMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}.size() == 3
		allMessagesResponse.responseData._embedded."yona:messages".size() == 4

		firstPageMessagesResponse.status == 200
		firstPageMessagesResponse.responseData._links.self.href.contains("page=0")
		firstPageMessagesResponse.responseData._links.self.href.contains("size=2")
		firstPageMessagesResponse.responseData._links.self.href.contains("sort=creationTime")
		!firstPageMessagesResponse.responseData._links.prev
		firstPageMessagesResponse.responseData._links.next
		firstPageMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectResponseMessage"}.size() == 0
		firstPageMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}.size() == 2
		firstPageMessagesResponse.responseData.page.totalElements == 4

		secondPageMessagesResponse.status == 200
		secondPageMessagesResponse.responseData._links.self.href.contains("page=1")
		secondPageMessagesResponse.responseData._links.self.href.contains("size=2")
		secondPageMessagesResponse.responseData._links.self.href.contains("sort=creationTime")
		secondPageMessagesResponse.responseData._links.prev
		!secondPageMessagesResponse.responseData._links.next
		secondPageMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectResponseMessage"}.size() == 1
		secondPageMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}.size() == 1
		secondPageMessagesResponse.responseData.page.totalElements == 4

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard retrieves only unread messages'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker'com")
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
		analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker'com")
		analysisService.postToAnalysisEngine(bob, ["news/media"], "http://www.refdag.nl")

		def initialGetMessagesResponse = appService.getMessages(richard)
		assert initialGetMessagesResponse.status == 200
		assert initialGetMessagesResponse.responseData.page.totalElements == 5

		markRead(richard, initialGetMessagesResponse.responseData._embedded."yona:messages"[0])
		markRead(richard, initialGetMessagesResponse.responseData._embedded."yona:messages"[2])
		markRead(richard, initialGetMessagesResponse.responseData._embedded."yona:messages"[3])

		when:
		def getUnreadMessagesResponse = appService.getMessages(richard, [ "onlyUnreadMessages" : true])

		then:
		getUnreadMessagesResponse.status == 200
		getUnreadMessagesResponse.responseData.page.totalElements == 2

		getUnreadMessagesResponse.responseData._embedded."yona:messages"[0]._links.self.href == initialGetMessagesResponse.responseData._embedded."yona:messages"[1]._links.self.href
		getUnreadMessagesResponse.responseData._embedded."yona:messages"[1]._links.self.href == initialGetMessagesResponse.responseData._embedded."yona:messages"[4]._links.self.href

		def secondGetMessagesResponse = appService.getMessages(richard, [ "onlyUnreadMessages" : false])
		secondGetMessagesResponse.responseData.page.totalElements == 5

		markUnread(richard, secondGetMessagesResponse.responseData._embedded."yona:messages"[2])

		def secondGetUnreadMessagesResponse = appService.getMessages(richard, [ "onlyUnreadMessages" : true])
		secondGetUnreadMessagesResponse.status == 200
		secondGetUnreadMessagesResponse.responseData.page.totalElements == 3

		secondGetUnreadMessagesResponse.responseData._embedded."yona:messages"[0]._links.self.href == initialGetMessagesResponse.responseData._embedded."yona:messages"[1]._links.self.href
		secondGetUnreadMessagesResponse.responseData._embedded."yona:messages"[1]._links.self.href == initialGetMessagesResponse.responseData._embedded."yona:messages"[2]._links.self.href
		secondGetUnreadMessagesResponse.responseData._embedded."yona:messages"[2]._links.self.href == initialGetMessagesResponse.responseData._embedded."yona:messages"[4]._links.self.href

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Bob tries to delete Richard\'s buddy request before it is processed'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		appService.sendBuddyConnectRequest(richard, bob)
		def messageUrl = appService.getMessages(bob).responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectRequestMessage"}[0]._links.self.href

		when:
		def response = appService.deleteResourceWithPassword(messageUrl, bob.password)

		then:
		response.status == 400
		response.responseData?.code == "error.cannot.delete.unprocessed.message"
		def buddyConnectRequestMessages = appService.getMessages(bob).responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectRequestMessage"}
		buddyConnectRequestMessages.size() == 1
		!buddyConnectRequestMessages[0]._links.edit

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Bob deletes Richard\'s buddy request after it is processed'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		appService.sendBuddyConnectRequest(richard, bob)
		def acceptUrl = appService.fetchBuddyConnectRequestMessage(bob).acceptUrl
		appService.postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], bob.password)
		def messageDeleteUrl = appService.getMessages(bob).responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectRequestMessage"}[0]._links.edit.href

		when:
		def response = appService.deleteResourceWithPassword(messageDeleteUrl, bob.password)

		then:
		response.status == 200
		!appService.getMessages(bob).responseData._embedded?."yona:messages"?.size()

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard tries to delete Bob\'s buddy acceptance before it is processed'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		appService.sendBuddyConnectRequest(richard, bob)
		def acceptUrl = appService.fetchBuddyConnectRequestMessage(bob).acceptUrl
		appService.postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], bob.password)
		def messageUrl = appService.getMessages(richard).responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectResponseMessage"}[0]._links.self.href

		when:
		def response = appService.deleteResourceWithPassword(messageUrl, richard.password)

		then:
		response.status == 400
		response.responseData?.code == "error.cannot.delete.unprocessed.message"
		def buddyConnectResponseMessages = appService.getMessages(richard).responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectResponseMessage"}
		buddyConnectResponseMessages.size() == 1
		!buddyConnectResponseMessages[0]._links.edit

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard deletes Bob\'s buddy acceptance after it is processed'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		appService.sendBuddyConnectRequest(richard, bob)
		def acceptUrl = appService.fetchBuddyConnectRequestMessage(bob).acceptUrl
		appService.postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], bob.password)
		def processUrl = appService.fetchBuddyConnectResponseMessage(richard).processUrl
		def processResponse = appService.postMessageActionWithPassword(processUrl, [ : ], richard.password)
		def messageDeleteUrl = appService.getMessages(richard).responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectResponseMessage"}[0]._links.edit.href

		when:
		def response = appService.deleteResourceWithPassword(messageDeleteUrl, richard.password)

		then:
		response.status == 200
		!appService.getMessages(richard).responseData._embedded?."yona:messages"?.size()

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard deletes a goal conflict message. After that, Bob still has it'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
		def messageDeleteUrl = appService.getMessages(richard).responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}[0]._links.edit.href

		when:
		def response = appService.deleteResourceWithPassword(messageDeleteUrl, richard.password)

		then:
		response.status == 200
		appService.getMessages(richard).responseData._embedded?."yona:messages"?.findAll{ it."@type" == "GoalConflictMessage"}.size() == 0

		appService.getMessages(bob).responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}.size() == 1

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Bob deletes a goal conflict message. After that, Richard still has it'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
		def messageDeleteUrl = appService.getMessages(bob).responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}[0]._links.edit.href

		when:
		def response = appService.deleteResourceWithPassword(messageDeleteUrl, bob.password)

		then:
		response.status == 200
		appService.getMessages(richard).responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}.size() == 1

		appService.getMessages(bob).responseData._embedded?."yona:messages"?.findAll{ it."@type" == "GoalConflictMessage"}.size() == 0

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	private void markRead(User user, def message)
	{
		assert message._links?."yona:markRead"?.href
		appService.postMessageActionWithPassword(message._links."yona:markRead".href, [ : ], user.password)
	}

	private void markUnread(User user, def message)
	{
		assert message._links?."yona:markUnread"?.href
		def response = appService.postMessageActionWithPassword(message._links."yona:markUnread".href, [ : ], user.password)
		assert response.status == 200
	}
}
