package nu.yona.server

import groovyx.net.http.RESTClient
import spock.lang.Ignore
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import groovy.json.*

class UserTest extends Specification {

	def baseURL = "http://localhost:8080"
	def YonaServer yonaServer = new YonaServer(baseURL)
	def timestamp = yonaServer.getTimeStamp()

	def 'Create John Doe'(){
		given:

		when:
			def response = yonaServer.addUser("""{
				"firstName":"John",
				"lastName":"Doe ${timestamp}",
				"nickName":"JD ${timestamp}",
				"emailAddress":"john${timestamp}@hotmail.com",
				"mobileNumber":"+${timestamp}",
				"devices":[
					"Galaxy mini"
				],
				"goals":[
					"gambling"
				]
			}""", "John Doe")

		then:
			response.status == 201
			testUser(response.responseData)
	}

	void testUser(responseData)
	{
		assert responseData.firstName == "John"
		assert responseData.lastName == "Doe ${timestamp}"
		assert responseData.nickName == "JD ${timestamp}"
		assert responseData.emailAddress == "john${timestamp}@hotmail.com"
		assert responseData.mobileNumber == "+${timestamp}"
		assert responseData.devices.size() == 1
		assert responseData.devices[0] == "Galaxy mini"
		assert responseData.goals.size() == 1
		assert responseData.goals[0] == "gambling"
	}
}
