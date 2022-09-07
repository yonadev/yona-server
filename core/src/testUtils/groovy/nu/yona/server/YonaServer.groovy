/*******************************************************************************
 * Copyright (c) 2015, 2022 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import java.nio.charset.StandardCharsets
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
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.stream.Collectors
import java.util.stream.Stream

import org.apache.http.HttpEntity

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

class YonaServer
{
	static class Response
	{
		int status
		def responseData
		def data
		def contentType
		def headers

		Response(int status, responseData, message, contentType, headers)
		{
			this.status = status
			this.responseData = responseData
			this.data = message
			this.contentType = contentType
			this.headers = headers
		}
	}
	static final ZoneId EUROPE_AMSTERDAM_ZONE = ZoneId.of("Europe/Amsterdam")
	static final Locale EN_US_LOCALE = Locale.forLanguageTag("en-US")
	private static final DateTimeFormatter ISO8601_WEEK_FORMATTER = new DateTimeFormatterBuilder().parseCaseInsensitive()
			.appendValue(IsoFields.WEEK_BASED_YEAR, 4, 10, SignStyle.EXCEEDS_PAD).appendLiteral("-W")
			.appendValue(IsoFields.WEEK_OF_WEEK_BASED_YEAR, 2)
			.parseDefaulting(WeekFields.ISO.dayOfWeek(), DayOfWeek.MONDAY.getValue()).toFormatter(Locale.forLanguageTag("en-US"))
	JsonSlurper jsonSlurper = new JsonSlurper()
	Proxy proxy = Proxy.NO_PROXY // new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 8888))
	def baseUrl
	int maxConcurrentRequests
	ExecutorService executorService

	YonaServer(baseUrl)
	{
		this.baseUrl = baseUrl
	}

	void enableConcurrentRequests(int maxConcurrentRequests)
	{
		this.maxConcurrentRequests = maxConcurrentRequests
		executorService = Executors.newFixedThreadPool(5)
	}

	void shutdown()
	{
		/* TODO
		if (asyncHttpClient)
		{
			asyncHttpClient.shutdown()
		}*/
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
		def urlConnection = buildUrl(path, parameters).openConnection(proxy)
		urlConnection.setInstanceFollowRedirects(false)
		urlConnection.setRequestMethod("DELETE")
		headers.each({ urlConnection.setRequestProperty(it.key, it.value) })
		logRequest(urlConnection)
		return buildResponseObject(urlConnection)
	}

	def getJsonWithPassword(path, password, parameters = [:], headers = [:])
	{
		getJson(path, parameters, addPasswordToHeaders(headers, password))
	}

	def getJson(path, parameters = [:], headers = [:])
	{
		return getResource(path, parameters, headers + ["Accept": "application/json"], true)
	}

	def getData(path, parameters = [:], headers = [:])
	{
		return getResource(path, parameters, headers, false)
	}

	private def getResource(path, parameters, headers, isJson)
	{
		def urlConnection = buildUrl(path, parameters).openConnection(proxy)
		urlConnection.setInstanceFollowRedirects(false)
		urlConnection.setRequestMethod("GET")
		headers.findAll { it.value != null }.each({ urlConnection.setRequestProperty(it.key, it.value) })
		logRequest(urlConnection)
		return buildResponseObject(urlConnection, isJson)
	}

	private URL buildUrl(String path, Map<Object, Object> parameters)
	{
		def queryParamsMap = getQueryParamsMap(path) + parameters
		def queryString = ""
		if (queryParamsMap.size() > 0)
		{
			queryString = "?" + queryParamsMapToString(queryParamsMap)
		}
		def prefix = (path.startsWith("http") ? "" : baseUrl)

		def url = new URL(prefix + stripQueryString(path) + queryString)
		url
	}

	def postJson(String path, Object body, Map<String, String> parameters = [:], Map<String, String> headers = [:])
	{
		postOrPutResource("POST", path, "application/json", "application/json", body, parameters, headers)
	}

	def postData(String path, String contentTypeHeader, String acceptHeader, Object body, Map<String, String> parameters = [:], Map<String, String> headers = [:])
	{
		postOrPutResource("POST", path, contentTypeHeader, acceptHeader, body, parameters, headers)
	}

	def putData(String path, String contentTypeHeader, String acceptHeader, Object body, Map<String, String> parameters = [:], Map<String, String> headers = [:])
	{
		postOrPutResource("PUT", path, contentTypeHeader, acceptHeader, body, parameters, headers)
	}

	private def postOrPutResource(String requestMethod, String path, String contentTypeHeader, String acceptHeader, Object body, Map<String, String> parameters = [:], Map<String, String> headers = [:])
	{
		def bodyText
		if (body instanceof Map)
		{
			bodyText = new JsonBuilder(body).toString()
		}
		else if (body instanceof JsonBuilder)
		{
			bodyText = body.toString()
		}
		else
		{
			bodyText = body
		}


		def url = buildUrl(path, parameters)
		def urlConnection = url.openConnection(proxy)
		urlConnection.setInstanceFollowRedirects(false)
		urlConnection.setRequestMethod(requestMethod)
		urlConnection.setDoOutput(true)
		urlConnection.setRequestProperty("Content-Type", contentTypeHeader)
		if (acceptHeader)
		{
			urlConnection.setRequestProperty("Accept", acceptHeader)
		}
		headers.each({ urlConnection.setRequestProperty(it.key, it.value) })
		logRequest(urlConnection, body)
		if (body instanceof HttpEntity)
		{
			body.writeTo(urlConnection.getOutputStream())
		}
		else
		{
			urlConnection.getOutputStream().write(bodyText.getBytes("UTF-8"))
		}
		return buildResponseObject(urlConnection, acceptHeader == "application/json")
	}

	private Response buildResponseObject(URLConnection urlConnection, isJson = true)
	{
		int responseCode = urlConnection.getResponseCode()
		def data = isSuccessCode(responseCode) ? urlConnection.getInputStream()?.getText() : urlConnection.getErrorStream()?.getText()
		def parsedData = data ? (isSuccessCode(responseCode) && isJson ? jsonSlurper.parseText(data) : tryParseErrorText(data)) : null
		logResponse(responseCode, data, parsedData)
		return new Response(responseCode, parsedData, data, urlConnection.getContentType(), urlConnection.getHeaderFields())
	}

	private static void logRequest(URLConnection urlConnection, Object body = null)
	{
		def url = urlConnection.getURL()
		def requestMethod = urlConnection.getRequestMethod()
		def headers = urlConnection.getRequestProperties()
		System.err.println "Open $url for $requestMethod with headers $headers"
	}

	private static void logResponse(int responseCode, String rawData, Object parsedData)
	{
		System.err.println "Response status: $responseCode"
	}

	def tryParseErrorText(def data)
	{
		try
		{
			return jsonSlurper.parseText(data)
		}
		catch (Throwable ignored)
		{
			return null
		}
	}

	private static boolean isSuccessCode(int responseCode)
	{
		responseCode >= 200 && responseCode <= 299
	}

	def putJson(path, body, parameters = [:], headers = [:])
	{
		postOrPutResource("PUT", path, "application/json", "application/json", body, parameters, headers)
	}

	static def getQueryParamsMap(String url)
	{
		URI uri = new URI(url)

		def queryString = uri.getRawQuery()
		if (queryString == null)
		{
			return [:]
		}
		return Stream.of(queryString.split("&"))
				.map(e -> e.split("="))
				.collect(Collectors.toMap(v -> v[0], v -> URLDecoder.decode(v[1], StandardCharsets.UTF_8)))
	}

	static def queryParamsMapToString(def queryParamsMap)
	{
		return queryParamsMap.entrySet().stream()
				.map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue().toString(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"))
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
		URI givenUri = new URI(url)
		def queryString = Stream.of(givenUri.getQuery().split("&"))
				.map(p -> p.split("="))
				.filter(p -> p[0] != param)
				.map(p -> String.join("=", p))
				.collect(Collectors.joining("&"))
		return new URI(givenUri.getScheme(), givenUri.getAuthority(), givenUri.getPath(), queryString, givenUri.getFragment()).toString()
	}

	static def appendToPath(String url, addition)
	{
		URI givenUri = new URI(url)
		return new URI(givenUri.getScheme(), givenUri.getAuthority(), givenUri.getPath() + addition, givenUri.getQuery(), givenUri.getFragment()).toString()
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

	def postJsonConcurrently(int numberOfTimes, String path, Object body, Map<String, String> parameters = [:], Map<String, String> headers = [:])
	{
		assert numberOfTimes <= maxConcurrentRequests, "numberOfTimes ($numberOfTimes) must be <= maxConcurrentRequests ($maxConcurrentRequests)"
		def futures = (1..numberOfTimes).collect {
			executorService.submit({ return postJson(path, body, parameters, headers) } as Callable<Response>)
		}
		futures.collect { it.get() }
	}
}
