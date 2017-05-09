/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.sms;

import java.util.Map;

public interface SmsService
{
	void send(String phoneNumber, SmsTemplate messageTemplate, Map<String, Object> templateParameters);
}
