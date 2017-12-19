/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

import groovy.json.*
import nu.yona.server.YonaServer

class CommonAssertions extends Service
{
	static final UUID_PATTERN = '[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}'

	static final PUBLIC_USER_PROPERTIES_APP_NOT_OPENED = ["firstName", "lastName", "mobileNumber", "creationTime", "_links"] as Set
	static final PUBLIC_USER_PROPERTIES_APP_OPENED = PUBLIC_USER_PROPERTIES_APP_NOT_OPENED + ["appLastOpenedDate"] as Set
	static final PRIVATE_USER_PROPERTIES_CREATED_ON_BUDDY_REQUEST = PUBLIC_USER_PROPERTIES_APP_NOT_OPENED + ["nickname", "yonaPassword"] as Set
	static final PRIVATE_USER_PROPERTIES_NUM_TO_BE_CONFIRMED = PRIVATE_USER_PROPERTIES_CREATED_ON_BUDDY_REQUEST + ["appLastOpenedDate"] as Set
	static final PRIVATE_USER_PROPERTIES_NUM_CONFIRMED_BEFORE_ACTIVITY = PRIVATE_USER_PROPERTIES_NUM_TO_BE_CONFIRMED + ["vpnProfile", "sslRootCertCN", "_embedded"] as Set
	static final PRIVATE_USER_PROPERTIES_NUM_CONFIRMED_AFTER_ACTIVITY = PRIVATE_USER_PROPERTIES_NUM_CONFIRMED_BEFORE_ACTIVITY + ["lastMonitoredActivityDate"] as Set
	static final BUDDY_USER_PROPERTIES = PUBLIC_USER_PROPERTIES_APP_OPENED + ["nickname"] as Set
	static final BUDDY_USER_PROPERTIES_VARYING = ["_embedded"] as Set
	static final PRIVATE_USER_COMMON_LINKS = ["self", "curies"] as Set
	static final PRIVATE_USER_LINKS_NUM_TO_BE_CONFIRMED = PRIVATE_USER_COMMON_LINKS + ["yona:confirmMobileNumber", "yona:resendMobileNumberConfirmationCode", "edit"] as Set
	static final PRIVATE_USER_LINKS_NUM_CONFIRMED = PRIVATE_USER_COMMON_LINKS + ["edit", "yona:postOpenAppEvent", "yona:messages", "yona:dailyActivityReports", "yona:weeklyActivityReports", "yona:dailyActivityReportsWithBuddies", "yona:newDeviceRequest", "yona:appActivity", "yona:sslRootCert", "yona:appleMobileConfig", "yona:editUserPhoto"] as Set
	static final PRIVATE_USER_LINKS_NUM_CONFIRMED_PIN_RESET_NOT_REQUESTED = PRIVATE_USER_LINKS_NUM_CONFIRMED + ["yona:requestPinReset"] as Set
	static final PRIVATE_USER_LINKS_NUM_CONFIRMED_PIN_RESET_REQUESTED_NOT_GENERATED = PRIVATE_USER_LINKS_NUM_CONFIRMED
	static final PRIVATE_USER_LINKS_NUM_CONFIRMED_PIN_RESET_REQUESTED_AND_GENERATED = PRIVATE_USER_LINKS_NUM_CONFIRMED + ["yona:verifyPinReset", "yona:resendPinResetConfirmationCode", "yona:clearPinReset"] as Set
	static final BUDDY_USER_LINKS =  ["self"] as Set
	static final PRIVATE_USER_EMBEDDED = ["yona:devices", "yona:goals", "yona:buddies"] as Set
	static final BUDDY_USER_EMBEDDED = ["yona:goals", "yona:devices"] as Set
	static final USER_LINKS_VARYING = ["yona:userPhoto"]

	static def assertUserCreationResponseDetails(def response)
	{
		assertResponseStatusCreated(response)
		assertUserWithPrivateData(response.responseData, false)
	}

	static def assertUserUpdateResponseDetails(def response)
	{
		assert response.status == 200
		assertUserWithPrivateData(response.responseData, false)
	}

	static def assertUserGetResponseDetailsWithPrivateData(def response)
	{
		assertResponseStatusSuccess(response)
		assertUserWithPrivateData(response.responseData, false)
	}

	static def assertUserGetResponseDetailsWithPrivateDataPinResetRequestedNotGenerated(def response)
	{
		assertResponseStatusSuccess(response)
		assertUserWithPrivateData(response.responseData, true)
		assert response.responseData.keySet() == PRIVATE_USER_PROPERTIES_NUM_CONFIRMED_BEFORE_ACTIVITY
		assert response.responseData._links.keySet() == PRIVATE_USER_LINKS_NUM_CONFIRMED_PIN_RESET_REQUESTED_NOT_GENERATED
		assert response.responseData._embedded.keySet() == PRIVATE_USER_EMBEDDED
	}

