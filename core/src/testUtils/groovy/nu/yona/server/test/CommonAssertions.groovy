/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation
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
	static final VPN_LOGIN_ID_PATTERN = "(?i)^$UUID_PATTERN\\\$[0-9]+\$"

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

	static void assertUserCreationResponseDetails(def response)
	{
		assertResponseStatusCreated(response)
		assertUserWithPrivateData(response.responseData, false)
	}

	static void assertUserUpdateResponseDetails(def response)
	{
		assertResponseStatusOk(response)
		assertUserWithPrivateData(response.responseData, false)
	}

	static void assertUserGetResponseDetailsWithPrivateDataIgnoreDefaultDevice(def response)
	{
		assertResponseStatusSuccess(response)
		assertUserWithPrivateData(response.responseData, false, false, false)
	}

	static void assertUserGetResponseDetailsWithPrivateData(def response, assertDefaultDevice = true)
	{
		assertResponseStatusSuccess(response)
		assertUserWithPrivateData(response.responseData, false, false, assertDefaultDevice)
	}

	static void assertUserGetResponseDetailsWithPrivateDataPinResetRequestedNotGenerated(def response)
	{
		assertResponseStatusSuccess(response)
		assertUserWithPrivateData(response.responseData, true)
		assert response.responseData.keySet() == PRIVATE_USER_PROPERTIES_NUM_CONFIRMED_BEFORE_ACTIVITY
		assert response.responseData._links.keySet() == PRIVATE_USER_LINKS_NUM_CONFIRMED_PIN_RESET_REQUESTED_NOT_GENERATED
		assert response.responseData._embedded.keySet() == PRIVATE_USER_EMBEDDED
	}

	static void assertUserGetResponseDetailsWithPrivateDataPinResetRequestedAndGenerated(def response)
	{
		assertResponseStatusSuccess(response)
		assertUserWithPrivateData(response.responseData, true)
		assert response.responseData.keySet() == PRIVATE_USER_PROPERTIES_NUM_CONFIRMED_BEFORE_ACTIVITY
		assert response.responseData._links.keySet() == PRIVATE_USER_LINKS_NUM_CONFIRMED_PIN_RESET_REQUESTED_AND_GENERATED
		assert response.responseData._embedded.keySet() == PRIVATE_USER_EMBEDDED
	}

	static void assertUserGetResponseDetailsWithPrivateDataCreatedOnBuddyRequest(def response)
	{
		assertResponseStatusSuccess(response)
		assertUserWithPrivateData(response.responseData, true, true)
		assert response.responseData.keySet() == PRIVATE_USER_PROPERTIES_CREATED_ON_BUDDY_REQUEST
	}

	static void assertUserGetResponseDetailsWithoutPrivateData(def response)
	{
		assertResponseStatusSuccess(response)
		assertPublicUserData(response.responseData, false, false)
	}

	static void assertUserWithPrivateData(user, boolean skipPropertySetAssertion = true, boolean userCreatedOnBuddyRequest = false, assertDefaultDevice = true)
	{
		assertPublicUserData(user, true, userCreatedOnBuddyRequest)
		assertPrivateUserData(user, skipPropertySetAssertion, userCreatedOnBuddyRequest, assertDefaultDevice)
	}

	static void assertUserGetResponseDetailsWithBuddyData(def response)
	{
		assertResponseStatusSuccess(response)
		assertBuddyUser(response.responseData)
	}

	static void assertPublicUserData(def user, boolean skipPropertySetAssertion = true, boolean userCreatedOnBuddyRequest)
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

	static void assertPrivateUserData(def user, boolean skipPropertySetAssertion, boolean userCreatedOnBuddyRequest, boolean assertDefaultDevice)
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
			if (!mobileNumberToBeConfirmed && assertDefaultDevice)
			{
				assert user._embedded."yona:devices"._embedded."yona:devices".size == 1
				assertDefaultOwnDevice(user._embedded."yona:devices"._embedded."yona:devices"[0])
			}
		}

		if (!mobileNumberToBeConfirmed)
		{
			assertVpnProfile(user.vpnProfile)
		}
	}

	static void assertVpnProfile(def vpnProfile)
	{
		if (vpnProfile instanceof VPNProfile)
		{
			// The User Groovy object follows the new camel casing pattern: Id with lowercase d
			assert vpnProfile.vpnLoginId ==~ /$VPN_LOGIN_ID_PATTERN/
		}
		else
		{
			// For backward compatibility, the JSON still has the old camel casing pattern: ID with uppercase D
			assert vpnProfile.vpnLoginID ==~ /$VPN_LOGIN_ID_PATTERN/
		}
		assert vpnProfile.vpnPassword.length() == 32
		if (vpnProfile instanceof VPNProfile)
		{
			assert vpnProfile.ovpnProfileUrl
		}
		else
		{
			assert vpnProfile._links."yona:ovpnProfile".href
		}
	}

	static void assertDefaultOwnDevice(def device)
	{
		if (!(device instanceof Device))
		{
			assert device.keySet() == ["name", "operatingSystem", "registrationTime", "appLastOpenedDate", "vpnProfile", "vpnConnected", "_links"] as Set
			assert device._links.keySet() == ["self", "edit"] as Set
		}
		assert device.name == "First device" || device.name ==~ /.*'s iPhone/
		assert device.operatingSystem == "UNKNOWN" || device.operatingSystem == "IOS"
		assertDateTimeFormat(device.registrationTime)
		assertDateFormat(device.appLastOpenedDate)
		assert device.vpnConnected == true || device.vpnConnected == false

		assertVpnProfile(device.vpnProfile)
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
		LocalDate date = YonaServer.parseIsoDateString(dateTimeString)
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

	static void assertResponseStatusNoContent(def response)
	{
		assertResponseStatus(response, 204)
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

	static void assertBuddyUser(def buddyUser)
	{
		assert buddyUser.keySet() -BUDDY_USER_PROPERTIES_VARYING == BUDDY_USER_PROPERTIES
		assert buddyUser._links.keySet() - USER_LINKS_VARYING == BUDDY_USER_LINKS
		assert buddyUser._embedded == null || buddyUser._embedded.keySet() == BUDDY_USER_EMBEDDED
	}
}
