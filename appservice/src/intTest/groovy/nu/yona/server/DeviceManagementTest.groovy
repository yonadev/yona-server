/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/. 
 *******************************************************************************/
package nu.yona.server


import static nu.yona.server.test.CommonAssertions.*

import nu.yona.server.test.CommonAssertions
import nu.yona.server.test.Device
import nu.yona.server.test.Goal
import nu.yona.server.test.User

class DeviceManagementTest extends AbstractAppServiceIntegrationTest
{
	def 'Set and get new device request'()
	{
		given:
		User richard = addRichard()

		when:
		def newDeviceRequestPassword = "Temp password"
		def response = appService.setNewDeviceRequest(richard.mobileNumber, richard.password, newDeviceRequestPassword)

		then:
		assertResponseStatusNoContent(response)

		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber)
		assertResponseStatusOk(getResponseAfter)
		getResponseAfter.responseData.yonaPassword == null
		getResponseAfter.responseData._links.self.href == richard.newDeviceRequestUrl
		getResponseAfter.responseData._links.edit.href == richard.newDeviceRequestUrl
		getResponseAfter.responseData._links."yona:user".href
		def baseUserUrl = YonaServer.stripQueryString(richard.url)
		YonaServer.stripQueryString(getResponseAfter.responseData._links."yona:user".href) == baseUserUrl
		// The below assert checks the path fragment. If it fails, the Swagger spec needs to be updated too
		getResponseAfter.responseData._links."yona:registerDevice".href == baseUserUrl + "/devices/"

