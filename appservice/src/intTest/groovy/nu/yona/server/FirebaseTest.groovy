/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.*

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
		assert responseRichard.responseData.title == "Message received"
		assert responseRichard.responseData.body == "Tap to open message"
		assert messageUrlRichard.endsWith(responseRichard.responseData.data.messageId.toString())

		when:
		def messageUrlBob = appService.getMessages(bob).responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectRequestMessage"}[0]._links.self.href
		def responseBob = appService.getLastFirebaseMessage(bob.requestingDevice.firebaseInstanceId)

		then:
		assertResponseStatusOk(responseBob)
		assert responseBob.responseData.title == "Message received"
		assert responseBob.responseData.body == "Tap to open message"
		assert messageUrlBob.endsWith(responseBob.responseData.data.messageId.toString())

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
		appService.sendBuddyConnectRequest(richard, bobAndroid)

		when:
		def messageUrlBobAndroid = appService.getMessages(bobAndroid).responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectRequestMessage"}[0]._links.self.href
		def responseBobAndroid = appService.getLastFirebaseMessage(bobAndroid.requestingDevice.firebaseInstanceId)

		then:
		assertResponseStatusOk(responseBobAndroid)
		assert responseBobAndroid.responseData.title == "Bericht ontvangen"
		assert responseBobAndroid.responseData.body == "Tik om het bericht te openen"
		assert messageUrlBobAndroid.endsWith(responseBobAndroid.responseData.data.messageId.toString())

		when:
		def messageUrlBobIos = appService.getMessages(bobIos).responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectRequestMessage"}[0]._links.self.href
		def responseBobIos = appService.getLastFirebaseMessage(bobIos.requestingDevice.firebaseInstanceId)

		then:
		assertResponseStatusOk(responseBobIos)
		assert responseBobIos.responseData.title == "Message received"
		assert responseBobIos.responseData.body == "Tap to open message"
		assert messageUrlBobIos.endsWith(responseBobIos.responseData.data.messageId.toString())

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
		assert response.responseData.title == "Message received"
		assert response.responseData.body == "Tap to open message"
		assert messageUrlBob.endsWith(response.responseData.data.messageId.toString())

		when:
		appService.postMessageActionWithPassword(messageUrlBob, [ : ], bob.password)
		device.postOpenAppEvent(appService, device.operatingSystem, Device.SOME_APP_VERSION, Device.SUPPORTED_APP_VERSION_CODE, "nl-NL")
		appService.sendBuddyConnectRequest(bea, bob)
		messageUrlBob = appService.getMessages(bob, [ "onlyUnreadMessages" : true]).responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectRequestMessage"}[0]._links.self.href
		response = appService.getLastFirebaseMessage(bob.requestingDevice.firebaseInstanceId)

		then:
		assertResponseStatusOk(response)
		assert response.responseData.title == "Bericht ontvangen"
		assert response.responseData.body == "Tik om het bericht te openen"
		assert messageUrlBob.endsWith(response.responseData.data.messageId.toString())

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}
}