package nu.yona.server

import groovyx.net.http.RESTClient
import groovyx.net.http.HttpResponseException
import spock.lang.Ignore
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import groovy.json.*

class UserValidationTest extends Specification {

	def appServiceBaseURL = System.properties.'yona.appservice.url'
	def YonaServer appService = new YonaServer(appServiceBaseURL)
	def timestamp = appService.getTimeStamp()
    def jsonSlurper = new JsonSlurper()
    def userCreationJSON = """{
                "firstName":"First ${timestamp}",
                "lastName":"Doe ${timestamp}",
                "nickName":"JD ${timestamp}",
                "emailAddress":"john${timestamp}@hotmail.com",
                "mobileNumber":"+${timestamp}",
                "devices":[
                    "Galaxy mini"
                ],
                "goals":[
                    "gambling"
                ]}"""
	def password = "John Doe"

	def 'Create - empty first name'(){
		when:
            def object = jsonSlurper.parseText(userCreationJSON)
			def response = appService.addUser(object, password)

		then:
            response.status == 400
            response.responseData.type == "ERROR"
            response.responseData.code == "error.user.firstname"
	}

	void testUser(responseData, includePrivateData)
	{
		assert responseData.firstName == "John"
		assert responseData.lastName == "Doe ${timestamp}"
		assert responseData.emailAddress == "john${timestamp}@hotmail.com"
		assert responseData.mobileNumber == "+${timestamp}"
		if (includePrivateData) {
			assert responseData.nickName == "JD ${timestamp}"
			assert responseData.devices.size() == 1
			assert responseData.devices[0] == "Galaxy mini"
			assert responseData.goals.size() == 1
			assert responseData.goals[0] == "gambling"
			
			assert responseData._embedded.buddies != null
			assert responseData._embedded.buddies.size() == 0
		} else {
			assert responseData.nickName == null
			assert responseData.devices == null
			assert responseData.goals == null
		}
	}

	void verifyUserDoesNotExist(userURL)
	{
		try {
			def response = appService.getUser(userURL, false)
			assert false;
		} catch (HttpResponseException e) {
			assert e.statusCode == 404
		}
	}
}
