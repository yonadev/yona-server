/*******************************************************************************
 * Copyright (c) 2018, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import org.junit.jupiter.api.Test;
import org.springframework.hateoas.Link;

import nl.jqno.equalsverifier.EqualsVerifier;

class GoalDtoTest
{
	@Test
	void equalsContract()
	{
		EqualsVerifier.forClass(GoalDto.class).withRedefinedSuperclass().withOnlyTheseFields("id")
				.withPrefabValues(Link.class, Link.of("edit"), Link.of("self")).withNonnullFields("id").verify();
	}
}
