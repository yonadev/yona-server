package nu.yona.server

import groovy.json.*

class BasicBuddyTest extends AbstractAppServiceIntegrationTest
{
	def 'Hacking attempt: Try to request one-way connection'()
	{
		given:
			def richard = addRichard();
			def bob = addBob();

		when:
			def response = newAppService.requestBuddy(richard.url, """{
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
			def response = newAppService.sendBuddyConnectRequest(richard, bob)

		then:
			response.status == 201
			response.responseData._embedded.user.firstName == "Bob"
			response.responseData._links.self.href.startsWith(richard.url)

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}

	def 'Bob finds the buddy request'()
	{
		given:
			def richard = addRichard()
			def bob = addBob()
			newAppService.sendBuddyConnectRequest(richard, bob)

		when:
			def response = newAppService.getDirectMessages(bob)

		then:
			response.status == 200
			response.responseData._embedded.buddyConnectRequestMessages[0].user.firstName == "Richard"
			response.responseData._embedded.buddyConnectRequestMessages[0].nickname == richard.nickname
			response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href.startsWith(bob.url + newAppService.DIRECT_MESSAGES_PATH_FRAGMENT)
			response.responseData._embedded.buddyConnectRequestMessages[0]._links.accept.href.startsWith(response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href)

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}

	def 'Bob accepts Richard\'s buddy request'()
	{
		given:
			def richard = addRichard()
			def bob = addBob()
			newAppService.sendBuddyConnectRequest(richard, bob)
			def acceptURL = newAppService.fetchBuddyConnectRequestMessage(bob).acceptURL

		when:
			def response = newAppService.postMessageActionWithPassword(acceptURL, ["message" : "Yes, great idea!"], bob.password)

		then:
			response.status == 200
			response.responseData.properties.status == "done"

			def buddies = newAppService.getBuddies(bob);
			buddies.size() == 1
			buddies[0].user.firstName == richard.firstName
			buddies[0].nickname == richard.nickname
			buddies[0].sendingStatus == "ACCEPTED"
			buddies[0].receivingStatus == "ACCEPTED"

			def bobWithBuddy = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBoddyRequest, bob.url, true, bob.password)
			bobWithBuddy.buddies != null
			bobWithBuddy.buddies.size() == 1
			bobWithBuddy.buddies[0].user.firstName == richard.firstName
			bobWithBuddy.buddies[0].sendingStatus == "ACCEPTED"
			bobWithBuddy.buddies[0].receivingStatus == "ACCEPTED"

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}

	def 'Richard finds Bob\'s buddy connect response'()
	{
		given:
			def richard = addRichard()
			def bob = addBob()
			newAppService.sendBuddyConnectRequest(richard, bob)
			def acceptURL = newAppService.fetchBuddyConnectRequestMessage(bob).acceptURL
			newAppService.postMessageActionWithPassword(acceptURL, ["message" : "Yes, great idea!"], bob.password)

		when:
			def response = newAppService.getAnonymousMessages(richard)

		then:
			response.status == 200
			response.responseData._embedded.buddyConnectResponseMessages[0].user.firstName == "Bob"
			response.responseData._embedded.buddyConnectResponseMessages[0].nickname == bob.nickname
			response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href.startsWith(richard.url + newAppService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
			response.responseData._embedded.buddyConnectResponseMessages[0]._links.process.href.startsWith(response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href)

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}

	def 'Richard processes Bob\'s buddy acceptance'()
	{
		given:
			def richard = addRichard()
			def bob = addBob()
			newAppService.sendBuddyConnectRequest(richard, bob)
			def acceptURL = newAppService.fetchBuddyConnectRequestMessage(bob).acceptURL
			newAppService.postMessageActionWithPassword(acceptURL, ["message" : "Yes, great idea!"], bob.password)
			def processURL = newAppService.fetchBuddyConnectResponseMessage(richard).processURL

		when:
			def response = newAppService.postMessageActionWithPassword(processURL, [ : ], richard.password)

		then:
			response.status == 200
			response.responseData.properties.status == "done"

			def buddies = newAppService.getBuddies(richard);
			buddies.size() == 1
			buddies[0].user.firstName == bob.firstName
			buddies[0].nickname == bob.nickname
			buddies[0].sendingStatus == "ACCEPTED"
			buddies[0].receivingStatus == "ACCEPTED"

			def richardWithBuddy = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBoddyRequest, richard.url, true, richard.password)
			richardWithBuddy.buddies != null
			richardWithBuddy.buddies.size() == 1
			richardWithBuddy.buddies[0].user.firstName == bob.firstName
			richardWithBuddy.buddies[0].sendingStatus == "ACCEPTED"
			richardWithBuddy.buddies[0].receivingStatus == "ACCEPTED"

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}

	def 'Goal conflict of Richard is reported to Richard and Bob'()
	{
		given:
			def richardAndBob = addRichardAndBobAsBuddies()
			def richard = richardAndBob.richard
			def bob = richardAndBob.bob

		when:
			newAnalysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")

		then:
			def getAnonMessagesRichardResponse = newAppService.getAnonymousMessages(richard)
			getAnonMessagesRichardResponse.status == 200
			getAnonMessagesRichardResponse.responseData._embedded.goalConflictMessages.size() == 1
			getAnonMessagesRichardResponse.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
			getAnonMessagesRichardResponse.responseData._embedded.goalConflictMessages[0].goalName == "news"
			getAnonMessagesRichardResponse.responseData._embedded.goalConflictMessages[0].url == "http://www.refdag.nl"

			def getAnonMessagesBobResponse = newAppService.getAnonymousMessages(bob)
			getAnonMessagesBobResponse.status == 200
			getAnonMessagesBobResponse.responseData._embedded.goalConflictMessages.size() == 1
			getAnonMessagesBobResponse.responseData._embedded.goalConflictMessages[0].nickname == richard.nickname
			getAnonMessagesBobResponse.responseData._embedded.goalConflictMessages[0].goalName == "news"
			getAnonMessagesBobResponse.responseData._embedded.goalConflictMessages[0].url == null

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}

	def 'Two conflicts within the conflict interval are reported as one message for each person'()
	{
		given:
			def richardAndBob = addRichardAndBobAsBuddies()
			def richard = richardAndBob.richard
			def bob = richardAndBob.bob

		when:
			newAnalysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
			newAnalysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")

		then:
			def getAnonMessagesRichardResponse = newAppService.getAnonymousMessages(richard)
			getAnonMessagesRichardResponse.status == 200
			getAnonMessagesRichardResponse.responseData._embedded.goalConflictMessages.size() == 1

			def getAnonMessagesBobResponse = newAppService.getAnonymousMessages(bob)
			getAnonMessagesBobResponse.status == 200
			getAnonMessagesBobResponse.responseData._embedded.goalConflictMessages.size() == 1
	}

	def 'Goal conflict of Bob is reported to Richard and Bob'()
	{
		given:
			def richardAndBob = addRichardAndBobAsBuddies()
			def richard = richardAndBob.richard
			def bob = richardAndBob.bob

		when:
			newAnalysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")

		then:
			def getAnonMessagesRichardResponse = newAppService.getAnonymousMessages(richard)
			getAnonMessagesRichardResponse.status == 200
			getAnonMessagesRichardResponse.responseData._embedded.goalConflictMessages.size() == 1
			getAnonMessagesRichardResponse.responseData._embedded.goalConflictMessages[0].nickname == bob.nickname
			getAnonMessagesRichardResponse.responseData._embedded.goalConflictMessages[0].goalName == "gambling"
			getAnonMessagesRichardResponse.responseData._embedded.goalConflictMessages[0].url == null

			def getAnonMessagesBobResponse = newAppService.getAnonymousMessages(bob)
			getAnonMessagesBobResponse.status == 200
			getAnonMessagesBobResponse.responseData._embedded.goalConflictMessages.size() == 1
			getAnonMessagesBobResponse.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
			getAnonMessagesBobResponse.responseData._embedded.goalConflictMessages[0].goalName == "gambling"
			getAnonMessagesBobResponse.responseData._embedded.goalConflictMessages[0].url == "http://www.poker.com"

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}

	def 'Richard removes Bob as buddy, so goal conflicts from Bob are gone'()
	{
		given:
			def richardAndBob = addRichardAndBobAsBuddies()
			def richard = richardAndBob.richard
			def bob = richardAndBob.bob
			newAnalysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
			newAnalysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")
			def buddy = newAppService.getBuddies(richard)[0]

		when:
			def response = newAppService.removeBuddy(richard, buddy, "Bob, as you know our ways parted, so I'll remove you as buddy.")

		then:
			response.status == 200
			newAppService.getBuddies(richard).size() == 0 // Buddy removed for Richard`
			newAppService.getBuddies(bob).size() == 1 // Buddy not yet removed for Bob (not processed yet)

			def getDirectMessagesRichardResponse = newAppService.getAnonymousMessages(richard)
			getDirectMessagesRichardResponse.status == 200
			getDirectMessagesRichardResponse.responseData._embedded.buddyConnectResponseMessages == null

			def getDirectMessagesBobResponse = newAppService.getDirectMessages(bob)
			getDirectMessagesBobResponse.status == 200
			getDirectMessagesBobResponse.responseData._embedded?.buddyConnectRequestMessages == null

			def getAnonMessagesRichardResponse = newAppService.getAnonymousMessages(richard)
			getAnonMessagesRichardResponse.status == 200
			getAnonMessagesRichardResponse.responseData._embedded.goalConflictMessages.size() == 1
			getAnonMessagesRichardResponse.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
			getAnonMessagesRichardResponse.responseData._embedded.goalConflictMessages[0].goalName == "news"

			def getAnonMessagesBobResponse = newAppService.getAnonymousMessages(bob)
			getAnonMessagesBobResponse.status == 200
			getAnonMessagesBobResponse.responseData._embedded.goalConflictMessages.size() == 1
			getAnonMessagesBobResponse.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
			getAnonMessagesBobResponse.responseData._embedded.goalConflictMessages[0].goalName == "gambling"

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}

	def 'Bob receives the buddy removal of Richard'()
	{
		given:
			def richardAndBob = addRichardAndBobAsBuddies()
			def richard = richardAndBob.richard
			def bob = richardAndBob.bob
			newAnalysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
			newAnalysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")
			def buddy = newAppService.getBuddies(richard)[0]
			def message = "Bob, as you know our ways parted, so I'll remove you as buddy."
			newAppService.removeBuddy(richard, buddy, message)

		when:
			def response = newAppService.getAnonymousMessages(bob)

		then:
			response.status == 200
			response.responseData._embedded.buddyDisconnectMessages[0].reason == "USER_REMOVED_BUDDY"
			response.responseData._embedded.buddyDisconnectMessages[0].nickname == "${richard.nickname}"
			response.responseData._embedded.buddyDisconnectMessages[0].message == message
			response.responseData._embedded.buddyDisconnectMessages[0]._links.self.href.startsWith(bob.url + newAppService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
			response.responseData._embedded.buddyDisconnectMessages[0]._links.process.href.startsWith(response.responseData._embedded.buddyDisconnectMessages[0]._links.self.href)

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}

	def 'Bob processes the buddy removal of Richard, so Richard is removed from his buddy list'()
	{
		given:
			def richardAndBob = addRichardAndBobAsBuddies()
			def richard = richardAndBob.richard
			def bob = richardAndBob.bob
			newAnalysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
			newAnalysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")
			def buddy = newAppService.getBuddies(richard)[0]
			def message = "Bob, as you know our ways parted, so I'll remove you as buddy."
			newAppService.removeBuddy(richard, buddy, message)
			def processURL = newAppService.getAnonymousMessages(bob).responseData._embedded.buddyDisconnectMessages[0]._links.process.href

		when:
			def response = newAppService.postMessageActionWithPassword(processURL, [ : ], bob.password)

		then:
			response.status == 200
			newAppService.getBuddies(bob).size() == 0

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}

	def 'After Richard removed Bob as buddy, new goal conflicts are not reported to the buddies anymore'()
	{
		given:
			def richardAndBob = addRichardAndBobAsBuddies()
			def richard = richardAndBob.richard
			def bob = richardAndBob.bob
			def buddy = newAppService.getBuddies(richard)[0]
			def response = newAppService.removeBuddy(richard, buddy, "Bob, as you know our ways parted, so I'll remove you as buddy.")

		when:
			newAnalysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
			newAnalysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")

		then:
			newAppService.getBuddies(richard).size() == 0 // Buddy removed for Richard`
			newAppService.getBuddies(bob).size() == 1 // Buddy not yet removed for Bob (not processed yet)

			def getAnonMessagesRichardResponse = newAppService.getAnonymousMessages(richard)
			getAnonMessagesRichardResponse.status == 200
			getAnonMessagesRichardResponse.responseData._embedded.goalConflictMessages.size() == 1
			getAnonMessagesRichardResponse.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
			getAnonMessagesRichardResponse.responseData._embedded.goalConflictMessages[0].goalName == "news"

			def getAnonMessagesBobResponse = newAppService.getAnonymousMessages(bob)
			getAnonMessagesBobResponse.status == 200
			getAnonMessagesBobResponse.responseData._embedded.goalConflictMessages.size() == 1
			getAnonMessagesBobResponse.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
			getAnonMessagesBobResponse.responseData._embedded.goalConflictMessages[0].goalName == "gambling"

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}
}
