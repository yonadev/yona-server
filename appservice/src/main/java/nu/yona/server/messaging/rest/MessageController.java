/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
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
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
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
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.messaging.rest.MessageController.MessageResource;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@ExposesResourceFor(MessageResource.class)
@RequestMapping(value = "/users/{userID}/messages")
public class MessageController
{
	@Autowired
	private MessageService messageService;

	@Autowired
	private UserService userService;

	@RequestMapping(value = "/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<MessageResource>> getAnonymousMessages(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID, Pageable pageable,
			PagedResourcesAssembler<MessageDTO> pagedResourcesAssembler)
	{

		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> createOKResponse(pagedResourcesAssembler.toResource(messageService.getAnonymousMessages(userID, pageable),
						new MessageResourceAssembler(userID))));
	}

	@RequestMapping(value = "/{messageID}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<MessageResource> getAnonymousMessage(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userID, @PathVariable UUID messageID)
	{

		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID), () -> createOKResponse(
				new MessageResourceAssembler(userID).toResource(messageService.getAnonymousMessage(userID, messageID))));

	}

	@RequestMapping(value = "/{id}/{action}", method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<MessageActionResource> handleAnonymousMessageAction(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID, @PathVariable UUID id,
			@PathVariable String action, @RequestBody MessageActionDTO requestPayload)
	{

		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> createOKResponse(new MessageActionResource(
						messageService.handleAnonymousMessageAction(userID, id, action, requestPayload), userID)));
	}

	@RequestMapping(value = "/{messageID}", method = RequestMethod.DELETE)
	@ResponseBody
	public HttpEntity<MessageActionResource> deleteAnonymousMessage(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PathVariable UUID messageID)
	{

		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID), () -> createOKResponse(
				new MessageActionResource(messageService.deleteAnonymousMessage(userID, messageID), userID)));
	}

	private HttpEntity<PagedResources<MessageResource>> createOKResponse(PagedResources<MessageResource> messages)
	{
		return new ResponseEntity<PagedResources<MessageResource>>(messages, HttpStatus.OK);
	}

	private HttpEntity<MessageResource> createOKResponse(MessageResource message)
	{
		return new ResponseEntity<MessageResource>(message, HttpStatus.OK);
	}

	private HttpEntity<MessageActionResource> createOKResponse(MessageActionResource messageAction)
	{
		return new ResponseEntity<MessageActionResource>(messageAction, HttpStatus.OK);
	}

	static ControllerLinkBuilder getAnonymousMessageLinkBuilder(UUID userID, UUID messageID)
	{
		MessageController methodOn = methodOn(MessageController.class);
		return linkTo(methodOn.getAnonymousMessage(Optional.empty(), userID, messageID));
	}

	static class MessageActionResource extends Resource<MessageActionDTO>
	{
		private UUID userID;

		public MessageActionResource(MessageActionDTO messageAction, UUID userID)
		{
			super(messageAction);
			this.userID = userID;
		}

		@JsonProperty("_embedded")
		public Map<String, List<MessageResource>> getEmbeddedResources()
		{
			Set<MessageDTO> affectedMessages = getContent().getAffectedMessages();
			return Collections.singletonMap("affectedMessages",
					new MessageResourceAssembler(userID).toResources(affectedMessages));
		}
	}

	static class MessageResource extends Resource<MessageDTO>
	{
		public MessageResource(MessageDTO message)
		{
			super(message);
		}
	}

	private static class MessageResourceAssembler extends ResourceAssemblerSupport<MessageDTO, MessageResource>
	{
		private UUID userID;

		public MessageResourceAssembler(UUID userID)
		{
			super(MessageController.class, MessageResource.class);
			this.userID = userID;
		}

		@Override
		public MessageResource toResource(MessageDTO message)
		{
			MessageResource messageResource = instantiateResource(message);
			ControllerLinkBuilder selfLinkBuilder = getAnonymousMessageLinkBuilder(userID, message.getID());
			addSelfLink(selfLinkBuilder, messageResource);
			addActionLinks(selfLinkBuilder, messageResource);
			addRelatedMessageLink(message, messageResource);
			if (message.canBeDeleted())
			{
				addEditLink(selfLinkBuilder, messageResource);
			}
			return messageResource;
		}

		private void addRelatedMessageLink(MessageDTO message, MessageResource messageResource)
		{
			if (message.getRelatedAnonymousMessageID() != null)
			{
				messageResource
						.add(getAnonymousMessageLinkBuilder(userID, message.getRelatedAnonymousMessageID()).withRel("related"));
			}
		}

		@Override
		protected MessageResource instantiateResource(MessageDTO message)
		{
			return new MessageResource(message);
		}

		private void addSelfLink(ControllerLinkBuilder selfLinkBuilder, MessageResource messageResource)
		{
			messageResource.add(selfLinkBuilder.withSelfRel());
		}

		private void addEditLink(ControllerLinkBuilder selfLinkBuilder, MessageResource messageResource)
		{
			messageResource.add(selfLinkBuilder.withRel(JsonRootRelProvider.EDIT_REL));
		}

		private void addActionLinks(ControllerLinkBuilder selfLinkBuilder, MessageResource messageResource)
		{
			messageResource.getContent().getPossibleActions().stream()
					.forEach(a -> messageResource.add(selfLinkBuilder.slash(a).withRel(a)));
		}
	}
}
