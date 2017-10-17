/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.InputStreamBody

import groovy.json.*
import nu.yona.server.test.User

class UserPhotoTest extends AbstractAppServiceIntegrationTest
{
	def 'User uploads user photo'()
	{
		when:
		MultipartEntityBuilder multiPartContent = new MultipartEntityBuilder()
		multiPartContent.addPart("file", new InputStreamBody(multipartImageFile.inputStream, multipartImageFile.contentType, multipartImageFile.originalFilename))
		def response = appService.yonaServer.restClient.post(path: "/userPhotos/", contentType:"multipart/form-data", body: multiPartContent)

		then:
		response.status == 201
		response.contentType == "application/json"
		response.responseData._links.self.href != null
	}

	def 'User downloads user photo'()
	{
		given:
		def richard = addRichardWithPhoto()

		when:
		def response = appService.yonaServer.restClient.get(path: richard.userPhotoUrl)

		then:
		response.status == 200
		response.contentType == "image/png"
	}

	def 'User adds user photo on create'()
	{
		given:
		def userPhotoUrl = "TODO"

		when:
		def richard = appService.addUser(User.makeUserJsonString("Richard", "Quinn", "RQ", userPhotoUrl, "+$timestamp"))

		then:
		richard.userPhotoUrl == userPhotoUrl

		cleanup:
		appService.deleteUser(richard)
	}

	def 'User adds user photo on update'()
	{
		given:
		def richard = addRichard()
		def userPhotoUrl = "TODO"

		when:
		def updatedRichardJson = richard.convertToJson()
		updatedRichardJson._links."yona:userPhoto" = userPhotoUrl
		User richardAfterUpdate = appService.updateUser(appService.&assertUserUpdateResponseDetails, new User(updatedRichardJson))

		then:
		richardAfterUpdate.userPhotoUrl == userPhotoUrl

		cleanup:
		appService.deleteUser(richard)
	}

	def 'User removes user photo on update'()
	{
		given:
		def richard = addRichardWithPhoto()

		when:
		def updatedRichardJson = richard.convertToJson()
		updatedRichardJson._links."yona:userPhoto" = null
		User richardAfterUpdate = appService.updateUser(appService.&assertUserUpdateResponseDetails, new User(updatedRichardJson))

		then:
		richardAfterUpdate.userPhotoUrl == null

		cleanup:
		appService.deleteUser(richard)
	}

	def 'User updates user photo on update'()
	{
		given:
		def richard = addRichardWithPhoto()
		def userPhotoUrl = "TODO"

		when:
		def updatedRichardJson = richard.convertToJson()
		updatedRichardJson._links."yona:userPhoto" = userPhotoUrl
		User richardAfterUpdate = appService.updateUser(appService.&assertUserUpdateResponseDetails, new User(updatedRichardJson))

		then:
		richardAfterUpdate.userPhotoUrl == userPhotoUrl

		cleanup:
		appService.deleteUser(richard)
	}

	def addRichardWithPhoto()
	{
		def richard = addRichard()
		def userPhotoUrl = "TODO"
		def richardJsonWithPhoto = richard.convertToJson()
		richardJsonWithPhoto._links."yona:userPhoto" = userPhotoUrl
		return appService.updateUser(appService.&assertUserUpdateResponseDetails, new User(richardJsonWithPhoto))
	}

	def 'Buddy updates user photo which causes buddy info change message and user photo update on process'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		def userPhotoUrl = "TODO"

		when:
		def updatedBobJson = bob.convertToJson()
		updatedBobJson._links."yona:userPhoto" = userPhotoUrl
		User bobAfterUpdate = appService.updateUser(appService.&assertUserUpdateResponseDetails, new User(updatedBobJson))
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
		buddyInfoUpdateMessages[0].nickname == "Bobby"
		buddyInfoUpdateMessages[0].message == "User changed personal info"

		User richardAfterProcess = appService.reloadUser(richard)
		richardAfterProcess.buddies[0].userPhotoUrl == bobAfterUpdate.userPhotoUrl

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}
}
