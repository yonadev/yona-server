package nu.yona.server

import groovy.json.*
import spock.lang.Shared

class BasicBuddyTest extends AbstractAppServiceIntegrationTest {

	@Shared
	def richardQuin
	@Shared
	def bobDunn
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
	def bobDunnBuddyRemoveMessageProcessURL

	def 'Add user Richard'(){
		given:

		when:
		richardQuin = addUser("Richard", "Quin")

		then:
		richardQuin
	}

	def 'Add user Bob'(){
		given:

		when:
		bobDunn = addUser("Bob", "Dunn")

		then:
		bobDunn
	}

	def 'Hacking attempt: Try to request one-way connection'(){
		given:

		when:
		def response = appService.requestBuddy(richardQuin.url, """{
					"_embedded":{
						"user":{
							"firstName":"Bob ${timestamp}",
							"lastName":"Dun ${timestamp}",
							"emailAddress":"bob${timestamp}@dunn.net",
							"mobileNumber":"${bobDunn.mobileNumber}"
						}
					},
					"message":"Would you like to be my buddy?",
					"sendingStatus":"NOT_REQUESTED",
					"receivingStatus":"REQUESTED"
				}""", richardQuin.password)

		then:
		response.status == 400
	}

	def 'Richard requests Bob to become his buddy'(){
		given:

		when:
		def response = appService.requestBuddy(richardQuin.url, """{
					"_embedded":{
						"user":{
							"mobileNumber":"${bobDunn.mobileNumber}"
						}
					},
					"message":"Would you like to be my buddy?",
					"sendingStatus":"REQUESTED",
					"receivingStatus":"REQUESTED"
				}""", richardQuin.password)
		richardQuinBobBuddyURL = response.responseData._links.self.href

		then:
		response.status == 201
		response.responseData._embedded.user.firstName == "Bob"
		richardQuinBobBuddyURL.startsWith(richardQuin.url)

		cleanup:
		println "URL buddy Richard: " + richardQuinBobBuddyURL
	}

	def 'Bob checks his direct messages'(){
		given:

		when:
		def response = appService.getDirectMessages(bobDunn.url, bobDunn.password)
		if (response.responseData._embedded && response.responseData._embedded.buddyConnectRequestMessages) {
			bobDunnBuddyMessageAcceptURL = response.responseData._embedded.buddyConnectRequestMessages[0]._links.accept.href
		}

		then:
		response.status == 200
		response.responseData._embedded.buddyConnectRequestMessages[0].user.firstName == "Richard"
		response.responseData._embedded.buddyConnectRequestMessages[0].nickname == richardQuin.nickname
		response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href.startsWith(bobDunn.url + appService.DIRECT_MESSAGES_PATH_FRAGMENT)
		response.responseData._embedded.buddyConnectRequestMessages[0].status == "REQUESTED"
		bobDunnBuddyMessageAcceptURL.startsWith(response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href)
	}

	def 'Bob accepts Richard\'s buddy request'(){
		given:

		when:
		def response = appService.postMessageActionWithPassword(bobDunnBuddyMessageAcceptURL, """{
					"properties":{
						"message":"Yes, great idea!"
					}
				}""", bobDunn.password)

		then:
		response.status == 200
		response.responseData.properties.status == "done"
	}

