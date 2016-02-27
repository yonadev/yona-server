/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

class BasicBuddyTest extends AbstractAppServiceIntegrationTest
{
	def 'Hacking attempt: Try to request one-way connection'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()

		when:
		def response = appService.requestBuddy(richard.url, """{
						"_embedded":{
							"user":{
								"firstName":"Bob",
								"lastName":"Dun",
								"emailAddress":"bob@dunn.net",
								"mobileNumber":"$bob.mobileNumber"
							}
						},
						"message":"Would you like to be my buddy?",
						"sendingStatus":"NOT_REQUESTED",
						"receivingStatus":"REQUESTED"
					}""", richard.password)

		then:
		response.status == 400
		response.responseData.code == "error.buddy.only.twoway.buddies.allowed"
	}

	def 'Richard requests Bob to become his buddy'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()

		when:
		def response = appService.sendBuddyConnectRequest(richard, bob)

		then:
		response.status == 201
		response.responseData._embedded.user.firstName == "Bob"
		response.responseData._links.self.href.startsWith(richard.url)

		def richardWithBuddy = appService.getUser(appService.&assertUserGetResponseDetailsPublicDataAndVpnProfile, richard.url, true, richard.password)
		richardWithBuddy.buddies != null
		richardWithBuddy.buddies.size() == 1
		richardWithBuddy.buddies[0].user.firstName == bob.firstName
		//goals should not be embedded before accept
		richardWithBuddy.buddies[0].goals == null
		richardWithBuddy.buddies[0].sendingStatus == "REQUESTED"
		richardWithBuddy.buddies[0].receivingStatus == "REQUESTED"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Bob finds the buddy request'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		appService.sendBuddyConnectRequest(richard, bob)

		when:
		def response = appService.getMessages(bob)

		then:
		response.status == 200
		response.responseData._embedded.messages.size() == 1
		def buddyConnectRequestMessages = response.responseData._embedded.messages.findAll{ it."@type" == "BuddyConnectRequestMessage"}
		buddyConnectRequestMessages.size() == 1
		buddyConnectRequestMessages[0].nickname == richard.nickname
		buddyConnectRequestMessages[0].status == "REQUESTED"
		buddyConnectRequestMessages[0].user.firstName == "Richard"
		buddyConnectRequestMessages[0]._links.self.href.startsWith(bob.messagesUrl)
		buddyConnectRequestMessages[0]._links.accept.href.startsWith(buddyConnectRequestMessages[0]._links.self.href)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Bob accepts Richard\'s buddy request'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		appService.sendBuddyConnectRequest(richard, bob)
		def connectRequestMessage = appService.fetchBuddyConnectRequestMessage(bob)
		def acceptURL = connectRequestMessage.acceptURL

		when:
		def response = appService.postMessageActionWithPassword(acceptURL, ["message" : "Yes, great idea!"], bob.password)

		then:
		response.status == 200
		response.responseData.properties.status == "done"
		response.responseData._embedded.affectedMessages.size() == 1
		response.responseData._embedded.affectedMessages[0]._links.self.href == connectRequestMessage.selfURL
		response.responseData._embedded.affectedMessages[0].status == "ACCEPTED"
		response.responseData._embedded.affectedMessages[0]._links.accept == null
		response.responseData._embedded.affectedMessages[0]._links.reject == null

		def buddies = appService.getBuddies(bob)
		buddies.size() == 1
		buddies[0].user.firstName == richard.firstName
		buddies[0].nickname == richard.nickname
		buddies[0].sendingStatus == "ACCEPTED"
		buddies[0].receivingStatus == "ACCEPTED"

		def bobWithBuddy = appService.getUser(appService.&assertUserGetResponseDetailsPublicDataAndVpnProfile, bob.url, true, bob.password)
		bobWithBuddy.buddies != null
		bobWithBuddy.buddies.size() == 1
		bobWithBuddy.buddies[0].user.firstName == richard.firstName
		bobWithBuddy.buddies[0].goals.size() == 2
		bobWithBuddy.buddies[0].sendingStatus == "ACCEPTED"
		bobWithBuddy.buddies[0].receivingStatus == "ACCEPTED"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard finds Bob\'s buddy connect response'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		appService.sendBuddyConnectRequest(richard, bob)
		def acceptURL = appService.fetchBuddyConnectRequestMessage(bob).acceptURL
		appService.postMessageActionWithPassword(acceptURL, ["message" : "Yes, great idea!"], bob.password)

		when:
		def response = appService.getMessages(richard)

		then:
		response.status == 200
		def buddyConnectResponseMessages = response.responseData._embedded.messages.findAll{ it."@type" == "BuddyConnectResponseMessage"}
		buddyConnectResponseMessages[0].user.firstName == "Bob"
		buddyConnectResponseMessages[0].nickname == bob.nickname
		buddyConnectResponseMessages[0].status == "ACCEPTED"
		buddyConnectResponseMessages[0]._links.self.href.startsWith(richard.messagesUrl)
		buddyConnectResponseMessages[0]._links.process.href.startsWith(buddyConnectResponseMessages[0]._links.self.href)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard processes Bob\'s buddy acceptance'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		appService.sendBuddyConnectRequest(richard, bob)
		def acceptURL = appService.fetchBuddyConnectRequestMessage(bob).acceptURL
		appService.postMessageActionWithPassword(acceptURL, ["message" : "Yes, great idea!"], bob.password)
		def connectResponseMessage = appService.fetchBuddyConnectResponseMessage(richard)
		def processURL = connectResponseMessage.processURL

		when:
		def response = appService.postMessageActionWithPassword(processURL, [ : ], richard.password)

		then:
		response.status == 200
		response.responseData.properties.status == "done"
		response.responseData._embedded.affectedMessages.size() == 1
		response.responseData._embedded.affectedMessages[0]._links.self.href == connectResponseMessage.selfURL
		response.responseData._embedded.affectedMessages[0]._links.process == null

		def buddies = appService.getBuddies(richard)
		buddies.size() == 1
		buddies[0].user.firstName == bob.firstName
		buddies[0].nickname == bob.nickname
		buddies[0].sendingStatus == "ACCEPTED"
		buddies[0].receivingStatus == "ACCEPTED"

		def richardWithBuddy = appService.getUser(appService.&assertUserGetResponseDetailsPublicDataAndVpnProfile, richard.url, true, richard.password)
		richardWithBuddy.buddies != null
		richardWithBuddy.buddies.size() == 1
		richardWithBuddy.buddies[0].user.firstName == bob.firstName
		richardWithBuddy.buddies[0].goals.size() == 2
		richardWithBuddy.buddies[0].sendingStatus == "ACCEPTED"
		richardWithBuddy.buddies[0].receivingStatus == "ACCEPTED"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Goal conflict of Richard is reported to Richard and Bob'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob

		when:
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")

		then:
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].nickname == "<self>"
		richardGoalConflictMessages[0].activityCategoryName == "news"
		richardGoalConflictMessages[0].url == "http://www.refdag.nl"

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		def bobGoalConflictMessages = getMessagesBobResponse.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}
		bobGoalConflictMessages.size() == 1
		bobGoalConflictMessages[0].nickname == richard.nickname
		bobGoalConflictMessages[0].activityCategoryName == "news"
		bobGoalConflictMessages[0].url == null

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Two conflicts within the conflict interval are reported as one message for each person'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob

		when:
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")

		then:
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		getMessagesRichardResponse.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}.size() == 1

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		getMessagesBobResponse.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}.size() == 1
	}

	def 'Goal conflict of Bob is reported to Richard and Bob'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob

		when:
		analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")

		then:
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].nickname == bob.nickname
		richardGoalConflictMessages[0].activityCategoryName == "gambling"
		richardGoalConflictMessages[0].url == null

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		def bobGoalConflictMessages = getMessagesBobResponse.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}
		bobGoalConflictMessages.size() == 1
		bobGoalConflictMessages[0].nickname == "<self>"
		bobGoalConflictMessages[0].activityCategoryName == "gambling"
		bobGoalConflictMessages[0].url == "http://www.poker.com"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard removes Bob as buddy, so goal conflicts from Bob are gone'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
		analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")
		def buddy = appService.getBuddies(richard)[0]

		when:
		def response = appService.removeBuddy(richard, buddy, "Bob, as you know our ways parted, so I'll remove you as buddy.")

		then:
		response.status == 200
		appService.getBuddies(richard).size() == 0 // Buddy removed for Richard`
		appService.getBuddies(bob).size() == 1 // Buddy not yet removed for Bob (not processed yet)

		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		getMessagesRichardResponse.responseData._embedded.messages.findAll{ it."@type" == "BuddyConnectResponseMessages"}.size() == 0
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].nickname == "<self>"
		richardGoalConflictMessages[0].activityCategoryName == "news"

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		getMessagesBobResponse.responseData._embedded.messages.findAll{ it."@type" == "BuddyConnectRequestMessages"}.size() == 0
		def bobGoalConflictMessages = getMessagesBobResponse.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}
		bobGoalConflictMessages.size() == 1
		bobGoalConflictMessages[0].nickname == "<self>"
		bobGoalConflictMessages[0].activityCategoryName == "gambling"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Bob receives the buddy removal of Richard'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
		analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")
		def buddy = appService.getBuddies(richard)[0]
		def message = "Bob, as you know our ways parted, so I'll remove you as buddy."
		appService.removeBuddy(richard, buddy, message)

		when:
		def response = appService.getMessages(bob)

		then:
		response.status == 200
		def buddyDisconnectMessages = response.responseData._embedded.messages.findAll{ it."@type" == "BuddyDisconnectMessage"}
		buddyDisconnectMessages.size() == 1
		buddyDisconnectMessages[0].reason == "USER_REMOVED_BUDDY"
		buddyDisconnectMessages[0].nickname == "${richard.nickname}"
		buddyDisconnectMessages[0].message == message
		buddyDisconnectMessages[0]._links.self.href.startsWith(bob.messagesUrl)
		buddyDisconnectMessages[0]._links.process.href.startsWith(buddyDisconnectMessages[0]._links.self.href)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Bob processes the buddy removal of Richard, so Richard is removed from his buddy list'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
		analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")
		def buddy = appService.getBuddies(richard)[0]
		def message = "Bob, as you know our ways parted, so I'll remove you as buddy."
		appService.removeBuddy(richard, buddy, message)
		def disconnectMessage = appService.getMessages(bob).responseData._embedded.messages.findAll{ it."@type" == "BuddyDisconnectMessage"}[0]
		def processURL = disconnectMessage._links.process.href

		when:
		def response = appService.postMessageActionWithPassword(processURL, [ : ], bob.password)

		then:
		response.status == 200
		response.responseData._embedded.affectedMessages.size() == 1
		response.responseData._embedded.affectedMessages[0]._links.self.href == disconnectMessage._links.self.href
		response.responseData._embedded.affectedMessages[0]._links.process == null

		appService.getBuddies(bob).size() == 0

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'After Richard removed Bob as buddy, new goal conflicts are not reported to the buddies anymore'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		def buddy = appService.getBuddies(richard)[0]
		appService.removeBuddy(richard, buddy, "Bob, as you know our ways parted, so I'll remove you as buddy.")

		when:
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
		analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")

		then:
		appService.getBuddies(richard).size() == 0 // Buddy removed for Richard`
		appService.getBuddies(bob).size() == 1 // Buddy not yet removed for Bob (not processed yet)
		!appService.getBuddies(bob)[0].goals

		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].nickname == "<self>"
		richardGoalConflictMessages[0].activityCategoryName == "news"

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		def bobGoalConflictMessages = getMessagesBobResponse.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}
		bobGoalConflictMessages.size() == 1
		bobGoalConflictMessages[0].nickname == "<self>"
		bobGoalConflictMessages[0].activityCategoryName == "gambling"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}
}
