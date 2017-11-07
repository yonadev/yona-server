/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.hal.CurieProvider;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.analysis.rest.BuddyActivityController;
import nu.yona.server.analysis.rest.UserActivityController;
import nu.yona.server.analysis.service.ActivityCommentMessageDto;
import nu.yona.server.analysis.service.DayActivityDto;
import nu.yona.server.analysis.service.GoalConflictMessageDto;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.rest.ActivityCategoryController;
import nu.yona.server.goals.service.GoalChangeMessageDto;
import nu.yona.server.messaging.service.BuddyMessageEmbeddedUserDto;
import nu.yona.server.messaging.service.BuddyMessageLinkedUserDto;
import nu.yona.server.messaging.service.DisclosureRequestMessageDto;
import nu.yona.server.messaging.service.DisclosureResponseMessageDto;
import nu.yona.server.messaging.service.MessageActionDto;
import nu.yona.server.messaging.service.MessageDto;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.subscriptions.rest.BuddyController;
import nu.yona.server.subscriptions.rest.UserController;
import nu.yona.server.subscriptions.service.BuddyConnectResponseMessageDto;
import nu.yona.server.subscriptions.service.BuddyDto;
import nu.yona.server.subscriptions.service.BuddyInfoChangeMessageDto;
import nu.yona.server.subscriptions.service.GoalIdMapping;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@ExposesResourceFor(MessageDto.class)
@RequestMapping(value = "/users/{userId}/messages", produces = { MediaType.APPLICATION_JSON_VALUE })
public class MessageController extends ControllerBase
{
	@Autowired
	private MessageService messageService;

	@Autowired
	private UserService userService;

	@Autowired
	private CurieProvider curieProvider;

	@Autowired
	private UserActivityController userActivityController;

	@Autowired
	private BuddyActivityController buddyActivityController;

	@RequestMapping(value = "/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<MessageDto>> getMessages(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = "onlyUnreadMessages", required = false, defaultValue = "false") String onlyUnreadMessagesStr,
			@PathVariable UUID userId, Pageable pageable, PagedResourcesAssembler<MessageDto> pagedResourcesAssembler)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			boolean onlyUnreadMessages = Boolean.TRUE.toString().equals(onlyUnreadMessagesStr);

