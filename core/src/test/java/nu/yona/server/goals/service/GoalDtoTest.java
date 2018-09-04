/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class GoalDtoTest
{
	@Test
	public void equalsContract()
	{
		EqualsVerifier.forClass(GoalDto.class).withRedefinedSuperclass().withOnlyTheseFields("id").withNonnullFields("id")
				.verify();
	}
}