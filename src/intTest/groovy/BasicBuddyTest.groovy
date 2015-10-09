import groovyx.net.http.RESTClient
import spock.lang.Ignore
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import groovy.json.*

class RestSpecification extends Specification {

	def baseURL = "http://localhost:8080"
	def goalsPath = "/goals/"
	def usersPath = "/users/"
	def analysisEnginePath = "/analysisEngine/"
	def buddiesPathFragment = "/buddies/"
	def directMessagesPathFragment = "/messages/direct/"
	def anonymousMessagesPathFragment = "/messages/anonymous/"
	@Shared
	def gamblingURL
	@Shared
	def programmingURL
	def richardQuinPassword = "R i c h a r d"
	def bobDunnPassword = "B o b"
	@Shared
	def richardQuinURL
	@Shared
	def richardQuinUsername
	@Shared
	def bobDunnURL
	@Shared
	def bobDunnUsername
	@Shared
	def richardQuinBobBuddyURL
	@Shared
	def bobDunnBuddyMessageAcceptURL
	@Shared
	def richardQuinBuddyMessageProcessURL 
	JsonSlurper jsonSlurper = new JsonSlurper()
	RESTClient restClient = new RESTClient(baseURL)

	def 'Add goal Gambling'(){
		given:

		when:
			gamblingURL = addGoal("""{
				"name":"gambling",
				"categories":[
					"poker",
					"lotto"
				]
			}""")

		then:
			gamblingURL.startsWith(baseURL + goalsPath)
	}

	def 'Add goal Programming'(){
		given:

		when:
			programmingURL = addGoal("""{
				"name":"programming",
				"categories":[
					"Java",
					"C++"
				]
			}""")

		then:
			programmingURL.startsWith(baseURL + goalsPath)
	}

	def 'Get all goals'(){
		given:

		when:
			def goals = getAllGoals()

		then:
			goals._links.self.href == baseURL + goalsPath
			goals._embedded.Goals.size() == 2
			
	}

	def 'Add user Richard Quin'(){
		given:

		when:
			def responseData = addUser("""{
				"firstName":"Richard",
				"lastName":"Quin",
				"nickName":"RQ",
				"emailAddress":"rich@quin.net",
				"mobileNumber":"+12345678",
				"devices":[
					"Nexus 6"
				],
				"goals":[
					"gambling"
				]
			}""", richardQuinPassword)
			richardQuinURL = stripQueryString(responseData._links.self.href)
			richardQuinUsername = responseData.vpnProfile.username;

		then:
			richardQuinURL.startsWith(baseURL + usersPath)

		cleanup:
			println "URL Richard: " + richardQuinURL
	}

	def 'Add user Bob Dunn'(){
		given:

		when:
			def responseData = addUser("""{
				"firstName":"Bob",
				"lastName":"Dunn",
				"nickName":"BD",
				"emailAddress":"bob@dunn.net",
				"mobileNumber":"+13456789",
				"devices":[
					"iPhone 6"
				],
				"goals":[
					"programming"
				]
			}""", bobDunnPassword)
			bobDunnURL = stripQueryString(responseData._links.self.href)
			bobDunnUsername = responseData.vpnProfile.username;

		then:
			bobDunnURL.startsWith(baseURL + usersPath)

		cleanup:
			println "URL Bob: " + bobDunnURL
	}

	def 'Richard requests Bob to become his buddy'(){
		given:

		when:
			def buddy = requestBuddy(richardQuinURL, """{
				"_embedded":{
					"user":{
						"firstName":"Bob",
						"lastName":"Dunn",
						"emailAddress":"bob@dunn.net",
						"mobileNumber":"+13456789"
					}
				},
				"message":"Would you like to be my buddy?",
			}""", richardQuinPassword)
			richardQuinBobBuddyURL = buddy._links.self.href

		then:
			buddy._embedded.user.firstName == "Bob"
			richardQuinBobBuddyURL.startsWith(richardQuinURL)

		cleanup:
			println "URL buddy Richard: " + richardQuinBobBuddyURL
	}

	def 'Bob checks his direct messages'(){
		given:

		when:
			def responseData = getDirectMessages(bobDunnURL, bobDunnPassword)
			bobDunnBuddyMessageAcceptURL = responseData._embedded.buddyConnectRequestMessages[0]._links.accept.href

		then:
			responseData._links.self.href == bobDunnURL + directMessagesPathFragment
			responseData._embedded.buddyConnectRequestMessages[0].requestingUser.firstName == "Richard"
			responseData._embedded.buddyConnectRequestMessages[0]._links.self.href.startsWith(responseData._links.self.href)
			bobDunnBuddyMessageAcceptURL.startsWith(responseData._embedded.buddyConnectRequestMessages[0]._links.self.href)
	}

	def 'Bob accepts Richard\'s buddy request'(){
		given:

		when:
			def responseData = postMessageActionWithPassword(bobDunnBuddyMessageAcceptURL, """{
				"properties":{
					"message":"Yes, great idea!"
				}
			}""", bobDunnPassword)

		then:
			responseData.properties.status == "done"
	}

