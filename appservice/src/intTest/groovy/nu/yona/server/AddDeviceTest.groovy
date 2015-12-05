package nu.yona.server


import groovy.json.*
import spock.lang.Shared

class AddDeviceTest extends AbstractAppServiceIntegrationTest {

	@Shared
	def richardQuin

	def 'Add user Richard'(){
		given:

		when:
		richardQuin = addUser("Richard", "Quin")

		then:
		richardQuin
	}

	def 'Set new device request'(){
		given:

		when:
		def response = appService.setNewDeviceRequest(richardQuin.url, richardQuin.password, """{
				"userSecret":"unknown secret"
			}""")

		then:
		response.status == 201
		def getResponseAfter = appService.getNewDeviceRequest(richardQuin.url)
		getResponseAfter.status == 200
	}

	def 'Get new device request with user secret'(){
		given:

		when:
		def response = appService.getNewDeviceRequest(richardQuin.url, "unknown secret")

		then:
		response.status == 200
		response.responseData.userPassword == richardQuin.password
	}

	def 'Try get new device request with wrong user secret'(){
		given:

		when:
		def response = appService.getNewDeviceRequest(richardQuin.url, "wrong secret")

		then:
		response.status == 400
	}

	def 'Try set new device request with wrong password'(){
		given:

		when:
		def response = appService.setNewDeviceRequest(richardQuin.url, "foo", """{
				"userSecret":"known secret"
			}""")

		then:
		response.status == 400
		def getResponseAfter = appService.getNewDeviceRequest(richardQuin.url)
		getResponseAfter.status == 200
	}

	def 'Overwrite new device request'(){
		given:

		when:
		def response = appService.setNewDeviceRequest(richardQuin.url, richardQuin.password, """{
				"userSecret":"unknown overwritten secret"
			}""")

		then:
		response.status == 200
		def getResponseAfter = appService.getNewDeviceRequest(richardQuin.url)
		getResponseAfter.status == 200
	}

	def 'Get overwritten device request with user secret'(){
		given:

		when:
		def response = appService.getNewDeviceRequest(richardQuin.url, "unknown overwritten secret")

		then:
		response.status == 200
		response.responseData.userPassword == richardQuin.password
	}

	def 'Clear new device request'(){
		given:

		when:
		def response = appService.clearNewDeviceRequest(richardQuin.url, richardQuin.password)

		then:
		response.status == 200
		def getResponseAfter = appService.getNewDeviceRequest(richardQuin.url)
		getResponseAfter.status == 400
		getResponseAfter.data.containsKey("code")
		getResponseAfter.data["code"] == "error.no.device.request.present"
	}
}
