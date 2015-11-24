package nu.yona.server

import groovy.json.*
import spock.lang.Shared
import spock.lang.Specification

class BasicBuddyTest extends Specification {

	def adminServiceBaseURL = System.properties.'yona.adminservice.url'
	def YonaServer adminService = new YonaServer(adminServiceBaseURL)

	def analysisServiceBaseURL = System.properties.'yona.analysisservice.url'
	def YonaServer analysisService = new YonaServer(analysisServiceBaseURL)

	def appServiceBaseURL = System.properties.'yona.appservice.url'
	def YonaServer appService = new YonaServer(appServiceBaseURL)
	@Shared
	def timestamp = YonaServer.getTimeStamp()

	@Shared
	def richardQuinPassword = "R i c h a r d"
	def bobDunnPassword = "B o b"
	@Shared
	def richardQuinURL
	@Shared
	def richardQuinLoginID
	@Shared
	def bobDunnURL
	@Shared
	def bobDunnLoginID
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
	def previousGoalConflictStartTimeForBob
	@Shared
	def previousGoalConflictEndTimeForBob
	@Shared
	def previousGoalConflictStartTimeForRichard
	@Shared
	def previousGoalConflictEndTimeForRichard

	def 'Add user Richard Quin'(){
		given:

		when:
			 def response = appService.addUser("""{
					"firstName":"Richard ${timestamp}",
					"lastName":"Quin ${timestamp}",
					"nickName":"RQ ${timestamp}",
					"mobileNumber":"+${timestamp}1",
					"devices":[
						"Nexus 6"
					],
					"goals":[
						"news"
					]
				}""", richardQuinPassword)
			if (response.status == 201) {
				richardQuinURL = appService.stripQueryString(response.responseData._links.self.href)
				richardQuinLoginID = response.responseData.vpnProfile.loginID;
			}

		then:
			response.status == 201
			richardQuinURL.startsWith(appServiceBaseURL + appService.USERS_PATH)

		cleanup:
			println "URL Richard: " + richardQuinURL
	}

	def 'Add user Bob Dunn'(){
		given:

		when:
			def response = appService.addUser("""{
					"firstName":"Bob ${timestamp}",
					"lastName":"Dunn ${timestamp}",
					"nickName":"BD ${timestamp}",
					"mobileNumber":"+${timestamp}2",
					"devices":[
						"iPhone 6"
					],
					"goals":[
						"gambling"
					]
				}""", bobDunnPassword)
			if (response.status == 201) {
				bobDunnURL = appService.stripQueryString(response.responseData._links.self.href)
				bobDunnLoginID = response.responseData.vpnProfile.loginID;
			}

		then:
			response.status == 201
			bobDunnURL.startsWith(appServiceBaseURL + appService.USERS_PATH)

		cleanup:
			println "URL Bob: " + bobDunnURL
	}

	def 'Richard requests Bob to become his buddy'(){
		given:

		when:
			def response = appService.requestBuddy(richardQuinURL, """{
					"_embedded":{
						"user":{
							"firstName":"Bob ${timestamp}",
							"lastName":"Dun ${timestamp}",
							"emailAddress":"bob${timestamp}@dunn.net",
							"mobileNumber":"+${timestamp}2"
						}
					},
					"message":"Would you like to be my buddy?"
				}""", richardQuinPassword)
			richardQuinBobBuddyURL = response.responseData._links.self.href

		then:
			response.status == 201
			response.responseData._embedded.user.firstName == "Bob ${timestamp}"
			richardQuinBobBuddyURL.startsWith(richardQuinURL)

		cleanup:
			println "URL buddy Richard: " + richardQuinBobBuddyURL
	}

	def 'Bob checks his direct messages'(){
		given:

		when:
			def response = appService.getDirectMessages(bobDunnURL, bobDunnPassword)
			if (response.responseData._embedded && response.responseData._embedded.buddyConnectRequestMessages) {
				bobDunnBuddyMessageAcceptURL = response.responseData._embedded.buddyConnectRequestMessages[0]._links.accept.href
			}

		then:
			response.status == 200
			response.responseData._links.self.href == bobDunnURL + appService.DIRECT_MESSAGE_PATH_FRAGMENT
			response.responseData._embedded.buddyConnectRequestMessages[0].user.firstName == "Richard ${timestamp}"
			response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href.startsWith(response.responseData._links.self.href)
			bobDunnBuddyMessageAcceptURL.startsWith(response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href)
	}