		def getWithPasswordResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, newDeviceRequestPassword)
		assertResponseStatusOk(getWithPasswordResponseAfter)
		getWithPasswordResponseAfter.responseData.yonaPassword == richard.password
		getWithPasswordResponseAfter.responseData._links.self.href == richard.newDeviceRequestUrl
		getWithPasswordResponseAfter.responseData._links.edit.href == richard.newDeviceRequestUrl
		getWithPasswordResponseAfter.responseData._links."yona:user".href
		YonaServer.stripQueryString(getWithPasswordResponseAfter.responseData._links."yona:user".href) == YonaServer.stripQueryString(richard.url)

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Richard adds new device; Bob gets the update'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		def firstDeviceName = richard.requestingDevice.name

		when:
		def newDeviceRequestPassword = "Temp password"
		def response = appService.setNewDeviceRequest(richard.mobileNumber, richard.password, newDeviceRequestPassword)

		then:
		assertResponseStatusNoContent(response)

		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber)
		assertResponseStatusOk(getResponseAfter)
		getResponseAfter.responseData.yonaPassword == null
		getResponseAfter.responseData._links.self.href == richard.newDeviceRequestUrl
		getResponseAfter.responseData._links.edit.href == richard.newDeviceRequestUrl
		getResponseAfter.responseData._links."yona:user".href
		def baseUserUrl = YonaServer.stripQueryString(richard.url)
		YonaServer.stripQueryString(getResponseAfter.responseData._links."yona:user".href) == baseUserUrl
		def registerUrl = getResponseAfter.responseData._links."yona:registerDevice".href
		// The below assert checks the path fragment. If it fails, the Swagger spec needs to be updated too
		registerUrl == baseUserUrl + "/devices/"

		def newDeviceName = "My S9"
		def newDeviceOs = "ANDROID"
		def newDeviceFirebaseInstanceId = "New firebase instance id"
		def registerResponse = appService.registerNewDevice(registerUrl, newDeviceRequestPassword, newDeviceName, newDeviceOs, Device.SOME_APP_VERSION, Device.SUPPORTED_APP_VERSION_CODE, newDeviceFirebaseInstanceId)
		assertResponseStatusCreated(registerResponse)
		assertUserGetResponseDetails(registerResponse, false)

		def devices = registerResponse.responseData._embedded."yona:devices"._embedded."yona:devices"
		devices.size == 2

		def defaultDevice = (devices[0].name == newDeviceName) ? devices[1] : devices[0]
		def newDevice = (devices[0].name == newDeviceName) ? devices[0] : devices[1]

		assertDefaultOwnDevice(defaultDevice, false)
		assert newDevice.name == newDeviceName
		assert newDevice.operatingSystem == newDeviceOs
		assert newDevice.firebaseInstanceId == newDeviceFirebaseInstanceId
		assertDateTimeFormat(newDevice.registrationTime)
		assertDateFormat(newDevice.appLastOpenedDate)

		// User self-link uses the new device as "requestingDeviceId"
		def idOfNewDevice = extractDeviceId(newDevice._links.self.href)
		registerResponse.responseData._links.self.href ==~ /.*requestingDeviceId=$idOfNewDevice.*/

		def bobMessagesAfterUpdate = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesAfterUpdate)
		def deviceChangeMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyDeviceChangeMessage" }

		deviceChangeMessages.size() == 1
		deviceChangeMessages[0]._links.keySet() == ["self", "edit", "yona:markRead", "yona:buddy", "yona:user"] as Set
		deviceChangeMessages[0]._links."yona:user".href == bob.buddies[0].user.url
		deviceChangeMessages[0].message == "User added a device named '$newDeviceName'"

		User bobAfterReload = appService.reloadUser(bob)
		bobAfterReload.buddies[0].user.devices.collect { it.name } as Set == [firstDeviceName, newDeviceName] as Set

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	private static String extractDeviceId(String url) {
		return url - ~/.*\// - ~/\?.*/
	}

	def 'Try register new device with wrong password'()
	{
		given:
		User richard = addRichard()
		def newDeviceRequestPassword = "Temp password"
		def setResponse = appService.setNewDeviceRequest(richard.mobileNumber, richard.password, newDeviceRequestPassword)
		assertResponseStatusNoContent(setResponse)
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber)
		assertResponseStatusOk(getResponseAfter)
		def registerUrl = getResponseAfter.responseData._links."yona:registerDevice".href

		when:
		def newDeviceName = "My iPhone"
		def newDeviceOs = "ANDROID"
		def response = appService.registerNewDevice(registerUrl, "Wrong password", newDeviceName, newDeviceOs)

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.device.request.invalid.password"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try register new device while no new device request exists'()
	{
		given:
		User richard = addRichard()
		def registerUrl = YonaServer.stripQueryString(richard.url)+ "/devices/"

		when:
		def newDeviceName = "My iPhone"
		def newDeviceOs = "ANDROID"
		def response = appService.registerNewDevice(registerUrl, "Wrong password", newDeviceName, newDeviceOs)

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.no.device.request.present"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try add device with null values'()
	{
		given:
		User richard = addRichard()
		def newDeviceRequestPassword = "Zomaar"
		assertResponseStatusSuccess(appService.setNewDeviceRequest(richard.mobileNumber, richard.password, newDeviceRequestPassword))

		def getResponse = appService.getNewDeviceRequest(richard.mobileNumber, newDeviceRequestPassword)
		assertResponseStatusSuccess(getResponse)

		when:
		def response
		response = appService.registerNewDevice(getResponse.responseData._links."yona:registerDevice".href, newDeviceRequestPassword, null, "IOS", Device.SOME_APP_VERSION, Device.SUPPORTED_APP_VERSION_CODE)

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.request.missing.property"
		response.data.message ==~ /^Mandatory property 'name'.*/

		when:
		response = appService.registerNewDevice(getResponse.responseData._links."yona:registerDevice".href, newDeviceRequestPassword, "TheName", null, Device.SOME_APP_VERSION, Device.SUPPORTED_APP_VERSION_CODE)

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.request.missing.property"
		response.data.message ==~ /^Mandatory property 'operatingSystem'.*/

		when:
		response = appService.registerNewDevice(getResponse.responseData._links."yona:registerDevice".href, newDeviceRequestPassword, "TheName", "IOS", null, Device.SUPPORTED_APP_VERSION_CODE)

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.request.missing.property"
		response.data.message ==~ /^Mandatory property 'appVersion'.*/

		when:
		response = appService.registerNewDevice(getResponse.responseData._links."yona:registerDevice".href, newDeviceRequestPassword, "TheName", "IOS", Device.SOME_APP_VERSION, null)

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.request.missing.property"
		response.data.message ==~ /^Mandatory property 'appVersionCode'.*/

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try get new device request with wrong information'(number, password, withNewDeviceRequest, expectedStatus, expectedCode)
	{
		given:
		User user = addRichard()
		if (withNewDeviceRequest) {
			appService.setNewDeviceRequest(user.mobileNumber, user.password, "right")
		}

		when:
		def response = appService.getNewDeviceRequest(number == "<user>" ? user.mobileNumber : number, password)

		then:
		assertResponseStatus(response, expectedStatus)
		response.responseData.code == expectedCode

		cleanup:
		appService.deleteUser(user)

		where:
		number | password | withNewDeviceRequest | expectedStatus | expectedCode
		"<user>" | "wrong" | true | 400 | "error.device.request.invalid.password"
		"+31610609189" | "wrong" | true | 400 | "error.no.device.request.present"
		"+31318123456" | "wrong" | true | 400 | "error.user.mobile.number.not.mobile"
		"+310610609189" | "wrong" | true | 400 | "error.user.mobile.number.invalid.leading.zero"
		"<user>" | "wrong" | false | 400 | "error.no.device.request.present"
		"<user>" | null | false | 400 | "error.no.device.request.present"
	}

	def 'Try set new device request with wrong information'()
	{
		given:
		User richard = addRichard()
		def newDeviceRequestPassword = "Temp password"
		appService.setNewDeviceRequest(richard.mobileNumber, richard.password, newDeviceRequestPassword)
		def getResponseImmmediately = appService.getNewDeviceRequest(richard.mobileNumber)
		assertResponseStatusOk(getResponseImmmediately)

		when:
		def responseWrongPassword = appService.setNewDeviceRequest(richard.mobileNumber, "foo", "Some password")
		def responseWrongMobileNumber = appService.setNewDeviceRequest("+31610609189", "foo", "Some password")

		then:
		assertResponseStatus(responseWrongPassword, 400)
		responseWrongPassword.responseData.code == "error.decrypting.data"
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, newDeviceRequestPassword)
		assertResponseStatusOk(getResponseAfter)
		getResponseAfter.responseData.yonaPassword == richard.password
		assertResponseStatus(responseWrongMobileNumber, 400)
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
		assertResponseStatusNoContent(response)
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, newDeviceRequestPassword)
		assertResponseStatusOk(getResponseAfter)
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
		assertResponseStatusNoContent(response)
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber)
		assertResponseStatus(getResponseAfter, 400)
		getResponseAfter.responseData.code == "error.no.device.request.present"

		def getWithPasswordResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, newDeviceRequestPassword)
		assertResponseStatus(getWithPasswordResponseAfter, 400)
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
		assertResponseStatus(responseWrongPassword, 400)
		responseWrongPassword.responseData.code == "error.decrypting.data"
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, newDeviceRequestPassword)
		assertResponseStatusOk(getResponseAfter)
		getResponseAfter.responseData.yonaPassword == richard.password
		assertResponseStatus(responseWrongMobileNumber, 400)
		responseWrongMobileNumber.responseData.code == "error.decrypting.data"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Richard updates his device name; Bob gets the update'()
	{
		given:
		def ts = timestamp
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		def updatedName = "Updated name"

		when:
		def response = appService.updateResourceWithPassword(richard.requestingDevice.editUrl, """{"name":"$updatedName"}""", richard.password)


		then:
		assertResponseStatusOk(response)
		def richardAfterReload = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsIgnoreDefaultDevice)

		richardAfterReload.devices.size == 1
		richardAfterReload.requestingDevice.name == updatedName
		richardAfterReload.requestingDevice.operatingSystem == "IOS"

		def bobMessagesAfterUpdate = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesAfterUpdate)
		def deviceChangeMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyDeviceChangeMessage"}

		deviceChangeMessages.size() == 1
		deviceChangeMessages[0]._links.self != null
		deviceChangeMessages[0]._links."yona:process" == null // Processing happens automatically these days
		deviceChangeMessages[0]._links."yona:user".href == bob.buddies[0].user.url
		deviceChangeMessages[0].message == "User renamed device 'Richard's iPhone' into '$updatedName'"

		User bobAfterReload = appService.reloadUser(bob)
		bobAfterReload.buddies[0].user.devices[0].name == updatedName

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard updates his device firebase instance id; device is updated but not disclosed to Bob'()
	{
		given:
		def ts = timestamp
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		def existingName = richard.requestingDevice.name
		def updatedFirebaseInstanceId = "Updated firebase instance id"

		when:
		def response = appService.updateResourceWithPassword(richard.requestingDevice.editUrl, """{"name":"$existingName", "firebaseInstanceId":"$updatedFirebaseInstanceId"}""", richard.password)

		then:
		assertResponseStatusOk(response)
		def richardAfterReload = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsIgnoreDefaultDevice)

		richardAfterReload.devices.size == 1
		richardAfterReload.requestingDevice.name == existingName
		richardAfterReload.requestingDevice.firebaseInstanceId == updatedFirebaseInstanceId

		def bobMessagesAfterUpdate = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesAfterUpdate)
		def deviceChangeMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyDeviceChangeMessage"}

		deviceChangeMessages.size() == 0

		User bobAfterReload = appService.reloadUser(bob)
		bobAfterReload.buddies[0].user.devices[0].name == existingName
		bobAfterReload.buddies[0].user.devices[0].firebaseInstanceId == null

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard updates device name after setting firebase instance id; firebase instance id is not changed'()
	{
		given:
		def ts = timestamp
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		def existingName = richard.requestingDevice.name
		def existingFirebaseInstanceId = "Existing firebase instance id"
		appService.updateResourceWithPassword(richard.requestingDevice.editUrl, """{"name":"$existingName", "firebaseInstanceId":"$existingFirebaseInstanceId"}""", richard.password)
		def updatedName = "Updated name"

		when:
		def response = appService.updateResourceWithPassword(richard.requestingDevice.editUrl, """{"name":"$updatedName"}""", richard.password)

		then:
		assertResponseStatusOk(response)
		def richardAfterReload = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsIgnoreDefaultDevice)

		richardAfterReload.devices.size == 1
		richardAfterReload.requestingDevice.name == updatedName
		richardAfterReload.requestingDevice.firebaseInstanceId == existingFirebaseInstanceId

		def bobMessagesAfterUpdate = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesAfterUpdate)
		def deviceChangeMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyDeviceChangeMessage"}

		deviceChangeMessages.size() == 1
		deviceChangeMessages[0]._links.self != null
		deviceChangeMessages[0]._links."yona:process" == null // Processing happens automatically these days
		deviceChangeMessages[0]._links."yona:user".href == bob.buddies[0].user.url
		deviceChangeMessages[0].message == "User renamed device 'Richard's iPhone' into '$updatedName'"

		User bobAfterReload = appService.reloadUser(bob)
		bobAfterReload.buddies[0].user.devices[0].name == updatedName
		bobAfterReload.buddies[0].user.devices[0].firebaseInstanceId == null // Firebase instance ID is not disclosed to buddies

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Try update device name without name'()
	{
		given:
		def ts = timestamp
		User richard = addRichard()
		Device deviceToUpdate = richard.devices[0]
		def existingName = "Existing name"
		appService.addDevice(richard, existingName, "ANDROID")

		when:
		def response = appService.updateResourceWithPassword(deviceToUpdate.editUrl, """{}""", richard.password)

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.request.missing.property"
		response.data.message ==~ /^Mandatory property 'name'.*/

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try update device name to device name containing a colon'()
	{
		given:
		def ts = timestamp
		User richard = addRichard()
		def updatedName = "Updated:name"

		when:
		def response = appService.updateResourceWithPassword(richard.requestingDevice.editUrl, """{"name":"$updatedName"}""", richard.password)

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.device.invalid.device.name"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try update device name to existing name'()
	{
		given:
		def ts = timestamp
		User richard = addRichard()
		Device deviceToUpdate = richard.requestingDevice
		def existingName = "Existing name"
		appService.addDevice(richard, existingName, "ANDROID")

		when:
		def response = appService.updateResourceWithPassword(deviceToUpdate.editUrl, """{"name":"$existingName"}""", richard.password)

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.device.name.already.exists"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Richard deletes a devices on which activities were done'()
	{
		given:
		def ts = timestamp
		User richardOnFirstDevice = addRichard()
		setCreationTimeOfMandatoryGoalsToNow(richardOnFirstDevice)
		setGoalCreationTime(richardOnFirstDevice, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		Device remainingDevice = richardOnFirstDevice.devices[0]
		def deletedDeviceName = "Second device"
		User richardOnSecondDevice = appService.addDevice(richardOnFirstDevice, deletedDeviceName, "ANDROID")
		Device deviceToDelete = richardOnSecondDevice.devices.find{ YonaServer.stripQueryString(it.url) != YonaServer.stripQueryString(remainingDevice.url) }
		reportAppActivity(richardOnSecondDevice, deviceToDelete, "NU.nl", "W-1 Mon 03:15", "W-1 Mon 03:35")
		Goal budgetGoalNewsRichard = richardOnFirstDevice.findActiveGoal(NEWS_ACT_CAT_URL)
		def expectedValuesRichardLastWeek = [
			"Mon" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: false, minutesBeyondGoal: 20, spread: [13 : 15, 14 : 5]]]],
			"Tue" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Wed" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Thu" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Fri" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Sat" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]]]
		def currentDayOfWeek = YonaServer.getCurrentDayOfWeek()
		def expectedTotalDays = 6 + currentDayOfWeek + 1
		def expectedTotalWeeks = 2


		when:
		def response = appService.deleteResourceWithPassword(deviceToDelete.editUrl, richardOnSecondDevice.password)

		then:
		assertResponseStatusNoContent(response)
		def richardAfterReload = appService.reloadUser(richardOnFirstDevice, CommonAssertions.&assertUserGetResponseDetailsIgnoreDefaultDevice)

		richardAfterReload.devices.size == 1
		richardAfterReload.devices[0].name == remainingDevice.name
		richardAfterReload.devices[0].operatingSystem == remainingDevice.operatingSystem

		when:
		def responseWeekOverviews = appService.getWeekActivityOverviews(richardOnFirstDevice)
		//get all days at once (max 2 weeks) to make assertion easy
		def responseDayOverviewsAll = appService.getDayActivityOverviews(richardOnFirstDevice, ["size": 14])

		then:
		assertWeekOverviewBasics(responseWeekOverviews, [2, 1], expectedTotalWeeks)

		def weekOverviewLastWeek = responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[1]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeek, budgetGoalNewsRichard, 6)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsRichard, expectedValuesRichardLastWeek, "Mon")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsRichard, expectedValuesRichardLastWeek, "Tue")

		cleanup:
		appService.deleteUser(richardOnFirstDevice)
	}

	def 'Richard deletes one of his devices; Bob gets the update'()
	{
		given:
		def ts = timestamp
		def richardAndBob = addRichardAndBobAsBuddies()
		User richardOnFirstDevice = richardAndBob.richard
		User bob = richardAndBob.bob
		Device remainingDevice = richardOnFirstDevice.requestingDevice
		def deletedDeviceName = "Second device"
		User richardOnSecondDevice = appService.addDevice(richardOnFirstDevice, deletedDeviceName, "ANDROID")
		Device deviceToDelete = richardOnSecondDevice.devices.find{ YonaServer.stripQueryString(it.url) != YonaServer.stripQueryString(remainingDevice.url) }

		when:
		def response = appService.deleteResourceWithPassword(deviceToDelete.editUrl, richardOnSecondDevice.password)

		then:
		assertResponseStatusNoContent(response)
		User richardAfterReload = appService.reloadUser(richardOnFirstDevice, CommonAssertions.&assertUserGetResponseDetailsIgnoreDefaultDevice)

		richardAfterReload.devices.size == 1
		richardAfterReload.requestingDevice.name == remainingDevice.name
		richardAfterReload.requestingDevice.operatingSystem == remainingDevice.operatingSystem

		def bobMessagesAfterUpdate = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesAfterUpdate)
		def deviceChangeMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyDeviceChangeMessage" && it.message ==~ /^User deleted.*/}

		deviceChangeMessages.size() == 1
		deviceChangeMessages[0]._links.self != null
		deviceChangeMessages[0]._links."yona:process" == null // Processing happens automatically these days
		deviceChangeMessages[0]._links."yona:user".href == bob.buddies[0].user.url
		deviceChangeMessages[0].message == "User deleted device '$deletedDeviceName'"

		User bobAfterReload = appService.reloadUser(bob)
		bobAfterReload.buddies[0].user.devices.size == 1
		bobAfterReload.buddies[0].user.devices[0].name == remainingDevice.name

		cleanup:
		appService.deleteUser(richardOnFirstDevice)
		appService.deleteUser(bob)
	}

	def 'Try delete last device'()
	{
		given:
		def ts = timestamp
		User richard = addRichard()
		Device deviceToDelete = richard.requestingDevice

		when:
		def response = appService.deleteResourceWithPassword(deviceToDelete.editUrl, richard.password)

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.device.cannot.delete.last.one"

		cleanup:
		appService.deleteUser(richard)
	}
}
