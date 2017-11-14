/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import org.apache.http.HttpEntity
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.InputStreamBody
import org.codehaus.groovy.runtime.MethodClosure

import groovy.json.*
import nu.yona.server.test.User

class UserPhotoTest extends AbstractAppServiceIntegrationTest
{
	def 'User uploads user photo'()
	{
		given:
		def richard = addRichard()
		appService.yonaServer.restClient.encoder.putAt("multipart/form-data", new MethodClosure(this, 'encodeMultipartEntity'))
		def multipartEntity = MultipartEntityBuilder.create()
				.addPart("file", new InputStreamBody(new ByteArrayInputStream(Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7")), "image/gif", "MyPhoto.png"))
				.build()

		when:
		def response = appService.yonaServer.restClient.put(path: richard.editUserPhotoUrl, requestContentType :"multipart/form-data", headers: ["Yona-Password": richard.password], body: multipartEntity)

		then:
		response.status == 200
		response.contentType == "application/json"
		response.responseData?._links?.self?.href != null

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Download user photo'()
	{
		given:
		def richard = addRichard()
		def userPhotoUrl = uploadUserPhoto(richard)

		when:
		def response = appService.yonaServer.restClient.get(path: userPhotoUrl)

		then:
		response.status == 200
		response.contentType == "image/png"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'User photo is present on buddy messages'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		def richardPhotoUrl = uploadUserPhoto(richard)

		then:
		def bobMessages = appService.getMessages(bob)
		def bobMessagesFromRichard = bobMessages.responseData._embedded?."yona:messages".findAll{ it."nickname" == "RQ"}
		bobMessagesFromRichard.each
		{
			it._links?."yona:userPhoto"?.href == richardPhotoUrl
		}

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Before buddy connect, user photo is already present in buddy request message and is retrievable'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		def richardPhotoUrl = uploadUserPhoto(richard)

		when:
		bob.emailAddress = "bob@dunn.net"
		appService.sendBuddyConnectRequest(richard, bob)

		then:
		def bobMessages = appService.getMessages(bob)
		def bobMessagesFromRichard = bobMessages.responseData._embedded?."yona:messages".findAll{ it."nickname" == "RQ"}
		bobMessagesFromRichard.each
		{
			it._links?."yona:userPhoto"?.href == richardPhotoUrl
		}
		def response = appService.yonaServer.restClient.get(path: richardPhotoUrl)
		response.status == 200

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'After user photo update, the previous user photo is still retrievable'()
	{
		given:
		def richard = addRichard()
		def previousUserPhotoUrl = uploadUserPhoto(richard)

		when:
		uploadUserPhoto(richard)

		then:
		def response = appService.yonaServer.restClient.get(path: previousUserPhotoUrl)
		response.status == 200

		cleanup:
		appService.deleteUser(richard)
	}

	def 'After buddy disconnect, user photo is still present in buddy messages and is still retrievable'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		def buddy = appService.getBuddies(richard)[0]
		def richardPhotoUrl = uploadUserPhoto(richard)

		when:
		appService.removeBuddy(richard, buddy, "Bob, as you know our ways parted, so I'll remove you as buddy.")

		then:
		def bobMessages = appService.getMessages(bob)
		def bobMessagesFromRichard = bobMessages.responseData._embedded?."yona:messages".findAll{ it."nickname" == "RQ"}
		bobMessagesFromRichard.each
		{
			it._links?."yona:userPhoto"?.href == richardPhotoUrl
		}
		def response = appService.yonaServer.restClient.get(path: richardPhotoUrl)
		response.status == 200

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'After delete of user, user photo is still present in buddy messages and is still retrievable'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		def richardPhotoUrl = uploadUserPhoto(richard)

		when:
		appService.deleteUser(richard)

		then:
		def bobMessages = appService.getMessages(bob)
		def bobMessagesFromRichard = bobMessages.responseData._embedded?."yona:messages".findAll{ it."nickname" == "RQ"}
		bobMessagesFromRichard.each
		{
			it._links?."yona:userPhoto"?.href == richardPhotoUrl
		}
		def response = appService.yonaServer.restClient.get(path: richardPhotoUrl)
		response.status == 200

		cleanup:
		appService.deleteUser(bob)
	}

	def 'Buddy updates user photo which causes buddy info change message and user photo update on process'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		uploadUserPhoto(bob)
		User bobAfterUpdate = appService.reloadUser(bob)
		def richardMessagesAfterUpdate = appService.getMessages(richard)
		assert richardMessagesAfterUpdate.status == 200
		def buddyInfoUpdateMessages = richardMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyInfoChangeMessage"}

		then:
		bobAfterUpdate.userPhotoUrl != bob.userPhotoUrl

		buddyInfoUpdateMessages.size() == 1
		buddyInfoUpdateMessages[0]._links.self != null
		buddyInfoUpdateMessages[0]._links."yona:process" == null // Processing happens automatically these days
		buddyInfoUpdateMessages[0]._links."yona:user".href == bob.url
		buddyInfoUpdateMessages[0]._links."yona:buddy".href == richard.buddies[0].url
		buddyInfoUpdateMessages[0]._links."yona:userPhoto".href == bobAfterUpdate.userPhotoUrl
		buddyInfoUpdateMessages[0].nickname == "BD"
		buddyInfoUpdateMessages[0].message == "User changed personal info"

		User richardAfterProcess = appService.reloadUser(richard)
		richardAfterProcess.buddies[0].userPhotoUrl == bobAfterUpdate.userPhotoUrl

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def uploadUserPhoto(User user)
	{
		appService.yonaServer.restClient.encoder.putAt("multipart/form-data", new MethodClosure(this, 'encodeMultipartEntity'))
		def multipartEntity = MultipartEntityBuilder.create()
				.addPart("file", new InputStreamBody(new ByteArrayInputStream(Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7")), "image/gif", "MyPhoto.png"))
				.build()
		def response = appService.yonaServer.restClient.put(path: user.editUserPhotoUrl, requestContentType :"multipart/form-data", headers: ["Yona-Password": user.password], body: multipartEntity)
		response.responseData?._links?.self?.href
	}

	def encodeMultipartEntity(HttpEntity entity)
	{
		return entity
	}
}
