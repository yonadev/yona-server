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
import spock.lang.IgnoreRest

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
	}

	def encodeMultipartEntity(HttpEntity entity)
	{
		return entity
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

	def 'User photo is present on buddy request message'()
	{
		//TODO
	}

	def 'User photo is present on buddy disconnect message'()
	{
		//TODO
	}

	def 'User photo is present on buddy messages'()
	{
		//TODO
	}

	def 'After delete of user, user photo is still present in buddy messages'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		def richardPhotoUrl = uploadUserPhoto(richard)
		appService.deleteUser(richard)

		when:
		//TODO: retrieve buddy messages
		def response = appService.yonaServer.restClient.get(path: richardPhotoUrl)

		then:
		response.status == 200
	}

	@IgnoreRest
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
}
