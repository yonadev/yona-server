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
	static final EXAMPLE_PNG_DATA_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAABIAAAAVCAYAAABLy77vAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAAlSURBVDhPY/j98dl/auBRgwjjUYMI41GDCONRgwjjUYMI4Wf/AVx5oubiBf17AAAAAElFTkSuQmCC"

	def setupSpec()
	{
		appService.yonaServer.restClient.encoder.putAt("multipart/form-data", new MethodClosure(this, 'encodeMultipartEntity'))
	}

	def 'Richard uploads his photo; the new URL is returned and is set on the user properties'()
	{
		given:
		def richard = addRichard()
		def multipartEntity = MultipartEntityBuilder.create()
				.addPart("file", new InputStreamBody(new ByteArrayInputStream(Base64.getDecoder().decode(EXAMPLE_PNG_DATA_BASE64)), "image/png", "MyPhoto.png"))
				.build()

		when:
		def response = appService.yonaServer.restClient.put(path: richard.editUserPhotoUrl, requestContentType :"multipart/form-data", headers: ["Yona-Password": richard.password], body: multipartEntity)

		then:
		response.status == 200
		response.contentType == "application/json"
		def newUserPhotoUrl = response.responseData?._links?.self?.href
		newUserPhotoUrl != null

		def richardAfterUpdate = appService.reloadUser(richard)
		richardAfterUpdate.userPhotoUrl == newUserPhotoUrl

		cleanup:
		appService.deleteUser(richard)
	}

	def 'The photo of Richard can be downloaded'()
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

	def 'Richard updates his photo; it is still retrievable but not linked to him'()
	{
		given:
		def richard = addRichard()
		def userPhotoUrlBefore = uploadUserPhoto(richard)

		when:
		def newUserPhotoUrl = uploadUserPhoto(richard)

		then:
		newUserPhotoUrl != userPhotoUrlBefore

		def richardAfterUpdate = appService.reloadUser(richard)
		richardAfterUpdate.userPhotoUrl == newUserPhotoUrl

		def retrievePhotoBeforeResponse = appService.yonaServer.restClient.get(path: userPhotoUrlBefore)
		retrievePhotoBeforeResponse.status == 200

		def retrieveNewPhotoResponse = appService.yonaServer.restClient.get(path: newUserPhotoUrl)
		retrieveNewPhotoResponse.status == 200

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Richard deletes his photo; it is still retrievable but not linked to him'()
	{
		given:
		def richard = addRichard()
		def userPhotoUrlBefore = uploadUserPhoto(richard)

		when:
		def response = appService.yonaServer.deleteResourceWithPassword(richard.editUserPhotoUrl, richard.password)

		then:
		response.status == 200

		def richardAfterUpdate = appService.reloadUser(richard)
		richardAfterUpdate.userPhotoUrl == null

		def retrievePhotoBeforeResponse = appService.yonaServer.restClient.get(path: userPhotoUrlBefore)
		retrievePhotoBeforeResponse.status == 200

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Bob can see the photo of Richard on the messages of Richard'()
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

	def 'Bob can see the photo of Richard in the buddy request message before accepting the request'()
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

	def 'Richard disconnects Bob as buddy; Bob can still see the photo of Richard on the messages of Richard'()
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

	def 'Richard is deleted; Bob can still see the photo of Richard on the messages of Richard'()
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

	def 'Richard updates his photo which causes buddy info change message to Bob and user photo update on process'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		def newUserPhotoUrl = uploadUserPhoto(richard)

		then:
		def bobMessagesAfterUpdate = appService.getMessages(bob)
		assert bobMessagesAfterUpdate.status == 200
		def buddyInfoUpdateMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyInfoChangeMessage"}
		buddyInfoUpdateMessages.size() == 1
		buddyInfoUpdateMessages[0]._links.self != null
		buddyInfoUpdateMessages[0]._links."yona:process" == null // Processing happens automatically these days
		buddyInfoUpdateMessages[0]._links."yona:user".href == richard.url
		buddyInfoUpdateMessages[0]._links."yona:buddy".href == bob.buddies[0].url
		buddyInfoUpdateMessages[0]._links."yona:userPhoto".href == newUserPhotoUrl
		buddyInfoUpdateMessages[0].nickname == "RQ"
		buddyInfoUpdateMessages[0].message == "User changed personal info"

		User bobAfterProcess = appService.reloadUser(bob)
		bobAfterProcess.buddies[0].userPhotoUrl == newUserPhotoUrl

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard deletes his photo which causes buddy info change message to Bob and user photo delete on process'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		bob.emailAddress = "bob@dunn.com"
		uploadUserPhoto(richard)
		appService.makeBuddies(richard, bob)
		bob = appService.reloadUser(bob)

		when:
		appService.yonaServer.deleteResourceWithPassword(richard.editUserPhotoUrl, richard.password)

		then:
		def bobMessagesAfterUpdate = appService.getMessages(bob)
		assert bobMessagesAfterUpdate.status == 200
		def buddyInfoUpdateMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyInfoChangeMessage"}
		buddyInfoUpdateMessages.size() == 1
		buddyInfoUpdateMessages[0]._links.self != null
		buddyInfoUpdateMessages[0]._links."yona:process" == null // Processing happens automatically these days
		buddyInfoUpdateMessages[0]._links."yona:user".href == richard.url
		buddyInfoUpdateMessages[0]._links."yona:buddy".href == bob.buddies[0].url
		buddyInfoUpdateMessages[0]._links."yona:userPhoto"?.href == null
		buddyInfoUpdateMessages[0].nickname == "RQ"
		buddyInfoUpdateMessages[0].message == "User changed personal info"

		User bobAfterProcess = appService.reloadUser(bob)
		bobAfterProcess.buddies[0].userPhotoUrl == null

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Harry cannot update photo of Richard with a wrong password'()
	{
		given:
		def richard = addRichard()

		when:
		def multipartEntity = MultipartEntityBuilder.create()
				.addPart("file", new InputStreamBody(new ByteArrayInputStream(Base64.getDecoder().decode(EXAMPLE_PNG_DATA_BASE64)), "image/png", "MyPhoto.png"))
				.build()
		def response = appService.yonaServer.restClient.put(path: richard.editUserPhotoUrl, requestContentType :"multipart/form-data", headers: ["Yona-Password": "Wrong password"], body: multipartEntity)

		then:
		response.status == 400

		def richardAfterUpdate = appService.reloadUser(richard)
		richardAfterUpdate.userPhotoUrl == null

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Harry cannot delete photo of Richard with a wrong password'()
	{
		given:
		def richard = addRichard()
		def userPhotoUrlBefore = uploadUserPhoto(richard)

		when:
		def response = appService.yonaServer.deleteResourceWithPassword(richard.editUserPhotoUrl, "Wrong password")

		then:
		response.status == 400

		def richardAfterUpdate = appService.reloadUser(richard)
		richardAfterUpdate.userPhotoUrl == userPhotoUrlBefore

		cleanup:
		appService.deleteUser(richard)
	}

	def uploadUserPhoto(User user)
	{
		def multipartEntity = MultipartEntityBuilder.create()
				.addPart("file", new InputStreamBody(new ByteArrayInputStream(Base64.getDecoder().decode(EXAMPLE_PNG_DATA_BASE64)), "image/png", "MyPhoto.png"))
				.build()
		def response = appService.yonaServer.restClient.put(path: user.editUserPhotoUrl, requestContentType :"multipart/form-data", headers: ["Yona-Password": user.password], body: multipartEntity)
		response.responseData?._links?.self?.href
	}

	def encodeMultipartEntity(HttpEntity entity)
	{
		return entity
	}
}
