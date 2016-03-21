/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.sms;

import java.util.Map;

public interface SmsService
{
	void send(String phoneNumber, String messageTemplateName, Map<String, Object> templateParameters);

	public static final String TemplateName_AddUserNumberConfirmation = "add-user-number-confirmation";
	public static final String TemplateName_ChangedUserNumberConfirmation = "changed-user-number-confirmation";
	public static final String TemplateName_OverwriteUserNumberConfirmation = "overwrite-user-number-confirmation";
	public static final String TemplateName_BuddyInvite = "buddy-invitation";
}
