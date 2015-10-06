import groovyx.net.http.RESTClient
import spock.lang.Ignore
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import groovy.json.*

class RestSpecification extends Specification {

	def baseURL = "http://localhost:8080"
	def goalPath = "/goal"
	def baseUserPath = "/user"
	@Shared
	def gamblingURL
	@Shared
	def programmingURL
	def richardQuinPassword = "s e c r e t"
	def bobDunnPassword = "u_n_k_n_o_w_n"
	@Shared
	def richardQuinURL
	@Shared
	def bobDunnURL
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
			gamblingURL.startsWith(baseURL + goalPath)
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
			programmingURL.startsWith(baseURL + goalPath)
	}

	def 'Add user Richard Quin'(){
		given:

		when:
			richardQuinURL = addUser("""{
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

		then:
			richardQuinURL.startsWith(baseURL + baseUserPath)
	}

	def 'Add user Bob Dunn'(){
		given:

		when:
			bobDunnURL = addUser("""{
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

		then:
			bobDunnURL.startsWith(baseURL + baseUserPath)
	}

	def 'Richard requests Bob to become his buddy'(){
		given:

		when:
			richardQuinBobBuddyURL = requestBuddy(richardQuinURL, """{
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

		then:
			richardQuinBobBuddyURL.startsWith(richardQuinURL)
	}

	def 'Bob checks his direct messages'(){
		given:

		when:
			def message = getDirectMessages(bobDunnURL, bobDunnPassword)
			bobDunnBuddyMessageAcceptURL = message._embedded.buddyConnectRequestMessages[0]._links.accept.href

		then:
			message._links.self.href.startsWith(bobDunnURL)
			message._embedded.buddyConnectRequestMessages[0].requestingUser.firstName == "Richard"
			bobDunnBuddyMessageAcceptURL.startsWith(message._embedded.buddyConnectRequestMessages[0]._links.self.href)
	}

	def 'Bob accepts Richard\'s buddy request'(){
		given:

		when:
			def message = postMessageActionWithPassword(bobDunnBuddyMessageAcceptURL, """{
				"properties":{
					"message":"Yes, great idea!"
				}
			}""", bobDunnPassword)

		then:
			message.properties.status == "done"
	}

	def 'Richard checks his direct messages'(){
		given:

		when:
			def message = getDirectMessages(richardQuinURL, richardQuinPassword)
			richardQuinBuddyMessageProcessURL = message._embedded.buddyConnectResponseMessages[0]._links.process.href

		then:
			message._links.self.href.startsWith(richardQuinURL)
			message._embedded.buddyConnectResponseMessages[0].respondingUser.firstName == "Bob"
			richardQuinBuddyMessageProcessURL.startsWith(message._embedded.buddyConnectResponseMessages[0]._links.self.href) 
	}

	def 'Richard processes Bob\'s buddy acceptance'(){
		given:

		when:
			def message = postMessageActionWithPassword(richardQuinBuddyMessageProcessURL, """{
				"properties":{
				}
			}""", richardQuinPassword)

		then:
			message.properties.status == "done"
	}

	def addGoal(jsonString)
	{
		def responseData = createResource(goalPath, jsonString)
		responseData._links.self.href
	}

	def addUser(jsonString, password)
	{
		def responseData = createResourceWithPassword(baseUserPath, jsonString, password)
		stripQueryString(responseData._links.self.href)
	}

	def requestBuddy(userPath, jsonString, password)
	{
		def responseData = createResourceWithPassword(userPath + "/buddy", jsonString, password)
		responseData._links.self.href
	}

	def getDirectMessages(userPath, password)
	{
		def responseData = getResourceWithPassword(userPath + "/message/direct/", password)
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

	def stripQueryString(url)
	{
		url - ~/\?.*/
	}
}
