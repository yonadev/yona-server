/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import groovy.json.*

import java.time.ZonedDateTime

import nu.yona.server.YonaServer

class AppActivity
{
	static class Activity
	{
		final String application
		final ZonedDateTime startTime
		final ZonedDateTime endTime

		public Activity(String application, ZonedDateTime startTime, ZonedDateTime endTime)
		{
			this.application = application
			this.startTime = startTime
			this.endTime = endTime
		}

		String getJson()
		{
			def startTimeString = YonaServer.toIsoDateTimeString(startTime)
			def endTimeString = YonaServer.toIsoDateTimeString(endTime)
			"""{
				"application":"$application",
				"startTime":"$startTimeString",
				"endTime":"$endTimeString"
			}"""
		}
	}
	ZonedDateTime deviceDateTime
	def activities

	AppActivity(def activities)
	{
		this(YonaServer.now, activities)
	}

	AppActivity(ZonedDateTime deviceDateTime, def activities)
	{
		this.deviceDateTime = deviceDateTime
		this.activities = activities
	}

	String getJson()
	{
		def deviceDateTimeString = YonaServer.toIsoDateTimeString(deviceDateTime)
		def activitiesString = buildActivitiesString(activities)
		"""{
			"deviceDateTime":"$deviceDateTimeString",
			"activities": $activitiesString
		}"""
	}

	static String buildActivitiesString(def activities)
	{
		def activitiesString = ""
		def first = true
		activities.each(
				{
					activitiesString += (activitiesString) ? ", " : ""
					activitiesString += it.getJson()
				})
		return "[" + activitiesString + "]"
	}


	static AppActivity singleActivity(application, ZonedDateTime startTime, ZonedDateTime endTime)
	{
		new AppActivity([new AppActivity.Activity(application, startTime, endTime)].toArray())
	}

	static AppActivity singleActivity(ZonedDateTime deviceDateTime, application, ZonedDateTime startTime, ZonedDateTime endTime)
	{
		new AppActivity(deviceDateTime, [new AppActivity.Activity(application, startTime, endTime)].toArray())
	}
}
