/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server


import groovy.json.*

class AddDeviceTest extends AbstractAppServiceIntegrationTest
{
	def 'Set and get new device request'()
	{
		given:
		def richard = addRichard()

		when:
		def userSecret = "unknown secret"
		def response = appService.setNewDeviceRequest(richard.newDeviceRequestUrl, richard.password, userSecret)

		then:
		response.status == 201
		def getResponseAfter = appService.getNewDeviceRequest(richard.newDeviceRequestUrl)
		getResponseAfter.status == 200

		def getWithPasswordResponseAfter = appService.getNewDeviceRequest(richard.newDeviceRequestUrl, userSecret)
		getWithPasswordResponseAfter.status == 200
		getWithPasswordResponseAfter.responseData.userPassword == richard.password

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try get new device request with wrong user secret'()
	{
		given:
		def userSecret = "unknown secret"
		def richard = addRichard()
		appService.setNewDeviceRequest(richard.newDeviceRequestUrl, richard.password, userSecret)

		when:
		def response = appService.getNewDeviceRequest(richard.newDeviceRequestUrl, "wrong secret")

		then:
		response.status == 400

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try set new device request with wrong password'()
	{
		given:
		def richard = addRichard()
		def userSecret = "unknown secret"
		appService.setNewDeviceRequest(richard.newDeviceRequestUrl, richard.password, userSecret)
		def getResponseImmmediately = appService.getNewDeviceRequest(richard.newDeviceRequestUrl)
		assert getResponseImmmediately.status == 200

		when:
		def response = appService.setNewDeviceRequest(richard.newDeviceRequestUrl, "foo", "Some secret")

		then:
		response.status == 400
		def getResponseAfter = appService.getNewDeviceRequest(richard.newDeviceRequestUrl, userSecret)
		getResponseAfter.status == 200
		getResponseAfter.responseData.userPassword == richard.password

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Overwrite new device request'()
	{
		given:
		def richard = addRichard()
		appService.setNewDeviceRequest(richard.newDeviceRequestUrl, richard.password, "Some secret")

		when:
		def userSecret = "unknown secret"
		def response = appService.setNewDeviceRequest(richard.newDeviceRequestUrl, richard.password, userSecret)

		then:
		response.status == 200
		def getResponseAfter = appService.getNewDeviceRequest(richard.newDeviceRequestUrl, userSecret)
		getResponseAfter.status == 200
		getResponseAfter.responseData.userPassword == richard.password

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Clear new device request'()
	{
		given:
		def userSecret = "unknown secret"
		def richard = addRichard()
		def initialResponse = appService.setNewDeviceRequest(richard.newDeviceRequestUrl, richard.password, userSecret)

		when:
		def response = appService.clearNewDeviceRequest(initialResponse.responseData._links.edit.href, richard.password)

		then:
		response.status == 200
		def getResponseAfter = appService.getNewDeviceRequest(richard.newDeviceRequestUrl)
		getResponseAfter.status == 400
		getResponseAfter.data.containsKey("code")
		getResponseAfter.data["code"] == "error.no.device.request.present"

		def getWithPasswordResponseAfter = appService.getNewDeviceRequest(richard.newDeviceRequestUrl, userSecret)
		getWithPasswordResponseAfter.status == 400
		getWithPasswordResponseAfter.data.containsKey("code")
		getWithPasswordResponseAfter.data["code"] == "error.no.device.request.present"

		cleanup:
		appService.deleteUser(richard)
	}
}
