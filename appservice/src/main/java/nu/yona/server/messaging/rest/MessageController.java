/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.analysis.rest.BuddyActivityController;
import nu.yona.server.analysis.rest.UserActivityController;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.messaging.service.MessageActionDto;
import nu.yona.server.messaging.service.MessageDto;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.subscriptions.rest.BuddyController;
import nu.yona.server.subscriptions.rest.UserPhotoController;
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

	@Autowired
	private MessageResourceDecoratorRegistry messageResourceDecoratorRegistry;

	@GetMapping(value = "/")
	@ResponseBody
	public HttpEntity<PagedResources<MessageDto>> getMessages(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = "onlyUnreadMessages", required = false, defaultValue = "false") String onlyUnreadMessagesStr,
			@PathVariable UUID userId, Pageable pageable, PagedResourcesAssembler<MessageDto> pagedResourcesAssembler)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			boolean onlyUnreadMessages = Boolean.TRUE.toString().equals(onlyUnreadMessagesStr);

			return getMessages(userId, pageable, pagedResourcesAssembler, onlyUnreadMessages);
		}
	}

	private HttpEntity<PagedResources<MessageDto>> getMessages(UUID userId, Pageable pageable,
			PagedResourcesAssembler<MessageDto> pagedResourcesAssembler, boolean onlyUnreadMessages)
	{
		UserDto user = userService.getValidatedUser(userId);
		Page<MessageDto> messages = messageService.getReceivedMessages(user, onlyUnreadMessages, pageable);
		return createOkResponse(user, messages, pagedResourcesAssembler);
	}

	@GetMapping(value = "/{messageId}")
	@ResponseBody
	public HttpEntity<MessageDto> getMessage(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable long messageId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			UserDto user = userService.getValidatedUser(userId);
			return createOkResponse(user, messageService.getMessage(user, messageId));
		}
	}

	private GoalIdMapping createGoalIdMapping(UserDto user)
	{
		return GoalIdMapping.createInstance(user);
	}

	public HttpEntity<MessageDto> createOkResponse(UserDto user, MessageDto message)
	{
		return createOkResponse(message, createResourceAssembler(createGoalIdMapping(user)));
	}

	public HttpEntity<PagedResources<MessageDto>> createOkResponse(UserDto user, Page<MessageDto> messages,
			PagedResourcesAssembler<MessageDto> pagedResourcesAssembler)
	{
		return createOkResponse(messages, pagedResourcesAssembler, createResourceAssembler(createGoalIdMapping(user)));
	}

	@PostMapping(value = "/{id}/{action}")
	@ResponseBody
	public HttpEntity<MessageActionResource> handleAnonymousMessageAction(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId, @PathVariable long id,
			@PathVariable String action, @RequestBody MessageActionDto requestPayload)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			UserDto user = userService.getValidatedUser(userId);

			return createOkResponse(new MessageActionResource(curieProvider,
					messageService.handleMessageAction(user, id, action, requestPayload), createGoalIdMapping(user), this));
		}
	}

	@DeleteMapping(value = "/{messageId}")
	@ResponseBody
	public HttpEntity<MessageActionResource> deleteAnonymousMessage(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable long messageId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			UserDto user = userService.getValidatedUser(userId);
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

	UserActivityController getUserActivityController()
	{
		return userActivityController;
	}

	BuddyActivityController getBuddyActivityController()
	{
		return buddyActivityController;
	}

	public Set<MessageResourceDecorator> getMessageResourceDecorators(Class<? extends MessageDto> classToDecorate)
	{
		return messageResourceDecoratorRegistry.getDecorators(classToDecorate);
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

	static class MessageResourceAssembler extends ResourceAssemblerSupport<MessageDto, MessageDto>
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
			if (message.isToIncludeSenderUserLink())
			{
				addSenderBuddyLink(message);
			}
			addSenderUserPhotoLinkIfAvailable(message);
			if (message.canBeDeleted())
			{
				addEditLink(selfLinkBuilder, message);
			}
			doDynamicDecoration(message);
			return message;
		}

		public GoalIdMapping getGoalIdMapping()
		{
			return goalIdMapping;
		}

		public CurieProvider getCurieProvider()
		{
			return curieProvider;
		}

		public MessageController getMessageController()
		{
			return messageController;
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

		private void addSenderBuddyLink(MessageDto message)
		{
			message.add(BuddyController.getBuddyLinkBuilder(goalIdMapping.getUserId(), getSenderBuddyId(message))
					.withRel(BuddyController.BUDDY_LINK));
		}

		private void addSenderUserPhotoLinkIfAvailable(MessageDto message)
		{
			message.getSenderUserPhotoId().ifPresent(
					userPhotoId -> message.add(UserPhotoController.getUserPhotoLinkBuilder(userPhotoId).withRel("userPhoto")));
		}

		protected void doDynamicDecoration(MessageDto message)
		{
			messageController.getMessageResourceDecorators(message.getClass()).stream().forEach(d -> d.decorate(this, message));
		}

		UUID getSenderBuddyId(MessageDto message)
		{
			return message.getSenderBuddyId()
					.orElseThrow(() -> new IllegalStateException("Sender buddy ID must be available in this context"));
		}
	}
}
