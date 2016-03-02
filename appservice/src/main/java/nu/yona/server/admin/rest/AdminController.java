package nu.yona.server.admin.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import nu.yona.server.subscriptions.service.OverwriteUserDTO;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@RequestMapping(value = "/admin")
public class AdminController
{

	@Autowired
	private UserService userService;

	@RequestMapping(value = "/requestUserOverwrite/", method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<Resource<OverwriteUserDTO>> setOverwriteUserConfirmationCode(@RequestParam String mobileNumber)
	{
		return new ResponseEntity<Resource<OverwriteUserDTO>>(
				new Resource<OverwriteUserDTO>(userService.setOverwriteUserConfirmationCode(mobileNumber)), HttpStatus.OK);
	}
}
