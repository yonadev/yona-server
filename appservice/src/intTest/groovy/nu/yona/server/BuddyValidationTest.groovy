/*******************************************************************************
 * Copyright (c) 2022 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.assertResponseStatus

import nu.yona.server.test.User

/**
 * These tests are to make sure the data validation of the user service is working. All fields are to be checked
 * and validated when adding a user*/
class BuddyValidationTest extends AbstractAppServiceIntegrationTest
{

	def userCreationJson = makeUserCreationJson()

	def makeUserCreationJson()
	{
		def json = [:]
		json.firstName = "John"
		json.lastName = "Doe"
		json.mobileNumber = "${makeMobileNumber(timestamp)}"
		json.emailAddress = "john@doe.com"
		return json
	}

	def 'AddBuddy - empty first name'()
	{
		given:
		User richard = addRichard()

		when:
		userCreationJson.remove('firstName')
		def response = appService.sendBuddyConnectRequest(richard, userCreationJson, false)

		then:
		assertResponseStatus(response, 400)
		response.json.code == "error.user.firstname"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'AddBuddy - empty last name'()
	{
		given:
		User richard = addRichard()

		when:
		userCreationJson.remove('lastName')
		def response = appService.sendBuddyConnectRequest(richard, userCreationJson, false)

		then:
		assertResponseStatus(response, 400)
		response.json.code == "error.user.lastname"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'AddBuddy - empty mobile number'()
	{
		given:
		User richard = addRichard()

		when:
		userCreationJson.remove('mobileNumber')
		def response = appService.sendBuddyConnectRequest(richard, userCreationJson, false)

		then:
		assertResponseStatus(response, 400)
		response.json.code == "error.user.mobile.number"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'AddBuddy - invalid mobile number'()
	{
		given:
		User richard = addRichard()

		when:
		userCreationJson.put('mobileNumber', '++55 5 ')
		def response = appService.sendBuddyConnectRequest(richard, userCreationJson, false)

		then:
		assertResponseStatus(response, 400)
		response.json.code == "error.user.mobile.number.invalid"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'AddBuddy - empty email address'()
	{
		given:
		User richard = addRichard()

		when:
		userCreationJson.remove('emailAddress')
		def response = appService.sendBuddyConnectRequest(richard, userCreationJson, false)

		then:
		assertResponseStatus(response, 400)
		response.json.code == "error.user.email.address"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'AddBuddy - invalid email address'()
	{
		given:
		User richard = addRichard()

		when:
		userCreationJson.put('emailAddress', 'a@b')
		def response1 = appService.sendBuddyConnectRequest(richard, userCreationJson, false)
		userCreationJson.put('emailAddress', '@b.c')
		def response2 = appService.sendBuddyConnectRequest(richard, userCreationJson, false)
		userCreationJson.put('emailAddress', 'a@b@c.c')
		def response3 = appService.sendBuddyConnectRequest(richard, userCreationJson, false)

		then:
		assertResponseStatus(response1, 400)
		response1.json.code == "error.user.email.address.invalid"
		assertResponseStatus(response2, 400)
		response2.json.code == "error.user.email.address.invalid"
		assertResponseStatus(response3, 400)
		response3.json.code == "error.user.email.address.invalid"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try Richard request himself as buddy'()
	{
		given:
		User richard = addRichard()
		richard.emailAddress = "richard@quinn.com"

		when:
		def response = appService.sendBuddyConnectRequest(richard, richard, false)

		then:
		assertResponseStatus(response, 400)
		response.json.code == "error.buddy.cannot.invite.self"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try Richard request Bob as buddy twice'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		bob.emailAddress = "bob@dunn.com"
		appService.sendBuddyConnectRequest(richard, bob)

		when:
		def response = appService.sendBuddyConnectRequest(richard, bob, false)

		then:
		assertResponseStatus(response, 400)
		response.json.code == "error.buddy.cannot.invite.existing.buddy"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}
}
