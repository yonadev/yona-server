/*******************************************************************************
 * Copyright (c) 2017, 2022 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

import nu.yona.server.YonaServer

class CommonAssertions
{
	static final UUID_PATTERN = '[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}'
	static final VPN_LOGIN_ID_PATTERN = "(?i)^$UUID_PATTERN\\\$[0-9]+\$"

	/*
	 * Explanation of the prefix (BASIC_USER_, BUDDY_USER_, USER_) of the below defined constants:
	 * 
	 * The user entity occurs in two situations:
	 * * Your own user, generally just called "user"
	 * * The user entity of a buddy, generally called "buddy user"
	 * 
	 * This naming convention is applied here as well. Next, there is a set of basic properties/links that applies to each of the two user variations.
	 */
	static final BASIC_USER_PROPERTIES_APP_NOT_OPENED = ["mobileNumber", "creationTime", "_links"] as Set
	static final BASIC_USER_PROPERTIES_APP_OPENED = BASIC_USER_PROPERTIES_APP_NOT_OPENED + ["appLastOpenedDate"] as Set
	static final USER_PROPERTIES_CREATED_ON_BUDDY_REQUEST = BASIC_USER_PROPERTIES_APP_NOT_OPENED + ["firstName", "lastName", "nickname", "yonaPassword"] as Set
	static final USER_PROPERTIES_NUM_TO_BE_CONFIRMED = USER_PROPERTIES_CREATED_ON_BUDDY_REQUEST + ["appLastOpenedDate"] as Set
	static final USER_PROPERTIES_NUM_CONFIRMED_BEFORE_ACTIVITY = USER_PROPERTIES_NUM_TO_BE_CONFIRMED + ["_embedded"] as Set
	static final USER_PROPERTIES_NUM_CONFIRMED_AFTER_ACTIVITY = USER_PROPERTIES_NUM_CONFIRMED_BEFORE_ACTIVITY + ["lastMonitoredActivityDate"] as Set
	static final BUDDY_USER_PROPERTIES = BASIC_USER_PROPERTIES_APP_OPENED + ["firstName", "lastName", "nickname", "_embedded", "_links"] as Set
	static final USER_COMMON_LINKS = ["self", "curies"] as Set
	static final USER_LINKS_NUM_TO_BE_CONFIRMED = USER_COMMON_LINKS + ["yona:confirmMobileNumber", "yona:resendMobileNumberConfirmationCode", "edit"] as Set
	static final USER_LINKS_NUM_CONFIRMED = USER_COMMON_LINKS + ["edit", "yona:messages", "yona:dailyActivityReports", "yona:weeklyActivityReports", "yona:dailyActivityReportsWithBuddies", "yona:newDeviceRequest", "yona:editUserPhoto"] as Set
	static final USER_LINKS_NUM_CONFIRMED_PIN_RESET_NOT_REQUESTED = USER_LINKS_NUM_CONFIRMED + ["yona:requestPinReset"] as Set
	static final USER_LINKS_NUM_CONFIRMED_PIN_RESET_REQUESTED_NOT_GENERATED = USER_LINKS_NUM_CONFIRMED
	static final USER_LINKS_NUM_CONFIRMED_PIN_RESET_REQUESTED_AND_GENERATED = USER_LINKS_NUM_CONFIRMED + ["yona:verifyPinReset", "yona:resendPinResetConfirmationCode", "yona:clearPinReset"] as Set
	static final BUDDY_USER_LINKS = ["self"] as Set
	static final USER_EMBEDDED = ["yona:devices", "yona:goals", "yona:buddies"] as Set
	static final BUDDY_USER_EMBEDDED = ["yona:goals", "yona:devices"] as Set
	static final USER_LINKS_VARYING = ["yona:userPhoto"]

	static final COMMON_DEVICE_PROPERTIES = ["name", "operatingSystem", "registrationTime", "appLastOpenedDate", "vpnProfile", "vpnConnected", "requestingDevice", "_links"] as Set
	static final REQUESTING_DEVICE_PROPERTIES = COMMON_DEVICE_PROPERTIES + ["sslRootCertCN"] as Set
	static final COMMON_DEVICE_LINKS = ["self", "edit"] as Set
	static final REQUESTING_DEVICE_LINKS = COMMON_DEVICE_LINKS + ["yona:postOpenAppEvent", "yona:appActivity", "yona:sslRootCert", "yona:appleMobileConfig"] as Set

	static void assertUserCreationResponseDetails(def response)
	{
		assertResponseStatusCreated(response)
		assertUser(response.json, false)
	}

	static void assertUserUpdateResponseDetails(def response)
	{
		assertResponseStatusOk(response)
		assertUser(response.json, false)
	}

	static void assertUserGetResponseDetailsIgnoreDefaultDevice(def response)
	{
		assertResponseStatusSuccess(response)
		assertUser(response.json, false, false, false)
	}

	static void assertUserGetResponseDetails(def response, assertDefaultDevice = true)
	{
		assertResponseStatusSuccess(response)
		assertUser(response.json, false, false, assertDefaultDevice)
	}

	static void assertUserGetResponseDetailsPinResetRequestedNotGenerated(def response)
	{
		assertResponseStatusSuccess(response)
		assertUser(response.json, true)
		assert response.json.keySet() == USER_PROPERTIES_NUM_CONFIRMED_BEFORE_ACTIVITY
		assert response.json._links.keySet() == USER_LINKS_NUM_CONFIRMED_PIN_RESET_REQUESTED_NOT_GENERATED
		assert response.json._embedded.keySet() == USER_EMBEDDED
	}

	static void assertUserGetResponseDetailsPinResetRequestedAndGenerated(def response)
	{
		assertResponseStatusSuccess(response)
		assertUser(response.json, true)
		assert response.json.keySet() == USER_PROPERTIES_NUM_CONFIRMED_BEFORE_ACTIVITY
		assert response.json._links.keySet() == USER_LINKS_NUM_CONFIRMED_PIN_RESET_REQUESTED_AND_GENERATED
		assert response.json._embedded.keySet() == USER_EMBEDDED
	}

	static void assertUserGetResponseDetailsCreatedOnBuddyRequest(def response)
	{
		assertResponseStatusSuccess(response)
		assertUser(response.json, true, true)
		assert response.json.keySet() == USER_PROPERTIES_CREATED_ON_BUDDY_REQUEST
	}

	static void assertUser(user, boolean skipPropertySetAssertion = true, boolean userCreatedOnBuddyRequest = false, assertDefaultDevice = true)
	{
		if (user instanceof User)
		{
			assert user.url != null
		}
		else
		{
			assert user._links.self.href != null
		}
		assert user.creationTime != null
		assert userCreatedOnBuddyRequest || user.appLastOpenedDate != null
		assert user.mobileNumber ==~ /^\+[0-9]+$/

		assert userCreatedOnBuddyRequest || user.nickname != null
		assert user.firstName != null
		assert user.lastName != null

		/*
		 * The below asserts use exclusive or operators. Either there should be a mobile number confirmation URL, or the other URL.
		 * The URLs shouldn't be both missing or both present.
		 */
		boolean mobileNumberToBeConfirmed
		if (user instanceof User)
		{
			mobileNumberToBeConfirmed = userCreatedOnBuddyRequest || user.mobileNumberConfirmationUrl != null
			if (userCreatedOnBuddyRequest)
			{
				assert user.mobileNumberConfirmationUrl == null
			}
			assert user.password.startsWith("AES:128:")
			assert mobileNumberToBeConfirmed ^ user.buddiesUrl != null
			assert mobileNumberToBeConfirmed ^ user.messagesUrl != null
			assert mobileNumberToBeConfirmed ^ user.newDeviceRequestUrl != null
		}
		else
		{
			mobileNumberToBeConfirmed = userCreatedOnBuddyRequest || ((boolean) user._links?."yona:confirmMobileNumber"?.href)
			assert user.yonaPassword.startsWith("AES:128:")
			assert mobileNumberToBeConfirmed ^ user._embedded?."yona:buddies"?._links?.self?.href != null
			assert mobileNumberToBeConfirmed ^ user._embedded?."yona:goals"?._links?.self?.href != null
			assert mobileNumberToBeConfirmed ^ user._embedded?."yona:devices"?._links?.self?.href != null
			if (userCreatedOnBuddyRequest)
			{
				assert user._links?."yona:confirmMobileNumber"?.href == null
			}
			assert mobileNumberToBeConfirmed ^ user._links?."yona:messages" != null
			assert mobileNumberToBeConfirmed ^ user._links?."yona:newDeviceRequest" != null
			assert skipPropertySetAssertion || (mobileNumberToBeConfirmed ? user.keySet() == USER_PROPERTIES_NUM_TO_BE_CONFIRMED : (user.keySet() == USER_PROPERTIES_NUM_CONFIRMED_BEFORE_ACTIVITY || user.keySet() == USER_PROPERTIES_NUM_CONFIRMED_AFTER_ACTIVITY))
			assert skipPropertySetAssertion || (mobileNumberToBeConfirmed ? user._links.keySet() - USER_LINKS_VARYING == USER_LINKS_NUM_TO_BE_CONFIRMED : user._links.keySet() - USER_LINKS_VARYING == USER_LINKS_NUM_CONFIRMED_PIN_RESET_NOT_REQUESTED)
			assert skipPropertySetAssertion || (mobileNumberToBeConfirmed ? user._embedded == null : user._embedded.keySet() == USER_EMBEDDED)
			assert skipPropertySetAssertion || user._links.self.href ==~ /(?i)^.*\/$UUID_PATTERN\?requestingUserId=$UUID_PATTERN\&requestingDeviceId=$UUID_PATTERN$/
			if (!mobileNumberToBeConfirmed && assertDefaultDevice)
			{
				assert user._embedded."yona:devices"._embedded."yona:devices".size == 1
				assertDefaultOwnDevice(user._embedded."yona:devices"._embedded."yona:devices"[0])
			}
		}
	}

	static void assertUserGetResponseDetailsWithBuddyDataEstablishedRelationship(def response)
	{
		assertResponseStatusSuccess(response)
		assertBuddyUser(response.json, true)
	}

	static void assertUserGetResponseDetailsWithBuddyDataNotYetEstablishedRelationship(def response)
	{
		assertResponseStatusSuccess(response)
		assertBuddyUser(response.json, false)
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

	static void assertDefaultOwnDevice(device, isRequestingDevice = false)
	{
		if (!(device instanceof Device))
		{
			assert device.keySet() == (isRequestingDevice) ? REQUESTING_DEVICE_PROPERTIES : COMMON_DEVICE_PROPERTIES
			assert device._links.keySet() == (isRequestingDevice) ? REQUESTING_DEVICE_LINKS : COMMON_DEVICE_LINKS
		}
		switch (device.operatingSystem)
		{
			case "UNKNOWN":
				assert device.name == "First device"
				break
			case "IOS":
				assert device.name ==~ /.*'s iPhone/
				break
			case "ANDROID":
				assert device.name ==~ /.*'s S8/
				break
			default:
				assert false, "Invalid operating system: '$device.operatingSystem'"
		}
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

		assert dateTime.isAfter(comparisonDateTime - Duration.ofMillis(epsilonMilliseconds))
		assert dateTime.isBefore(comparisonDateTime + Duration.ofMillis(epsilonMilliseconds))
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
		assert response.status == status, "Invalid status: $response.status (expecting $status). Response: $response.rawData"
	}

	static void assertResponseStatusSuccess(def response)
	{
		assert response.status >= 200 && response.status < 300, "Invalid status: $response.status (expecting 2xx). Response: $response.rawData"
	}

	static assertBuddyUsers(response)
	{
		if (response.json._embedded?."yona:buddies"?._embedded?."yona:buddies")
		{
			response.json._embedded."yona:buddies"._embedded."yona:buddies".each { assertBuddyUser(it._embedded."yona:user", it.sendingStatus == "ACCEPTED") }
		}
	}

	static void assertBuddyUser(def buddyUser, boolean isBuddyRelationshipEstablished)
	{
		if (isBuddyRelationshipEstablished)
		{
			assert buddyUser.keySet() == BUDDY_USER_PROPERTIES
			assert buddyUser._embedded.keySet() == BUDDY_USER_EMBEDDED
		}
		else
		{
			assert buddyUser.keySet() == BUDDY_USER_PROPERTIES - ["_embedded"] as Set
		}
		assert buddyUser._links.keySet() - USER_LINKS_VARYING == BUDDY_USER_LINKS
	}
}
