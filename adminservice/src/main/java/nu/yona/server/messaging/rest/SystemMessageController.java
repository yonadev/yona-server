package nu.yona.server.messaging.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import nu.yona.server.messaging.service.MessageService;

@Controller
@RequestMapping(value = "/systemMessages")
public class SystemMessageController
{
	@Autowired
	private MessageService messageService;

	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String getIndexPage()
	{
		return "system-messages";
	}

	@RequestMapping(value = "/", method = RequestMethod.POST)
	public String addSystemMessage(@RequestParam String message)
	{
		messageService.broadcastSystemMessage(message);

		return "redirect:/systemMessages/";
	}
}
