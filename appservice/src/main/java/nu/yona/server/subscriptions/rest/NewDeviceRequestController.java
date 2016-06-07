package nu.yona.server.subscriptions.rest;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.crypto.CryptoException;
import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.rest.Constants;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.subscriptions.rest.NewDeviceRequestController.NewDeviceRequestResource;
import nu.yona.server.subscriptions.service.BuddyDTO;
import nu.yona.server.subscriptions.service.DeviceRequestException;
import nu.yona.server.subscriptions.service.NewDeviceRequestDTO;
import nu.yona.server.subscriptions.service.NewDeviceRequestService;
import nu.yona.server.subscriptions.service.UserDTO;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.subscriptions.service.UserServiceException;

@Controller
@ExposesResourceFor(NewDeviceRequestResource.class)
@RequestMapping(value = "/newDeviceRequests", produces = { MediaType.APPLICATION_JSON_VALUE })
public class NewDeviceRequestController
{
	private static final Logger logger = LoggerFactory.getLogger(NewDeviceRequestController.class);

	@Autowired
	private UserService userService;

	@Autowired
	private NewDeviceRequestService newDeviceRequestService;

	@RequestMapping(value = "/{mobileNumber}", method = RequestMethod.PUT)
	@ResponseStatus(HttpStatus.OK)
	public void setNewDeviceRequestForUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@PathVariable String mobileNumber, @RequestBody NewDeviceRequestCreationDTO newDeviceRequestCreation)
	{
		try
		{
			userService.validateMobileNumber(mobileNumber);
			UUID userID = userService.getUserByMobileNumber(mobileNumber).getID();
			checkPassword(password, userID);
			newDeviceRequestService.setNewDeviceRequestForUser(userID, password.get(),
					newDeviceRequestCreation.getNewDeviceRequestPassword());
		}
		catch (UserServiceException e)
		{
			logger.error("Caught UserServiceException. Mapping it to CryptoException", e);
			throw CryptoException.decryptingData();
		}
	}

	@RequestMapping(value = "/{mobileNumber}", method = RequestMethod.GET)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public HttpEntity<NewDeviceRequestResource> getNewDeviceRequestForUser(
			@RequestHeader(value = "Yona-NewDeviceRequestPassword") Optional<String> newDeviceRequestPassword,
			@PathVariable String mobileNumber)
	{
		try
		{
			userService.validateMobileNumber(mobileNumber);
			UserDTO user = userService.getUserByMobileNumber(mobileNumber);
			return createNewDeviceRequestResponse(user,
					newDeviceRequestService.getNewDeviceRequestForUser(user.getID(), newDeviceRequestPassword), HttpStatus.OK);
		}
		catch (UserServiceException e)
		{
			logger.error("Caught UserServiceException. Mapping it to DeviceRequestException", e);
			throw DeviceRequestException.noDeviceRequestPresent(mobileNumber);
		}
	}

	@RequestMapping(value = "/{mobileNumber}", method = RequestMethod.DELETE)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public void clearNewDeviceRequestForUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@PathVariable String mobileNumber)
	{
		try
		{
			userService.validateMobileNumber(mobileNumber);
			UUID userID = userService.getUserByMobileNumber(mobileNumber).getID();
			checkPassword(password, userID);
			newDeviceRequestService.clearNewDeviceRequestForUser(userID);
		}
		catch (UserServiceException e)
		{
			logger.error("Caught UserServiceException. Mapping it to CryptoException", e);
			throw CryptoException.decryptingData();
		}
	}

	private void checkPassword(Optional<String> password, UUID userID)
	{
		CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID), () -> null);
	}

	private HttpEntity<NewDeviceRequestResource> createNewDeviceRequestResponse(UserDTO user,
			NewDeviceRequestDTO newDeviceRequest, HttpStatus statusCode)
	{
		return new ResponseEntity<NewDeviceRequestResource>(
				new NewDeviceRequestResourceAssembler(user).toResource(newDeviceRequest), statusCode);
	}

	static ControllerLinkBuilder getNewDeviceRequestLinkBuilder(String mobileNumber)
	{
		NewDeviceRequestController methodOn = methodOn(NewDeviceRequestController.class);
		return linkTo(methodOn.getNewDeviceRequestForUser(null, mobileNumber));
	}

	public static class NewDeviceRequestResource extends Resource<NewDeviceRequestDTO>
	{
		public NewDeviceRequestResource(NewDeviceRequestDTO newDeviceRequest)
		{
			super(newDeviceRequest);
		}
	}

	public static class NewDeviceRequestResourceAssembler
			extends ResourceAssemblerSupport<NewDeviceRequestDTO, NewDeviceRequestResource>
	{
		private UserDTO user;

		public NewDeviceRequestResourceAssembler(UserDTO user)
		{
			super(NewDeviceRequestController.class, NewDeviceRequestResource.class);
			this.user = user;
		}

		@Override
		public NewDeviceRequestResource toResource(NewDeviceRequestDTO newDeviceRequest)
		{
			NewDeviceRequestResource newDeviceRequestResource = instantiateResource(newDeviceRequest);
			addSelfLink(newDeviceRequestResource);
			addEditLink(newDeviceRequestResource);/* always editable */
			addUserLink(newDeviceRequestResource);
			return newDeviceRequestResource;
		}

		@Override
		protected NewDeviceRequestResource instantiateResource(NewDeviceRequestDTO newDeviceRequest)
		{
			return new NewDeviceRequestResource(newDeviceRequest);
		}

		private void addSelfLink(Resource<NewDeviceRequestDTO> newDeviceRequestResource)
		{
			newDeviceRequestResource
					.add(NewDeviceRequestController.getNewDeviceRequestLinkBuilder(user.getMobileNumber()).withSelfRel());
		}

		private void addEditLink(Resource<NewDeviceRequestDTO> newDeviceRequestResource)
		{
			newDeviceRequestResource.add(NewDeviceRequestController.getNewDeviceRequestLinkBuilder(user.getMobileNumber())
					.withRel(JsonRootRelProvider.EDIT_REL));
		}

		private void addUserLink(Resource<NewDeviceRequestDTO> newDeviceRequestResource)
		{
			newDeviceRequestResource.add(UserController.getPrivateUserLink(BuddyDTO.USER_REL_NAME, user.getID()));
		}
	}
}
