/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server


import groovy.json.*
import nu.yona.server.test.User

class AddDeviceTest extends AbstractAppServiceIntegrationTest
{
	def 'Set and get new device request'()
	{
		given:
		User richard = addRichard()

		when:
		def newDeviceRequestPassword = "Temp password"
		def response = appService.setNewDeviceRequest(richard.mobileNumber, richard.password, newDeviceRequestPassword)

		then:
		response.status == 200
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber)
		getResponseAfter.status == 200

		def getWithPasswordResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, newDeviceRequestPassword)
		getWithPasswordResponseAfter.status == 200
		getWithPasswordResponseAfter.responseData.yonaPassword == richard.password
		getWithPasswordResponseAfter.responseData._links.self.href == richard.newDeviceRequestUrl
		getWithPasswordResponseAfter.responseData._links.edit.href == richard.newDeviceRequestUrl
		getWithPasswordResponseAfter.responseData._links."yona:user".href
		YonaServer.stripQueryString(getWithPasswordResponseAfter.responseData._links."yona:user".href) == YonaServer.stripQueryString(richard.url)

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try get new device request with wrong information'()
	{
		given:
		def newDeviceRequestPassword = "Temp password"
		User richard = addRichard()
		User bob = addBob()
		appService.setNewDeviceRequest(richard.mobileNumber, richard.password, newDeviceRequestPassword)

		when:
		def responseWrongNewDeviceRequestPassword = appService.getNewDeviceRequest(richard.mobileNumber, "wrong temp password")
		def responseWrongMobileNumber = appService.getNewDeviceRequest("+31610609189", "wrong temp password")
		def responseNoNewDeviceRequestWrongPassword = appService.getNewDeviceRequest(bob.mobileNumber, "wrong temp password")
		def responseNoNewDeviceRequestNoPassword = appService.getNewDeviceRequest(bob.mobileNumber)

		then:
		responseWrongNewDeviceRequestPassword.status == 400
		responseWrongNewDeviceRequestPassword.responseData.code == "error.device.request.invalid.password"
		responseWrongMobileNumber.status == 400
		responseWrongMobileNumber.responseData.code == "error.no.device.request.present"
		responseNoNewDeviceRequestWrongPassword.status == 400
		responseNoNewDeviceRequestWrongPassword.responseData.code == "error.no.device.request.present"
		responseNoNewDeviceRequestNoPassword.status == 400
		responseNoNewDeviceRequestNoPassword.responseData.code == "error.no.device.request.present"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Try set new device request with wrong information'()
	{
		given:
		User richard = addRichard()
		def newDeviceRequestPassword = "Temp password"
		appService.setNewDeviceRequest(richard.mobileNumber, richard.password, newDeviceRequestPassword)
		def getResponseImmmediately = appService.getNewDeviceRequest(richard.mobileNumber)
		assert getResponseImmmediately.status == 200

		when:
		def responseWrongPassword = appService.setNewDeviceRequest(richard.mobileNumber, "foo", "Some password")
		def responseWrongMobileNumber = appService.setNewDeviceRequest("+31610609189", "foo", "Some password")

		then:
		responseWrongPassword.status == 400
		responseWrongPassword.responseData.code == "error.decrypting.data"
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, newDeviceRequestPassword)
		getResponseAfter.status == 200
		getResponseAfter.responseData.yonaPassword == richard.password
		responseWrongMobileNumber.status == 400
		responseWrongMobileNumber.responseData.code == "error.decrypting.data"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Overwrite new device request'()
	{
		given:
		User richard = addRichard()
		appService.setNewDeviceRequest(richard.mobileNumber, richard.password, "Some password")

		when:
		def newDeviceRequestPassword = "Temp password"
		def response = appService.setNewDeviceRequest(richard.mobileNumber, richard.password, newDeviceRequestPassword)

		then:
		response.status == 200
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, newDeviceRequestPassword)
		getResponseAfter.status == 200
		getResponseAfter.responseData.yonaPassword == richard.password

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Clear new device request'()
	{
		given:
		def newDeviceRequestPassword = "Temp password"
		User richard = addRichard()
		def initialResponse = appService.setNewDeviceRequest(richard.mobileNumber, richard.password, newDeviceRequestPassword)

		when:
		def response = appService.clearNewDeviceRequest(richard.mobileNumber, richard.password)

		then:
		response.status == 200
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber)
		getResponseAfter.status == 400
		getResponseAfter.responseData.code == "error.no.device.request.present"

		def getWithPasswordResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, newDeviceRequestPassword)
		getWithPasswordResponseAfter.status == 400
		getWithPasswordResponseAfter.responseData.code == "error.no.device.request.present"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try clear new device request with wrong information'()
	{
		given:
		def newDeviceRequestPassword = "Temp password"
		User richard = addRichard()
		def initialResponse = appService.setNewDeviceRequest(richard.mobileNumber, richard.password, newDeviceRequestPassword)

		when:
		def responseWrongPassword = appService.clearNewDeviceRequest(richard.mobileNumber, "foo")
		def responseWrongMobileNumber = appService.clearNewDeviceRequest("+31610609189", "foo")

		then:
		responseWrongPassword.status == 400
		responseWrongPassword.responseData.code == "error.decrypting.data"
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, newDeviceRequestPassword)
		getResponseAfter.status == 200
		getResponseAfter.responseData.yonaPassword == richard.password
		responseWrongMobileNumber.status == 400
		responseWrongMobileNumber.responseData.code == "error.decrypting.data"

		cleanup:
		appService.deleteUser(richard)
	}
}
