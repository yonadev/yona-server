package nu.yona.server


import groovy.json.*
import spock.lang.Shared

class AddDeviceTest extends AbstractAppServiceIntegrationTest
{
	def 'Set new device request'()
	{
		given:
			def richard = addRichard();

		when:
			def userSecret = "unknown secret"
			def response = newAppService.setNewDeviceRequest(richard.url, richard.password, userSecret)

		then:
			response.status == 201
			def getResponseAfter = newAppService.getNewDeviceRequest(richard.url)
			getResponseAfter.status == 200

			def getWithPasswordResponseAfter = newAppService.getNewDeviceRequest(richard.url, userSecret)
			getWithPasswordResponseAfter.status == 200
			getWithPasswordResponseAfter.responseData.userPassword == richard.password

		cleanup:
			newAppService.deleteUser(richard)
	}

	def 'Try get new device request with wrong user secret'()
	{
		given:
			def userSecret = "unknown secret"
			def richard = addRichard();
			newAppService.setNewDeviceRequest(richard.url, richard.password, userSecret)

		when:
			def response = newAppService.getNewDeviceRequest(richard.url, "wrong secret")

		then:
			response.status == 400

		cleanup:
			newAppService.deleteUser(richard)
	}

	def 'Try set new device request with wrong password'()
	{
		given:
			def richard = addRichard();
			def userSecret = "unknown secret"
			newAppService.setNewDeviceRequest(richard.url, richard.password, userSecret)
			def getResponseImmmediately = newAppService.getNewDeviceRequest(richard.url)
			assert getResponseImmmediately.status == 200

		when:
			def response = newAppService.setNewDeviceRequest(richard.url, "foo", "Some secret")

		then:
			response.status == 400
			def getResponseAfter = newAppService.getNewDeviceRequest(richard.url, userSecret)
			getResponseAfter.status == 200
			getResponseAfter.responseData.userPassword == richard.password

		cleanup:
			newAppService.deleteUser(richard)
	}

	def 'Overwrite new device request'()
	{
		given:
			def richard = addRichard();
			newAppService.setNewDeviceRequest(richard.url, richard.password, "Some secret")

		when:
			def userSecret = "unknown secret"
			def response = newAppService.setNewDeviceRequest(richard.url, richard.password, userSecret)

		then:
			response.status == 200
			def getResponseAfter = newAppService.getNewDeviceRequest(richard.url, userSecret)
			getResponseAfter.status == 200
			getResponseAfter.responseData.userPassword == richard.password

		cleanup:
			newAppService.deleteUser(richard)
	}

	def 'Clear new device request'()
	{
		given:
			def userSecret = "unknown secret"
			def richard = addRichard();
			newAppService.setNewDeviceRequest(richard.url, richard.password, userSecret)

		when:
			def response = newAppService.clearNewDeviceRequest(richard.url, richard.password)

		then:
			response.status == 200
			def getResponseAfter = newAppService.getNewDeviceRequest(richard.url)
			getResponseAfter.status == 400
			getResponseAfter.data.containsKey("code")
			getResponseAfter.data["code"] == "error.no.device.request.present"

			def getWithPasswordResponseAfter = newAppService.getNewDeviceRequest(richard.url, userSecret)
			getWithPasswordResponseAfter.status == 400
			getWithPasswordResponseAfter.data.containsKey("code")
			getWithPasswordResponseAfter.data["code"] == "error.no.device.request.present"

		cleanup:
			newAppService.deleteUser(richard)
	}
}