	def 'Richard checks his direct messages'(){
		given:

		when:
			def responseData = getDirectMessages(richardQuinURL, richardQuinPassword)
			richardQuinBuddyMessageProcessURL = responseData._embedded.buddyConnectResponseMessages[0]._links.process.href

		then:
			responseData._links.self.href == richardQuinURL + directMessagesPathFragment
			responseData._embedded.buddyConnectResponseMessages[0].respondingUser.firstName == "Bob"
			responseData._embedded.buddyConnectResponseMessages[0]._links.self.href.startsWith(responseData._links.self.href)
			richardQuinBuddyMessageProcessURL.startsWith(responseData._embedded.buddyConnectResponseMessages[0]._links.self.href) 
	}

	def 'Richard processes Bob\'s buddy acceptance'(){
		given:

		when:
			def responseData = postMessageActionWithPassword(richardQuinBuddyMessageProcessURL, """{
				"properties":{
				}
			}""", richardQuinPassword)

		then:
			responseData.properties.status == "done"
	}

	def 'Richard checks he has no anonymous messages'(){
		given:

		when:
			def responseData = getAnonymousMessages(richardQuinURL, richardQuinPassword)

		then:
			responseData._embedded == null
	}

	def 'Bob checks he has no anonymous messages'(){
		given:

		when:
			def responseData = getAnonymousMessages(bobDunnURL, bobDunnPassword)

		then:
			responseData._embedded == null
	}

	def 'Classification engine detects a potential conflict for Richard'(){
		given:

		when:
			def response = postToAnalysisEngine("""{
			"accessorID":"${richardQuinUsername}",
			"category":"poker",
			"url":"http://www.poker.com"
			}""")

		then:
			response.status == 200
	}

	def 'Bob checks he has anonymous messages and finds a conflict for Richard'(){
		given:

		when:
			def responseData = getAnonymousMessages(bobDunnURL, bobDunnPassword)

		then:
			responseData._embedded.goalConflictMessages.size() == 1
			responseData._embedded.goalConflictMessages[0].nickname == "RQ"
			responseData._embedded.goalConflictMessages[0].goalName == "gambling"
			responseData._embedded.goalConflictMessages[0].url =~ /poker/
	}

	def 'Richard checks he has anonymous messages and finds a conflict for himself'(){
		given:

		when:
			def responseData = getAnonymousMessages(richardQuinURL, richardQuinPassword)

		then:
			responseData._embedded.goalConflictMessages.size() == 1
			responseData._embedded.goalConflictMessages[0].nickname == "<self>"
			responseData._embedded.goalConflictMessages[0].goalName == "gambling"
			responseData._embedded.goalConflictMessages[0].url =~ /poker/
	}

	def addGoal(jsonString)
	{
		def responseData = createResource(goalsPath, jsonString)
		responseData._links.self.href
	}

	def addUser(jsonString, password)
	{
		createResourceWithPassword(usersPath, jsonString, password)
	}

	def requestBuddy(userPath, jsonString, password)
	{
		def responseData = createResourceWithPassword(userPath + buddiesPathFragment, jsonString, password)
	}

	def getAllGoals()
	{
		def responseData = getResource(goalsPath)
		responseData
	}

	def getDirectMessages(userPath, password)
	{
		def responseData = getResourceWithPassword(userPath + directMessagesPathFragment, password)
		responseData
	}

	def getAnonymousMessages(userPath, password)
	{
		def responseData = getResourceWithPassword(userPath + anonymousMessagesPathFragment, password)
		responseData
	}

	def createResourceWithPassword(path, jsonString, password)
	{
		createResource(path, jsonString, ["Yona-Password": password])
	}

	def createResource(path, jsonString, headers = [:])
	{
		def response = postJson(path, jsonString, headers);
		assert response.status == 201
		response.responseData
	}

	def postJson(path, jsonString, headers = [:])
	{
		def object = jsonSlurper.parseText(jsonString)
		def response = restClient.post(path: path,
			body: object,
			contentType:'application/json',
			headers: headers)
		response
	}

	def getResourceWithPassword(path, password)
	{
		getResource(path, ["Yona-Password": password])
	}

	def getResource(path, headers = [:])
	{
		def response = restClient.get(path: path,
			contentType:'application/json',
			headers: headers)
		assert response.status == 200
		response.responseData
	}

	def postMessageActionWithPassword(path, jsonString, password)
	{
		postMessageAction(path, jsonString, ["Yona-Password": password])
	}

	def postMessageAction(path, jsonString, headers = [:])
	{
		def response = postJson(path, jsonString, headers);
		assert response.status == 200
		response.responseData
	}

	def postToAnalysisEngine(jsonString)
	{
		postJson(analysisEnginePath, jsonString);
	}

	def stripQueryString(url)
	{
		url - ~/\?.*/
	}
}
