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
	def timestamp = YonaServer.getTimeStamp()
	
	@Shared
	def richardQuinURL
	@Shared
	def richardQuinPassword = "R i c h a r d"
	
	def 'Add user Richard Quin'(){
		given:

		when:
			def response = appService.addUser("""{
				"firstName":"Richard ${timestamp}",
				"lastName":"Quin ${timestamp}",
				"nickName":"RQ ${timestamp}",
				"emailAddress":"rich${timestamp}@quin.net",
				"mobileNumber":"+${timestamp}1",
				"devices":[
					"Nexus 6"
				],
				"goals":[
					"gambling"
				]
			}""", richardQuinPassword)
			if(response.status == 201)
			{
				richardQuinURL = appService.stripQueryString(response.responseData._links.self.href)
			}

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

		then:
			response.status == 201
			def getResponseAfter = appService.getNewDeviceRequest(richardQuinURL)
			getResponseAfter.status == 200
	}
	
	def 'Get new device request with user secret'(){
		given:

		when:
			def response = appService.getNewDeviceRequest(richardQuinURL, "unknown secret")
			
		then:
			response.status == 200
			response.responseData.userPassword == richardQuinPassword
	}
	
	def 'Try get new device request with wrong user secret'(){
		given:

		when:
			def response = appService.getNewDeviceRequest(richardQuinURL, "wrong secret")
			
		then:
			response.status == 400
	}
	
	def 'Try set new device request with wrong password'(){
		given:

		when:
			def response = appService.setNewDeviceRequest(richardQuinURL, "foo", """{
				"userSecret":"known secret"
			}""")
			
		then:
			response.status == 400
			def getResponseAfter = appService.getNewDeviceRequest(richardQuinURL)
			getResponseAfter.status == 200
	}
	
	def 'Overwrite new device request'(){
		given:

		when:
			def response = appService.setNewDeviceRequest(richardQuinURL, richardQuinPassword, """{
				"userSecret":"unknown overwritten secret"
			}""")
			
		then:
			response.status == 200
			def getResponseAfter = appService.getNewDeviceRequest(richardQuinURL)
			getResponseAfter.status == 200
	}
	
	def 'Get overwritten device request with user secret'(){
		given:

		when:
			def response = appService.getNewDeviceRequest(richardQuinURL, "unknown overwritten secret")
			
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
			def getResponseAfter = appService.getNewDeviceRequest(richardQuinURL)
			getResponseAfter.status == 400
			getResponseAfter.data.containsKey("code")
			getResponseAfter.data["code"] == "error.no.device.request.present"
	}
	
	def 'Delete Richard Quin'(){
		given:

		when:
			def response = appService.deleteUser(richardQuinURL, richardQuinPassword)

		then:
			response.status == 200
	}
}