			return getMessages(userId, pageable, pagedResourcesAssembler, onlyUnreadMessages);
		}
	}

	private HttpEntity<PagedResources<MessageDto>> getMessages(UUID userId, Pageable pageable,
			PagedResourcesAssembler<MessageDto> pagedResourcesAssembler, boolean onlyUnreadMessages)
	{
		UserDto user = messageService.prepareMessageCollection(userService.getPrivateValidatedUser(userId));
		Page<MessageDto> messages = messageService.getReceivedMessages(user, onlyUnreadMessages, pageable);
		return createOkResponse(pagedResourcesAssembler, user, messages);
	}

	@RequestMapping(value = "/{messageId}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<MessageDto> getMessage(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable long messageId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			UserDto user = userService.getPrivateValidatedUser(userId);
			return createOkResponse(createGoalIdMapping(user), messageService.getMessage(user, messageId));
		}
	}

	private GoalIdMapping createGoalIdMapping(UserDto user)
	{
		return GoalIdMapping.createInstance(user);
	}

	public HttpEntity<MessageDto> createOkResponse(GoalIdMapping goalIdMapping, MessageDto message)
	{
		return createOkResponse(message, createResourceAssembler(goalIdMapping));
	}

	public HttpEntity<PagedResources<MessageDto>> createOkResponse(PagedResourcesAssembler<MessageDto> pagedResourcesAssembler,
			UserDto user, Page<MessageDto> messages)
	{
		return createOkResponse(messages, pagedResourcesAssembler, createResourceAssembler(createGoalIdMapping(user)));
	}

	@RequestMapping(value = "/{id}/{action}", method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<MessageActionResource> handleAnonymousMessageAction(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId, @PathVariable long id,
			@PathVariable String action, @RequestBody MessageActionDto requestPayload)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			UserDto user = userService.getPrivateValidatedUser(userId);

			return createOkResponse(new MessageActionResource(curieProvider,
					messageService.handleMessageAction(user, id, action, requestPayload), createGoalIdMapping(user), this));
		}
	}

	@RequestMapping(value = "/{messageId}", method = RequestMethod.DELETE)
	@ResponseBody
	public HttpEntity<MessageActionResource> deleteAnonymousMessage(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable long messageId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			UserDto user = userService.getPrivateValidatedUser(userId);
			return createOkResponse(new MessageActionResource(curieProvider, messageService.deleteMessage(user, messageId),
					createGoalIdMapping(user), this));
		}
	}

	private HttpEntity<MessageActionResource> createOkResponse(MessageActionResource messageAction)
	{
		return new ResponseEntity<>(messageAction, HttpStatus.OK);
	}

	private MessageResourceAssembler createResourceAssembler(GoalIdMapping goalIdMapping)
	{
		return new MessageResourceAssembler(curieProvider, goalIdMapping, this);
	}

	public static ControllerLinkBuilder getAnonymousMessageLinkBuilder(UUID userId, long messageId)
	{
		MessageController methodOn = methodOn(MessageController.class);
		return linkTo(methodOn.getMessage(Optional.empty(), userId, messageId));
	}

	public static Link getMessagesLink(UUID userId)
	{
		ControllerLinkBuilder linkBuilder = linkTo(
				methodOn(MessageController.class).getMessages(Optional.empty(), null, userId, null, null));
		return linkBuilder.withRel("messages").expand();
	}

	private UserActivityController getUserActivityController()
	{
		return userActivityController;
	}

	private BuddyActivityController getBuddyActivityController()
	{
		return buddyActivityController;
	}

	static class MessageActionResource extends Resource<MessageActionDto>
	{
		private final GoalIdMapping goalIdMapping;
		private final CurieProvider curieProvider;
		private final MessageController messageController;

		public MessageActionResource(CurieProvider curieProvider, MessageActionDto messageAction, GoalIdMapping goalIdMapping,
				MessageController messageController)
		{
			super(messageAction);
			this.curieProvider = curieProvider;
			this.goalIdMapping = goalIdMapping;
			this.messageController = messageController;
		}

		@JsonProperty("_embedded")
		public Map<String, List<MessageDto>> getEmbeddedResources()
		{
			Set<MessageDto> affectedMessages = getContent().getAffectedMessages();
			return Collections.singletonMap(curieProvider.getNamespacedRelFor("affectedMessages"),
					new MessageResourceAssembler(curieProvider, goalIdMapping, messageController).toResources(affectedMessages));
		}
	}

	private static class MessageResourceAssembler extends ResourceAssemblerSupport<MessageDto, MessageDto>
	{
		private final GoalIdMapping goalIdMapping;
		private final CurieProvider curieProvider;
		private final MessageController messageController;

		public MessageResourceAssembler(CurieProvider curieProvider, GoalIdMapping goalIdMapping,
				MessageController messageController)
		{
			super(MessageController.class, MessageDto.class);
			this.curieProvider = curieProvider;
			this.goalIdMapping = goalIdMapping;
			this.messageController = messageController;
		}

		@Override
		public MessageDto toResource(MessageDto message)
		{
			message.removeLinks(); // So we are sure the below links are the only ones
			ControllerLinkBuilder selfLinkBuilder = getAnonymousMessageLinkBuilder(goalIdMapping.getUserId(),
					message.getMessageId());
			addSelfLink(selfLinkBuilder, message);
			addActionLinks(selfLinkBuilder, message);
			addRelatedMessageLink(message, message);
			if (message.canBeDeleted())
			{
				addEditLink(selfLinkBuilder, message);
			}
			doDynamicDecoration(message);
			return message;
		}

		private void addRelatedMessageLink(MessageDto message, MessageDto messageResource)
		{
			message.getRelatedMessageId().ifPresent(rid -> messageResource
					.add(getAnonymousMessageLinkBuilder(goalIdMapping.getUserId(), rid).withRel("related")));
		}

		@Override
		protected MessageDto instantiateResource(MessageDto message)
		{
			return message;
		}

		private void addSelfLink(ControllerLinkBuilder selfLinkBuilder, MessageDto messageResource)
		{
			messageResource.add(selfLinkBuilder.withSelfRel());
		}

		private void addEditLink(ControllerLinkBuilder selfLinkBuilder, MessageDto messageResource)
		{
			messageResource.add(selfLinkBuilder.withRel(JsonRootRelProvider.EDIT_REL));
		}

		private void addActionLinks(ControllerLinkBuilder selfLinkBuilder, MessageDto messageResource)
		{
			messageResource.getPossibleActions().stream().forEach(a -> messageResource.add(selfLinkBuilder.slash(a).withRel(a)));
		}

		protected void doDynamicDecoration(MessageDto message)
		{
			if (message instanceof BuddyMessageEmbeddedUserDto)
			{
				embedBuddyUserIfAvailable((BuddyMessageEmbeddedUserDto) message);
			}
			if (message instanceof BuddyMessageLinkedUserDto)
			{
				addUserLinkIfAvailable((BuddyMessageLinkedUserDto) message);
			}
			if (message instanceof BuddyConnectResponseMessageDto || message instanceof GoalConflictMessageDto
					|| message instanceof BuddyInfoChangeMessageDto || message instanceof GoalChangeMessageDto)
			{
				addSenderBuddyLinkIfAvailable(message);
			}
			if (message instanceof GoalConflictMessageDto)
			{
				addGoalConflictMessageLinks((GoalConflictMessageDto) message);
			}
			if (message instanceof DisclosureRequestMessageDto)
			{
				addDayActivityDetailLink((DisclosureRequestMessageDto) message);
			}
			if (message instanceof DisclosureResponseMessageDto)
			{
				addDayActivityDetailLinkIfDisclosureAccepted((DisclosureResponseMessageDto) message);
			}
			if (message instanceof GoalChangeMessageDto)
			{
				addRelatedActivityCategoryLink((GoalChangeMessageDto) message);
			}
			if (message instanceof ActivityCommentMessageDto)
			{
				addActivityCommentMessageLinks((ActivityCommentMessageDto) message);
			}
		}

		private void addDayActivityDetailLinkIfDisclosureAccepted(DisclosureResponseMessageDto message)
		{
			if (message.getStatus() == GoalConflictMessage.Status.DISCLOSURE_ACCEPTED)
			{
				addDayActivityDetailLink(message);
			}
		}

		private void addSenderBuddyLinkIfAvailable(MessageDto message)
		{
			if (message.getSenderBuddyId().isPresent())
			{
				message.add(BuddyController.getBuddyLinkBuilder(goalIdMapping.getUserId(), getSenderBuddyId(message))
						.withRel(BuddyController.BUDDY_LINK));
			}
		}

		private void addRelatedActivityCategoryLink(GoalChangeMessageDto message)
		{
			message.add(ActivityCategoryController.getActivityCategoryLinkBuilder(message.getActivityCategoryIdOfChangedGoal())
					.withRel("related"));
		}

		private void addGoalConflictMessageLinks(GoalConflictMessageDto message)
		{
			addActivityCategoryLink(message);
			addDayActivityDetailLink(message);
		}

		private void addActivityCategoryLink(GoalConflictMessageDto message)
		{
			message.add(ActivityCategoryController.getActivityCategoryLinkBuilder(message.getActivityCategoryId())
					.withRel("activityCategory"));
		}

		private void addDayActivityDetailLink(DisclosureRequestMessageDto message)
		{
			String dateStr = DayActivityDto.formatDate(message.getGoalConflictStartTime());
			message.add(UserActivityController
					.getUserDayActivityDetailLinkBuilder(goalIdMapping.getUserId(), dateStr, message.getGoalId())
					.withRel(UserActivityController.DAY_DETAIL_LINK));
		}

		private void addDayActivityDetailLink(DisclosureResponseMessageDto message)
		{
			String dateStr = DayActivityDto.formatDate(message.getGoalConflictStartTime());
			message.add(BuddyActivityController.getBuddyDayActivityDetailLinkBuilder(goalIdMapping.getUserId(),
					getSenderBuddyId(message), dateStr, message.getGoalId()).withRel(BuddyActivityController.DAY_DETAIL_LINK));
		}

		private void addDayActivityDetailLink(GoalConflictMessageDto message)
		{
			String dateStr = DayActivityDto.formatDate(message.getActivityStartTime().toLocalDate());
			if (message.isSentFromBuddy())
			{
				message.add(BuddyActivityController.getBuddyDayActivityDetailLinkBuilder(goalIdMapping.getUserId(),
						getSenderBuddyId(message), dateStr, message.getGoalId())
						.withRel(BuddyActivityController.DAY_DETAIL_LINK));
			}
			else
			{
				message.add(UserActivityController
						.getUserDayActivityDetailLinkBuilder(goalIdMapping.getUserId(), dateStr, message.getGoalId())
						.withRel(UserActivityController.DAY_DETAIL_LINK));
			}
		}

		private UUID getSenderBuddyId(MessageDto message)
		{
			return message.getSenderBuddyId()
					.orElseThrow(() -> new IllegalStateException("Sender buddy ID must be available in this context"));
		}

		private void embedBuddyUserIfAvailable(BuddyMessageEmbeddedUserDto buddyMessage)
		{
			buddyMessage.getSenderUser()
					.ifPresent(user -> buddyMessage.setEmbeddedUser(curieProvider.getNamespacedRelFor(BuddyDto.USER_REL_NAME),
							new UserController.UserResourceAssembler(curieProvider, false).toResource(user)));
		}

		private void addUserLinkIfAvailable(BuddyMessageLinkedUserDto buddyMessage)
		{
			buddyMessage.getSenderUser()
					.ifPresent(user -> buddyMessage.add(UserController.getPublicUserLink(BuddyDto.USER_REL_NAME, user.getId())));
		}

		private void addActivityCommentMessageLinks(ActivityCommentMessageDto message)
		{
			IntervalActivity activity = IntervalActivity.getIntervalActivityRepository().findOne(message.getIntervalActivityId());
			Objects.requireNonNull(activity,
					String.format("Activity linked from activity comment message not found from sender '%s' and activity id '%s'",
							message.getSenderNickname(), message.getIntervalActivityId()));
			Goal goal = Objects.requireNonNull(activity.getGoal(),
					String.format("Activity getGoal() returns null for '%s' instance with id '%s' and start time '%s'",
							activity.getClass().getSimpleName(), activity.getId(), activity.getStartDate()));
			if (goalIdMapping.isUserGoal(goal.getId()))
			{
				messageController.getUserActivityController().addLinks(goalIdMapping, activity, message);
			}
			else
			{
				messageController.getBuddyActivityController().addLinks(goalIdMapping, activity, message);
			}
		}
	}
}
