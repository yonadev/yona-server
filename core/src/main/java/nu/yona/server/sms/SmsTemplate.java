/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.sms;

public enum SmsTemplate
{
	ADD_USER_NUMBER_CONFIRMATION("add-user-number-confirmation"), CHANGED_USER_NUMBER_CONFIRMATION(
			"changed-user-number-confirmation"), OVERWRITE_USER_CONFIRMATION(
					"overwrite-user-confirmation"), PIN_RESET_REQUEST_CONFIRMATION(
							"pin-reset-request-confirmation"), BUDDY_INVITE(
									"buddy-invitation"), DIRECT_MESSAGE_NOTIFICATION("direct-message-notification");

	private final String name;

	private SmsTemplate(String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}
}
