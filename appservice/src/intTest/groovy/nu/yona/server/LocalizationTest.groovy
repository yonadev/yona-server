/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.*

class LocalizationTest extends AbstractAppServiceIntegrationTest
{
	def 'Verify default language'()
	{
		given:
		def wrongNumber = "NotANumber"
		when:
		def successResponse = appService.yonaServer.getResource(GAMBLING_ACT_CAT_URL)
		def errorResponse = appService.yonaServer.getResource("$appService.NEW_DEVICE_REQUESTS_PATH$wrongNumber", [:], ["Yona-NewDeviceRequestPassword" : ""])

		then:
		assertResponseStatusOk(successResponse)
		successResponse.responseData.name == "Gambling"
		successResponse.responseData.description == "This challenge includes apps and sites like Poker and Blackjack"
		successResponse.headers."Content-Language" == "en-US"
		assertResponseStatus(errorResponse, 400)
		errorResponse.data.code == "error.user.mobile.number.invalid"
		errorResponse.data.message == "The mobile number '$wrongNumber' is invalid. It must start with a + sign, with no spaces between the digits"
		errorResponse.data.correlationId ==~ /(?i)^$UUID_PATTERN$/
		errorResponse.headers."Content-Language" == "en-US"
	}

	def 'Verify Dutch'()
	{
		given:
		def wrongNumber = "NotANumber"
		when:
		def successResponse = appService.yonaServer.getResource(GAMBLING_ACT_CAT_URL, [:], ["Accept-Language" : "nl-NL"])
		def errorResponse = appService.yonaServer.getResource("$appService.NEW_DEVICE_REQUESTS_PATH$wrongNumber", [:], ["Accept-Language" : "nl-NL", "Yona-NewDeviceRequestPassword" : ""])

		then:
		assertResponseStatusOk(successResponse)
		successResponse.responseData.name == "Gokken"
		successResponse.responseData.description == "Deze challenge bevat apps en sites zoals Poker en Blackjack"
		successResponse.headers."Content-Language" == "nl-NL"
		assertResponseStatus(errorResponse, 400)
		errorResponse.data.code == "error.user.mobile.number.invalid"
		errorResponse.data.message == "Het mobiele nummer '$wrongNumber' is ongeldig. Het moet beginnen met een +-teken, zonder spaties tussen de tekens"
		errorResponse.data.correlationId ==~ /(?i)^$UUID_PATTERN$/
		errorResponse.headers."Content-Language" == "nl-NL"
	}

	def 'Verify Latin'()
	{
		given:
		def wrongNumber = "NotANumber"
		when:
		def successResponse = appService.yonaServer.getResource(GAMBLING_ACT_CAT_URL, [:], ["Accept-Language" : "la"])
		def errorResponse = appService.yonaServer.getResource("$appService.NEW_DEVICE_REQUESTS_PATH$wrongNumber", [:], ["Accept-Language" : "la", "Yona-NewDeviceRequestPassword" : ""])

		then:
		assertResponseStatusOk(successResponse)
		successResponse.responseData.name == "Gambling"
		successResponse.responseData.description == "This challenge includes apps and sites like Poker and Blackjack"
		successResponse.headers."Content-Language" == "en-US"
		assertResponseStatus(errorResponse, 400)
		errorResponse.data.code == "error.user.mobile.number.invalid"
		errorResponse.data.message == "The mobile number '$wrongNumber' is invalid. It must start with a + sign, with no spaces between the digits"
		errorResponse.data.correlationId ==~ /(?i)^$UUID_PATTERN$/
		errorResponse.headers."Content-Language" == "en-US"
	}
}
