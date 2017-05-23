/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
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

import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.analysis.service.ActivityCommentMessageDto;
import nu.yona.server.analysis.service.DayActivityDto;
import nu.yona.server.analysis.service.DayActivityOverviewDto;
import nu.yona.server.analysis.service.PostPutActivityCommentMessageDto;
import nu.yona.server.analysis.service.WeekActivityDto;
import nu.yona.server.analysis.service.WeekActivityOverviewDto;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.messaging.rest.MessageController;
import nu.yona.server.messaging.service.MessageDto;
import nu.yona.server.subscriptions.rest.BuddyController;
import nu.yona.server.subscriptions.service.GoalIdMapping;

/*
 * Controller to retrieve activity data for a user.
 */
@Controller
@RequestMapping(value = "/users/{userId}/buddies/{buddyId}/activity", produces = { MediaType.APPLICATION_JSON_VALUE })
public class BuddyActivityController extends ActivityControllerBase
{
	@Autowired
	private MessageController messageController;

	@RequestMapping(value = WEEK_ACTIVITY_OVERVIEWS_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<WeekActivityOverviewResource>> getBuddyWeekActivityOverviews(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable UUID buddyId, @PageableDefault(size = WEEKS_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<WeekActivityOverviewDto> pagedResourcesAssembler)
	{
		return getWeekActivityOverviews(password, userId, pagedResourcesAssembler, () -> activityService.getBuddyWeekActivityOverviews(buddyId, pageable),
				new BuddyActivityLinkProvider(userId, buddyId));
	}

	@RequestMapping(value = DAY_OVERVIEWS_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<DayActivityOverviewResource>> getBuddyDayActivityOverviews(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable UUID buddyId, @PageableDefault(size = DAYS_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<DayActivityOverviewDto<DayActivityDto>> pagedResourcesAssembler)
	{
		return getDayActivityOverviews(password, userId, pagedResourcesAssembler, () -> activityService.getBuddyDayActivityOverviews(buddyId, pageable),
				new BuddyActivityLinkProvider(userId, buddyId));
	}

	@RequestMapping(value = WEEK_ACTIVITY_DETAIL_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<WeekActivityResource> getBuddyWeekActivityDetail(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable UUID buddyId, @PathVariable(value = DATE_PATH_VARIABLE) String dateStr,
			@PathVariable(value = GOAL_PATH_VARIABLE) UUID goalId)
	{
		return getWeekActivityDetail(password, userId, dateStr,
				date -> activityService.getBuddyWeekActivityDetail(buddyId, date, goalId),
				new BuddyActivityLinkProvider(userId, buddyId));
	}

	@RequestMapping(value = WEEK_ACTIVITY_DETAIL_MESSAGES_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<MessageDto>> getBuddyWeekActivityDetailMessages(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable UUID buddyId, @PathVariable(value = DATE_PATH_VARIABLE) String dateStr,
			@PathVariable(value = GOAL_PATH_VARIABLE) UUID goalId,
			@PageableDefault(size = MESSAGES_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<MessageDto> pagedResourcesAssembler)
	{
		return getActivityDetailMessages(password,
				userId, pagedResourcesAssembler, () -> activityService.getBuddyWeekActivityDetailMessages(userId,
						buddyId, WeekActivityDto.parseDate(dateStr), goalId, pageable), new BuddyActivityLinkProvider(userId, buddyId));
	}

	@RequestMapping(value = WEEK_ACTIVITY_DETAIL_MESSAGES_URI_FRAGMENT, method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<MessageDto> addBuddyWeekActivityDetailMessage(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable UUID buddyId, @PathVariable(value = DATE_PATH_VARIABLE) String dateStr,
			@PathVariable(value = GOAL_PATH_VARIABLE) UUID goalId, @RequestBody PostPutActivityCommentMessageDto newMessage)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			return new ResponseEntity<>(
					messageController.toMessageResource(createGoalIdMapping(userId), activityService
							.addMessageToWeekActivity(userId, buddyId, WeekActivityDto.parseDate(dateStr), goalId, newMessage)),
					HttpStatus.OK);
		}
	}

	@RequestMapping(value = DAY_ACTIVITY_DETAIL_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<DayActivityResource> getBuddyDayActivityDetail(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable UUID buddyId, @PathVariable(value = DATE_PATH_VARIABLE) String dateStr,
			@PathVariable(value = GOAL_PATH_VARIABLE) UUID goalId)
	{
		return getDayActivityDetail(password, userId, dateStr,
				date -> activityService.getBuddyDayActivityDetail(buddyId, date, goalId),
				new BuddyActivityLinkProvider(userId, buddyId));
	}

	@RequestMapping(value = DAY_ACTIVITY_DETAIL_MESSAGES_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<MessageDto>> getBuddyDayActivityDetailMessages(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable UUID buddyId, @PathVariable(value = DATE_PATH_VARIABLE) String dateStr,
			@PathVariable(value = GOAL_PATH_VARIABLE) UUID goalId,
			@PageableDefault(size = MESSAGES_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<MessageDto> pagedResourcesAssembler)
	{
		return getActivityDetailMessages(password,
				userId, pagedResourcesAssembler, () -> activityService.getBuddyDayActivityDetailMessages(userId,
						buddyId, DayActivityDto.parseDate(dateStr), goalId, pageable), new BuddyActivityLinkProvider(userId, buddyId));
	}

	@RequestMapping(value = DAY_ACTIVITY_DETAIL_MESSAGES_URI_FRAGMENT, method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<MessageDto> addBuddyDayActivityDetailMessage(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable UUID buddyId, @PathVariable(value = DATE_PATH_VARIABLE) String dateStr,
			@PathVariable(value = GOAL_PATH_VARIABLE) UUID goalId, @RequestBody PostPutActivityCommentMessageDto newMessage)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			return new ResponseEntity<>(
					messageController.toMessageResource(createGoalIdMapping(userId), activityService
							.addMessageToDayActivity(userId, buddyId, DayActivityDto.parseDate(dateStr), goalId, newMessage)),
					HttpStatus.OK);
		}
	}

	@Override
	public void addLinks(GoalIdMapping goalIdMapping, IntervalActivity activity, ActivityCommentMessageDto message)
	{
		LinkProvider linkProvider = new BuddyActivityLinkProvider(goalIdMapping.getUserId(),
				goalIdMapping.getBuddyId(activity.getGoal().getId()));
		addStandardLinks(goalIdMapping, linkProvider, activity, message);
	}

	public static ControllerLinkBuilder getBuddyDayActivityOverviewsLinkBuilder(UUID userId, UUID buddyId)
	{
		BuddyActivityController methodOn = methodOn(BuddyActivityController.class);
		return linkTo(methodOn.getBuddyDayActivityOverviews(null, userId, buddyId, null, null));
	}

	public static ControllerLinkBuilder getBuddyWeekActivityOverviewsLinkBuilder(UUID userId, UUID buddyId)
	{
		BuddyActivityController methodOn = methodOn(BuddyActivityController.class);
		return linkTo(methodOn.getBuddyWeekActivityOverviews(null, userId, buddyId, null, null));
	}

	public static ControllerLinkBuilder getBuddyDayActivityDetailLinkBuilder(UUID userId, UUID buddyId, String dateStr,
			UUID goalId)
	{
		BuddyActivityController methodOn = methodOn(BuddyActivityController.class);
		return linkTo(methodOn.getBuddyDayActivityDetail(null, userId, buddyId, dateStr, goalId));
	}

	private static final class BuddyActivityLinkProvider implements LinkProvider
	{
		private final UUID userId;
		private final UUID buddyId;

		public BuddyActivityLinkProvider(UUID userId, UUID buddyId)
		{
			this.userId = userId;
			this.buddyId = buddyId;
		}

		@Override
		public ControllerLinkBuilder getDayActivityDetailLinkBuilder(String dateStr, UUID goalId)
		{
			return getBuddyDayActivityDetailLinkBuilder(userId, buddyId, dateStr, goalId);
		}

		@Override
		public ControllerLinkBuilder getWeekActivityDetailLinkBuilder(String dateStr, UUID goalId)
		{
			BuddyActivityController methodOn = methodOn(BuddyActivityController.class);
			return linkTo(methodOn.getBuddyWeekActivityDetail(null, userId, buddyId, dateStr, goalId));
		}

		@Override
		public ControllerLinkBuilder getGoalLinkBuilder(UUID goalId)
		{
			return BuddyController.getGoalLinkBuilder(userId, buddyId, goalId);
		}

		@Override
		public ControllerLinkBuilder getDayActivityDetailMessagesLinkBuilder(String dateStr, UUID goalId)
		{
			BuddyActivityController methodOn = methodOn(BuddyActivityController.class);
			return linkTo(
					methodOn.getBuddyDayActivityDetailMessages(Optional.empty(), userId, buddyId, dateStr, goalId, null, null));
		}

		@Override
		public Optional<ControllerLinkBuilder> getDayActivityDetailAddCommentLinkBuilder(String dateStr, UUID goalId)
		{
			BuddyActivityController methodOn = methodOn(BuddyActivityController.class);
			return Optional.of(
					linkTo(methodOn.addBuddyDayActivityDetailMessage(Optional.empty(), userId, buddyId, dateStr, goalId, null)));
		}

		@Override
		public ControllerLinkBuilder getWeekActivityDetailMessagesLinkBuilder(String dateStr, UUID goalId)
		{
			BuddyActivityController methodOn = methodOn(BuddyActivityController.class);
			return linkTo(
					methodOn.getBuddyWeekActivityDetailMessages(Optional.empty(), userId, buddyId, dateStr, goalId, null, null));
		}

		@Override
		public Optional<ControllerLinkBuilder> getWeekActivityDetailAddCommentLinkBuilder(String dateStr, UUID goalId)
		{
			BuddyActivityController methodOn = methodOn(BuddyActivityController.class);
			return Optional.of(
					linkTo(methodOn.addBuddyWeekActivityDetailMessage(Optional.empty(), userId, buddyId, dateStr, goalId, null)));
		}
	}
}
