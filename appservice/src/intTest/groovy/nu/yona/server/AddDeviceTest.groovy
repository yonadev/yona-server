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
		def userSecret = "unknown secret"
		def response = appService.setNewDeviceRequest(richard.mobileNumber, richard.password, userSecret)

		then:
		response.status == 201
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber)
		getResponseAfter.status == 200

		def getWithPasswordResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, userSecret)
		getWithPasswordResponseAfter.status == 200
		getWithPasswordResponseAfter.responseData.userPassword == richard.password

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try get new device request with wrong information'()
	{
		given:
		def userSecret = "unknown secret"
		User richard = addRichard()
		User bob = addBob()
		appService.setNewDeviceRequest(richard.mobileNumber, richard.password, userSecret)

		when:
		def responseWrongSecret = appService.getNewDeviceRequest(richard.mobileNumber, "wrong secret")
		def responseWrongMobileNumber = appService.getNewDeviceRequest("+31690609181", "wrong secret")
		def responseNoNewDeviceRequestWrongPassword = appService.getNewDeviceRequest(bob.mobileNumber, "wrong secret")
		def responseNoNewDeviceRequestNoPassword = appService.getNewDeviceRequest(bob.mobileNumber)

		then:
		responseWrongSecret.status == 400
		responseWrongSecret.responseData.code == "error.device.request.invalid.secret"
		responseWrongMobileNumber.status == 400
		responseWrongMobileNumber.responseData.code == "error.no.device.request.present"
		responseNoNewDeviceRequestWrongPassword.status == 400
		responseNoNewDeviceRequestWrongPassword.responseData.code == "error.no.device.request.present"
		responseNoNewDeviceRequestNoPassword.status == 400
		responseNoNewDeviceRequestNoPassword.responseData.code == "error.no.device.request.present"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try set new device request with wrong information'()
	{
		given:
		User richard = addRichard()
		def userSecret = "unknown secret"
		appService.setNewDeviceRequest(richard.mobileNumber, richard.password, userSecret)
		def getResponseImmmediately = appService.getNewDeviceRequest(richard.mobileNumber)
		assert getResponseImmmediately.status == 200

		when:
		def responseWrongPassword = appService.setNewDeviceRequest(richard.mobileNumber, "foo", "Some secret")
		def responseWrongMobileNumber = appService.setNewDeviceRequest("+31690609181", "foo", "Some secret")

		then:
		responseWrongPassword.status == 400
		responseWrongPassword.responseData.code == "error.decrypting.data"
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, userSecret)
		getResponseAfter.status == 200
		getResponseAfter.responseData.userPassword == richard.password
		responseWrongMobileNumber.status == 400
		responseWrongMobileNumber.responseData.code == "error.decrypting.data"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Overwrite new device request'()
	{
		given:
		User richard = addRichard()
		appService.setNewDeviceRequest(richard.mobileNumber, richard.password, "Some secret")

		when:
		def userSecret = "unknown secret"
		def response = appService.setNewDeviceRequest(richard.mobileNumber, richard.password, userSecret)

		then:
		response.status == 200
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, userSecret)
		getResponseAfter.status == 200
		getResponseAfter.responseData.userPassword == richard.password

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Clear new device request'()
	{
		given:
		def userSecret = "unknown secret"
		User richard = addRichard()
		def initialResponse = appService.setNewDeviceRequest(richard.mobileNumber, richard.password, userSecret)

		when:
		def response = appService.clearNewDeviceRequest(richard.mobileNumber, richard.password)

		then:
		response.status == 200
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber)
		getResponseAfter.status == 400
		getResponseAfter.responseData.code == "error.no.device.request.present"

		def getWithPasswordResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, userSecret)
		getWithPasswordResponseAfter.status == 400
		getWithPasswordResponseAfter.responseData.code == "error.no.device.request.present"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try clear new device request with wrong information'()
	{
		given:
		def userSecret = "unknown secret"
		User richard = addRichard()
		def initialResponse = appService.setNewDeviceRequest(richard.mobileNumber, richard.password, userSecret)

		when:
		def responseWrongPassword = appService.clearNewDeviceRequest(richard.mobileNumber, "foo")
		def responseWrongMobileNumber = appService.clearNewDeviceRequest("+31690609181", "foo")

		then:
		responseWrongPassword.status == 400
		responseWrongPassword.responseData.code == "error.decrypting.data"
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, userSecret)
		getResponseAfter.status == 200
		getResponseAfter.responseData.userPassword == richard.password
		responseWrongMobileNumber.status == 400
		responseWrongMobileNumber.responseData.code == "error.decrypting.data"

		cleanup:
		appService.deleteUser(richard)
	}
}
