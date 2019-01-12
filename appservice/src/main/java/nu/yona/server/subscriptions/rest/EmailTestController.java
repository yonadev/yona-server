/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import nu.yona.server.email.EmailService;
import nu.yona.server.email.EmailService.EmailDto;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.subscriptions.rest.EmailTestController.EmailResource;

/**
 * This controller is only created for integration tests. When the email service is configured for testing, it doesn't send email
 * but stores the last one in a field. This controller fetches that field.
 */
@Controller
@ExposesResourceFor(EmailResource.class)
@RequestMapping(value = "/emails", produces = { MediaType.APPLICATION_JSON_VALUE })
public class EmailTestController extends ControllerBase
{
	@Autowired
	private EmailService emailService;

	/**
	 * This method returns the last e-mail that was prepared to be sent (but not sent because e-mail was disabled).
	 * 
	 * @param password The Yona password as passed on in the header of the request.
	 * @param userId The ID of the user. This is part of the URL.
	 * @return the list of buddies for the current user
	 */
	@GetMapping(value = "/last")
	@ResponseBody
	public HttpEntity<EmailResource> getLast()
	{
		return createOkResponse(emailService.getLastEmail(), createResourceAssembler());
	}

	private EmailResourceAssembler createResourceAssembler()
	{
		return new EmailResourceAssembler();
	}

	static class EmailResource extends Resource<EmailDto>
	{
		public EmailResource(EmailDto email)
		{
			super(email);
		}

	}

	static class EmailResourceAssembler extends ResourceAssemblerSupport<EmailDto, EmailResource>
	{
		public EmailResourceAssembler()
		{
			super(EmailTestController.class, EmailResource.class);
		}

		@Override
		public EmailResource toResource(EmailDto email)
		{
			return instantiateResource(email);
		}

		@Override
		protected EmailResource instantiateResource(EmailDto email)
		{
			return new EmailResource(email);
		}
	}
}
