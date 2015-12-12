package nu.yona.server

import groovy.json.*
import spock.lang.Shared

class DisclosureTest extends AbstractAppServiceIntegrationTest {

	@Shared
	def richardQuin
	@Shared
	def bobDunn
	@Shared
	def richardQuinGoalConflictMessage1URL
	@Shared
	def disclosureRequest1URL
	@Shared
	def disclosureRequest2URL
	@Shared
	def goalDiscloseRequestMessage1AcceptURL
	@Shared
	def goalDiscloseRequestMessage2RejectURL

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

	def 'Classification engine detects 2 potential conflicts for Richard'(){
		given:

		when:
		def response1 = analysisService.postToAnalysisEngine("""{
					"vpnLoginID":"${richardQuin.vpnLoginID}",
					"categories": ["news/media"],
					"url":"http://www.refdag.nl"
					}""")
		def response2 = analysisService.postToAnalysisEngine("""{
					"vpnLoginID":"${richardQuin.vpnLoginID}",
					"categories": ["Gambling"],
					"url":"http://www.poker.com"
					}""")

		then:
		response1.status == 200
		response2.status == 200
	}

	def 'Bob checks he has anonymous messages and finds 2 conflicts for Richard'(){
		given:

		when:
		def response = appService.getAnonymousMessages(bobDunn.url, bobDunn.password)
		if(response.status == 200 && response.responseData._embedded.goalConflictMessages) {
			disclosureRequest1URL = response.responseData._embedded.goalConflictMessages[0]._links.requestDisclosure.href
			disclosureRequest2URL = response.responseData._embedded.goalConflictMessages[1]._links.requestDisclosure.href
		}

		then:
		response.status == 200
		response.responseData._embedded.goalConflictMessages.size() == 2
		response.responseData._embedded.goalConflictMessages[0].nickname == richardQuin.nickname
		response.responseData._embedded.goalConflictMessages[0].goalName == "gambling"
		response.responseData._embedded.goalConflictMessages[0].url == null
		response.responseData._embedded.goalConflictMessages[0]._links.requestDisclosure
		response.responseData._embedded.goalConflictMessages[1].nickname == richardQuin.nickname
		response.responseData._embedded.goalConflictMessages[1].goalName == "news"
		response.responseData._embedded.goalConflictMessages[1].url == null
		response.responseData._embedded.goalConflictMessages[1]._links.requestDisclosure
	}

	def 'Richard does not have disclosure request links on his own goal conflict messages'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuin.url, richardQuin.password)
		if(response.status == 200 && response.responseData._embedded.goalConflictMessages) {
			richardQuinGoalConflictMessage1URL = response.responseData._embedded.goalConflictMessages[0]._links.self.href
		}

		then:
		response.status == 200
		response.responseData._embedded.goalConflictMessages.size() == 2
		response.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
		response.responseData._embedded.goalConflictMessages[0].goalName == "gambling"
		response.responseData._embedded.goalConflictMessages[0].url == "http://www.poker.com"
		response.responseData._embedded.goalConflictMessages[0].endTime != null
		!response.responseData._embedded.goalConflictMessages[0]._links.requestDisclosure
		response.responseData._embedded.goalConflictMessages[1].nickname == "<self>"
		response.responseData._embedded.goalConflictMessages[1].goalName == "news"
		response.responseData._embedded.goalConflictMessages[1].url == "http://www.refdag.nl"
		response.responseData._embedded.goalConflictMessages[].endTime != null
		!response.responseData._embedded.goalConflictMessages[1]._links.requestDisclosure
	}

	def 'Bob asks for disclosure of both'(){
		given:

		when:
		def response1 = appService.postMessageActionWithPassword(disclosureRequest1URL, """{
						"properties":{
						}
					}""", bobDunn.password)
		def response2 = appService.postMessageActionWithPassword(disclosureRequest2URL, """{
						"properties":{
						}
					}""", bobDunn.password)

		then:
		response1.status == 200
		response2.status == 200
	}

	def 'Richard checks his anonymous messages and finds the disclosure request'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuin.url, richardQuin.password)
		if(response.status == 200 && response.responseData._embedded.discloseRequestMessages) {
			goalDiscloseRequestMessage1AcceptURL = response.responseData._embedded.discloseRequestMessages[0]._links.accept.href
			goalDiscloseRequestMessage2RejectURL = response.responseData._embedded.discloseRequestMessages[1]._links.reject.href
		}

		then:
		response.status == 200
		response.responseData._embedded.discloseRequestMessages
		response.responseData._embedded.discloseRequestMessages.size() == 2
		response.responseData._embedded.discloseRequestMessages[1].nickname == bobDunn.nickname
		response.responseData._embedded.discloseRequestMessages[1].targetGoalConflictMessage
		response.responseData._embedded.discloseRequestMessages[1].targetGoalConflictMessage.goalName == "gambling"
		//TODO response.responseData._embedded.discloseRequestMessages[1].targetGoalConflictMessage.creationTime
		response.responseData._embedded.discloseRequestMessages[1]._links.related.href == richardQuinGoalConflictMessage1URL
		response.responseData._embedded.discloseRequestMessages[1].status == "DISCLOSE_REQUESTED"
		goalDiscloseRequestMessage1AcceptURL
		goalDiscloseRequestMessage2RejectURL
	}

	def 'Richard accepts the disclosure request of 1 and rejects of 2'(){
		given:

		when:
		def response1 = appService.postMessageActionWithPassword(goalDiscloseRequestMessage1AcceptURL, """{
						"properties":{
						}
					}""", richardQuin.password)
		def response2 = appService.postMessageActionWithPassword(goalDiscloseRequestMessage2RejectURL, """{
						"properties":{
						}
					}""", richardQuin.password)

		then:
		response1.status == 200
		response2.status == 200
	}

	def 'Bob checks he has anonymous messages and finds the URL of the first disclosed and the second denied'(){
		given:

		when:
		def response = appService.getAnonymousMessages(bobDunn.url, bobDunn.password)

		then:
		response.status == 200
		response.responseData._embedded.goalConflictMessages.size() == 2
		response.responseData._embedded.goalConflictMessages[0].nickname == richardQuin.nickname
		response.responseData._embedded.goalConflictMessages[0].goalName == "gambling"
		response.responseData._embedded.goalConflictMessages[0].url == null
		response.responseData._embedded.goalConflictMessages[0].status == "DISCLOSE_REJECTED"
		!response.responseData._embedded.goalConflictMessages[0]._links.requestDisclosure
		response.responseData._embedded.goalConflictMessages[1].nickname == richardQuin.nickname
		response.responseData._embedded.goalConflictMessages[1].goalName == "news"
		response.responseData._embedded.goalConflictMessages[1].url == "http://www.refdag.nl"
		response.responseData._embedded.goalConflictMessages[1].status == "DISCLOSE_ACCEPTED"
		!response.responseData._embedded.goalConflictMessages[1]._links.requestDisclosure
		response.responseData._embedded.discloseResponseMessages.size() == 2
		response.responseData._embedded.discloseResponseMessages[0].status == "DISCLOSE_REJECTED"
		response.responseData._embedded.discloseResponseMessages[1].status == "DISCLOSE_ACCEPTED"
	}
}
