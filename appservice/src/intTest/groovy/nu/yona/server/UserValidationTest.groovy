/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

/**
 * These tests are to make sure the data validation of the user service is working. All fields are to be checked
 * and validated when adding a user
 */
class UserValidationTest extends AbstractAppServiceIntegrationTest
{

	def jsonSlurper = new JsonSlurper()
	def userCreationJSON = """{
				"firstName":"John",
				"lastName":"Doe",
				"nickname":"JD",
				"mobileNumber":"+${timestamp}",
				"devices":[
					"Galaxy mini"
				]}"""
	def password = "John Doe"

	def 'AddUser - empty first name'()
	{
		when:
		def object = jsonSlurper.parseText(userCreationJSON)
		object.remove('firstName')
		def response = appService.addUser(object, password)

		then:
		response.status == 400
		response.responseData.code == "error.user.firstname"
	}

	def 'AddUser - empty last name'()
	{
		when:
		def object = jsonSlurper.parseText(userCreationJSON)
		object.remove('lastName')
		def response = appService.addUser(object, password)

		then:
		response.status == 400
		response.responseData.code == "error.user.lastname"
	}

	def 'AddUser - empty nickname'()
	{
		when:
		def object = jsonSlurper.parseText(userCreationJSON)
		object.remove('nickname')
		def response = appService.addUser(object, password)

		then:
		response.status == 400
		response.responseData.code == "error.user.nickname"
	}

	def 'AddUser - empty mobile number'()
	{
		when:
		def object = jsonSlurper.parseText(userCreationJSON)
		object.remove('mobileNumber')
		def response = appService.addUser(object, password)

		then:
		response.status == 400
		response.responseData.code == "error.user.mobile.number"
	}

	def 'AddUser - invalid mobile number'()
	{
		when:
		def object = jsonSlurper.parseText(userCreationJSON)
		object.put('mobileNumber', '++55 5 ')
		def response = appService.addUser(object, password)

		then:
		response.status == 400
		response.responseData.code == "error.user.mobile.number.invalid"
	}
}
