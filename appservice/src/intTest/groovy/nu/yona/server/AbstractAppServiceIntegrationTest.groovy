package nu.yona.server

import groovy.json.*

import spock.lang.Shared
import spock.lang.Specification

import java.text.SimpleDateFormat

import nu.yona.server.test.AnalysisService
import nu.yona.server.test.AppService

abstract class AbstractAppServiceIntegrationTest extends Specification
{
	@Shared
	def AnalysisService analysisService = new AnalysisService()

	@Shared
	def AppService appService = new AppService()

	@Shared
	private String baseTimestamp = createBaseTimestamp()

	@Shared
	private int sequenceNumber = 0

	def addRichard()
	{
		def richard = appService.addUser(appService.&assertUserCreationResponseDetails, "R i c h a r d", "Richard", "Quinn", "RQ",
			"+$timestamp", [ "Nexus 6" ], [ "news", "gambling" ])
		appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, richard)
		return richard
	}

	def addBob()
	{
		def bob = appService.addUser(appService.&assertUserCreationResponseDetails, "B o b", "Bob", "Dunn", "BD",
			"+$timestamp", [ "iPhone 5" ], [ "news", "gambling" ])
		appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, bob)
		return bob
	}

	def addRichardAndBobAsBuddies()
	{
		def richard = addRichard()
		def bob = addBob()
		appService.makeBuddies(richard, bob)
		return ["richard" : richard, "bob" : bob]
	}

	private static String createBaseTimestamp()
	{
		def formatter = new SimpleDateFormat("yyyyMMddhhmmss")
		formatter.format(new Date())
	}

	protected String getTimestamp() 
	{
		int num = sequenceNumber++
		return "$baseTimestamp$num"
	}
}