	def 'Bob accepts Richard\'s buddy request'(){
		given:

		when:
			def response = appService.postMessageActionWithPassword(bobDunnBuddyMessageAcceptURL, """{
					"properties":{
						"message":"Yes, great idea!"
					}
				}""", bobDunnPassword)

		then:
			response.status == 200
			response.responseData.properties.status == "done"
	}

	def 'Richard checks his direct messages'(){
		given:

		when:
			def response = appService.getDirectMessages(richardQuinURL, richardQuinPassword)
			if (response.responseData._embedded && response.responseData._embedded.buddyConnectResponseMessages) {
				richardQuinBuddyMessageProcessURL = response.responseData._embedded.buddyConnectResponseMessages[0]._links.process.href
			}

		then:
			response.status == 200
			response.responseData._links.self.href == richardQuinURL + appService.DIRECT_MESSAGE_PATH_FRAGMENT
			response.responseData._embedded.buddyConnectResponseMessages[0].user.firstName == "Bob ${timestamp}"
			response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href.startsWith(response.responseData._links.self.href)
			richardQuinBuddyMessageProcessURL.startsWith(response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href)
	}

	def 'Richard processes Bob\'s buddy acceptance'(){
		given:

		when:
			def response = appService.postMessageActionWithPassword(richardQuinBuddyMessageProcessURL, """{
					"properties":{
					}
				}""", richardQuinPassword)

		then:
			response.status == 200
			response.responseData.properties.status == "done"
	}

	def 'Richard checks his buddy list and will find Bob there'(){
		given:

		when:
			def response = appService.getBuddies(richardQuinURL, richardQuinPassword);

		then:
			 response.status == 200
			response.responseData._embedded.buddies.size() == 1
			response.responseData._embedded.buddies[0]._embedded.user.firstName == "Bob ${timestamp}"
	}

	def 'Bob checks his buddy list and will find Richard there'(){
		given:

		when:
			def response = appService.getBuddies(bobDunnURL, bobDunnPassword);

		then:
			response.status == 200
			response.responseData._embedded.buddies.size() == 1
			response.responseData._embedded.buddies[0]._embedded.user.firstName == "Richard ${timestamp}"
	}

	def 'When Richard would retrieve his user, Bob would be embedded as buddy'(){
		given:

		when:
			def response = appService.getUser(richardQuinURL, true, richardQuinPassword)

		then:
			response.status == 200
			response.responseData._embedded.buddies != null
			response.responseData._embedded.buddies.size() == 1
			response.responseData._embedded.buddies[0]._embedded.user.firstName == "Bob ${timestamp}"
	}

	def 'Richard checks he has no anonymous messages'(){
		given:

		when:
			def response = appService.getAnonymousMessages(richardQuinURL, richardQuinPassword)

		then:
			response.status == 200
			response.responseData._embedded == null || response.responseData._embedded.goalConflictMessages == null
	}

	def 'Bob checks he has no anonymous messages'(){
		given:

		when:
			def response = appService.getAnonymousMessages(bobDunnURL, bobDunnPassword)

		then:
			response.status == 200
			response.responseData._embedded == null || response.responseData._embedded.goalConflictMessages == null
	}

	def 'Classification engine detects a potential conflict for Richard'(){
		given:

		when:
			def response = analysisService.postToAnalysisEngine("""{
				"loginID":"${richardQuinLoginID}",
				"categories": ["news/media"],
				"url":"http://www.refdag.nl"
				}""")

		then:
			response.status == 200
	}

