/*******************************************************************************
 * Copyright (c) 2017, 2019 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.assertResponseStatus
import static nu.yona.server.test.CommonAssertions.assertResponseStatusNoContent
import static nu.yona.server.test.CommonAssertions.assertResponseStatusOk

import org.apache.http.HttpEntity
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.InputStreamBody
import org.codehaus.groovy.runtime.MethodClosure

import nu.yona.server.test.User

class UserPhotoTest extends AbstractAppServiceIntegrationTest
{
	static final EXAMPLE_PNG_DATA_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAABIAAAAVCAYAAABLy77vAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAAlSURBVDhPY/j98dl/auBRgwjjUYMI41GDCONRgwjjUYMI4Wf/AVx5oubiBf17AAAAAElFTkSuQmCC"
	static final EXAMPLE_GIF_DATA_BASE64 = "R0lGODlhFAAXAPcAAAAAAAAAMwAAZgAAmQAAzAAA/wArAAArMwArZgArmQArzAAr/wBVAABVMwBVZgBVmQBVzABV/wCAAACAMwCAZgCAmQCAzACA/wCqAACqMwCqZgCqmQCqzACq/wDVAADVMwDVZgDVmQDVzADV/wD/AAD/MwD/ZgD/mQD/zAD//zMAADMAMzMAZjMAmTMAzDMA/zMrADMrMzMrZjMrmTMrzDMr/zNVADNVMzNVZjNVmTNVzDNV/zOAADOAMzOAZjOAmTOAzDOA/zOqADOqMzOqZjOqmTOqzDOq/zPVADPVMzPVZjPVmTPVzDPV/zP/ADP/MzP/ZjP/mTP/zDP//2YAAGYAM2YAZmYAmWYAzGYA/2YrAGYrM2YrZmYrmWYrzGYr/2ZVAGZVM2ZVZmZVmWZVzGZV/2aAAGaAM2aAZmaAmWaAzGaA/2aqAGaqM2aqZmaqmWaqzGaq/2bVAGbVM2bVZmbVmWbVzGbV/2b/AGb/M2b/Zmb/mWb/zGb//5kAAJkAM5kAZpkAmZkAzJkA/5krAJkrM5krZpkrmZkrzJkr/5lVAJlVM5lVZplVmZlVzJlV/5mAAJmAM5mAZpmAmZmAzJmA/5mqAJmqM5mqZpmqmZmqzJmq/5nVAJnVM5nVZpnVmZnVzJnV/5n/AJn/M5n/Zpn/mZn/zJn//8wAAMwAM8wAZswAmcwAzMwA/8wrAMwrM8wrZswrmcwrzMwr/8xVAMxVM8xVZsxVmcxVzMxV/8yAAMyAM8yAZsyAmcyAzMyA/8yqAMyqM8yqZsyqmcyqzMyq/8zVAMzVM8zVZszVmczVzMzV/8z/AMz/M8z/Zsz/mcz/zMz///8AAP8AM/8AZv8Amf8AzP8A//8rAP8rM/8rZv8rmf8rzP8r//9VAP9VM/9VZv9Vmf9VzP9V//+AAP+AM/+AZv+Amf+AzP+A//+qAP+qM/+qZv+qmf+qzP+q///VAP/VM//VZv/Vmf/VzP/V////AP//M///Zv//mf//zP///wAAAAAAAAAAAAAAACH5BAEAAPwALAAAAAAUABcAAAilAAFA2EewoMGDBaMhXIhQIcOH+xxCnEjRYCZimS5mxMhxo0aMmoZ91EiyY8lMIVEOS2kS48WVmYaB0phM08VPGpVpDMkSZUybLoPG/NQR40qRLS/ahJlMIyimPJtmPApUZU+eF0Ei/SmSZ1eYS5/a1HnTpc6jMKnu3KrVq82UR63GfOqx5kqiZX9yhWlUY9qqINdWxWpXZaaC+hISTByxouPHBgMCADs="
	static final EXAMPLE_JPEG_DATA_BASE64 = "/9j/4AAQSkZJRgABAQEAYABgAAD/4RCcRXhpZgAATU0AKgAAAAgABAE7AAIAAAAOAAAISodpAAQAAAABAAAIWJydAAEAAAAcAAAQeOocAAcAAAgMAAAAPgAAAAAc6gAAAAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEFyaXMgdmFuIERpamsAAAHqHAAHAAAIDAAACGoAAAAAHOoAAAAIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEEAcgBpAHMAIAB2AGEAbgAgAEQAaQBqAGsAAAD/4QpmaHR0cDovL25zLmFkb2JlLmNvbS94YXAvMS4wLwA8P3hwYWNrZXQgYmVnaW49J++7vycgaWQ9J1c1TTBNcENlaGlIenJlU3pOVGN6a2M5ZCc/Pg0KPHg6eG1wbWV0YSB4bWxuczp4PSJhZG9iZTpuczptZXRhLyI+PHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj48cmRmOkRlc2NyaXB0aW9uIHJkZjphYm91dD0idXVpZDpmYWY1YmRkNS1iYTNkLTExZGEtYWQzMS1kMzNkNzUxODJmMWIiIHhtbG5zOmRjPSJodHRwOi8vcHVybC5vcmcvZGMvZWxlbWVudHMvMS4xLyIvPjxyZGY6RGVzY3JpcHRpb24gcmRmOmFib3V0PSJ1dWlkOmZhZjViZGQ1LWJhM2QtMTFkYS1hZDMxLWQzM2Q3NTE4MmYxYiIgeG1sbnM6ZGM9Imh0dHA6Ly9wdXJsLm9yZy9kYy9lbGVtZW50cy8xLjEvIj48ZGM6Y3JlYXRvcj48cmRmOlNlcSB4bWxuczpyZGY9Imh0dHA6Ly93d3cudzMub3JnLzE5OTkvMDIvMjItcmRmLXN5bnRheC1ucyMiPjxyZGY6bGk+QXJpcyB2YW4gRGlqazwvcmRmOmxpPjwvcmRmOlNlcT4NCgkJCTwvZGM6Y3JlYXRvcj48L3JkZjpEZXNjcmlwdGlvbj48L3JkZjpSREY+PC94OnhtcG1ldGE+DQogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgIDw/eHBhY2tldCBlbmQ9J3cnPz7/2wBDAAcFBQYFBAcGBQYIBwcIChELCgkJChUPEAwRGBUaGRgVGBcbHichGx0lHRcYIi4iJSgpKywrGiAvMy8qMicqKyr/2wBDAQcICAoJChQLCxQqHBgcKioqKioqKioqKioqKioqKioqKioqKioqKioqKioqKioqKioqKioqKioqKioqKioqKir/wAARCAAWABcDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDy2loFAr6c7QFFLRVFCUtFFJCQCiiimM//2Q=="

	def setupSpec()
	{
		// Register MIME type encoder to call our method encodeMultipartEntity
		appService.yonaServer.restClient.encoder["multipart/form-data"] = new MethodClosure(this, 'encodeMultipartEntity')
	}

	def 'Richard uploads his photo; the new URL is returned and is set on the user properties'()
	{
		given:
		def richard = addRichard()
		def multipartEntity = MultipartEntityBuilder.create()
				.addPart("file", new InputStreamBody(new ByteArrayInputStream(Base64.getDecoder().decode(EXAMPLE_PNG_DATA_BASE64)), "image/png", "MyPhoto.png"))
				.build()

		when:
		def response = appService.yonaServer.restClient.put(path: richard.editUserPhotoUrl, requestContentType: "multipart/form-data", headers: ["Yona-Password": richard.password], body: multipartEntity)

		then:
		assertResponseStatusOk(response)
		response.contentType == "application/json"
		def newUserPhotoUrl = response.responseData?._links?."yona:userPhoto"?.href
		newUserPhotoUrl != null

		def richardAfterUpdate = appService.reloadUser(richard)
		richardAfterUpdate.userPhotoUrl == newUserPhotoUrl

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Richard uploads his photo in GIF format; it is converted to PNG format'()
	{
		given:
		def richard = addRichard()
		def multipartEntity = MultipartEntityBuilder.create()
				.addPart("file", new InputStreamBody(new ByteArrayInputStream(Base64.getDecoder().decode(EXAMPLE_GIF_DATA_BASE64)), "image/gif", "MyPhoto.gif"))
				.build()

		when:
		def response = appService.yonaServer.restClient.put(path: richard.editUserPhotoUrl, requestContentType: "multipart/form-data", headers: ["Yona-Password": richard.password], body: multipartEntity)

		then:
		assertResponseStatusOk(response)
		response.contentType == "application/json"
		def newUserPhotoUrl = response.responseData?._links?."yona:userPhoto"?.href
		newUserPhotoUrl != null

		def downloadResponse = appService.yonaServer.restClient.get(path: newUserPhotoUrl)
		assertResponseStatusOk(downloadResponse)
		downloadResponse.contentType == "image/png"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Richard uploads his photo in JPEG format; it is converted to PNG format'()
	{
		given:
		def richard = addRichard()
		def multipartEntity = MultipartEntityBuilder.create()
				.addPart("file", new InputStreamBody(new ByteArrayInputStream(Base64.getDecoder().decode(EXAMPLE_JPEG_DATA_BASE64)), "image/jpeg", "MyPhoto.jpg"))
				.build()

		when:
		def response = appService.yonaServer.restClient.put(path: richard.editUserPhotoUrl, requestContentType: "multipart/form-data", headers: ["Yona-Password": richard.password], body: multipartEntity)

		then:
		assertResponseStatusOk(response)
		response.contentType == "application/json"
		def newUserPhotoUrl = response.responseData?._links?."yona:userPhoto"?.href
		newUserPhotoUrl != null

		def downloadResponse = appService.yonaServer.restClient.get(path: newUserPhotoUrl)
		assertResponseStatusOk(downloadResponse)
		downloadResponse.contentType == "image/png"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Richard uploads a photo that is too large; returns HTTP 413 Too Large'()
	{
		given:
		def richard = addRichard()
		def multipartEntity = MultipartEntityBuilder.create()
				.addPart("file", new InputStreamBody(new ByteArrayInputStream(new File('src/intTest/resources/UserPhotoTest_ImageTooLarge.png').getBytes()), "image/png", "UserPhotoTest_ImageTooLarge.png"))
				.build()

		when:
		def response = appService.yonaServer.restClient.put(path: richard.editUserPhotoUrl, requestContentType: "multipart/form-data", headers: ["Yona-Password": richard.password], body: multipartEntity)

		then:
		assertResponseStatus(response, 413)
		response.responseData.code == null // As the app should take care for uploading a resized photo, this is not a user error, so it does not need to have a code

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
		assertResponseStatusOk(response)
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
		assertResponseStatusOk(retrievePhotoBeforeResponse)

		def retrieveNewPhotoResponse = appService.yonaServer.restClient.get(path: newUserPhotoUrl)
		assertResponseStatusOk(retrieveNewPhotoResponse)

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
		assertResponseStatusNoContent(response)

		def richardAfterUpdate = appService.reloadUser(richard)
		richardAfterUpdate.userPhotoUrl == null

		def retrievePhotoBeforeResponse = appService.yonaServer.restClient.get(path: userPhotoUrlBefore)
		assertResponseStatusOk(retrievePhotoBeforeResponse)

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Richard and Bob can see each other\'s photos after becoming buddies'()
	{
		given:
		User richard = addRichard(false)
		def richardUserPhotoUrl = uploadUserPhoto(richard)
		User bob = addBob(false)
		def bobUserPhotoUrl = uploadUserPhoto(bob)

		when:
		bob.emailAddress = "bob@dunn.com"
		appService.makeBuddies(richard, bob)
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)

		then:
		richard.buddies[0].user.userPhotoUrl == bobUserPhotoUrl
		bob.buddies[0].user.userPhotoUrl == richardUserPhotoUrl

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
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
		def bobMessagesResponse = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesResponse)
		def bobMessagesFromRichard = bobMessagesResponse.responseData._embedded?."yona:messages"?.findAll { it."nickname" == "RQ" }
		bobMessagesFromRichard.each {
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
		def bobMessagesResponse = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesResponse)
		def bobMessagesFromRichard = bobMessagesResponse.responseData._embedded?."yona:messages"?.findAll { it."nickname" == "RQ" }
		bobMessagesFromRichard.each {
			it._links?."yona:userPhoto"?.href == richardPhotoUrl
		}
		def response = appService.yonaServer.restClient.get(path: richardPhotoUrl)
		assertResponseStatusOk(response)

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
		def bobMessagesResponse = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesResponse)
		def bobMessagesFromRichard = bobMessagesResponse.responseData._embedded?."yona:messages"?.findAll { it."nickname" == "RQ" }
		bobMessagesFromRichard.each {
			it._links?."yona:userPhoto"?.href == richardPhotoUrl
		}
		def response = appService.yonaServer.restClient.get(path: richardPhotoUrl)
		assertResponseStatusOk(response)

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
		def bobMessagesResponse = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesResponse)
		def bobMessagesFromRichard = bobMessagesResponse.responseData._embedded?."yona:messages"?.findAll { it."nickname" == "RQ" }
		bobMessagesFromRichard.each {
			it._links?."yona:userPhoto"?.href == richardPhotoUrl
		}
		def response = appService.yonaServer.restClient.get(path: richardPhotoUrl)
		assertResponseStatusOk(response)

		cleanup:
		appService.deleteUser(bob)
	}

	def 'Richard adds a photo, which causes buddy info change message to Bob and user photo linkage on process'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		def newUserPhotoUrl = uploadUserPhoto(richard)

		then:
		def bobMessagesAfterUpdate = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesAfterUpdate)
		def buddyInfoUpdateMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages"?.findAll { it."@type" == "BuddyInfoChangeMessage" }
		buddyInfoUpdateMessages.size() == 1
		buddyInfoUpdateMessages[0]._links.self != null
		buddyInfoUpdateMessages[0]._links."yona:process" == null // Processing happens automatically these days
		buddyInfoUpdateMessages[0]._links."yona:user".href.startsWith(YonaServer.stripQueryString(richard.url))
		buddyInfoUpdateMessages[0]._links."yona:buddy".href == bob.buddies[0].url
		buddyInfoUpdateMessages[0]._links."yona:userPhoto".href == newUserPhotoUrl
		buddyInfoUpdateMessages[0].nickname == "RQ"
		buddyInfoUpdateMessages[0].message == "User changed personal info"

		User bobAfterProcess = appService.reloadUser(bob)
		bobAfterProcess.buddies[0].user.userPhotoUrl == newUserPhotoUrl

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard updates his photo, which causes buddy info change message to Bob and user photo update on process'()
	{
		given:
		User richard = addRichard(false)
		uploadUserPhoto(richard) // Add photo before adding buddy
		User bob = addBob(false)
		bob.emailAddress = "bob@dunn.com"
		appService.makeBuddies(richard, bob)
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)

		when:
		def newUserPhotoUrl = uploadUserPhoto(richard)

		then:
		def bobMessagesAfterUpdate = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesAfterUpdate)
		def buddyInfoUpdateMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages"?.findAll { it."@type" == "BuddyInfoChangeMessage" }
		buddyInfoUpdateMessages.size() == 1
		buddyInfoUpdateMessages[0]._links.self != null
		buddyInfoUpdateMessages[0]._links."yona:process" == null // Processing happens automatically these days
		buddyInfoUpdateMessages[0]._links."yona:user".href.startsWith(YonaServer.stripQueryString(richard.url))
		buddyInfoUpdateMessages[0]._links."yona:buddy".href == bob.buddies[0].url
		buddyInfoUpdateMessages[0]._links."yona:userPhoto".href == newUserPhotoUrl
		buddyInfoUpdateMessages[0].nickname == "RQ"
		buddyInfoUpdateMessages[0].message == "User changed personal info"

		User bobAfterProcess = appService.reloadUser(bob)
		bobAfterProcess.buddies[0].user.userPhotoUrl == newUserPhotoUrl

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard deletes his photo, which causes buddy info change message to Bob and user photo delete on process'()
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
		assertResponseStatusOk(bobMessagesAfterUpdate)
		def buddyInfoUpdateMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages"?.findAll { it."@type" == "BuddyInfoChangeMessage" }
		buddyInfoUpdateMessages.size() == 1
		buddyInfoUpdateMessages[0]._links.self != null
		buddyInfoUpdateMessages[0]._links."yona:process" == null // Processing happens automatically these days
		buddyInfoUpdateMessages[0]._links."yona:user".href.startsWith(YonaServer.stripQueryString(richard.url))
		buddyInfoUpdateMessages[0]._links."yona:buddy".href == bob.buddies[0].url
		buddyInfoUpdateMessages[0]._links."yona:userPhoto"?.href == null
		buddyInfoUpdateMessages[0].nickname == "RQ"
		buddyInfoUpdateMessages[0].message == "User changed personal info"

		User bobAfterProcess = appService.reloadUser(bob)
		bobAfterProcess.buddies[0].user.userPhotoUrl == null

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Hacking attempt: Cannot update photo of Richard with a wrong password'()
	{
		given:
		def richard = addRichard()

		when:
		def multipartEntity = MultipartEntityBuilder.create()
				.addPart("file", new InputStreamBody(new ByteArrayInputStream(Base64.getDecoder().decode(EXAMPLE_PNG_DATA_BASE64)), "image/png", "MyPhoto.png"))
				.build()
		def response = appService.yonaServer.restClient.put(path: richard.editUserPhotoUrl, requestContentType: "multipart/form-data", headers: ["Yona-Password": "Wrong password"], body: multipartEntity)

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.decrypting.data"

		def richardAfterUpdate = appService.reloadUser(richard)
		richardAfterUpdate.userPhotoUrl == null

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Hacking attempt: Cannot delete photo of Richard with a wrong password'()
	{
		given:
		def richard = addRichard()
		def userPhotoUrlBefore = uploadUserPhoto(richard)

		when:
		def response = appService.yonaServer.deleteResourceWithPassword(richard.editUserPhotoUrl, "Wrong password")

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.decrypting.data"

		def richardAfterUpdate = appService.reloadUser(richard)
		richardAfterUpdate.userPhotoUrl == userPhotoUrlBefore

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Richard\'s photo link is still available after he updated his nickname'()
	{
		given:
		User richard = addRichard()
		uploadUserPhoto(richard)

		when:
		def newNickname = "NewNickName"
		def updatedRichard = richard.convertToJson()
		updatedRichard.nickname = newNickname
		def response = appService.updateUser(richard.url, updatedRichard, richard.password)

		then:
		assertResponseStatusOk(response)
		response.contentType == "application/json"
		def newUserPhotoUrl = response.responseData?._links?."yona:userPhoto"?.href
		newUserPhotoUrl != null
		response.responseData.nickname == newNickname

		def richardAfterUpdate = appService.reloadUser(richard)
		richardAfterUpdate.userPhotoUrl == newUserPhotoUrl
		richardAfterUpdate.nickname == newNickname

		cleanup:
		appService.deleteUser(richard)
	}

	def uploadUserPhoto(User user)
	{
		def multipartEntity = MultipartEntityBuilder.create()
				.addPart("file", new InputStreamBody(new ByteArrayInputStream(Base64.getDecoder().decode(EXAMPLE_PNG_DATA_BASE64)), "image/png", "MyPhoto.png"))
				.build()
		def response = appService.yonaServer.restClient.put(path: user.editUserPhotoUrl, requestContentType: "multipart/form-data", headers: ["Yona-Password": user.password], body: multipartEntity)
		assertResponseStatusOk(response)
		response.responseData?._links?."yona:userPhoto"?.href
	}

	def encodeMultipartEntity(HttpEntity entity)
	{
		return entity
	}
}
