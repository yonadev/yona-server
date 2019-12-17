/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.*

import java.time.ZonedDateTime

import nu.yona.server.test.AppActivity
import nu.yona.server.test.Device
import nu.yona.server.test.User

class FirebaseTest extends AbstractAppServiceIntegrationTest
{
	def 'Richard and Bob both have a notification for the buddy request/acceptance'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		def messageUrlRichard = appService.getMessages(richard).responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectResponseMessage"}[0]._links.self.href
		def responseRichard = appService.getLastFirebaseMessage(richard.requestingDevice.firebaseInstanceId)

		then:
		assertResponseStatusOk(responseRichard)
		responseRichard.responseData.title == "Message received"
		responseRichard.responseData.body == "Tap to open message"
		messageUrlRichard.endsWith(responseRichard.responseData.data.messageId.toString())

		when:
		def messageUrlBob = appService.getMessages(bob).responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectRequestMessage"}[0]._links.self.href
		def responseBob = appService.getLastFirebaseMessage(bob.requestingDevice.firebaseInstanceId)

		then:
		assertResponseStatusOk(responseBob)
		responseBob.responseData.title == "Message received"
		responseBob.responseData.body == "Tap to open message"
		messageUrlBob.endsWith(responseBob.responseData.data.messageId.toString())

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Bob gets a notification on both devices'()
	{
		given:
		User richard = addRichard()
		User bobAndroid = addBob(true, "ANDROID", "nl-NL")
		bobAndroid.emailAddress = "bob@dunn.com"
		User bobIos = appService.addDevice(bobAndroid, "Bob's iPhone", "IOS", Device.SOME_APP_VERSION)
		def appOs = "ANDROID"
		def appVersionCode = 123
		def appVersionName = "1.2 (I'm Rich)"
		def headers = ["Yona-App-Version" : "$appOs/$appVersionCode/$appVersionName"]
		appService.sendBuddyConnectRequest(richard, bobAndroid, true, headers)

		when:
		def messageUrlBobAndroid = appService.getMessages(bobAndroid, headers, [:]).responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectRequestMessage"}[0]._links.self.href
		def responseBobAndroid = appService.getLastFirebaseMessage(bobAndroid.requestingDevice.firebaseInstanceId)

		then:
		assertResponseStatusOk(responseBobAndroid)
		responseBobAndroid.responseData.title == "Bericht ontvangen"
		responseBobAndroid.responseData.body == "Tik om het bericht te openen"
		messageUrlBobAndroid.endsWith(responseBobAndroid.responseData.data.messageId.toString())
		responseBobAndroid.responseData.appOs == appOs
		responseBobAndroid.responseData.appVersionCode == appVersionCode
		responseBobAndroid.responseData.appVersionName == appVersionName

		when:
		def messageUrlBobIos = appService.getMessages(bobIos).responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectRequestMessage"}[0]._links.self.href
		def responseBobIos = appService.getLastFirebaseMessage(bobIos.requestingDevice.firebaseInstanceId)

		then:
		assertResponseStatusOk(responseBobIos)
		responseBobIos.responseData.title == "Message received"
		responseBobIos.responseData.body == "Tap to open message"
		messageUrlBobIos.endsWith(responseBobIos.responseData.data.messageId.toString())
		responseBobIos.responseData.appOs == appOs
		responseBobIos.responseData.appVersionCode == appVersionCode
		responseBobIos.responseData.appVersionName == appVersionName

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bobAndroid)
	}

	def 'Bob gets a notifications in the right language when changing language'()
	{
		given:
		User richard = addRichard()
		User bob = addBob(true, "ANDROID", "nl-NL")
		User bea = addBea()
		bob.emailAddress = "bob@dunn.com"
		Device device = bob.requestingDevice

		when:
		device.postOpenAppEvent(appService, device.operatingSystem) // Defaults to US-English
		appService.sendBuddyConnectRequest(richard, bob)
		def messageUrlBob = appService.getMessages(bob).responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectRequestMessage"}[0]._links.self.href
		def response = appService.getLastFirebaseMessage(bob.requestingDevice.firebaseInstanceId)

		then:
		assertResponseStatusOk(response)
		response.responseData.title == "Message received"
		response.responseData.body == "Tap to open message"
		messageUrlBob.endsWith(response.responseData.data.messageId.toString())

		when:
		appService.postMessageActionWithPassword(messageUrlBob, [ : ], bob.password)
		device.postOpenAppEvent(appService, device.operatingSystem, Device.SOME_APP_VERSION, Device.SUPPORTED_APP_VERSION_CODE, "nl-NL")
		appService.sendBuddyConnectRequest(bea, bob)
		messageUrlBob = appService.getMessages(bob, [ "onlyUnreadMessages" : true]).responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectRequestMessage"}[0]._links.self.href
		response = appService.getLastFirebaseMessage(bob.requestingDevice.firebaseInstanceId)

		then:
		assertResponseStatusOk(response)
		response.responseData.title == "Bericht ontvangen"
		response.responseData.body == "Tik om het bericht te openen"
		messageUrlBob.endsWith(response.responseData.data.messageId.toString())

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard and Bob get notifications on network goal conflict'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		assertResponseStatusNoContent(analysisService.postToAnalysisEngine(richard.requestingDevice, ["news/media"], "http://www.refdag.nl"))

		then:
		def responseRichard = analysisService.getLastFirebaseMessage(richard.requestingDevice.firebaseInstanceId)
		def messageUrlRichard = appService.getMessages(richard).responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}[0]._links.self.href
		assertResponseStatusOk(responseRichard)
		responseRichard.responseData.title == "Message received"
		responseRichard.responseData.body == "Tap to open message"
		messageUrlRichard.endsWith(responseRichard.responseData.data.messageId.toString())
		responseRichard.responseData.appOs == null
		responseRichard.responseData.appVersionCode == 0
		responseRichard.responseData.appVersionName == null


		def responseBob = analysisService.getLastFirebaseMessage(bob.requestingDevice.firebaseInstanceId)
		def messageUrlBob = appService.getMessages(bob).responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}[0]._links.self.href
		assertResponseStatusOk(responseBob)
		responseBob.responseData.title == "Message received"
		responseBob.responseData.body == "Tap to open message"
		messageUrlBob.endsWith(responseBob.responseData.data.messageId.toString())
		responseBob.responseData.appOs == null
		responseBob.responseData.appVersionCode == 0
		responseBob.responseData.appVersionName == null

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard and Bob get notifications on app usage goal conflict'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		setCreationTime(richard, "W-1 Mon 02:18")
		setGoalCreationTime(richard, GAMBLING_ACT_CAT_URL, "W-1 Mon 02:18")
		ZonedDateTime startTime = YonaServer.relativeDateTimeStringToZonedDateTime("W-1 Mon 11:00")
		ZonedDateTime endTime = startTime.plusHours(1)
		def appOs = "ANDROID"
		def appVersionCode = 123
		def appVersionName = "1.2 (I'm Rich)"
		def headers = ["Yona-App-Version" : "$appOs/$appVersionCode/$appVersionName"]

		when:
		assertResponseStatusNoContent(appService.postAppActivityToAnalysisEngine(richard, richard.requestingDevice, AppActivity.singleActivity("Poker App", startTime, endTime), headers, [:]))

		then:
		def responseRichard = analysisService.getLastFirebaseMessage(richard.requestingDevice.firebaseInstanceId)
		def messageUrlRichard = appService.getMessages(richard).responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}[0]._links.self.href
		assertResponseStatusOk(responseRichard)
		responseRichard.responseData.title == "Message received"
		responseRichard.responseData.body == "Tap to open message"
		messageUrlRichard.endsWith(responseRichard.responseData.data.messageId.toString())
		responseRichard.responseData.appOs == appOs
		responseRichard.responseData.appVersionCode == appVersionCode
		responseRichard.responseData.appVersionName == appVersionName


		def responseBob = analysisService.getLastFirebaseMessage(bob.requestingDevice.firebaseInstanceId)
		def messageUrlBob = appService.getMessages(bob).responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}[0]._links.self.href
		assertResponseStatusOk(responseBob)
		responseBob.responseData.title == "Message received"
		responseBob.responseData.body == "Tap to open message"
		messageUrlBob.endsWith(responseBob.responseData.data.messageId.toString())
		responseBob.responseData.appOs == appOs
		responseBob.responseData.appVersionCode == appVersionCode
		responseBob.responseData.appVersionName == appVersionName

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}
}