	def 'Bob checks he has anonymous messages and finds a conflict for Richard'(){
		given:

		when:
			def response = appService.getAnonymousMessages(bobDunnURL, bobDunnPassword)
			previousGoalConflictStartTimeForBob = response.responseData?._embedded?.goalConflictMessages[0]?.creationTime
			previousGoalConflictEndTimeForBob = response.responseData?._embedded?.goalConflictMessages[0]?.endTime

		then:
			response.status == 200
			response.responseData._embedded.goalConflictMessages.size() == 1
			response.responseData._embedded.goalConflictMessages[0].nickname == "RQ ${timestamp}"
			response.responseData._embedded.goalConflictMessages[0].goalName == "news"
			response.responseData._embedded.goalConflictMessages[0].url =~ /refdag/
			previousGoalConflictStartTimeForBob != null
			previousGoalConflictEndTimeForBob != null
	}

	def 'Richard checks he has anonymous messages and finds a conflict for himself'(){
		given:

		when:
			def response = appService.getAnonymousMessages(richardQuinURL, richardQuinPassword)
			previousGoalConflictStartTimeForRichard = response.responseData?._embedded?.goalConflictMessages[0]?.creationTime
			previousGoalConflictEndTimeForRichard = response.responseData?._embedded?.goalConflictMessages[0]?.endTime

		then:
			response.status == 200
			response.responseData._embedded.goalConflictMessages.size() == 1
			response.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
			response.responseData._embedded.goalConflictMessages[0].goalName == "news"
			response.responseData._embedded.goalConflictMessages[0].url =~ /refdag/
			previousGoalConflictStartTimeForRichard != null
			previousGoalConflictEndTimeForRichard != null
	}

	def 'Classification engine detects a potential conflict for Richard (second conflict message)'(){
		given:

		when:
			def response = analysisService.postToAnalysisEngine("""{
				"loginID":"${richardQuinLoginID}",
				"categories": ["news/media"],
				"url":"http://www.refdag.nl"
				}""")

		then:
			response.status == 200
	}

	def 'Bob checks he has anonymous messages and finds a conflict for Richard (second conflict message)'(){
		given:

		when:
			def response = appService.getAnonymousMessages(bobDunnURL, bobDunnPassword)
			def goalConflictStartTime = response.responseData?._embedded?.goalConflictMessages[0]?.creationTime
			def goalConflictEndTime = response.responseData?._embedded?.goalConflictMessages[0]?.endTime

		then:
			response.status == 200
			response.responseData._embedded.goalConflictMessages.size() == 1
			response.responseData._embedded.goalConflictMessages[0].nickname == "RQ ${timestamp}"
			response.responseData._embedded.goalConflictMessages[0].goalName == "news"
			response.responseData._embedded.goalConflictMessages[0].url =~ /refdag/
			goalConflictStartTime != null
			goalConflictEndTime != null
			goalConflictStartTime == previousGoalConflictStartTimeForBob
			goalConflictEndTime >= previousGoalConflictEndTimeForBob
	}

	def 'Richard checks he has anonymous messages and finds a conflict for himself (second conflict message)'(){
		given:

		when:
			def response = appService.getAnonymousMessages(richardQuinURL, richardQuinPassword)
			def goalConflictStartTime = response.responseData?._embedded?.goalConflictMessages[0]?.creationTime
			def goalConflictEndTime = response.responseData?._embedded?.goalConflictMessages[0]?.endTime

		then:
			response.status == 200
			response.responseData._embedded.goalConflictMessages.size() == 1
			response.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
			response.responseData._embedded.goalConflictMessages[0].goalName == "news"
			response.responseData._embedded.goalConflictMessages[0].url =~ /refdag/
			goalConflictStartTime != null
			goalConflictEndTime != null
			goalConflictStartTime == previousGoalConflictStartTimeForRichard
			goalConflictEndTime >= previousGoalConflictEndTimeForRichard
	}