	static def assertUserGetResponseDetailsWithPrivateDataPinResetRequestedAndGenerated(def response)
	{
		assertResponseStatusSuccess(response)
		assertUserWithPrivateData(response.responseData, true)
		assert response.responseData.keySet() == PRIVATE_USER_PROPERTIES_NUM_CONFIRMED_BEFORE_ACTIVITY
		assert response.responseData._links.keySet() == PRIVATE_USER_LINKS_NUM_CONFIRMED_PIN_RESET_REQUESTED_AND_GENERATED
		assert response.responseData._embedded.keySet() == PRIVATE_USER_EMBEDDED
	}

	static def assertUserGetResponseDetailsWithPrivateDataCreatedOnBuddyRequest(def response)
	{
		assertResponseStatusSuccess(response)
		assertUserWithPrivateData(response.responseData, true, true)
		assert response.responseData.keySet() == PRIVATE_USER_PROPERTIES_CREATED_ON_BUDDY_REQUEST
	}

	static def assertUserGetResponseDetailsWithoutPrivateData(def response)
	{
		assertResponseStatusSuccess(response)
		assertPublicUserData(response.responseData, false, false)
	}

	static def assertUserWithPrivateData(user, boolean skipPropertySetAssertion = true, boolean userCreatedOnBuddyRequest = false)
	{
		assertPublicUserData(user, true, userCreatedOnBuddyRequest)
		assertPrivateUserData(user, skipPropertySetAssertion, userCreatedOnBuddyRequest)
	}

	static def assertUserGetResponseDetailsWithBuddyData(def response)
	{
		assertResponseStatusSuccess(response)
		assertBuddyUser(response.responseData)
	}

	static def assertPublicUserData(def user, boolean skipPropertySetAssertion = true, boolean userCreatedOnBuddyRequest)
	{
		if (user instanceof User)
		{
			assert user.url != null
		}
		else
		{
			assert user._links.self.href != null
			assert skipPropertySetAssertion || user.keySet() == PUBLIC_USER_PROPERTIES_APP_NOT_OPENED || user.keySet() == PUBLIC_USER_PROPERTIES_APP_OPENED
		}
		assert user.creationTime != null
		assert userCreatedOnBuddyRequest || user.appLastOpenedDate != null
		assert user.firstName != null
		assert user.lastName != null
		assert user.mobileNumber ==~/^\+[0-9]+$/
	}

	static def assertPrivateUserData(def user, boolean skipPropertySetAssertion = true, boolean userCreatedOnBuddyRequest)
	{
		assert userCreatedOnBuddyRequest || user.nickname != null
		boolean mobileNumberToBeConfirmed

		/*
		 * The below asserts use exclusive or operators. Either there should be a mobile number confirmation URL, or the other URL.
		 * The URLs shouldn't be both missing or both present.
		 */
		if (user instanceof User)
		{
			mobileNumberToBeConfirmed = ((boolean) user.mobileNumberConfirmationUrl)
			assert user.password.startsWith("AES:128:")
			assert mobileNumberToBeConfirmed ^ ((boolean) user.buddiesUrl)
			assert mobileNumberToBeConfirmed ^ ((boolean) user.messagesUrl)
			assert mobileNumberToBeConfirmed ^ ((boolean) user.newDeviceRequestUrl)
			assert mobileNumberToBeConfirmed ^ ((boolean) user.appActivityUrl)
		}
		else
		{
			mobileNumberToBeConfirmed = ((boolean) user._links?."yona:confirmMobileNumber"?.href)
			assert user.yonaPassword.startsWith("AES:128:")
			assert mobileNumberToBeConfirmed ^ ((boolean) user._embedded?."yona:buddies"?._links?.self?.href)
			assert mobileNumberToBeConfirmed ^ ((boolean) user._embedded?."yona:goals"?._links?.self?.href)
			assert mobileNumberToBeConfirmed ^ ((boolean) user._embedded?."yona:devices"?._links?.self?.href)
			assert mobileNumberToBeConfirmed ^ ((boolean) user._links?."yona:messages")
			assert mobileNumberToBeConfirmed ^ ((boolean) user._links?."yona:newDeviceRequest")
			assert mobileNumberToBeConfirmed ^ ((boolean) user._links?."yona:appActivity")
			assert skipPropertySetAssertion || (mobileNumberToBeConfirmed ? user.keySet() == PRIVATE_USER_PROPERTIES_NUM_TO_BE_CONFIRMED : (user.keySet() == PRIVATE_USER_PROPERTIES_NUM_CONFIRMED_BEFORE_ACTIVITY || user.keySet() == PRIVATE_USER_PROPERTIES_NUM_CONFIRMED_AFTER_ACTIVITY))
			assert skipPropertySetAssertion || (mobileNumberToBeConfirmed ? user._links.keySet() - USER_LINKS_VARYING == PRIVATE_USER_LINKS_NUM_TO_BE_CONFIRMED : user._links.keySet() - USER_LINKS_VARYING == PRIVATE_USER_LINKS_NUM_CONFIRMED_PIN_RESET_NOT_REQUESTED)
			assert skipPropertySetAssertion || (mobileNumberToBeConfirmed ? user._embedded == null : user._embedded.keySet() == PRIVATE_USER_EMBEDDED)
			assert skipPropertySetAssertion || user._links.self.href ==~/(?i)^.*\/$UUID_PATTERN\?requestingUserId=$UUID_PATTERN\&requestingDeviceId=$UUID_PATTERN$/
			if (!mobileNumberToBeConfirmed)
			{
				assertDefaultOwnDevice(user._embedded."yona:devices"._embedded."yona:devices"[0])
			}
		}

		if (!mobileNumberToBeConfirmed)
		{
			assertVpnProfile(user)
		}
	}

