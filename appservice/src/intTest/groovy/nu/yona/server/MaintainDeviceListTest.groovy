package nu.yona.server

import groovyx.net.http.RESTClient
import groovyx.net.http.HttpResponseException
import spock.lang.Ignore
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import groovy.json.*

class MaintainDeviceListTest extends Specification {

	def appServiceBaseURL = System.properties.'yona.appservice.url'
	def YonaServer appService = new YonaServer(appServiceBaseURL)
	
	@Shared
	def richardQuinURL
	@Shared
	def richardQuinPassword = "R i c h a r d"
	
	def 'Add user Richard Quin'(){
		given:

		when:
			def response = appService.addUser("""{
				"firstName":"Richard",
				"lastName":"Quin",
				"nickName":"RQ",
				"emailAddress":"rich@quin.net",
				"mobileNumber":"+12345678",
				"devices":[
					"Nexus 6",
					"HP Laptop"
				],
				"goals":[
					"gambling"
				]
			}""", richardQuinPassword)
			richardQuinURL = appService.stripQueryString(response.responseData._links.self.href)

		then:
			response.status == 201

		cleanup:
			println "URL Richard: " + richardQuinURL
	}
	
	def 'Get devices list'(){
		given:

		when:
			def response = appService.getDeviceList(richardQuinURL, richardQuinPassword)

		then:
			response.status == 200
			response.responseData.devices.size() == 2
			response.responseData.devices.contains("Nexus 6")
			response.responseData.devices.contains("HP Laptop")
	}
	
	def 'Add new device to list'(){
		given:

		when:
			def response = appService.addDeviceToList(richardQuinURL, '"iPhone 1001"', richardQuinPassword)
			
		then:
			response.status == 200
			response.responseData.devices.size() == 3
			response.responseData.devices.contains("Nexus 6")
			response.responseData.devices.contains("HP Laptop")
			response.responseData.devices.contains("iPhone 1001")
	}
	
	def 'Remove device from list'(){
		given:

		when:
			def response = appService.removeDeviceFromList(richardQuinURL, "iPhone 1001", richardQuinPassword)

		then:
			response.status == 200
			def getResponseAfter = appService.getDeviceList(richardQuinURL, richardQuinPassword)			
			getResponseAfter.responseData.devices.size() == 2
			getResponseAfter.responseData.devices.contains("Nexus 6")
			getResponseAfter.responseData.devices.contains("HP Laptop")
	}
	
	def 'Delete Richard Quin'(){
		given:

		when:
			def response = appService.deleteUser(richardQuinURL, richardQuinPassword)

		then:
			response.status == 200
	}
}