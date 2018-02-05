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