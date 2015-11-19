package nu.yona.server

import groovyx.net.http.RESTClient
import groovyx.net.http.HttpResponseException
import spock.lang.Ignore
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import groovy.json.*

class AddDeviceTest extends Specification {

	def appServiceBaseURL = System.properties.'yona.appservice.url'
	def YonaServer appService = new YonaServer(appServiceBaseURL)
	
	@Shared
	def richardQuinURL
	@Shared
	def richardQuinPassword = "R i c h a r d"
	@Shared
	def newDeviceRequestExpirationDateTime
	
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
					"Nexus 6"
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
	
	def 'Set new device request'(){
		given:

		when:
			def response = appService.setNewDeviceRequest(richardQuinURL, richardQuinPassword, """{
				"userSecret":"unknown secret"
			}""")
			newDeviceRequestExpirationDateTime = response.responseData.expirationDateTime

		then:
			response.status == 200
			response.responseData.expirationDateTime != null
			def getResponseAfter = appService.getNewDeviceRequest(richardQuinURL, richardQuinPassword)
			getResponseAfter.status == 200
			getResponseAfter.responseData.expirationDateTime == newDeviceRequestExpirationDateTime
	}
	
	def 'Get new device request with user secret'(){
		given:

		when:
			def response = appService.getNewDeviceRequest(richardQuinURL, richardQuinPassword, "unknown secret")
			
		then:
			response.status == 200
			response.responseData.userPassword == richardQuinPassword
	}
	
	def 'Try set new device request with wrong password'(){
		given:

		when:
			try {
				appService.setNewDeviceRequest(richardQuinURL, "foo", """{
					"userSecret":"known secret"
				}""")
				assert false;
			} catch (HttpResponseException e) {
				assert e.statusCode == 400
			}

		then:
			def getResponseAfter = appService.getNewDeviceRequest(richardQuinURL, richardQuinPassword)
			getResponseAfter.status == 200
			getResponseAfter.responseData.expirationDateTime == newDeviceRequestExpirationDateTime
	}
	
	def 'Overwrite new device request'(){
		given:

		when:
			def response = appService.setNewDeviceRequest(richardQuinURL, richardQuinPassword, """{
				"userSecret":"unknown overwritten secret"
			}""")
			
		then:
			response.status == 200
			def getResponseAfter = appService.getNewDeviceRequest(richardQuinURL, richardQuinPassword)
			getResponseAfter.status == 200
			getResponseAfter.responseData.expirationDateTime > newDeviceRequestExpirationDateTime
	}
	
	def 'Get overwritten device request with user secret'(){
		given:

		when:
			def response = appService.getNewDeviceRequest(richardQuinURL, richardQuinPassword, "unknown overwritten secret")
			
		then:
			response.status == 200
			response.responseData.userPassword == richardQuinPassword
	}
	
	def 'Clear new device request'(){
		given:

		when:
			def response = appService.clearNewDeviceRequest(richardQuinURL, richardQuinPassword)

		then:
			response.status == 200
			def getResponseAfter = appService.getNewDeviceRequest(richardQuinURL, richardQuinPassword)
			getResponseAfter.status == 200
			getResponseAfter.responseData.expirationDateTime == null
	}
	
	def 'Delete Richard Quin'(){
		given:

		when:
			def response = appService.deleteUser(richardQuinURL, richardQuinPassword)

		then:
			response.status == 200
	}
}