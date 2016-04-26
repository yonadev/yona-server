/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import groovy.json.*
import nu.yona.server.YonaServer

class AppActivity
{
	static class Activity
	{
		final String application
		final Date startTime
		final Date endTime

		public Activity(String application, Date startTime, Date endTime)
		{
			this.application = application
			this.startTime = startTime
			this.endTime = endTime
		}

		String getJson()
		{
			def startTimeString = YonaServer.toIsoDateString(startTime)
			def endTimeString = YonaServer.toIsoDateString(endTime)
			"""{
				"application":"$application",
				"startTime":"$startTimeString",
				"endTime":"$endTimeString"
			}"""
		}
	}
	Date deviceDateTime
	def activities

	AppActivity(def activities)
	{
		this(new Date(), activities)
	}

	AppActivity(Date deviceDateTime, def activities)
	{
		this.deviceDateTime = deviceDateTime
		this.activities = activities
	}

	String getJson()
	{
		def deviceDateTimeString = YonaServer.toIsoDateString(deviceDateTime)
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


	static AppActivity singleActivity(application, Date startTime, Date endTime)
	{
		new AppActivity([new AppActivity.Activity(application, startTime, endTime)].toArray())
	}

	static AppActivity singleActivity(Date deviceDateTime, application, Date startTime, Date endTime)
	{
		new AppActivity(deviceDateTime, [new AppActivity.Activity(application, startTime, endTime)].toArray())
	}
}