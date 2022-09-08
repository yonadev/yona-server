/*******************************************************************************
 * Copyright (c) 2019, 2022 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.assertResponseStatusNoContent
import static nu.yona.server.test.CommonAssertions.assertResponseStatusOk

import java.time.ZonedDateTime
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

import nu.yona.server.test.AppActivity
import nu.yona.server.test.Device
import nu.yona.server.test.User

class FirebaseTest extends AbstractAppServiceIntegrationTest
{
	def setupSpec()
	{
		enableConcurrentRequests(5)
	}

	def 'Richard and Bob both have a notification for the buddy request/acceptance'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		def messageUrlRichard = appService.getMessages(richard).json._embedded."yona:messages".findAll { it."@type" == "BuddyConnectResponseMessage" }[0]._links.self.href
		def responseRichard = appService.getLastFirebaseMessage(richard.requestingDevice.firebaseInstanceId)

		then:
		assertResponseStatusOk(responseRichard)
		responseRichard.json.title == "Message received"
		responseRichard.json.body == "Tap to open message"
		messageUrlRichard.endsWith(responseRichard.json.data.messageId.toString())

		when:
		def messageUrlBob = appService.getMessages(bob).json._embedded."yona:messages".findAll { it."@type" == "BuddyConnectRequestMessage" }[0]._links.self.href
		def responseBob = appService.getLastFirebaseMessage(bob.requestingDevice.firebaseInstanceId)

		then:
		assertResponseStatusOk(responseBob)
		responseBob.json.title == "Message received"
		responseBob.json.body == "Tap to open message"
		messageUrlBob.endsWith(responseBob.json.data.messageId.toString())

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
		def richardAppOs = "ANDROID"
		def richardAppVersionCode = 123
		def richardAppVersionName = "1.2 (I'm Rich)"
		def headers = ["Yona-App-Version": "$richardAppOs/$richardAppVersionCode/$richardAppVersionName"]
		appService.sendBuddyConnectRequest(richard, bobAndroid, true, [:], headers)

		when:
		def messageUrlBobAndroid = appService.getMessages(bobAndroid, [:], headers).json._embedded."yona:messages".findAll { it."@type" == "BuddyConnectRequestMessage" }[0]._links.self.href
		def responseBobAndroid = appService.getLastFirebaseMessage(bobAndroid.requestingDevice.firebaseInstanceId)

		then:
		assertResponseStatusOk(responseBobAndroid)
		responseBobAndroid.json.title == "Bericht ontvangen"
		responseBobAndroid.json.body == "Tik om het bericht te openen"
		messageUrlBobAndroid.endsWith(responseBobAndroid.json.data.messageId.toString())
		responseBobAndroid.json.appOs == richardAppOs
		responseBobAndroid.json.appVersionCode == richardAppVersionCode
		responseBobAndroid.json.appVersionName == richardAppVersionName

		when:
		def messageUrlBobIos = appService.getMessages(bobIos).json._embedded."yona:messages".findAll { it."@type" == "BuddyConnectRequestMessage" }[0]._links.self.href
		def responseBobIos = appService.getLastFirebaseMessage(bobIos.requestingDevice.firebaseInstanceId)

		then:
		assertResponseStatusOk(responseBobIos)
		responseBobIos.json.title == "Message received"
		responseBobIos.json.body == "Tap to open message"
		messageUrlBobIos.endsWith(responseBobIos.json.data.messageId.toString())
		responseBobIos.json.appOs == richardAppOs
		responseBobIos.json.appVersionCode == richardAppVersionCode
		responseBobIos.json.appVersionName == richardAppVersionName

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
		def messageUrlBob = appService.getMessages(bob).json._embedded."yona:messages".findAll { it."@type" == "BuddyConnectRequestMessage" }[0]._links.self.href
		def response = appService.getLastFirebaseMessage(bob.requestingDevice.firebaseInstanceId)

		then:
		assertResponseStatusOk(response)
		response.json.title == "Message received"
		response.json.body == "Tap to open message"
		messageUrlBob.endsWith(response.json.data.messageId.toString())

		when:
		appService.postMessageActionWithPassword(messageUrlBob, [:], bob.password)
		device.postOpenAppEvent(appService, device.operatingSystem, Device.SOME_APP_VERSION, Device.SUPPORTED_APP_VERSION_CODE, "nl-NL")
		appService.sendBuddyConnectRequest(bea, bob)
		messageUrlBob = appService.getMessages(bob, ["onlyUnreadMessages": true]).json._embedded."yona:messages".findAll { it."@type" == "BuddyConnectRequestMessage" }[0]._links.self.href
		response = appService.getLastFirebaseMessage(bob.requestingDevice.firebaseInstanceId)

		then:
		assertResponseStatusOk(response)
		response.json.title == "Bericht ontvangen"
		response.json.body == "Tik om het bericht te openen"
		messageUrlBob.endsWith(response.json.data.messageId.toString())

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
		def messageUrlRichard = appService.getMessages(richard).json._embedded."yona:messages".findAll { it."@type" == "GoalConflictMessage" }[0]._links.self.href
		assertResponseStatusOk(responseRichard)
		responseRichard.json.title == "Message received"
		responseRichard.json.body == "Tap to open message"
		messageUrlRichard.endsWith(responseRichard.json.data.messageId.toString())
		responseRichard.json.appOs == null
		responseRichard.json.appVersionCode == 0
		responseRichard.json.appVersionName == null


		def responseBob = analysisService.getLastFirebaseMessage(bob.requestingDevice.firebaseInstanceId)
		def messageUrlBob = appService.getMessages(bob).json._embedded."yona:messages".findAll { it."@type" == "GoalConflictMessage" }[0]._links.self.href
		assertResponseStatusOk(responseBob)
		responseBob.json.title == "Message received"
		responseBob.json.body == "Tap to open message"
		messageUrlBob.endsWith(responseBob.json.data.messageId.toString())
		responseBob.json.appOs == null
		responseBob.json.appVersionCode == 0
		responseBob.json.appVersionName == null

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	/*
	 * Note that this test also verifies the pass-through headers functionality, as the app usage is posted on the app service while the Firebase notification is sent from the analysis service.
	 */

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
		def richardAppOs = "ANDROID"
		def richardAppVersionCode = 123
		def richardAppVersionName = "1.2 (I'm Rich)"
		def headers = ["Yona-App-Version": "$richardAppOs/$richardAppVersionCode/$richardAppVersionName"]

		when:
		assertResponseStatusNoContent(appService.postAppActivityToAnalysisEngine(richard, richard.requestingDevice, AppActivity.singleActivity("Poker App", startTime, endTime), [:], headers))

		then:
		def responseRichard = analysisService.getLastFirebaseMessage(richard.requestingDevice.firebaseInstanceId)
		def messageUrlRichard = appService.getMessages(richard).json._embedded."yona:messages".findAll { it."@type" == "GoalConflictMessage" }[0]._links.self.href
		assertResponseStatusOk(responseRichard)
		responseRichard.json.title == "Message received"
		responseRichard.json.body == "Tap to open message"
		messageUrlRichard.endsWith(responseRichard.json.data.messageId.toString())
		responseRichard.json.appOs == richardAppOs
		responseRichard.json.appVersionCode == richardAppVersionCode
		responseRichard.json.appVersionName == richardAppVersionName


		def responseBob = analysisService.getLastFirebaseMessage(bob.requestingDevice.firebaseInstanceId)
		def messageUrlBob = appService.getMessages(bob).json._embedded."yona:messages".findAll { it."@type" == "GoalConflictMessage" }[0]._links.self.href
		assertResponseStatusOk(responseBob)
		responseBob.json.title == "Message received"
		responseBob.json.body == "Tap to open message"
		messageUrlBob.endsWith(responseBob.json.data.messageId.toString())
		responseBob.json.appOs == richardAppOs
		responseBob.json.appVersionCode == richardAppVersionCode
		responseBob.json.appVersionName == richardAppVersionName

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Pass-through headers are handled correctly during multithreading'()
	{
		given:
		def appHeader1 = "ANDROID/1111/1.1.1"
		def appHeader2 = "ANDROID/2222/2.2.2"
		ExecutorService executorService = Executors.newFixedThreadPool(5)
		Future task1 = getResource(executorService, "/test/passThroughHeaders", ["Yona-App-Version": appHeader1])
		Future task2 = getResource(executorService, "/test/passThroughHeaders", ["Yona-App-Version": appHeader2])

		when:
		def response1 = task1.get()
		def response2 = task2.get()

		then:
		response1.json.passThroughHeaders."Yona-App-Version" == appHeader1
		response2.json.passThroughHeaders."Yona-App-Version" == appHeader2
	}

	Future getResource(ExecutorService executorService, path, headers = [:])
	{
		executorService.submit({ return appService.yonaServer.getJson(path, [:], headers) } as Callable<YonaServer.Response>)
	}
}
