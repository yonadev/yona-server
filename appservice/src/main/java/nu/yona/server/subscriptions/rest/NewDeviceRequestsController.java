package nu.yona.server.subscriptions.rest;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.rest.Constants;
import nu.yona.server.subscriptions.rest.UserController.NewDeviceRequestResource;
import nu.yona.server.subscriptions.rest.UserController.UserResource;
import nu.yona.server.subscriptions.service.NewDeviceRequestDTO;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@ExposesResourceFor(UserResource.class)
@RequestMapping(value = "/newDeviceRequests")
public class NewDeviceRequestsController
{
	@Autowired
	private UserService userService;

	@RequestMapping(value = "/{mobileNumber}", method = RequestMethod.PUT)
	@ResponseBody
	public HttpEntity<NewDeviceRequestResource> setNewDeviceRequestForUser(
			@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable String mobileNumber,
			@RequestBody NewDeviceRequestCreationDTO newDeviceRequestCreation)
	{
		UUID userID = userService.getUserByMobileNumber(mobileNumber).getID();
		checkPassword(password, userID);
		NewDeviceRequestDTO newDeviceRequestResult = userService.setNewDeviceRequestForUser(userID, password.get(),
				newDeviceRequestCreation.getUserSecret());
		return createNewDeviceRequestResponse(newDeviceRequestResult, getNewDeviceRequestLinkBuilder(mobileNumber),
				newDeviceRequestResult.getIsUpdatingExistingRequest() ? HttpStatus.OK : HttpStatus.CREATED);
	}

	@RequestMapping(value = "/{mobileNumber}", params = { "userSecret" }, method = RequestMethod.GET)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public HttpEntity<NewDeviceRequestResource> getNewDeviceRequestForUser(@PathVariable String mobileNumber,
			@RequestParam(value = "userSecret", required = false) String userSecret)
	{
		UUID userID = userService.getUserByMobileNumber(mobileNumber).getID();
		return createNewDeviceRequestResponse(userService.getNewDeviceRequestForUser(userID, userSecret),
				getNewDeviceRequestLinkBuilder(mobileNumber), HttpStatus.OK);
	}

	@RequestMapping(value = "/{mobileNumber}", method = RequestMethod.DELETE)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public void clearNewDeviceRequestForUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@PathVariable String mobileNumber)
	{
		UUID userID = userService.getUserByMobileNumber(mobileNumber).getID();
		checkPassword(password, userID);
		userService.clearNewDeviceRequestForUser(userID);
	}

	private void checkPassword(Optional<String> password, UUID userID)
	{
		CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID), () -> null);
	}

	private HttpEntity<NewDeviceRequestResource> createNewDeviceRequestResponse(NewDeviceRequestDTO newDeviceRequest,
			ControllerLinkBuilder entityLinkBuilder, HttpStatus statusCode)
	{
		return new ResponseEntity<NewDeviceRequestResource>(new NewDeviceRequestResource(newDeviceRequest, entityLinkBuilder),
				statusCode);
	}

	static ControllerLinkBuilder getNewDeviceRequestLinkBuilder(String mobileNumber)
	{
		NewDeviceRequestsController methodOn = methodOn(NewDeviceRequestsController.class);
		return linkTo(methodOn.getNewDeviceRequestForUser(mobileNumber, null));
	}
}
