/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

class LocalizationTest extends AbstractAppServiceIntegrationTest
{
	def 'Verify default language'()
	{
		given:
		def wrongNumber = "NotANumber"
		when:
		def response = appService.yonaServer.getResource("$appService.NEW_DEVICE_REQUESTS_PATH$wrongNumber", ["Yona-NewDeviceRequestPassword" : ""])

		then:
		response.status == 400
		response.data.code == "error.user.mobile.number.invalid"
		response.data.message == "The mobile number '$wrongNumber' is invalid. It must start with a + sign, with no spaces between the digits"
		response.headers."Content-Language" == "en-US"
	}

	def 'Verify Dutch'()
	{
		given:
		def wrongNumber = "NotANumber"
		when:
		def response = appService.yonaServer.getResource("$appService.NEW_DEVICE_REQUESTS_PATH$wrongNumber", ["Accept-Language" : "nl-NL", "Yona-NewDeviceRequestPassword" : ""])

		then:
		response.status == 400
		response.data.code == "error.user.mobile.number.invalid"
		response.data.message == "Het mobiele nummer '$wrongNumber' is ongeldig. Het moet beginnen met een +-teken, zonder spaties tussen de tekens"
		response.headers."Content-Language" == "nl-NL"
	}

	def 'Verify Latin'()
	{
		given:
		def wrongNumber = "NotANumber"
		when:
		def response = appService.yonaServer.getResource("$appService.NEW_DEVICE_REQUESTS_PATH$wrongNumber", ["Accept-Language" : "la", "Yona-NewDeviceRequestPassword" : ""])

		then:
		response.status == 400
		response.data.code == "error.user.mobile.number.invalid"
		response.data.message == "The mobile number '$wrongNumber' is invalid. It must start with a + sign, with no spaces between the digits"
		response.headers."Content-Language" == "en-US"
	}
}
