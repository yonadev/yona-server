package nu.yona.server

import groovy.json.*
import spock.lang.Shared

class DeleteDirectMessageTest extends AbstractAppServiceIntegrationTest {

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
	def bobDunnBuddyConnectRequestDeleteURL
	@Shared
	def bobDunnBuddyMessageProcessURL
	@Shared
	def richardQuinBuddyMessageAcceptURL
	@Shared
	def richardQuinBuddyMessageProcessURL
	@Shared
	def richardQuinGoalConflictMessageDeleteUrl
	@Shared
	def richardQuinBuddyMessageDeleteURL

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
			bobDunnBuddyConnectRequestDeleteURL = response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href
		}

		then:
		response.status == 200
		response.responseData._embedded.buddyConnectRequestMessages[0].user.firstName == "Richard"
		response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href.startsWith(bobDunn.url + appService.DIRECT_MESSAGES_PATH_FRAGMENT)
		bobDunnBuddyMessageAcceptURL.startsWith(response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href)
		bobDunnBuddyConnectRequestDeleteURL != null
	}

	def 'Bob tries to delete Richard\'s buddy request before it is processed'(){
		given:

		when:
		def response = appService.deleteResourceWithPassword(bobDunnBuddyConnectRequestDeleteURL, bobDunn.password)

		then:
		response.status == 400
		response.responseData?.code == "error.cannot.delete.unprocessed.message"
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
			richardQuinBuddyMessageDeleteURL = response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href
		}

		then:
		response.status == 200
		response.responseData._embedded.buddyConnectResponseMessages[0].user.firstName == "Bob"
		response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href.startsWith(richardQuin.url + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
		richardQuinBuddyMessageProcessURL.startsWith(response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href)
		richardQuinBuddyMessageDeleteURL != null
	}

	def 'Richard tries to delete Bob\'s buddy acceptance before it is processed'(){
		given:

		when:
		def response = appService.deleteResourceWithPassword(richardQuinBuddyMessageDeleteURL, richardQuin.password)

		then:
		response.status == 400
		response.responseData?.code == "error.cannot.delete.unprocessed.message"
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

	def 'Richard checks he has no conflict messages'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuin.url, richardQuin.password)

		then:
		response.status == 200
		response.responseData._embedded == null || response.responseData._embedded.goalConflictMessages == null
	}

	def 'Bob checks he has no conflict messages'(){
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
		response.responseData._embedded.goalConflictMessages[0].nickname == richardQuin.nickname
		response.responseData._embedded.goalConflictMessages[0].goalName == "news"
		response.responseData._embedded.goalConflictMessages[0].url == null
	}

	def 'Richard checks he has anonymous messages and finds a conflict for himself'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuin.url, richardQuin.password)
		richardQuinGoalConflictMessageDeleteUrl = response.responseData?._embedded?.goalConflictMessages[0]._links?.self?.href

		then:
		response.status == 200
		response.responseData._embedded.goalConflictMessages.size() == 1
		response.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
		response.responseData._embedded.goalConflictMessages[0].goalName == "news"
		response.responseData._embedded.goalConflictMessages[0].url =~ /refdag/
		richardQuinGoalConflictMessageDeleteUrl != null
	}

	def 'Richard deletes his own goal conflict message'(){
		given:

		when:
		def response = appService.deleteResourceWithPassword(richardQuinGoalConflictMessageDeleteUrl, richardQuin.password)

		then:
		response.status == 200
	}

	def 'Richard checks he no longer has any anonymous messages'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuin.url, richardQuin.password)

		then:
		response.status == 200
		response.responseData._embedded == null || response.responseData._embedded.goalConflictMessages == null
	}

	def 'Bob checks he still has a goal conflict message for Richard'(){
		given:

		when:
		def response = appService.getAnonymousMessages(bobDunn.url, bobDunn.password)

		then:
		response.status == 200
		response.responseData._embedded.goalConflictMessages.size() == 1
		response.responseData._embedded.goalConflictMessages[0].nickname == richardQuin.nickname
		response.responseData._embedded.goalConflictMessages[0].goalName == "news"
		response.responseData._embedded.goalConflictMessages[0].url == null
	}

	def 'Bob deletes Richard\'s buddy request'(){
		given:

		when:
		def response = appService.deleteResourceWithPassword(bobDunnBuddyConnectRequestDeleteURL, bobDunn.password)

		then:
		response.status == 200
	}

	def 'Bob checks his direct messages to see that the buddy request message was deleted'(){
		given:

		when:
		def response = appService.getDirectMessages(bobDunn.url, bobDunn.password)

		then:
		response.status == 200
		response.responseData._embedded?.buddyConnectRequestMessages?.length ?: 0 == 0
	}

	def 'Richard deletes Bob\'s buddy acceptance message'(){
		given:

		when:
		def response = appService.deleteResourceWithPassword(richardQuinBuddyMessageDeleteURL, richardQuin.password)

		then:
		response.status == 200
	}

	def 'Richard checks his direct messages to see that the buddy acceptance message was deleted'(){
		given:

		when:
		def response = appService.getDirectMessages(richardQuin.url, richardQuin.password)

		then:
		response.status == 200
		response.responseData._embedded?.buddyConnectRequestMessages?.length ?: 0 == 0
	}
}
