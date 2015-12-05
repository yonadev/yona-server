package nu.yona.server

import groovy.json.*
import spock.lang.Shared

class RemoveUserTest extends AbstractAppServiceIntegrationTest {

	@Shared
	def richardQuin
	@Shared
	def bobDunn
	@Shared
	def bobDunnBuddyRemoveMessageProcessURL

	def 'Add Richard and Bob who are buddies'(){
		given:

		when:
		richardQuin = addUser("Richard", "Quin")
		bobDunn = addUser("Bob", "Dunn")
		makeBuddies(richardQuin, bobDunn)

		then:
		richardQuin
		bobDunn
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

	def 'Richard deletes his account'() {
		given:
		when:
		def response = appService.deleteUser(richardQuin.url, richardQuin.password, "Goodbye friends! I deinstalled the Internet")

		then:
		response.status == 200
	}

	def 'Test what happens if the classification engine detects a potential conflict for Bob (second conflict message) when the buddy disconnect is not yet processed'(){
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
	
	def 'Test what happens if Bob checks his buddy list when the buddy disconnect is not yet processed'(){
		given:

		when:
			def response = appService.getBuddies(bobDunnURL, bobDunnPassword);

		then:
			response.status == 200
	}

	def 'Bob checks his anonymous messages and will find a remove buddy message'(){
		given:

		when:
		def response = appService.getAnonymousMessages(bobDunn.url, bobDunn.password)
		if (response.responseData._embedded && response.responseData._embedded.buddyDisconnectMessages) {
			bobDunnBuddyRemoveMessageProcessURL = response.responseData._embedded.buddyDisconnectMessages[0]._links.process.href
		}

		then:
		response.status == 200
		response.responseData._embedded.buddyDisconnectMessages[0].reason == "USER_ACCOUNT_DELETED"
		response.responseData._embedded.buddyDisconnectMessages[0].message == "Goodbye friends! I deinstalled the Internet"
		response.responseData._embedded.buddyDisconnectMessages[0].nickname == richardQuin.nickname
		response.responseData._embedded.buddyDisconnectMessages[0]._links.self.href.startsWith(bobDunn.url + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
		bobDunnBuddyRemoveMessageProcessURL.startsWith(response.responseData._embedded.buddyDisconnectMessages[0]._links.self.href)
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

	def 'Bob checks his buddy list and will not find Richard there anymore'(){
		given:

		when:
		def response = appService.getBuddies(bobDunn.url, bobDunn.password);

		then:
		response.status == 200
		response.responseData._embedded == null
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

	def 'Bob checks his direct messages and the messages of Richard are no longer there'(){
		given:

		when:
		def response = appService.getDirectMessages(bobDunn.url, bobDunn.password)

		then:
		response.status == 200
		response.responseData._embedded == null || response.responseData._embedded.buddyConnectRequestMessages == null
	}
}
