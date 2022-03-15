/*******************************************************************************
 * Copyright (c) 2022 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/

package nu.yona.server


import nu.yona.server.test.AppService
import spock.lang.Shared
import spock.lang.Specification

class AdminConnectTest extends Specification
{
	@Shared
	AdminService adminService = new AdminService()

	@Shared
	AppService appService = new AppService()

	def 'Just say it\'s fine'()
	{
		when:
		def adminUrl = adminService.yonaServer.restClient.uri
		def appUrl = appService.yonaServer.restClient.uri

		then:
		println "Admin url: " + adminUrl
		println "App url: " + appUrl
		adminUrl
	}
}
