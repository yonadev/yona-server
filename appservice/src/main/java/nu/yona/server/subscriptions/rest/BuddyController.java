/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.hal.CurieProvider;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.analysis.rest.BuddyActivityController;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.goals.rest.GoalController;
import nu.yona.server.goals.rest.GoalController.GoalResourceAssembler;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.rest.BuddyController.BuddyResource;
import nu.yona.server.subscriptions.service.BuddyDto;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.BuddyServiceException;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.util.TimeUtil;

@Controller
@ExposesResourceFor(BuddyResource.class)
@RequestMapping(value = "/users/{userId}/buddies", produces = { MediaType.APPLICATION_JSON_VALUE })
public class BuddyController extends ControllerBase
{
	public static final String BUDDY_LINK = "buddy";

	@Autowired
	private BuddyService buddyService;

	@Autowired
	private UserService userService;

	@Autowired
	private CurieProvider curieProvider;

	/**
	 * This method returns all the buddies that the given user has.
	 * 
	 * @param password The Yona password as passed on in the header of the request.
	 * @param userId The ID of the user. This is part of the URL.
	 * @return the list of buddies for the current user
	 */
	@GetMapping(value = "/")
	@ResponseBody
	public HttpEntity<Resources<BuddyResource>> getAllBuddies(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			return new ResponseEntity<>(
					createAllBuddiesCollectionResource(curieProvider, userId, buddyService.getBuddiesOfUser(userId)),
					HttpStatus.OK);
		}
	}

	@GetMapping(value = "/{buddyId}")
	@ResponseBody
	public HttpEntity<BuddyResource> getBuddy(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable UUID buddyId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{

			return createOkResponse(buddyService.getBuddy(buddyId), createResourceAssembler(userId));
		}
	}

	@PostMapping(value = "/")
	@ResponseBody
	public HttpEntity<BuddyResource> addBuddy(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @RequestBody PostBuddyDto postBuddy)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			return createResponse(buddyService.addBuddyToRequestingUser(userId, convertToBuddy(postBuddy), this::getInviteUrl),
					HttpStatus.CREATED, createResourceAssembler(userId));
		}
	}

	/**
	 * This operation is for test purposes only. It only allows updating the last status change time of the buddy entity.
	 */
	@PutMapping(value = "/{buddyId}")
	@ResponseBody
	public HttpEntity<BuddyResource> updateBuddy(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable UUID buddyId,
			@RequestBody LastStatusChangeTimeUpdateDto lastStatusChangeTimeUpdateDto)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			return createResponse(
					buddyService.updateLastStatusChangeTime(userId, buddyId, lastStatusChangeTimeUpdateDto.lastStatusChangeTime),
					HttpStatus.OK, createResourceAssembler(userId));
		}
	}

	@DeleteMapping(value = "/{buddyId}")
	@ResponseBody
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeBuddy(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable UUID buddyId, @RequestParam(value = "message", required = false) String messageStr)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			buddyService.removeBuddy(userId, buddyId, Optional.ofNullable(messageStr));
		}
	}

	private BuddyDto convertToBuddy(PostBuddyDto postBuddy)
	{
		String userRelName = curieProvider.getNamespacedRelFor(BuddyDto.USER_REL_NAME);
		UserDto user = postBuddy.userInMap.get(userRelName);
		if (user == null)
		{
			throw BuddyServiceException.missingUser(userRelName);
		}
		return new BuddyDto(user, postBuddy.message, postBuddy.sendingStatus, postBuddy.receivingStatus, TimeUtil.utcNow());
	}

	public static Resources<BuddyResource> createAllBuddiesCollectionResource(CurieProvider curieProvider, UUID userId,
			Set<BuddyDto> allBuddiesOfUser)
	{
		return new Resources<>(new BuddyResourceAssembler(curieProvider, userId).toResources(allBuddiesOfUser),
				getAllBuddiesLinkBuilder(userId).withSelfRel());
	}

	static ControllerLinkBuilder getAllBuddiesLinkBuilder(UUID userId)
	{
		BuddyController methodOn = methodOn(BuddyController.class);
		return linkTo(methodOn.getAllBuddies(null, userId));
	}

	public String getInviteUrl(UUID newUserId, String tempPassword)
	{
		// getUserSelfLinkWithTempPassword should actually call expand, so we don't need to strip template parameters
		// This is not being done because of https://github.com/spring-projects/spring-hateoas/issues/703
		return stripTemplateParameters(UserController.getUserSelfLinkWithTempPassword(newUserId, tempPassword));
	}

	private String stripTemplateParameters(Link link)
	{
		String linkString = link.getHref();
		if (link.isTemplated())
		{
			return linkString.substring(0, linkString.indexOf('{'));
		}
		return linkString;
	}

	private BuddyResourceAssembler createResourceAssembler(UUID userId)
	{
		return new BuddyResourceAssembler(curieProvider, userId);
	}

	public static Resources<GoalDto> createAllGoalsCollectionResource(UUID requestingUserId, UUID userId,
			Set<GoalDto> allGoalsOfUser)
	{
		return new Resources<>(new GoalResourceAssembler(true, goalId -> getGoalLinkBuilder(requestingUserId, userId, goalId))
				.toResources(allGoalsOfUser), getAllGoalsLinkBuilder(requestingUserId, userId).withSelfRel());
	}

	public static ControllerLinkBuilder getBuddyLinkBuilder(UUID userId, UUID buddyId)
	{
		BuddyController methodOn = methodOn(BuddyController.class);
		return linkTo(methodOn.getBuddy(Optional.empty(), userId, buddyId));
	}

	public static ControllerLinkBuilder getGoalLinkBuilder(UUID requestingUserId, UUID userId, UUID goalId)
	{
		return GoalController.getGoalLinkBuilder(requestingUserId, userId, goalId);
	}

	private static ControllerLinkBuilder getAllGoalsLinkBuilder(UUID requestingUserId, UUID userId)
	{
		return GoalController.getAllGoalsLinkBuilder(requestingUserId, userId);
	}

	static class LastStatusChangeTimeUpdateDto
	{
		private final LocalDateTime lastStatusChangeTime;

		@JsonCreator
		public LastStatusChangeTimeUpdateDto(
				@JsonFormat(pattern = nu.yona.server.Constants.ISO_DATE_TIME_PATTERN) @JsonProperty("lastStatusChangeTime") ZonedDateTime lastStatusChangeTime)
		{
			this.lastStatusChangeTime = TimeUtil.toUtcLocalDateTime(lastStatusChangeTime);
		}
	}

	static class PostBuddyDto
	{
		private final Map<String, UserDto> userInMap;
		private final String message;
		private final Status sendingStatus;
		private final Status receivingStatus;

		@JsonCreator
		public PostBuddyDto(@JsonProperty(value = "_embedded", required = true) Map<String, UserDto> userInMap,
				@JsonProperty("message") String message,
				@JsonProperty(value = "sendingStatus", required = true) Status sendingStatus,
				@JsonProperty(value = "receivingStatus", required = true) Status receivingStatus)
		{
			this.userInMap = userInMap;
			this.message = message;
			this.sendingStatus = sendingStatus;
			this.receivingStatus = receivingStatus;
		}
	}

	static class BuddyResource extends Resource<BuddyDto>
	{
		private final CurieProvider curieProvider;
		private final UUID userId;

		public BuddyResource(CurieProvider curieProvider, UUID userId, BuddyDto buddy)
		{
			super(buddy);
			this.curieProvider = curieProvider;
			this.userId = userId;
		}

		@JsonProperty("_embedded")
		public Map<String, Object> getEmbeddedResources()
		{
			HashMap<String, Object> result = new HashMap<>();
			result.put(curieProvider.getNamespacedRelFor(BuddyDto.USER_REL_NAME), UserController.UserResourceAssembler
					.createInstanceForBuddy(curieProvider, userId).toResource(getContent().getUser()));

			return result;
		}
	}

	static class BuddyResourceAssembler extends ResourceAssemblerSupport<BuddyDto, BuddyResource>
	{
		private final UUID userId;
		private final CurieProvider curieProvider;

		public BuddyResourceAssembler(CurieProvider curieProvider, UUID userId)
		{
			super(BuddyController.class, BuddyResource.class);
			this.curieProvider = curieProvider;
			this.userId = userId;
		}

		@Override
		public BuddyResource toResource(BuddyDto buddy)
		{
			BuddyResource buddyResource = instantiateResource(buddy);
			ControllerLinkBuilder selfLinkBuilder = getSelfLinkBuilder(buddy.getId());
			addSelfLink(selfLinkBuilder, buddyResource);
			addEditLink(selfLinkBuilder, buddyResource);
			if (buddy.getSendingStatus() == Status.ACCEPTED)
			{
				addDayActivityOverviewsLink(buddyResource);
				addWeekActivityOverviewsLink(buddyResource);
			}
			return buddyResource;
		}

		@Override
		protected BuddyResource instantiateResource(BuddyDto buddy)
		{
			return new BuddyResource(curieProvider, userId, buddy);
		}

		private ControllerLinkBuilder getSelfLinkBuilder(UUID buddyId)
		{
			return getBuddyLinkBuilder(userId, buddyId);
		}

		private void addSelfLink(ControllerLinkBuilder selfLinkBuilder, BuddyResource buddyResource)
		{
			buddyResource.add(selfLinkBuilder.withSelfRel());
		}

		private void addEditLink(ControllerLinkBuilder selfLinkBuilder, BuddyResource buddyResource)
		{
			buddyResource.add(selfLinkBuilder.withRel(JsonRootRelProvider.EDIT_REL));
		}

		private void addWeekActivityOverviewsLink(BuddyResource buddyResource)
		{
			buddyResource.add(
					BuddyActivityController.getBuddyWeekActivityOverviewsLinkBuilder(userId, buddyResource.getContent().getId())
							.withRel(BuddyActivityController.WEEK_OVERVIEW_LINK));
		}

		private void addDayActivityOverviewsLink(BuddyResource buddyResource)
		{
			buddyResource.add(
					BuddyActivityController.getBuddyDayActivityOverviewsLinkBuilder(userId, buddyResource.getContent().getId())
							.withRel(BuddyActivityController.DAY_OVERVIEW_LINK));
		}
	}
}
