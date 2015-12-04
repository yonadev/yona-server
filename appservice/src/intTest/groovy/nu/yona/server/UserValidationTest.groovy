package nu.yona.server

import groovy.json.*
import spock.lang.Specification

/**
 * These tests are to make sure the data validation of the user service is working. All fields are to be checked
 * and validated when adding a user
 *
 * @author pgussow
 */
class UserValidationTest extends Specification {

	def appServiceBaseURL = System.properties.'yona.appservice.url'
	def YonaServer appService = new YonaServer(appServiceBaseURL)
	def timestamp = appService.getTimeStamp()
	def jsonSlurper = new JsonSlurper()
	def userCreationJSON = """{
				"firstName":"First ${timestamp}",
				"lastName":"Doe ${timestamp}",
				"mobileNumber":"+${timestamp}",
				"devices":[
					"Galaxy mini"
				],
				"goals":[
					"gambling"
				]}"""
	def password = "John Doe"

	def 'AddUser - empty first name'(){
		when:
			def object = jsonSlurper.parseText(userCreationJSON)
			object.remove('firstName')
			def response = appService.addUser(object, password)

		then:
			response.status == 400
			response.responseData.type == "ERROR"
			response.responseData.code == "error.user.firstname"
	}

	def 'AddUser - empty last name'(){
		when:
			def object = jsonSlurper.parseText(userCreationJSON)
			object.remove('lastName')
			def response = appService.addUser(object, password)

		then:
			response.status == 400
			response.responseData.type == "ERROR"
			response.responseData.code == "error.user.lastname"
	}

	def 'AddUser - empty mobile number'(){
		when:
			def object = jsonSlurper.parseText(userCreationJSON)
			object.remove('mobileNumber')
			def response = appService.addUser(object, password)

		then:
			response.status == 400
			response.responseData.type == "ERROR"
			response.responseData.code == "error.user.mobile.number"
	}

	def 'AddUser - invalid mobile number'(){
		when:
			def object = jsonSlurper.parseText(userCreationJSON)
			object.put('mobileNumber', '++55 5 ')
			def response = appService.addUser(object, password)

		then:
			response.status == 400
			response.responseData.type == "ERROR"
			response.responseData.code == "error.user.mobile.number.invalid"
	}
}
