/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.hal.CurieProvider;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.BruteForceAttemptService;
import nu.yona.server.DOSProtectionService;
import nu.yona.server.analysis.rest.AppActivityController;
import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.goals.rest.GoalController;
import nu.yona.server.goals.service.GoalDTO;
import nu.yona.server.messaging.rest.MessageController;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.rest.Constants;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.subscriptions.rest.UserController.UserResource;
import nu.yona.server.subscriptions.service.BuddyDTO;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.NewDeviceRequestDTO;
import nu.yona.server.subscriptions.service.OverwriteUserDTO;
import nu.yona.server.subscriptions.service.UserDTO;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@ExposesResourceFor(UserResource.class)
@RequestMapping(value = "/users")
public class UserController
{
	@Autowired
	private UserService userService;

	@Autowired
	private BuddyService buddyService;

	@Autowired
	private BruteForceAttemptService bruteForceAttemptService;

	@Autowired
	private DOSProtectionService dosProtectionService;

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private CurieProvider curieProvider;

	@RequestMapping(value = "/{id}", params = { "includePrivateData" }, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<UserResource> getUser(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = "tempPassword", required = false) String tempPasswordStr,
			@RequestParam(value = "includePrivateData", defaultValue = "false") String includePrivateDataStr,
			@PathVariable UUID id)
	{
		Optional<String> tempPassword = Optional.ofNullable(tempPasswordStr);
		Optional<String> passwordToUse = getPasswordToUse(password, tempPassword);
		boolean includePrivateData = Boolean.TRUE.toString().equals(includePrivateDataStr);
		if (includePrivateData)
		{
			return CryptoSession.execute(passwordToUse, () -> userService.canAccessPrivateData(id),
					() -> createOKResponse(userService.getPrivateUser(id), includePrivateData));
		}
		else
		{
			return getPublicUser(passwordToUse, id);
		}
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<UserResource> getPublicUser(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID id)
	{
		return createOKResponse(userService.getPublicUser(id), false);
	}

	@RequestMapping(value = "/", method = RequestMethod.POST)
	@ResponseBody
	@ResponseStatus(HttpStatus.CREATED)
	public HttpEntity<UserResource> addUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = "overwriteUserConfirmationCode", required = false) String overwriteUserConfirmationCode,
			@RequestBody UserDTO user, HttpServletRequest request)
	{
		return dosProtectionService.executeAttempt(getAddUserLinkBuilder().toUri(), request,
				yonaProperties.getSecurity().getMaxCreateUserAttemptsPerTimeWindow(),
				() -> addUser(password, Optional.ofNullable(overwriteUserConfirmationCode), user));
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.PUT)
	@ResponseBody
	public HttpEntity<UserResource> updateUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = "tempPassword", required = false) String tempPasswordStr, @PathVariable UUID id,
			@RequestBody UserDTO userResource)
	{
		Optional<String> tempPassword = Optional.ofNullable(tempPasswordStr);
		if (tempPassword.isPresent())
		{
			return CryptoSession.execute(password, null,
					() -> createOKResponse(userService.updateUserCreatedOnBuddyRequest(id, tempPassword.get(), userResource),
							true));
		}
		else
		{
			return CryptoSession.execute(password, () -> userService.canAccessPrivateData(id),
					() -> createOKResponse(userService.updateUser(id, userResource), true));
		}
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public void deleteUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID id,
			@RequestParam(value = "message", required = false) String messageStr)
	{
		CryptoSession.execute(password, () -> userService.canAccessPrivateData(id), () -> {
			userService.deleteUser(id, Optional.ofNullable(messageStr));
			return null;
		});
	}

	@RequestMapping(value = "/{id}/confirmMobileNumber", method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<UserResource> confirmMobileNumber(
			@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID id,
			@RequestBody MobileNumberConfirmationDTO mobileNumberConfirmation)
	{
		return CryptoSession
				.execute(password, () -> userService.canAccessPrivateData(id),
						() -> bruteForceAttemptService.executeAttempt(getConfirmMobileNumberLinkBuilder(id).toUri(),
								yonaProperties.getSms()
										.getMobileNumberConfirmationMaxAttempts(),
						() -> createOKResponse(userService.confirmMobileNumber(id, mobileNumberConfirmation.getCode()), true)));
	}

	@RequestMapping(value = "/", method = RequestMethod.PUT)
	@ResponseBody
	public HttpEntity<Resource<OverwriteUserDTO>> setOverwriteUserConfirmationCode(@RequestParam String mobileNumber)
	{
		return new ResponseEntity<Resource<OverwriteUserDTO>>(
				new Resource<OverwriteUserDTO>(userService.setOverwriteUserConfirmationCode(mobileNumber)), HttpStatus.OK);
	}

	@RequestMapping(value = "/{userID}/newDeviceRequest", method = RequestMethod.PUT)
	@ResponseBody
	public HttpEntity<NewDeviceRequestResource> setNewDeviceRequestForUser(
			@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@RequestBody NewDeviceRequestCreationDTO newDeviceRequestCreation)
	{
		checkPassword(password, userID);
		NewDeviceRequestDTO newDeviceRequestResult = userService.setNewDeviceRequestForUser(userID, password.get(),
				newDeviceRequestCreation.getUserSecret());
		return createNewDeviceRequestResponse(newDeviceRequestResult, getNewDeviceRequestLinkBuilder(userID),
				newDeviceRequestResult.getIsUpdatingExistingRequest() ? HttpStatus.OK : HttpStatus.CREATED);
	}

	@RequestMapping(value = "/{userID}/newDeviceRequest", params = { "userSecret" }, method = RequestMethod.GET)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public HttpEntity<NewDeviceRequestResource> getNewDeviceRequestForUser(@PathVariable UUID userID,
			@RequestParam(value = "userSecret", required = false) String userSecret)
	{
		return createNewDeviceRequestResponse(userService.getNewDeviceRequestForUser(userID, userSecret),
				getNewDeviceRequestLinkBuilder(userID), HttpStatus.OK);
	}

	@RequestMapping(value = "/{userID}/newDeviceRequest", method = RequestMethod.DELETE)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public void clearNewDeviceRequestForUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userID)
	{
		checkPassword(password, userID);
		userService.clearNewDeviceRequestForUser(userID);
	}

	static ControllerLinkBuilder getAddUserLinkBuilder()
	{
		UserController methodOn = methodOn(UserController.class);
		return linkTo(methodOn.addUser(Optional.empty(), null, null, null));
	}

	private void checkPassword(Optional<String> password, UUID userID)
	{
		CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID), () -> null);
	}

	static ControllerLinkBuilder getConfirmMobileNumberLinkBuilder(UUID userID)
	{
		UserController methodOn = methodOn(UserController.class);
		return linkTo(methodOn.confirmMobileNumber(Optional.empty(), userID, null));
	}

	static ControllerLinkBuilder getNewDeviceRequestLinkBuilder(UUID userID)
	{
		UserController methodOn = methodOn(UserController.class);
		return linkTo(methodOn.getNewDeviceRequestForUser(userID, null));
	}

	private HttpEntity<NewDeviceRequestResource> createNewDeviceRequestResponse(NewDeviceRequestDTO newDeviceRequest,
			ControllerLinkBuilder entityLinkBuilder, HttpStatus statusCode)
	{
		return new ResponseEntity<NewDeviceRequestResource>(new NewDeviceRequestResource(newDeviceRequest, entityLinkBuilder),
				statusCode);
	}

	public static class NewDeviceRequestResource extends Resource<NewDeviceRequestDTO>
	{
		public NewDeviceRequestResource(NewDeviceRequestDTO newDeviceRequest, ControllerLinkBuilder entityLinkBuilder)
		{
			super(newDeviceRequest, entityLinkBuilder.withSelfRel(),
					entityLinkBuilder.withRel(JsonRootRelProvider.EDIT_REL) /* always editable */);
		}
	}

	private HttpEntity<UserResource> addUser(Optional<String> password, Optional<String> overwriteUserConfirmationCode,
			UserDTO user)
	{
		if (overwriteUserConfirmationCode.isPresent())
		{
			return bruteForceAttemptService.executeAttempt(getAddUserLinkBuilder().toUri(),
					yonaProperties.getSms().getMobileNumberConfirmationMaxAttempts(),
					() -> CryptoSession.execute(password,
							() -> createResponse(userService.addUser(user, overwriteUserConfirmationCode), true,
									HttpStatus.CREATED)),
					() -> userService.clearOverwriteUserConfirmationCode(user.getMobileNumber()), user.getMobileNumber());
		}
		else
		{
			return CryptoSession.execute(password,
					() -> createResponse(userService.addUser(user, Optional.empty()), true, HttpStatus.CREATED));
		}
	}

	private Optional<String> getPasswordToUse(Optional<String> password, Optional<String> tempPassword)
	{
		if (password.isPresent())
		{
			return password;
		}
		if (tempPassword.isPresent())
		{
			return tempPassword;
		}
		return Optional.empty();
	}

	private HttpEntity<UserResource> createResponse(UserDTO user, boolean includePrivateData, HttpStatus status)
	{
		if (includePrivateData)
		{
			Set<BuddyDTO> buddies = buddyService.getBuddiesOfUser(user.getID());
			user.getPrivateData().setBuddies(buddies);
		}
		return new ResponseEntity<UserResource>(new UserResourceAssembler(curieProvider, includePrivateData).toResource(user),
				status);
	}

	private HttpEntity<UserResource> createOKResponse(UserDTO user, boolean includePrivateData)
	{
		return createResponse(user, includePrivateData, HttpStatus.OK);
	}

	static Link getUserSelfLinkWithTempPassword(UUID userID, String tempPassword)
	{
		ControllerLinkBuilder linkBuilder = linkTo(
				methodOn(UserController.class).updateUser(Optional.empty(), tempPassword, userID, null));
		return linkBuilder.withSelfRel();
	}

	private static Link getConfirmMobileLink(UUID userID)
	{
		ControllerLinkBuilder linkBuilder = linkTo(
				methodOn(UserController.class).confirmMobileNumber(Optional.empty(), userID, null));
		return linkBuilder.withRel("confirmMobileNumber");
	}

	private static Link getUserSelfLink(UUID userID, boolean includePrivateData)
	{
		ControllerLinkBuilder linkBuilder;
		if (includePrivateData)
		{
			linkBuilder = linkTo(methodOn(UserController.class).getUser(Optional.empty(), null, Boolean.TRUE.toString(), userID));
		}
		else
		{
			linkBuilder = linkTo(methodOn(UserController.class).getPublicUser(Optional.empty(), userID));
		}
		return linkBuilder.withSelfRel();
	}

	static class UserResource extends Resource<UserDTO>
	{
		private final CurieProvider curieProvider;

		public UserResource(CurieProvider curieProvider, UserDTO user)
		{
			super(user);
			this.curieProvider = curieProvider;
		}

		@JsonProperty("_embedded")
		@JsonInclude(Include.NON_EMPTY)
		public Map<String, Object> getEmbeddedResources()
		{
			if ((getContent().getPrivateData() == null) || !getContent().isMobileNumberConfirmed())
			{
				return Collections.emptyMap();
			}

			Set<BuddyDTO> buddies = getContent().getPrivateData().getBuddies();
			HashMap<String, Object> result = new HashMap<String, Object>();
			result.put(curieProvider.getNamespacedRelFor(UserDTO.BUDDIES_REL_NAME),
					BuddyController.createAllBuddiesCollectionResource(curieProvider, getContent().getID(), buddies));

			Set<GoalDTO> goals = getContent().getPrivateData().getGoals();
			result.put(curieProvider.getNamespacedRelFor(UserDTO.GOALS_REL_NAME),
					GoalController.createAllGoalsCollectionResource(getContent().getID(), goals));

			return result;
		}

		static ControllerLinkBuilder getAllBuddiesLinkBuilder(UUID requestingUserID)
		{
			BuddyController methodOn = methodOn(BuddyController.class);
			return linkTo(methodOn.getAllBuddies(null, requestingUserID));
		}
	}

	static class UserResourceAssembler extends ResourceAssemblerSupport<UserDTO, UserResource>
	{
		private final boolean includePrivateData;
		private CurieProvider curieProvider;

		public UserResourceAssembler(CurieProvider curieProvider, boolean includePrivateData)
		{
			super(UserController.class, UserResource.class);
			this.curieProvider = curieProvider;
			this.includePrivateData = includePrivateData;
		}

		@Override
		public UserResource toResource(UserDTO user)
		{
			UserResource userResource = instantiateResource(user);
			addSelfLink(userResource, includePrivateData);
			if (includePrivateData && !user.isMobileNumberConfirmed())
			{
				// The mobile number is not yet confirmed, so we can add the link
				addConfirmMobileNumberLink(userResource, user.getMobileNumberConfirmationCode());
			}
			if (includePrivateData)
			{
				addEditLink(userResource);
				if (user.isMobileNumberConfirmed())
				{
					addMessagesLink(userResource);
					addNewDeviceRequestLink(userResource);
					addAppActivityLink(userResource);
				}
			}
			return userResource;
		}

		@Override
		protected UserResource instantiateResource(UserDTO user)
		{
			return new UserResource(curieProvider, user);
		}

		private static void addSelfLink(Resource<UserDTO> userResource, boolean includePrivateData)
		{
			if (userResource.getContent().getID() == null)
			{
				// removed user
				return;
			}

			userResource.add(UserController.getUserSelfLink(userResource.getContent().getID(), includePrivateData));
		}

		private static void addEditLink(Resource<UserDTO> userResource)
		{
			userResource.add(linkTo(
					methodOn(UserController.class).updateUser(Optional.empty(), null, userResource.getContent().getID(), null))
							.withRel(JsonRootRelProvider.EDIT_REL));
		}

		private static void addConfirmMobileNumberLink(Resource<UserDTO> userResource, String confirmationCode)
		{
			userResource.add(UserController.getConfirmMobileLink(userResource.getContent().getID()));
		}

		private void addMessagesLink(UserResource userResource)
		{
			userResource.add(MessageController.getConfirmMobileLink(userResource.getContent().getID()));
		}

		private void addNewDeviceRequestLink(UserResource userResource)
		{
			userResource.add(
					UserController.getNewDeviceRequestLinkBuilder(userResource.getContent().getID()).withRel("newDeviceRequest"));
		}

		private void addAppActivityLink(UserResource userResource)
		{
			userResource.add(AppActivityController.getAppActivityLink(userResource.getContent().getID()));
		}

	}
}
