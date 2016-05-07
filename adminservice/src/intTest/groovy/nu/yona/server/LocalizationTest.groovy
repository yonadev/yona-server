/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*
import spock.lang.Shared
import spock.lang.Specification

class LocalizationTest extends Specification
{
	@Shared
	def AdminService adminService = new AdminService()

	@Shared
	public String GAMBLING_ACT_CAT_URL = "$adminService.url$AdminService.ACTIVITY_CATEGORIES_PATH"+ "192d69f4-8d3e-499b-983c-36ca97340ba9"

	def 'Verify default language'()
	{
		given:
		when:
		def successResponse = adminService.yonaServer.getResource(GAMBLING_ACT_CAT_URL)

		then:
		successResponse.status == 200
		successResponse.responseData.name == "Gambling"
		successResponse.headers."Content-Language" == "en-US"
	}

	def 'Verify Dutch'()
	{
		given:
		when:
		def successResponse = adminService.yonaServer.getResource(GAMBLING_ACT_CAT_URL, ["Accept-Language" : "nl-NL"])

		then:
		successResponse.status == 200
		successResponse.responseData.name == "Gokken"
		successResponse.headers."Content-Language" == "nl-NL"
	}

	def 'Verify Latin'()
	{
		given:
		when:
		def successResponse = adminService.yonaServer.getResource(GAMBLING_ACT_CAT_URL, ["Accept-Language" : "la"])

		then:
		successResponse.status == 200
		successResponse.responseData.name == "Gambling"
		successResponse.headers."Content-Language" == "en-US"
	}
}