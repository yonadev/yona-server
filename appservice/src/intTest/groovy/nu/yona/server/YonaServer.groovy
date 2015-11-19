package nu.yona.server

import groovyx.net.http.RESTClient
import groovy.json.*

import java.text.SimpleDateFormat

import javax.mail.*
import javax.mail.search.*

import javax.management.InstanceOfQueryExp;

class YonaServer {
	final GOALS_PATH = "/goals/"
	final USERS_PATH = "/users/"
	final ANALYSIS_ENGINE_PATH = "/analysisEngine/"
	final BUDDIES_PATH_FRAGMENT = "/buddies/"
	final DIRECT_MESSAGE_PATH_FRAGMENT = "/messages/direct/"
	final ANONYMOUS_MESSAGES_PATH_FRAGMENT = "/messages/anonymous/"
	final RELEVANT_CATEGORIES_PATH_FRAGMENT = "/relevantCategories/"

	JsonSlurper jsonSlurper = new JsonSlurper()
	RESTClient restClient

	YonaServer (baseURL)
	{
		restClient = new RESTClient(baseURL)
        
        restClient.handler.failure = restClient.handler.success 
	}

	def static getTimeStamp()
	{
		def formatter = new SimpleDateFormat("yyyyMMddhhmmss")
		formatter.format(new Date())
	}

	def deleteGoal(goalURL)
	{
		deleteResource(goalURL)
	}

	def addGoal(jsonString)
	{
		createResource(GOALS_PATH, jsonString)
	}
	
	def getGoal(goalURL)
	{
		getResource(goalURL)
	}

	def addUser(jsonString, password)
	{
		createResourceWithPassword(USERS_PATH, jsonString, password)
	}

	def getUser(userURL, boolean includePrivateData, password = null)
	{
		if (includePrivateData) {
			getResourceWithPassword(userURL, password, ["includePrivateData": "true"])
		} else {
			getResourceWithPassword(userURL, password)
		}
	}
	
	def updateUser(userURL, jsonString, password)
	{
		updateResourceWithPassword(userURL, jsonString, password)
	}

	def deleteUser(userURL, password)
	{
		deleteResourceWithPassword(userURL, password)
	}

	def requestBuddy(userPath, jsonString, password)
	{
		createResourceWithPassword(userPath + BUDDIES_PATH_FRAGMENT, jsonString, password)
	}

	def getRelevantCategories()
	{
		getResource(ANALYSIS_ENGINE_PATH + RELEVANT_CATEGORIES_PATH_FRAGMENT)
	}

	def getAllGoals()
	{
		getResource(GOALS_PATH)
	}

	def getBuddies(userPath, password)
	{
		getResourceWithPassword(userPath + BUDDIES_PATH_FRAGMENT, password)
	}

	def getDirectMessages(userPath, password)
	{
		getResourceWithPassword(userPath + DIRECT_MESSAGE_PATH_FRAGMENT, password)
	}

	def getAnonymousMessages(userPath, password)
	{
		getResourceWithPassword(userPath + ANONYMOUS_MESSAGES_PATH_FRAGMENT, password)
	}

	def createResourceWithPassword(path, jsonString, password)
	{
		createResource(path, jsonString, ["Yona-Password": password])
	}

	def createResource(path, jsonString, headers = [:])
	{
		postJson(path, jsonString, headers);
	}
	
	def updateResourceWithPassword(path, jsonString, password)
	{
		createResource(path, jsonString, ["Yona-Password": password])
	}

	def updateResource(path, jsonString, headers = [:])
	{
		putJson(path, jsonString, headers);
	}

	def deleteResourceWithPassword(path, password)
	{
		deleteResource(path, ["Yona-Password": password])
	}

	def deleteResource(path, headers = [:])
	{
		restClient.delete(path: path, headers: headers)
	}

	def getResourceWithPassword(path, password, parameters = [:])
	{
		getResource(path, password ?  ["Yona-Password": password] : [ : ], parameters)
	}

	def postMessageActionWithPassword(path, jsonString, password)
	{
		postMessageAction(path, jsonString, ["Yona-Password": password])
	}

	def postMessageAction(path, jsonString, headers = [:])
	{
		postJson(path, jsonString, headers);
	}

	def postToAnalysisEngine(jsonString)
	{
		postJson(ANALYSIS_ENGINE_PATH, jsonString);
	}

	def getResource(path, headers = [:], parameters = [:])
	{
		restClient.get(path: path,
			contentType:'application/json',
			headers: headers,
			query: parameters)
	}

	def postJson(path, jsonString, headers = [:])
	{
        def object = null
        if (jsonString instanceof Map)
        {
            object = jsonString;
        }
        else
        {
            object = jsonSlurper.parseText(jsonString)
        }
        
		restClient.post(path: path,
			body: object,
			contentType:'application/json',
			headers: headers)
	}
	
	def putJson(path, jsonString, headers = [:])
	{
        def object = null
        if (jsonString instanceof Map)
        {
            object = jsonString;
        }
        else
        {
            object = jsonSlurper.parseText(jsonString)
        }
        
		restClient.post(path: path,
			body: object,
			contentType:'application/json',
			headers: headers)
	}

	def stripQueryString(url)
	{
		url - ~/\?.*/
	}

	def getLastMessageFromGmail(email, pwd)
	{
		Properties props = new Properties()
		props.setProperty("mail.store.protocol", "imap")
		props.setProperty("mail.imap.host", "imap.gmail.com")
		props.setProperty("mail.imap.port", "993")
		props.setProperty("mail.imap.ssl.enable", "true");
		def session = Session.getDefaultInstance(props, null)
		def store = session.getStore("imap")
		
		def inbox
		def lastMessage
		try 
		{
			store.connect(host, email, pwd)
			inbox = store.getFolder("INBOX")
			inbox.open(Folder.READ_WRITE)
			def maxWaitSeconds = 15
			def pollSeconds = 3
			def retries = maxWaitSeconds / pollSeconds
			sleep(1000)
			for (i = 0; i <retries; i++) 
			{
				def messages = inbox.search(
					new FlagTerm(new Flags(Flags.Flag.SEEN), false))
				if(messages.size() > 0)
				{
					lastMessage = inbox.messages[0]
					lastMessage.setFlag(Flags.Flag.SEEN, true)
					break;
				}
				sleep(pollSeconds * 1000)
			}
			assert lastMessage
		}
		finally
		{
			 if(inbox) 
			 {
			    inbox.close(true)
			 }
			 store.close()
		}
		
		lastMessage
	}
}
