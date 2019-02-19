/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test.util;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import nu.yona.server.exceptions.ResourceBasedException;

public class Matchers
{
	private Matchers()
	{
		// No instances
	}

	public static Matcher<ResourceBasedException> hasMessageId(String messageId)
	{
		return new BaseMatcher<ResourceBasedException>() {
			@Override
			public boolean matches(Object item)
			{
				final ResourceBasedException exception = (ResourceBasedException) item;
				return exception.getMessageId().equals(messageId);
			}

			@Override
			public void describeTo(final Description description)
			{
				description.appendText("message ID should be ").appendValue(messageId);
			}
		};
	}

}