	static def assertVpnProfile(def user)
	{
		if (user instanceof User)
		{
			// The User Groovy object follows the new camel casing pattern: Id with lowercase d
			assert user.vpnProfile.vpnLoginId ==~ /(?i)^$UUID_PATTERN$/
		}
		else
		{
			// For backward compatibility, the JSON still has the old camel casing pattern: ID with uppercase D
			assert user.vpnProfile.vpnLoginID ==~ /(?i)^$UUID_PATTERN$/
		}
		assert user.vpnProfile.vpnPassword.length() == 32
		if (user instanceof User)
		{
			assert user.vpnProfile.ovpnProfileUrl
		}
		else
		{
			assert user.vpnProfile._links."yona:ovpnProfile".href
		}
	}

	static void assertDefaultOwnDevice(def device)
	{
		if (device instanceof Device)
		{
			assert device.name == "First device"
			assert device.operatingSystem == "UNKNOWN"
		}
		else
		{
			assert device.keySet() == ["name", "operatingSystem", "registrationTime", "appLastOpenedDate", "vpnConnected", "_links"] as Set
			assert device._links.keySet() == ["self", "edit"] as Set
			assert device.name == "First device"
			assert device.operatingSystem == "UNKNOWN"
			assertDateTimeFormat(device.registrationTime)
			assertDateFormat(device.appLastOpenedDate)
			assert device.vpnConnected == true || device.vpnConnected == false
		}
	}

	static void assertEquals(String dateTimeString, ZonedDateTime comparisonDateTime, int epsilonSeconds = 10)
	{
		// Example date/time string: 2016-02-23T21:28:58.556+0000
		ZonedDateTime dateTime = YonaServer.parseIsoDateTimeString(dateTimeString)
		assertEquals(dateTime, comparisonDateTime, epsilonSeconds)
	}

	static void assertEquals(ZonedDateTime dateTime, ZonedDateTime comparisonDateTime, int epsilonSeconds = 10)
	{
		int epsilonMilliseconds = epsilonSeconds * 1000

		assert dateTime.isAfter(comparisonDateTime.minus(Duration.ofMillis(epsilonMilliseconds)))
		assert dateTime.isBefore(comparisonDateTime.plus(Duration.ofMillis(epsilonMilliseconds)))
	}

	static void assertDateTimeFormat(dateTimeString)
	{
		assert dateTimeString ==~ /[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}\+\d{4}/
	}

	static void assertEquals(String dateTimeString, LocalDate comparisonDate)
	{
		// Example date string: 2016-02-23
		ZonedDateTime date = YonaServer.parseIsoDateString(dateTimeString)
		assertEquals(date, comparisonDate)
	}

	static void assertEquals(LocalDate date, LocalDate comparisonDate)
	{
		assert date == comparisonDate
	}

	static void assertDateFormat(dateTimeString)
	{
		assert dateTimeString ==~ /[0-9]{4}-[0-9]{2}-[0-9]{2}/
	}

	static void assertResponseStatusOk(def response)
	{
		assertResponseStatus(response, 200)
	}

	static void assertResponseStatusCreated(def response)
	{
		assertResponseStatus(response, 201)
	}

	static void assertResponseStatus(def response, int status)
	{
		assert response.status == status, "Invalid status: $response.status (expecting $status). Response: $response.data"
	}

	static void assertResponseStatusSuccess(def response)
	{
		assert response.status >= 200 && response.status < 300, "Invalid status: $response.status (expecting 2xx). Response: $response.data"
	}

	private static assertBuddyUsers(response)
	{
		response.responseData._embedded?."yona:buddies"?._embedded?."yona:buddies".each{assertBuddyUser(it._embedded."yona:user")}
	}

	static def assertBuddyUser(def buddyUser)
	{
		assert buddyUser.keySet() -BUDDY_USER_PROPERTIES_VARYING == BUDDY_USER_PROPERTIES
		assert buddyUser._links.keySet() - USER_LINKS_VARYING == BUDDY_USER_LINKS
		assert buddyUser._embedded == null || buddyUser._embedded.keySet() == BUDDY_USER_EMBEDDED
	}
}