	def 'Richard checks his anonymous messages'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuin.url, richardQuin.password)
		if (response.responseData._embedded && response.responseData._embedded.buddyConnectResponseMessages) {
			richardQuinBuddyMessageProcessURL = response.responseData._embedded.buddyConnectResponseMessages[0]._links.process.href
		}

		then:
		response.status == 200
		response.responseData._embedded.buddyConnectResponseMessages[0].status == "ACCEPTED"
		response.responseData._embedded.buddyConnectResponseMessages[0].user.firstName == "Bob"
		response.responseData._embedded.buddyConnectResponseMessages[0].nickname == bobDunn.nickname
		response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href.startsWith(richardQuin.url + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
		richardQuinBuddyMessageProcessURL.startsWith(response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href)
	}

	def 'Richard processes Bob\'s buddy acceptance'(){
		given:

		when:
		def response = appService.postMessageActionWithPassword(richardQuinBuddyMessageProcessURL, """{
					"properties":{
					}
				}""", richardQuin.password)

		then:
		response.status == 200
		response.responseData.properties.status == "done"
	}

	def 'Richard checks his buddy list and will find Bob there'(){
		given:

		when:
		def response = appService.getBuddies(richardQuin.url, richardQuin.password);

		then:
		response.status == 200
		response.responseData._embedded.buddies.size() == 1
		response.responseData._embedded.buddies[0]._embedded.user.firstName == "Bob"
		response.responseData._embedded.buddies[0].nickname == bobDunn.nickname
		response.responseData._embedded.buddies[0].sendingStatus == "ACCEPTED"
		response.responseData._embedded.buddies[0].receivingStatus == "ACCEPTED"
	}

	def 'Bob checks his buddy list and will find Richard there'(){
		given:

		when:
		def response = appService.getBuddies(bobDunn.url, bobDunn.password);

		then:
		response.status == 200
		response.responseData._embedded.buddies.size() == 1
		response.responseData._embedded.buddies[0]._embedded.user.firstName == "Richard"
		response.responseData._embedded.buddies[0].nickname == richardQuin.nickname
		response.responseData._embedded.buddies[0].sendingStatus == "ACCEPTED"
		response.responseData._embedded.buddies[0].receivingStatus == "ACCEPTED"
	}

	def 'When Richard would retrieve his user, Bob would be embedded as buddy'(){
		given:

		when:
		def response = appService.getUser(richardQuin.url, true, richardQuin.password)

		then:
		response.status == 200
		response.responseData._embedded.buddies != null
		response.responseData._embedded.buddies.size() == 1
		response.responseData._embedded.buddies[0]._embedded.user.firstName == "Bob"
		response.responseData._embedded.buddies[0].nickname == bobDunn.nickname
	}

	def 'Richard checks he has no anonymous messages'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuin.url, richardQuin.password)

		then:
		response.status == 200
		response.responseData._embedded == null || response.responseData._embedded.goalConflictMessages == null
	}

	def 'Bob checks he has no anonymous messages'(){
		given:

		when:
		def response = appService.getAnonymousMessages(bobDunn.url, bobDunn.password)

		then:
		response.status == 200
		response.responseData._embedded == null || response.responseData._embedded.goalConflictMessages == null
	}

	def 'Classification engine detects a potential conflict for Richard'(){
		given:

		when:
		def response = analysisService.postToAnalysisEngine("""{
				"vpnLoginID":"${richardQuin.vpnLoginID}",
				"categories": ["news/media"],
				"url":"http://www.refdag.nl"
				}""")

		then:
		response.status == 200
	}

	def 'Bob checks he has anonymous messages and finds a conflict for Richard'(){
		given:

		when:
		def response = appService.getAnonymousMessages(bobDunn.url, bobDunn.password)

		then:
		response.status == 200
		response.responseData._embedded.goalConflictMessages.size() == 1
		response.responseData._embedded.goalConflictMessages[0].nickname == "${richardQuin.nickname}"
		response.responseData._embedded.goalConflictMessages[0].goalName == "news"
		response.responseData._embedded.goalConflictMessages[0].url == null
		response.responseData._embedded.goalConflictMessages[0]._links.self.href.startsWith(bobDunn.url + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
	}

	def 'Richard checks he has anonymous messages and finds a conflict for himself'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuin.url, richardQuin.password)

		then:
		response.status == 200
		response.responseData._embedded.goalConflictMessages.size() == 1
		response.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
		response.responseData._embedded.goalConflictMessages[0].goalName == "news"
		response.responseData._embedded.goalConflictMessages[0].url =~ /refdag/
		response.responseData._embedded.goalConflictMessages[0]._links.self.href.startsWith(richardQuin.url + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
	}

	def 'Classification engine detects a potential conflict for Richard (second conflict message)'(){
		given:

		when:
		def response = analysisService.postToAnalysisEngine("""{
				"vpnLoginID":"${richardQuin.vpnLoginID}",
				"categories": ["news/media"],
				"url":"http://www.refdag.nl"
				}""")

		then:
		response.status == 200
	}

	def 'Bob checks he has anonymous messages and finds a conflict for Richard (second conflict message)'(){
		given:

		when:
		def response = appService.getAnonymousMessages(bobDunn.url, bobDunn.password)

		then:
		response.status == 200
		response.responseData._embedded.goalConflictMessages.size() == 1
		response.responseData._embedded.goalConflictMessages[0].nickname == "${richardQuin.nickname}"
		response.responseData._embedded.goalConflictMessages[0].goalName == "news"
		response.responseData._embedded.goalConflictMessages[0].url == null
	}

	def 'Richard checks he has anonymous messages and finds a conflict for himself (second conflict message)'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuin.url, richardQuin.password)

		then:
		response.status == 200
		response.responseData._embedded.goalConflictMessages.size() == 1
		response.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
		response.responseData._embedded.goalConflictMessages[0].goalName == "news"
		response.responseData._embedded.goalConflictMessages[0].url =~ /refdag/
	}

	def 'Classification engine detects a potential conflict for Bob'(){
		given:

		when:
		def response = analysisService.postToAnalysisEngine("""{
				"vpnLoginID":"${bobDunn.vpnLoginID}",
				"categories": ["Gambling"],
				"url":"http://www.poker.com"
				}""")

		then:
		response.status == 200
	}

	def 'Richard checks he has anonymous messages and finds a conflict for Bob'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuin.url, richardQuin.password)

		then:
		response.status == 200
		response.responseData._embedded.goalConflictMessages.size() == 2
		response.responseData._embedded.goalConflictMessages[0].nickname == "${bobDunn.nickname}"
		response.responseData._embedded.goalConflictMessages[0].goalName == "gambling"
		response.responseData._embedded.goalConflictMessages[0].url == null
		response.responseData._embedded.goalConflictMessages[1].nickname == "<self>"
		response.responseData._embedded.goalConflictMessages[1].goalName == "news"
		response.responseData._embedded.goalConflictMessages[1].url =~ /refdag/
	}

	def 'Bob checks he has anonymous messages and finds a conflict for himself'(){
		given:

		when:
		def response = appService.getAnonymousMessages(bobDunn.url, bobDunn.password)

		then:
		response.status == 200
		response.responseData._embedded.goalConflictMessages.size() == 2
		response.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
		response.responseData._embedded.goalConflictMessages[0].goalName == "gambling"
		response.responseData._embedded.goalConflictMessages[0].url =~ /poker/
		response.responseData._embedded.goalConflictMessages[1].nickname == "${richardQuin.nickname}"
		response.responseData._embedded.goalConflictMessages[1].goalName == "news"
		response.responseData._embedded.goalConflictMessages[1].url == null
	}

	def 'Classification engine detects a potential conflict for Bob (second conflict message)'(){
		given:

		when:
		def response = analysisService.postToAnalysisEngine("""{
				"vpnLoginID":"${bobDunn.vpnLoginID}",
				"categories": ["Gambling"],
				"url":"http://www.poker.com"
				}""")

		then:
		response.status == 200
	}

	def 'Richard checks he has anonymous messages and finds a conflict for Bob (second conflict message)'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuin.url, richardQuin.password)

		then:
		response.status == 200
		response.responseData._embedded.goalConflictMessages.size() == 2
		response.responseData._embedded.goalConflictMessages[0].nickname == "${bobDunn.nickname}"
		response.responseData._embedded.goalConflictMessages[0].goalName == "gambling"
		response.responseData._embedded.goalConflictMessages[0].url == null
		response.responseData._embedded.goalConflictMessages[0]._links.requestDisclosure
		response.responseData._embedded.goalConflictMessages[1].nickname == "<self>"
		response.responseData._embedded.goalConflictMessages[1].goalName == "news"
		response.responseData._embedded.goalConflictMessages[1].url =~ /refdag/
		!response.responseData._embedded.goalConflictMessages[1]._links.requestDisclosure
	}

	def 'Bob checks he has anonymous messages and finds a conflict for himself (second conflict message)'(){
		given:

		when:
		def response = appService.getAnonymousMessages(bobDunn.url, bobDunn.password)

		then:
		response.status == 200
		response.responseData._embedded.goalConflictMessages.size() == 2
		response.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
		response.responseData._embedded.goalConflictMessages[0].goalName == "gambling"
		response.responseData._embedded.goalConflictMessages[0].url =~ /poker/
		response.responseData._embedded.goalConflictMessages[1].nickname == "${richardQuin.nickname}"
		response.responseData._embedded.goalConflictMessages[1].goalName == "news"
		response.responseData._embedded.goalConflictMessages[1].url == null
	}

	def 'Richard removes Bob as buddy'() {
		given:
		when:
		def response = appService.removeBuddy(richardQuinBobBuddyURL, richardQuin.password, "Bob, as you know our ways parted so I'll remove you as a buddy.")

		then:
		response.status == 200
	}

	def 'Bob checks his direct messages and will find a remove buddy message'(){
		given:

		when:
		def response = appService.getAnonymousMessages(bobDunn.url, bobDunn.password)
		if (response.responseData._embedded && response.responseData._embedded.buddyDisconnectMessages) {
			bobDunnBuddyRemoveMessageProcessURL = response.responseData._embedded.buddyDisconnectMessages[0]._links.process.href
		}

		then:
		response.status == 200
		response.responseData._embedded.buddyDisconnectMessages[0].reason == "USER_REMOVED_BUDDY"
		response.responseData._embedded.buddyDisconnectMessages[0].nickname == "${richardQuin.nickname}"
		response.responseData._embedded.buddyDisconnectMessages[0].message == "Bob, as you know our ways parted so I'll remove you as a buddy."
		response.responseData._embedded.buddyDisconnectMessages[0]._links.self.href.startsWith(bobDunn.url + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
		bobDunnBuddyRemoveMessageProcessURL.startsWith(response.responseData._embedded.buddyDisconnectMessages[0]._links.self.href)
	}

	def 'Test what happens if the classification engine detects a potential conflict for Bob (third conflict message) when the buddy disconnect is not yet processed'(){
		given:

		when:
		def response = analysisService.postToAnalysisEngine("""{
				"vpnLoginID":"${bobDunn.vpnLoginID}",
				"categories": ["news/media"],
				"url":"http://www.refdag.nl"
				}""")

		then:
		response.status == 200
	}

	def 'Bob checks his anonymous messages and the messages of Richard are no longer there'(){
		given:

		when:
		def response = appService.getAnonymousMessages(bobDunn.url, bobDunn.password)

		then:
		response.status == 200
		response.responseData._embedded.goalConflictMessages.size() == 2
		response.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
		response.responseData._embedded.goalConflictMessages[0].goalName == "news"
		response.responseData._embedded.goalConflictMessages[0].url =~ /refdag/
		response.responseData._embedded.goalConflictMessages[1].nickname == "<self>"
		response.responseData._embedded.goalConflictMessages[1].goalName == "gambling"
		response.responseData._embedded.goalConflictMessages[1].url =~ /poker/
	}

	def 'Richard checks his anonymous messages and the messages of Bob are no longer there'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuin.url, richardQuin.password)

		then:
		response.status == 200
		response.responseData._embedded.goalConflictMessages.size() == 1
		response.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
		response.responseData._embedded.goalConflictMessages[0].goalName == "news"
		response.responseData._embedded.goalConflictMessages[0].url =~ /refdag/
	}

	def 'Bob checks his direct messages and the messages of Richard are no longer there'(){
		given:

		when:
		def response = appService.getDirectMessages(bobDunn.url, bobDunn.password)

		then:
		response.status == 200
		response.responseData._embedded == null || response.responseData._embedded.buddyConnectRequestMessages == null
	}

	def 'Richard checks his direct messages and the messages of Bob are no longer there'(){
		given:

		when:
		def response = appService.getDirectMessages(richardQuin.url, richardQuin.password)

		then:
		response.status == 200
		response.responseData._embedded == null || response.responseData._embedded.buddyConnectRequestMessages == null
	}

	def 'Bob processes the remove buddy message'(){
		given:

		when:
		def response = appService.postMessageActionWithPassword(bobDunnBuddyRemoveMessageProcessURL, """{
					"properties":{
					}
				}""", bobDunn.password)

		then:
		response.status == 200
		response.responseData.properties.status == "done"
	}

	def 'Richard checks his buddy list and Bob is no longer there'(){
		given:

		when:
		def response = appService.getBuddies(richardQuin.url, richardQuin.password);

		then:
		response.status == 200
		response.responseData._embedded == null
	}

	def 'Bob checks his buddy list and Richard is no longer there'(){
		given:

		when:
		def response = appService.getBuddies(bobDunn.url, bobDunn.password);

		then:
		response.status == 200
		response.responseData._embedded == null
	}
}