	def 'Bob requests Richard to become his buddy (automatic pairing)'(){
		given:

		when:
			def response = appService.requestBuddy(bobDunnURL, """{
					"_embedded":{
						"user":{
							"firstName":"Richard ${timestamp}",
							"lastName":"Quin ${timestamp}",
							"emailAddress":"rich${timestamp}@quin.net",
							"mobileNumber":"+${timestamp}1",
						}
					},
					"message":"Would you like to be my buddy?"
				}""", bobDunnPassword)
			bobDunnRichardBuddyURL = response.responseData._links.self.href

		then:
			response.status == 201
			response.responseData._embedded.user.firstName == "Richard ${timestamp}"
			bobDunnRichardBuddyURL.startsWith(bobDunnURL)

		cleanup:
			println "URL buddy Richard: " + bobDunnRichardBuddyURL
	}

	def 'Richard checks his direct messages (automatic pairing)'(){
		given:

		when:
			def response = appService.getDirectMessages(richardQuinURL, richardQuinPassword)
			if (response.responseData._embedded && response.responseData._embedded.buddyConnectRequestMessages) {
				richardQuinBuddyMessageAcceptURL = response.responseData._embedded.buddyConnectRequestMessages[0]._links.accept.href
			}

		then:
			response.status == 200
			response.responseData._links.self.href == richardQuinURL + appService.DIRECT_MESSAGE_PATH_FRAGMENT
			response.responseData._embedded.buddyConnectRequestMessages[0].user
			response.responseData._embedded.buddyConnectRequestMessages[0].user.firstName == "Bob ${timestamp}"
			response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href.startsWith(response.responseData._links.self.href)
			richardQuinBuddyMessageAcceptURL.startsWith(response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href)
	}

	def 'Richard accepts Bob\'s buddy request (automatic pairing)'(){
		given:

		when:
			def response = appService.postMessageActionWithPassword(richardQuinBuddyMessageAcceptURL, """{
					"properties":{
						"message":"Yes, great idea!"
					}
				}""", richardQuinPassword)

		then:
			response.status == 200
			response.responseData.properties.status == "done"
	}

	def 'Bob checks his direct messages (automatic pairing)'(){
		given:

		when:
			def response = appService.getDirectMessages(bobDunnURL, bobDunnPassword)
			if (response.responseData._embedded && response.responseData._embedded.buddyConnectResponseMessages) {
				bobDunnBuddyMessageProcessURL = response.responseData._embedded.buddyConnectResponseMessages[0]._links.process.href
			}

		then:
			response.status == 200
			response.responseData._links.self.href == bobDunnURL + appService.DIRECT_MESSAGE_PATH_FRAGMENT
			response.responseData._embedded.buddyConnectResponseMessages[0].user
			response.responseData._embedded.buddyConnectResponseMessages[0].user.firstName == "Richard ${timestamp}"
			response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href.startsWith(response.responseData._links.self.href)
			bobDunnBuddyMessageProcessURL.startsWith(response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href)
	}

	def 'Bob processes Richard\'s buddy acceptance (automatic pairing)'(){
		given:

		when:
			def response = appService.postMessageActionWithPassword(bobDunnBuddyMessageProcessURL, """{
					"properties":{
					}
				}""", bobDunnPassword)

		then:
			response.status == 200
			response.responseData.properties.status == "done"
	}

	def 'Classification engine detects a potential conflict for Bob'(){
		given:

		when:
			def response = analysisService.postToAnalysisEngine("""{
				"loginID":"${bobDunnLoginID}",
				"categories": ["Gambling"],
				"url":"http://www.poker.com"
				}""")

		then:
			response.status == 200
	}

	def 'Richard checks he has anonymous messages and finds a conflict for Bob'(){
		given:

		when:
			def response = appService.getAnonymousMessages(richardQuinURL, richardQuinPassword)
			previousGoalConflictStartTimeForRichard = response.responseData?._embedded?.goalConflictMessages[0]?.creationTime
			previousGoalConflictEndTimeForRichard = response.responseData?._embedded?.goalConflictMessages[0]?.endTime

		then:
			response.status == 200
			response.responseData._embedded.goalConflictMessages.size() == 2
			response.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
			response.responseData._embedded.goalConflictMessages[0].goalName == "news"
			response.responseData._embedded.goalConflictMessages[0].url =~ /refdag/
			response.responseData._embedded.goalConflictMessages[1].nickname == "BD ${timestamp}"
			response.responseData._embedded.goalConflictMessages[1].goalName == "gambling"
			response.responseData._embedded.goalConflictMessages[1].url =~ /poker/
			previousGoalConflictStartTimeForRichard != null
			previousGoalConflictEndTimeForRichard != null
	}

