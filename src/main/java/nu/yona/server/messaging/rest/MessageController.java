/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
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

import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.goals.rest.GoalController;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.subscriptions.service.UserService;

@Controller
public class MessageController {
	@Autowired
	private MessageService messageService;

	@Autowired
	private UserService userService;

	@RequestMapping(value = "/user/{userID}/message/direct/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<Resource<MessageDTO>>> getDirectMessages(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable long userID) {

		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> buildResponse("message/direct/", userID, messageService.getDirectMessages(userID)));

	}

	@RequestMapping(value = "/user/{userID}/message/direct/{id}/{action}", method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<Resource<MessageActionDTO>> handleMessageAction(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable long userID,
			@PathVariable UUID id, @PathVariable String action, @RequestBody MessageActionDTO requestPayload) {

		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> buildResponse(messageService.handleMessageAction(userID, id, action, requestPayload)));
	}

	@RequestMapping(value = "/user/{userID}/message/anonymous/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<Resource<MessageDTO>>> getAnonymousMessages(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable long userID) {

		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> buildResponse("message/anonymous/", userID, messageService.getAnonymousMessages(userID)));
	}

	private ResponseEntity<Resource<MessageActionDTO>> buildResponse(MessageActionDTO dto) {
		return new ResponseEntity<Resource<MessageActionDTO>>(new Resource<>(dto), HttpStatus.OK);
	}

	private ResponseEntity<Resources<Resource<MessageDTO>>> buildResponse(String path, long userID,
			List<MessageDTO> messages) {
		return new ResponseEntity<Resources<Resource<MessageDTO>>>(wrapMessagesAsResourceList(path, userID, messages),
				HttpStatus.OK);
	}

	private Resources<Resource<MessageDTO>> wrapMessagesAsResourceList(String path, long userID,
			List<MessageDTO> messages) {
		// TODO: Use message resource assembler here
		// TODO: Check whether trailing slash is retained
		ControllerLinkBuilder directMessagesRootLinkBuilder = createDirectMessagesRootLinkBuilder(path, userID);
		List<Resource<MessageDTO>> resources = wrapMessagesAsResources(userID, messages, directMessagesRootLinkBuilder);

		return new Resources<>(resources, directMessagesRootLinkBuilder.withSelfRel());
	}

	private ControllerLinkBuilder createDirectMessagesRootLinkBuilder(String path, long userID) {
		return linkTo(MessageController.class).slash("/user").slash(userID).slash(path);
	}

	private List<Resource<MessageDTO>> wrapMessagesAsResources(long userID, List<MessageDTO> messages,
			ControllerLinkBuilder directMessagesRootLinkBuilder) {
		List<Resource<MessageDTO>> messageResources = messages.stream()
				.map(mr -> wrapMessageAsResource(directMessagesRootLinkBuilder, mr)).collect(Collectors.toList());
		return messageResources;
	}

	private Resource<MessageDTO> wrapMessageAsResource(ControllerLinkBuilder directMessagesRootLinkBuilder,
			MessageDTO message) {
		ControllerLinkBuilder thisMessageLinkBuilder = directMessagesRootLinkBuilder.slash(message.getID());
		Collection<Link> allLinks = createActionLinks(thisMessageLinkBuilder, message);
		Link selfLink = thisMessageLinkBuilder.withSelfRel();
		allLinks.add(selfLink);
		Collection<Link> allLinksExpanded = allLinks.stream().map(l -> l.expand(message.getID()))
				.collect(Collectors.toList());
		return new Resource<MessageDTO>(message, allLinksExpanded);
	}

	private Collection<Link> createActionLinks(ControllerLinkBuilder thisMessageLinkBuilder, MessageDTO message) {
		return message.getPossibleActions().stream().map(a -> thisMessageLinkBuilder.slash(a).withRel(a))
				.collect(Collectors.toList());
	}

	static class MessageResource extends Resource<MessageDTO> {
		public MessageResource(MessageDTO message) {
			super(message);
		}
	}

	private static class MessageResourceAssembler extends ResourceAssemblerSupport<MessageDTO, MessageResource> {
		public MessageResourceAssembler() {
			super(GoalController.class, MessageResource.class);
		}

		@Override
		public MessageResource toResource(MessageDTO message) {
			return super.createResourceWithId(message.getID(), message);
		}

		@Override
		protected MessageResource instantiateResource(MessageDTO message) {
			return new MessageResource(message);
		}
	}
}
