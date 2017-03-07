/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*
import nu.yona.server.test.User

/**
 * These tests are to make sure the data validation of the user service is working. All fields are to be checked
 * and validated when adding a user
 */
class BuddyValidationTest extends AbstractAppServiceIntegrationTest
{

	def jsonSlurper = new JsonSlurper()
	def userCreationJson = """{
				"firstName":"John",
				"lastName":"Doe",
				"mobileNumber":"+${timestamp}"
				}"""
	def password = "John Doe"

	def 'AddBuddy - empty first name'()
	{
		given:
		User richard = addRichard();
		
		when:
		def object = jsonSlurper.parseText(userCreationJson)
		object.remove('firstName')
		def response = appService.sendBuddyConnectRequest(richard, object, false)

		then:
		response.status == 400
		response.responseData.code == "error.user.firstname"
		
		cleanup:
		appService.deleteUser(richard)
	}

	def 'AddBuddy - empty last name'()
	{
		given:
		User richard = addRichard();
		
		when:
		def object = jsonSlurper.parseText(userCreationJson)
		object.remove('lastName')
		def response = appService.sendBuddyConnectRequest(richard, object, false)

		then:
		response.status == 400
		response.responseData.code == "error.user.lastname"
		
		cleanup:
		appService.deleteUser(richard)
	}

	def 'AddBuddy - empty mobile number'()
	{
		given:
		User richard = addRichard();
		
		when:
		def object = jsonSlurper.parseText(userCreationJson)
		object.remove('mobileNumber')
		def response = appService.sendBuddyConnectRequest(richard, object, false)

		then:
		response.status == 400
		response.responseData.code == "error.user.mobile.number"
		
		cleanup:
		appService.deleteUser(richard)
	}

	def 'AddBuddy - invalid mobile number'()
	{
		given:
		User richard = addRichard();
		
		when:
		def object = jsonSlurper.parseText(userCreationJson)
		object.put('mobileNumber', '++55 5 ')
		def response = appService.sendBuddyConnectRequest(richard, object, false)

		then:
		response.status == 400
		response.responseData.code == "error.user.mobile.number.invalid"
		
		cleanup:
		appService.deleteUser(richard)
	}
	
	def 'Try Richard request himself as buddy'()
	{
		given:
		User richard = addRichard();

		when:
		def response = appService.sendBuddyConnectRequest(richard, richard, false)

		then:
		response.status == 400
		response.responseData.code == "error.buddy.cannot.invite.self"
		
		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try Richard request Bob as buddy twice'()
	{
		given:
		User richard = addRichard();
		User bob = addBob()
		appService.sendBuddyConnectRequest(richard, bob)
		
		when:
		def response = appService.sendBuddyConnectRequest(richard, bob, false)

		then:
		response.status == 400
		response.responseData.code == "error.buddy.cannot.invite.existing.buddy"
		
		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

}