	def 'Bob checks he has anonymous messages and finds a conflict for himself'(){
		given:

		when:
			def response = appService.getAnonymousMessages(bobDunnURL, bobDunnPassword)
			previousGoalConflictStartTimeForBob = response.responseData?._embedded?.goalConflictMessages[0]?.creationTime
			previousGoalConflictEndTimeForBob = response.responseData?._embedded?.goalConflictMessages[0]?.endTime

		then:
			response.status == 200
			response.responseData._embedded.goalConflictMessages.size() == 2
			response.responseData._embedded.goalConflictMessages[0].nickname == "RQ ${timestamp}"
			response.responseData._embedded.goalConflictMessages[0].goalName == "news"
			response.responseData._embedded.goalConflictMessages[0].url =~ /refdag/
			response.responseData._embedded.goalConflictMessages[1].nickname == "<self>"
			response.responseData._embedded.goalConflictMessages[1].goalName == "gambling"
			response.responseData._embedded.goalConflictMessages[1].url =~ /poker/
			previousGoalConflictStartTimeForBob != null
			previousGoalConflictEndTimeForBob != null
	}

	def 'Classification engine detects a potential conflict for Bob (second conflict message)'(){
		given:

		when:
			def response = analysisService.postToAnalysisEngine("""{
				"loginID":"${bobDunnLoginID}",
				"categories": ["Gambling"],
				"url":"http://www.poker.com"
				}""")

		then:
			response.status == 200
	}

	def 'Richard checks he has anonymous messages and finds a conflict for Bob (second conflict message)'(){
		given:

		when:
			def response = appService.getAnonymousMessages(richardQuinURL, richardQuinPassword)
			def goalConflictStartTime = response.responseData?._embedded?.goalConflictMessages[0]?.creationTime
			def goalConflictEndTime = response.responseData?._embedded?.goalConflictMessages[0]?.endTime

		then:
			response.status == 200
			response.responseData._embedded.goalConflictMessages.size() == 2
			response.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
			response.responseData._embedded.goalConflictMessages[0].goalName == "news"
			response.responseData._embedded.goalConflictMessages[0].url =~ /refdag/
			response.responseData._embedded.goalConflictMessages[1].nickname == "BD ${timestamp}"
			response.responseData._embedded.goalConflictMessages[1].goalName == "gambling"
			response.responseData._embedded.goalConflictMessages[1].url =~ /poker/
			goalConflictStartTime != null
			goalConflictEndTime != null
			goalConflictStartTime == previousGoalConflictStartTimeForRichard
			goalConflictEndTime >= previousGoalConflictEndTimeForRichard
	}

	def 'Bob checks he has anonymous messages and finds a conflict for himself (second conflict message)'(){
		given:

		when:
			def response = appService.getAnonymousMessages(bobDunnURL, bobDunnPassword)
			def goalConflictStartTime = response.responseData?._embedded?.goalConflictMessages[0]?.creationTime
			def goalConflictEndTime = response.responseData?._embedded?.goalConflictMessages[0]?.endTime

		then:
			response.status == 200
			response.responseData._embedded.goalConflictMessages.size() == 2
			response.responseData._embedded.goalConflictMessages[0].nickname == "RQ ${timestamp}"
			response.responseData._embedded.goalConflictMessages[0].goalName == "news"
			response.responseData._embedded.goalConflictMessages[0].url =~ /refdag/
			response.responseData._embedded.goalConflictMessages[1].nickname == "<self>"
			response.responseData._embedded.goalConflictMessages[1].goalName == "gambling"
			response.responseData._embedded.goalConflictMessages[1].url =~ /poker/
			goalConflictStartTime != null
			goalConflictEndTime != null
			goalConflictStartTime == previousGoalConflictStartTimeForBob
			goalConflictEndTime >= previousGoalConflictEndTimeForBob
	}
}
