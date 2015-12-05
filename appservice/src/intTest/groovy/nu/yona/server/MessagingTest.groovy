package nu.yona.server

import groovy.json.*
import spock.lang.Shared

class MessagingTest extends AbstractAppServiceIntegrationTest {

	@Shared
	def richardQuin
	@Shared
	def bobDunn

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

	def 'Classification engine detects a potential conflict for Bob (second conflict message)'(){
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

	def 'Richard checks his full anonymous message list'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuin.url, richardQuin.password)

		then:
		response.status == 200
		response.responseData._links.self.href == richardQuin.url + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT + "{?page,size,sort}"
		response.responseData._embedded.buddyConnectResponseMessages
		response.responseData._embedded.buddyConnectResponseMessages.size() == 1
		response.responseData._embedded.goalConflictMessages
		response.responseData._embedded.goalConflictMessages.size() == 3
	}

	def 'Richard checks first page of 2 of his anonymous message list'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuin.url, richardQuin.password, [
			"page": 0,
			"size": 2,
			"sort": "creationTime"
		])

		then:
		response.status == 200
		response.responseData._links.self.href == richardQuin.url + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT + "?page=0&size=2&sort=creationTime"
		!response.responseData._links.prev
		response.responseData._links.next
		!response.responseData._embedded.buddyConnectResponseMessages
		response.responseData._embedded.goalConflictMessages
		response.responseData._embedded.goalConflictMessages.size() == 2
		response.responseData.page.totalElements == 4
	}

	def 'Richard checks second page of 2 of his anonymous message list'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuin.url, richardQuin.password, [
			"page": 1,
			"size": 2,
			"sort": "creationTime"
		])

		then:
		response.status == 200
		response.responseData._links.self.href == richardQuin.url + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT + "?page=1&size=2&sort=creationTime"
		response.responseData._links.prev
		!response.responseData._links.next
		response.responseData._embedded.buddyConnectResponseMessages
		response.responseData._embedded.buddyConnectResponseMessages.size() == 1
		response.responseData._embedded.goalConflictMessages
		response.responseData._embedded.goalConflictMessages.size() == 1
		response.responseData.page.totalElements == 4
	}
}
