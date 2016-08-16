/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
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

import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.analysis.rest.BuddyActivityController;
import nu.yona.server.analysis.rest.UserActivityController;
import nu.yona.server.analysis.service.ActivityCommentMessageDTO;
import nu.yona.server.analysis.service.DayActivityDTO;
import nu.yona.server.analysis.service.GoalConflictMessageDTO;
import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.goals.rest.ActivityCategoryController;
import nu.yona.server.goals.service.GoalChangeMessageDTO;
import nu.yona.server.messaging.service.BuddyMessageEmbeddedUserDTO;
import nu.yona.server.messaging.service.BuddyMessageLinkedUserDTO;
import nu.yona.server.messaging.service.DisclosureRequestMessageDTO;
import nu.yona.server.messaging.service.DisclosureResponseMessageDTO;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.subscriptions.rest.BuddyController;
import nu.yona.server.subscriptions.rest.UserController;
import nu.yona.server.subscriptions.service.BuddyConnectResponseMessageDTO;
import nu.yona.server.subscriptions.service.BuddyDTO;
import nu.yona.server.subscriptions.service.GoalIDMapping;
import nu.yona.server.subscriptions.service.UserDTO;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@ExposesResourceFor(MessageDTO.class)
@RequestMapping(value = "/users/{userID}/messages", produces = { MediaType.APPLICATION_JSON_VALUE })
public class MessageController
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
	public HttpEntity<PagedResources<MessageDTO>> getMessages(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = "onlyUnreadMessages", required = false, defaultValue = "false") String onlyUnreadMessagesStr,
			@PathVariable UUID userID, Pageable pageable, PagedResourcesAssembler<MessageDTO> pagedResourcesAssembler)
	{
		boolean onlyUnreadMessages = Boolean.TRUE.toString().equals(onlyUnreadMessagesStr);
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> createOKResponse(pagedResourcesAssembler.toResource(
						messageService.getReceivedMessages(userID, onlyUnreadMessages, pageable),
						new MessageResourceAssembler(curieProvider, createGoalIDMapping(userID), this))));
	}

	@RequestMapping(value = "/{messageID}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<MessageDTO> getMessage(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userID, @PathVariable UUID messageID)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID), () -> createOKResponse(
				toMessageResource(createGoalIDMapping(userID), messageService.getMessage(userID, messageID))));
	}

	private GoalIDMapping createGoalIDMapping(UUID userID)
	{
		return GoalIDMapping.createInstance(userService.getPrivateUser(userID));
	}

	public MessageDTO toMessageResource(GoalIDMapping goalIDMapping, MessageDTO message)
	{
		return new MessageResourceAssembler(curieProvider, goalIDMapping, this).toResource(message);
	}

	@RequestMapping(value = "/{id}/{action}", method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<MessageActionResource> handleAnonymousMessageAction(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID, @PathVariable UUID id,
			@PathVariable String action, @RequestBody MessageActionDTO requestPayload)
	{

		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> createOKResponse(new MessageActionResource(curieProvider,
						messageService.handleMessageAction(userID, id, action, requestPayload), createGoalIDMapping(userID),
						this)));
	}

	@RequestMapping(value = "/{messageID}", method = RequestMethod.DELETE)
	@ResponseBody
	public HttpEntity<MessageActionResource> deleteAnonymousMessage(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PathVariable UUID messageID)
	{

		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> createOKResponse(new MessageActionResource(curieProvider, messageService.deleteMessage(userID, messageID),
						createGoalIDMapping(userID), this)));
	}

	private HttpEntity<PagedResources<MessageDTO>> createOKResponse(PagedResources<MessageDTO> messages)
	{
		return new ResponseEntity<PagedResources<MessageDTO>>(messages, HttpStatus.OK);
	}

	private HttpEntity<MessageDTO> createOKResponse(MessageDTO message)
	{
		return new ResponseEntity<MessageDTO>(message, HttpStatus.OK);
	}

	private HttpEntity<MessageActionResource> createOKResponse(MessageActionResource messageAction)
	{
		return new ResponseEntity<MessageActionResource>(messageAction, HttpStatus.OK);
	}

	public static ControllerLinkBuilder getAnonymousMessageLinkBuilder(UUID userID, UUID messageID)
	{
		MessageController methodOn = methodOn(MessageController.class);
		return linkTo(methodOn.getMessage(Optional.empty(), userID, messageID));
	}

	public static Link getMessagesLink(UUID userID)
	{
		ControllerLinkBuilder linkBuilder = linkTo(
				methodOn(MessageController.class).getMessages(Optional.empty(), null, userID, null, null));
		return linkBuilder.withRel("messages");
	}

	private UserActivityController getUserActivityController()
	{
		return userActivityController;
	}

	private BuddyActivityController getBuddyActivityController()
	{
		return buddyActivityController;
	}

	static class MessageActionResource extends Resource<MessageActionDTO>
	{
		private final GoalIDMapping goalIDMapping;
		private final CurieProvider curieProvider;
		private final MessageController messageController;

		public MessageActionResource(CurieProvider curieProvider, MessageActionDTO messageAction, GoalIDMapping goalIDMapping,
				MessageController messageController)
		{
			super(messageAction);
			this.curieProvider = curieProvider;
			this.goalIDMapping = goalIDMapping;
			this.messageController = messageController;
		}

		@JsonProperty("_embedded")
		public Map<String, List<MessageDTO>> getEmbeddedResources()
		{
			Set<MessageDTO> affectedMessages = getContent().getAffectedMessages();
			return Collections.singletonMap(curieProvider.getNamespacedRelFor("affectedMessages"),
					new MessageResourceAssembler(curieProvider, goalIDMapping, messageController).toResources(affectedMessages));
		}
	}

	public static class MessageResourceAssembler extends ResourceAssemblerSupport<MessageDTO, MessageDTO>
	{
		private final GoalIDMapping goalIDMapping;
		private final CurieProvider curieProvider;
		private final MessageController messageController;

		public MessageResourceAssembler(CurieProvider curieProvider, GoalIDMapping goalIDMapping,
				MessageController messageController)
		{
			super(MessageController.class, MessageDTO.class);
			this.curieProvider = curieProvider;
			this.goalIDMapping = goalIDMapping;
			this.messageController = messageController;
		}

		@Override
		public MessageDTO toResource(MessageDTO message)
		{
			message.removeLinks(); // So we are sure the below links are the only ones
			ControllerLinkBuilder selfLinkBuilder = getAnonymousMessageLinkBuilder(goalIDMapping.getUserID(), message.getID());
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

		private void addRelatedMessageLink(MessageDTO message, MessageDTO messageResource)
		{
			if (message.getRelatedMessageID() != null)
			{
				messageResource.add(getAnonymousMessageLinkBuilder(goalIDMapping.getUserID(), message.getRelatedMessageID())
						.withRel("related"));
			}
		}

		@Override
		protected MessageDTO instantiateResource(MessageDTO message)
		{
			return message;
		}

		private void addSelfLink(ControllerLinkBuilder selfLinkBuilder, MessageDTO messageResource)
		{
			messageResource.add(selfLinkBuilder.withSelfRel());
		}

		private void addEditLink(ControllerLinkBuilder selfLinkBuilder, MessageDTO messageResource)
		{
			messageResource.add(selfLinkBuilder.withRel(JsonRootRelProvider.EDIT_REL));
		}

		private void addActionLinks(ControllerLinkBuilder selfLinkBuilder, MessageDTO messageResource)
		{
			messageResource.getPossibleActions().stream().forEach(a -> messageResource.add(selfLinkBuilder.slash(a).withRel(a)));
		}

		protected void doDynamicDecoration(MessageDTO message)
		{
			if (message instanceof BuddyMessageEmbeddedUserDTO)
			{
				embedBuddyUserIfAvailable((BuddyMessageEmbeddedUserDTO) message);
			}
			if (message instanceof BuddyMessageLinkedUserDTO)
			{
				addUserLinkIfAvailable((BuddyMessageLinkedUserDTO) message);
			}
			if (message instanceof BuddyConnectResponseMessageDTO)
			{
				addSenderBuddyLinkIfAvailable((BuddyConnectResponseMessageDTO) message);
			}
			if (message instanceof GoalConflictMessageDTO)
			{
				addGoalConflictMessageLinks((GoalConflictMessageDTO) message);
			}
			if (message instanceof DisclosureRequestMessageDTO)
			{
				addDayActivityDetailLink((DisclosureRequestMessageDTO) message);
			}
			if (message instanceof DisclosureResponseMessageDTO)
			{
				addDayActivityDetailLink((DisclosureResponseMessageDTO) message);
			}
			if (message instanceof GoalChangeMessageDTO)
			{
				addRelatedActivityCategoryLink((GoalChangeMessageDTO) message);
			}
			if (message instanceof ActivityCommentMessageDTO)
			{
				addActivityCommentMessageLinks((ActivityCommentMessageDTO) message);
			}
		}

		private void addSenderBuddyLinkIfAvailable(MessageDTO message)
		{
			if (message.getSenderBuddyID().isPresent())
			{
				message.add(BuddyController.getBuddyLinkBuilder(goalIDMapping.getUserID(), message.getSenderBuddyID().get())
						.withRel("buddy"));
			}
		}

		private void addRelatedActivityCategoryLink(GoalChangeMessageDTO message)
		{
			message.add(ActivityCategoryController
					.getActivityCategoryLinkBuilder(message.getChangedGoal().getActivityCategoryID()).withRel("related"));
		}

		private void addGoalConflictMessageLinks(GoalConflictMessageDTO message)
		{
			addActivityCategoryLink(message);
			addDayActivityDetailLink(message);
		}

		private void addActivityCategoryLink(GoalConflictMessageDTO message)
		{
			message.add(ActivityCategoryController.getActivityCategoryLinkBuilder(message.getActivityCategoryID())
					.withRel("activityCategory"));
		}

		private void addDayActivityDetailLink(DisclosureRequestMessageDTO message)
		{
			String dateStr = DayActivityDTO.formatDate(message.getTargetGoalConflictDate());
			message.add(UserActivityController.getUserDayActivityDetailLinkBuilder(goalIDMapping.getUserID(), dateStr,
					message.getTargetGoalConflictGoalID()).withRel(UserActivityController.DAY_DETAIL_LINK));
		}

		private void addDayActivityDetailLink(DisclosureResponseMessageDTO message)
		{
			String dateStr = DayActivityDTO.formatDate(message.getTargetGoalConflictDate());
			message.add(
					BuddyActivityController
							.getBuddyDayActivityDetailLinkBuilder(goalIDMapping.getUserID(), message.getSenderBuddyID().get(),
									dateStr, message.getTargetGoalConflictGoalID())
							.withRel(BuddyActivityController.DAY_DETAIL_LINK));
		}

		private void addDayActivityDetailLink(GoalConflictMessageDTO message)
		{
			String dateStr = DayActivityDTO.formatDate(message.getActivityStartTime().toLocalDate());
			if (message.isSentFromBuddy())
			{
				message.add(BuddyActivityController.getBuddyDayActivityDetailLinkBuilder(goalIDMapping.getUserID(),
						message.getSenderBuddyID().get(), dateStr, message.getGoalID())
						.withRel(BuddyActivityController.DAY_DETAIL_LINK));
			}
			else
			{
				message.add(UserActivityController
						.getUserDayActivityDetailLinkBuilder(goalIDMapping.getUserID(), dateStr, message.getGoalID())
						.withRel(UserActivityController.DAY_DETAIL_LINK));
			}
		}

		private void embedBuddyUserIfAvailable(BuddyMessageEmbeddedUserDTO buddyMessage)
		{
			UserDTO user = buddyMessage.getUser();
			if (user != null)
			{
				buddyMessage.setEmbeddedUser(curieProvider.getNamespacedRelFor(BuddyDTO.USER_REL_NAME),
						new UserController.UserResourceAssembler(curieProvider, false).toResource(user));
			}
		}

		private void addUserLinkIfAvailable(BuddyMessageLinkedUserDTO buddyMessage)
		{
			UserDTO user = buddyMessage.getUser();
			if (user != null)
			{
				buddyMessage.add(UserController.getPublicUserLink(BuddyDTO.USER_REL_NAME, user.getID()));
			}
		}

		private void addActivityCommentMessageLinks(ActivityCommentMessageDTO message)
		{
			IntervalActivity activity = IntervalActivity.getIntervalActivityRepository().findOne(message.getActivityID());
			if (goalIDMapping.isUserGoal(activity.getGoal().getID()))
			{
				messageController.getUserActivityController().addLinks(goalIDMapping, activity, message);
			}
			else
			{
				messageController.getBuddyActivityController().addLinks(goalIDMapping, activity, message);
			}
		}
	}
}
