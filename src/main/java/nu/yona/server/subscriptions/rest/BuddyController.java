/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
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

import nu.yona.server.subscriptions.rest.BuddyController.BuddyResource;
import nu.yona.server.subscriptions.service.BuddyDTO;
import nu.yona.server.subscriptions.service.BuddyService;

@Controller
@ExposesResourceFor(BuddyResource.class)
@RequestMapping(value = "/user/{requestingUserID}")
public class BuddyController {
	@Autowired
	private BuddyService buddyService;

	@RequestMapping(value = "/buddy/{buddyID}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<BuddyResource> getBuddy(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable long requestingUserID, @PathVariable long buddyID) {

		return createOKResponse(requestingUserID, buddyService.getBuddy(password, requestingUserID, buddyID));
	}

	@RequestMapping(value = "/buddy", method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<BuddyResource> addBuddy(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable long requestingUserID, @RequestBody BuddyDTO buddy) {
		return createResponse(requestingUserID, buddyService.addBuddy(password, requestingUserID, buddy),
				HttpStatus.CREATED);
	}

	private HttpEntity<BuddyResource> createOKResponse(long requestingUserID, BuddyDTO buddy) {
		return createResponse(requestingUserID, buddy, HttpStatus.OK);
	}

	private HttpEntity<BuddyResource> createResponse(long requestingUserID, BuddyDTO buddy, HttpStatus status) {
		return new ResponseEntity<BuddyResource>(new BuddyResourceAssembler(requestingUserID).toResource(buddy),
				status);
	}

	static class BuddyResource extends Resource<BuddyDTO> {
		public BuddyResource(BuddyDTO buddy) {
			super(buddy);
		}

		@JsonProperty("_embedded")
		public Map<String, UserController.UserResource> getEmbeddedResources() {
			return Collections.singletonMap(BuddyDTO.USER_REL_NAME,
					new UserController.UserResourceAssembler(false).toResource(getContent().getUser()));
		}
	}

	private static class BuddyResourceAssembler extends ResourceAssemblerSupport<BuddyDTO, BuddyResource> {
		private long requestingUserID;

		public BuddyResourceAssembler(long requestingUserID) {
			super(BuddyController.class, BuddyResource.class);
			this.requestingUserID = requestingUserID;
		}

		@Override
		public BuddyResource toResource(BuddyDTO buddy) {
			BuddyResource buddyResource = instantiateResource(buddy);
			addSelfLink(buddyResource);
			return buddyResource;
		}

		@Override
		protected BuddyResource instantiateResource(BuddyDTO buddy) {
			return new BuddyResource(buddy);
		}

		private void addSelfLink(Resource<BuddyDTO> buddyResource) {
			BuddyController methodOn = methodOn(BuddyController.class);
			BuddyDTO buddy = buddyResource.getContent();
			HttpEntity<BuddyResource> buddyHttpEntity = methodOn.getBuddy(Optional.empty(), requestingUserID,
					buddy.getID());
			Link withSelfRel = linkTo(buddyHttpEntity).withSelfRel();
			buddyResource.add(withSelfRel);
		}
	}
}
