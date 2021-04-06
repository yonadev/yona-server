/*******************************************************************************
 * Copyright (c) 2015, 2021 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.ChronoField
import java.time.temporal.IsoFields
import java.time.temporal.WeekFields

import groovy.json.JsonSlurper
import groovyx.net.http.AsyncHTTPBuilder
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.RESTClient
import groovyx.net.http.URIBuilder

class YonaServer
{
	static final ZoneId EUROPE_AMSTERDAM_ZONE = ZoneId.of("Europe/Amsterdam")
	static final Locale EN_US_LOCALE = Locale.forLanguageTag("en-US")
	private static final DateTimeFormatter ISO8601_WEEK_FORMATTER = new DateTimeFormatterBuilder().parseCaseInsensitive()
			.appendValue(IsoFields.WEEK_BASED_YEAR, 4, 10, SignStyle.EXCEEDS_PAD).appendLiteral("-W")
			.appendValue(IsoFields.WEEK_OF_WEEK_BASED_YEAR, 2)
			.parseDefaulting(WeekFields.ISO.dayOfWeek(), DayOfWeek.MONDAY.getValue()).toFormatter(Locale.forLanguageTag("en-US"))
	JsonSlurper jsonSlurper = new JsonSlurper()
	RESTClient restClient
	AsyncHTTPBuilder asyncHttpClient
	int maxConcurrentRequests

	YonaServer(baseUrl)
	{
		restClient = new RESTClient(baseUrl)

		restClient.handler.failure = restClient.handler.success
	}

	void enableConcurrentRequests(int maxConcurrentRequests)
	{
		this.maxConcurrentRequests = maxConcurrentRequests
		asyncHttpClient = new AsyncHTTPBuilder(poolSize: maxConcurrentRequests, uri: restClient.uri)
	}

	void shutdown()
	{
		if (asyncHttpClient)
		{
			asyncHttpClient.shutdown()
		}
	}

	static ZonedDateTime getNow()
	{
		ZonedDateTime.now(YonaServer.EUROPE_AMSTERDAM_ZONE)
	}

	def static getTimeStamp()
	{
		def formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssS")
		formatter.format(now)
	}

	def createResourceWithPassword(path, jsonString, password, parameters = [:], headers = [:])
	{
		createResource(path, jsonString, parameters, addPasswordToHeaders(headers, password))
	}

	static addPasswordToHeaders(Map<String, String> headers, String password)
	{
		Map<String, String> headersWithPassword = cloneMap(headers)
		if (password)
		{
			headersWithPassword["Yona-Password"] = password
		}
		return headersWithPassword
	}

	private static <K, V> Map<K, V> cloneMap(Map<K, V> map)
	{
		map.getClass().newInstance(map) as Map<K, V>
	}

	def createResource(path, jsonString, parameters = [:], headers = [:])
	{
		postJson(path, jsonString, parameters, headers)
	}

	def updateResourceWithPassword(path, jsonString, password, parameters = [:], headers = [:])
	{
		updateResource(path, jsonString, parameters, addPasswordToHeaders(headers, password))
	}

	def updateResource(path, jsonString, parameters = [:], headers = [:])
	{
		putJson(path, jsonString, parameters, headers)
	}

	def deleteResourceWithPassword(path, password, parameters = [:], headers = [:])
	{
		deleteResource(path, parameters, addPasswordToHeaders(headers, password))
	}

	def deleteResource(path, parameters = [:], headers = [:])
	{
		restClient.delete(path: stripQueryString(path), query: parameters + getQueryParams(path), headers: headers)
	}

	def getResourceWithPassword(path, password, parameters = [:], headers = [:])
	{
		getResource(path, parameters, addPasswordToHeaders(headers, password))
	}

	def getResource(path, parameters = [:], headers = [:])
	{
		restClient.get(path: stripQueryString(path),
				contentType: 'application/json',
				query: parameters + getQueryParams(path),
				headers: headers)
	}

	def postJson(String path, Object body, Map<String, String> parameters = [:], Map<String, String> headers = [:])
	{
		postThroughHttpBuilder(restClient, path, body, parameters, headers)
	}

	private def postThroughHttpBuilder(HTTPBuilder httpBuilder, String path, Object body, Map<String, String> parameters = [:], Map<String, String> headers = [:])
	{
		def parsedBody
		if (body instanceof Map)
		{
			parsedBody = body
		}
		else
		{
			parsedBody = jsonSlurper.parseText(body)
		}

		httpBuilder.post(path: stripQueryString(path),
				body: parsedBody,
				contentType: 'application/json',
				query: parameters + getQueryParams(path),
				headers: headers)
	}

	def putJson(path, jsonString, parameters = [:], headers = [:])
	{
		def object
		if (jsonString instanceof Map)
		{
			object = jsonString
		}
		else
		{
			object = jsonSlurper.parseText(jsonString)
		}

		restClient.put(path: stripQueryString(path),
				body: object,
				contentType: 'application/json',
				headers: headers,
				query: parameters + getQueryParams(path))
	}

	static def getQueryParams(String url)
	{
		def uriBuilder = new URIBuilder(url)
		if (uriBuilder.query)
		{
			return uriBuilder.query
		}
		else
		{
			return [:]
		}
	}

	static void storeStatistics(Map<String, Integer> statistics, String heading)
	{
		def file = new File("build/reports/tests/intTest/" + heading + ".md")
		file << "# $heading\n\n"
		List<String> statNames = (statistics[statistics.keySet().first()].keySet().findAll { it != "startTime" && it != "sqlStatements" } as List<String>).sort()
		storeRow(file, ["Operation"] + statNamesToHeadingNames(statNames))
		storeRow(file, ["---"] * (statNames.size() + 1))
		statistics.each { String k, v -> storeRow(file, [k] + statNames.collect { v[it] }) }

		file << "\n# SQL statements\n\n"
		statistics.each { k, v -> storeSqlStatements(file, k, v["sqlStatements"]) }
	}

	private static def statNamesToHeadingNames(def statNames)
	{
		statNames = statNames*.minus("Count")
		statNames*.uncapitalize()
		statNames.collect { it.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])")*.uncapitalize().join(" ") }*.capitalize()
	}

	private static storeRow(def file, def cells)
	{
		cells.each { file << "| $it" }
		file << "\n"
	}

	private static storeSqlStatements(file, testName, sqlStatements)
	{
		file << "\n## $testName\n"
		sqlStatements.each { file << "1. ``$it``\n" }
	}

	static def stripQueryString(url)
	{
		url - ~/\?.*/
	}

	static def removeRequestParam(String url, param)
	{
		URIBuilder uriBuilder = new URIBuilder(url)
		return uriBuilder.removeQueryParam(param).toString()
	}

	static def appendToPath(String url, addition)
	{
		URIBuilder uriBuilder = new URIBuilder(url)
		uriBuilder.path += addition
		return uriBuilder.toString()
	}

	static String makeStringList(List<String> strings)
	{
		def stringList = ""
		strings.each({
			stringList += (stringList) ? ", " : ""
			stringList += '\"' + it + '\"'
		})
		return stringList
	}

	static String makeStringMap(Map<String, String> strings)
	{
		def stringList = ""
		strings.keySet().each({
			stringList += (stringList) ? ", " : ""
			stringList += '\"' + it + '\" : \"' + strings[it] + '\"'
		})
		return stringList
	}

	static ZonedDateTime parseIsoDateTimeString(dateTimeString)
	{
		assert dateTimeString ==~ /[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}\+\d{4}/
		ZonedDateTime.parse((String) dateTimeString, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
	}

	static LocalDate parseIsoDateString(dateTimeString)
	{
		assert dateTimeString ==~ /[0-9]{4}-[0-9]{2}-[0-9]{2}/
		LocalDate.parse((String) dateTimeString, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
	}

	static String toIsoDateString(ZonedDateTime dateTime)
	{
		DateTimeFormatter.ofPattern("yyyy-MM-dd").format(dateTime)
	}

	static String toIsoDateTimeString(ZonedDateTime dateTime)
	{
		DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(dateTime)
	}

	static String toIsoWeekDateString(ZonedDateTime dateTime)
	{
		ISO8601_WEEK_FORMATTER.format(dateTime)
	}

	static def relativeDateTimeStringToZonedDateTime(relativeDateTimeString)
	{
		List<String> fields = relativeDateTimeString.tokenize(' ')
		assert fields.size() <= 3
		assert fields.size() > 0
		int parsedFields = 0
		int weekOffset = 0
		int dayOffset = 0

		switch (fields.size())
		{
			case 3:
				assert fields[0].startsWith("W")
				weekOffset = Integer.parseInt(fields[0].substring(1))
				parsedFields++
		// Fall through
			case 2:
				int weekDay = getDayOfWeek(DateTimeFormatter.ofPattern("eee")
						.withLocale(Locale.forLanguageTag("en-US"))
						.parse(fields[parsedFields]).get(ChronoField.DAY_OF_WEEK))
				dayOffset = weekDay - getDayOfWeek(now)
				parsedFields++
		// Fall through
			case 1:
				ZonedDateTime dateTime = parseTimeForDay(fields[parsedFields], now.plusDays(dayOffset).plusWeeks(weekOffset).getLong(ChronoField.EPOCH_DAY))
				assert dateTime <= now // Must be in the past
				return dateTime
		}
	}

	private static ZonedDateTime parseTimeForDay(String timeString, long epochDay)
	{
		DateTimeFormatter formatter =
				new DateTimeFormatterBuilder().appendPattern("HH:mm[:ss][.SSS]")
						.parseDefaulting(ChronoField.EPOCH_DAY, epochDay)
						.toFormatter()
						.withZone(EUROPE_AMSTERDAM_ZONE)
		ZonedDateTime.parse(timeString, formatter)
	}

	/**
	 * Given a number of weeks back and a short day (e.g. Mon), calculates the number of days since today.
	 * This allows to use it in an array of days, where [0] is today.
	 *
	 * @param weeksBack The number of weeks back in time
	 * @param shortDay Short day, e.g. Sun or Mon
	 *
	 * @return The number of days since today.
	 */
	static def relativeDateStringToDaysOffset(int weeksBack, String shortDay)
	{
		int targetWeekDay = getDayOfWeek(DateTimeFormatter.ofPattern("eee")
				.withLocale(Locale.forLanguageTag("en-US"))
				.parse(shortDay).get(ChronoField.DAY_OF_WEEK))
		int currentWeekDay = now.dayOfWeek.value
		int dayOffset = currentWeekDay - targetWeekDay
		return weeksBack * 7 + dayOffset
	}

	static int getCurrentDayOfWeek()
	{
		getDayOfWeek(now)
	}

	static int getDayOfWeek(ZonedDateTime dateTime)
	{
		getDayOfWeek(dateTime.dayOfWeek.value)
	}

	static int getDayOfWeek(int javaDayOfWeek)
	{
		// In Java, Sunday is the last day of the week, but in Yona the first one
		(javaDayOfWeek == 7) ? 0 : javaDayOfWeek
	}

	public def postJsonConcurrently(int numberOfTimes, String path, Object body, Map<String, String> parameters = [:], Map<String, String> headers = [:])
	{
		assert numberOfTimes <= maxConcurrentRequests, "numberOfTimes ($numberOfTimes) must be <= maxConcurrentRequests ($maxConcurrentRequests)"
		asyncHttpClient.handler.success = { resp -> resp.status }
		asyncHttpClient.handler.failure = asyncHttpClient.handler.success
		def futures = (1..numberOfTimes).collect {
			postThroughHttpBuilder(asyncHttpClient, path, body, parameters, headers)
		}
		futures.collect { it.get() }
	}
}
