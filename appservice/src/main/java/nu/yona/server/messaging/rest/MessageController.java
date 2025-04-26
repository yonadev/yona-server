/*******************************************************************************
 * Copyright (c) 2015, 2021 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.rest;

import static nu.yona.server.rest.RestConstants.PASSWORD_HEADER;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.server.ExposesResourceFor;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
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
import com.google.common.collect.Lists;

import nu.yona.server.analysis.rest.BuddyActivityController;
import nu.yona.server.analysis.rest.UserActivityController;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.messaging.service.MessageActionDto;
import nu.yona.server.messaging.service.MessageDto;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.rest.BuddyController;
import nu.yona.server.subscriptions.rest.UserPhotoController;
import nu.yona.server.subscriptions.service.GoalIdMapping;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
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
	private UserAnonymizedService userAnonymizedService;

	@Autowired
	private CurieProvider curieProvider;

	@Autowired
	@Lazy
	private UserActivityController userActivityController;

	@Autowired
	@Lazy
	private BuddyActivityController buddyActivityController;

	@Autowired
	private MessageResourceDecoratorRegistry messageResourceDecoratorRegistry;

	@GetMapping(value = "/")
	@ResponseBody
	public HttpEntity<PagedModel<MessageDto>> getMessages(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = "onlyUnreadMessages", required = false, defaultValue = "false") String onlyUnreadMessagesStr,
			@PathVariable UUID userId, Pageable pageable, PagedResourcesAssembler<MessageDto> pagedResourcesAssembler)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			boolean onlyUnreadMessages = Boolean.TRUE.toString().equals(onlyUnreadMessagesStr);

			return getMessages(userId, pageable, pagedResourcesAssembler, onlyUnreadMessages);
		}
	}

	private HttpEntity<PagedModel<MessageDto>> getMessages(UUID userId, Pageable pageable,
			PagedResourcesAssembler<MessageDto> pagedResourcesAssembler, boolean onlyUnreadMessages)
	{
		User user = userService.getValidatedUserEntity(userId);
		Page<MessageDto> messages = messageService.getReceivedMessages(user, onlyUnreadMessages, pageable);
		return createOkResponse(user, messages, pagedResourcesAssembler);
	}

	@GetMapping(value = "/{messageId}")
	@ResponseBody
	public HttpEntity<MessageDto> getMessage(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable long messageId)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			User user = userService.getValidatedUserEntity(userId);
			return createOkResponse(user, messageService.getMessage(user, messageId));
		}
	}

	private GoalIdMapping createGoalIdMapping(User user)
	{
		return GoalIdMapping.createInstance(userAnonymizedService, user);
	}

	public HttpEntity<MessageDto> createOkResponse(User user, MessageDto message)
	{
		return createOkResponse(message, createResourceAssembler(createGoalIdMapping(user)));
	}

	public HttpEntity<PagedModel<MessageDto>> createOkResponse(User user, Page<MessageDto> messages,
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
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			User user = userService.getValidatedUserEntity(userId);

			return createOkResponse(new MessageActionResource(curieProvider,
					messageService.handleMessageAction(userId, id, action, requestPayload), createGoalIdMapping(user), this));
		}
	}

	@DeleteMapping(value = "/{messageId}")
	@ResponseBody
	public HttpEntity<MessageActionResource> deleteAnonymousMessage(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable long messageId)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			User user = userService.getValidatedUserEntity(userId);
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

	public static WebMvcLinkBuilder getAnonymousMessageLinkBuilder(UUID userId, long messageId)
	{
		MessageController methodOn = methodOn(MessageController.class);
		return linkTo(methodOn.getMessage(Optional.empty(), userId, messageId));
	}

	public static Link getMessagesLink(UUID userId)
	{
		WebMvcLinkBuilder linkBuilder = linkTo(
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

	static class MessageActionResource extends EntityModel<MessageActionDto>
	{
		public static final LinkRelation AFFECTED_MESSAGES_REL = LinkRelation.of("affectedMessages");
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

		@Override
		@Nonnull
		public MessageActionDto getContent()
		{
			return Objects.requireNonNull(super.getContent());
		}

		@JsonProperty("_embedded")
		public Map<String, List<MessageDto>> getEmbeddedResources()
		{
			Set<MessageDto> affectedMessages = getContent().getAffectedMessages();
			CollectionModel<MessageDto> messageDtos = new MessageResourceAssembler(curieProvider, goalIdMapping,
					messageController).toCollectionModel(affectedMessages);
			return Collections.singletonMap(curieProvider.getNamespacedRelFor(AFFECTED_MESSAGES_REL).value(),
					Lists.newArrayList(messageDtos));
		}
	}

	static class MessageResourceAssembler extends RepresentationModelAssemblerSupport<MessageDto, MessageDto>
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
		public @Nonnull MessageDto toModel(@Nonnull MessageDto message)
		{
			message.removeLinks(); // So we are sure the below links are the only ones
			WebMvcLinkBuilder selfLinkBuilder = getAnonymousMessageLinkBuilder(goalIdMapping.getUserId(), message.getMessageId());
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
			message.getRelatedMessageId().ifPresent(rid -> messageResource.add(
					getAnonymousMessageLinkBuilder(goalIdMapping.getUserId(), rid).withRel("related")));
		}

		@Override
		protected @Nonnull MessageDto instantiateModel(@Nonnull MessageDto message)
		{
			return message;
		}

		private void addSelfLink(WebMvcLinkBuilder selfLinkBuilder, MessageDto messageResource)
		{
			messageResource.add(selfLinkBuilder.withSelfRel());
		}

		private void addEditLink(WebMvcLinkBuilder selfLinkBuilder, MessageDto messageResource)
		{
			messageResource.add(selfLinkBuilder.withRel(IanaLinkRelations.EDIT));
		}

		private void addActionLinks(WebMvcLinkBuilder selfLinkBuilder, MessageDto messageResource)
		{
			messageResource.getPossibleActions().forEach(a -> messageResource.add(selfLinkBuilder.slash(a).withRel(a)));
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
			messageController.getMessageResourceDecorators(message.getClass()).forEach(d -> d.decorate(this, message));
		}

		UUID getSenderBuddyId(MessageDto message)
		{
			return message.getSenderBuddyId()
					.orElseThrow(() -> new IllegalStateException("Sender buddy ID must be available in this context"));
		}
	}
}
