/*******************************************************************************
 * Copyright (c) 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;

@ExtendWith(MockitoExtension.class)
class IntervalActivityTest
{
	@Mock
	private ActivityCategory ACTIVITY_CATEGORY;
	private final static LocalDate FIRST_MONDAY_OF_MARCH = LocalDate.of(2019, 3, 4);

	@ParameterizedTest
	@MethodSource("provideHasPreviousValues")
	public void hasPrevious(LocalDate earliestPossibleDate, LocalDate goalCreationTime, LocalDate startTime,
			TemporalUnit timeUnit, boolean expected)
	{
		Goal goal = BudgetGoal.createNoGoInstance(goalCreationTime.atStartOfDay(), ACTIVITY_CATEGORY);
		ZonedDateTime startTimeZoned = ZonedDateTime.of(startTime.atStartOfDay(), ZoneId.of("Europe/Amsterdam"));
		boolean result = IntervalActivity.hasPrevious(earliestPossibleDate, goal, startTimeZoned, timeUnit);
		assertThat(result, equalTo(expected));
	}

	private static Stream<Arguments> provideHasPreviousValues()
	{
		// @formatter:off
		return Stream.of(
				Arguments.of(FIRST_MONDAY_OF_MARCH, FIRST_MONDAY_OF_MARCH.plusDays(19), FIRST_MONDAY_OF_MARCH, ChronoUnit.DAYS, false), // User creation day
				Arguments.of(FIRST_MONDAY_OF_MARCH, FIRST_MONDAY_OF_MARCH.plusDays(19), FIRST_MONDAY_OF_MARCH.plusDays(1), ChronoUnit.DAYS, false), // Before goal creation
				Arguments.of(FIRST_MONDAY_OF_MARCH, FIRST_MONDAY_OF_MARCH.plusDays(19), FIRST_MONDAY_OF_MARCH.plusDays(19), ChronoUnit.DAYS, false), // Goal creation day
				Arguments.of(FIRST_MONDAY_OF_MARCH, FIRST_MONDAY_OF_MARCH.plusDays(19), FIRST_MONDAY_OF_MARCH.plusDays(20), ChronoUnit.DAYS, true), // Day after goal creation
				Arguments.of(FIRST_MONDAY_OF_MARCH, FIRST_MONDAY_OF_MARCH.plusDays(19), FIRST_MONDAY_OF_MARCH.minusDays(1), ChronoUnit.WEEKS, false), // User creation week
				Arguments.of(FIRST_MONDAY_OF_MARCH, FIRST_MONDAY_OF_MARCH.plusDays(19), FIRST_MONDAY_OF_MARCH.minusDays(1).plusDays(7), ChronoUnit.WEEKS, false), // Before goal creation
				Arguments.of(FIRST_MONDAY_OF_MARCH, FIRST_MONDAY_OF_MARCH.plusDays(19), FIRST_MONDAY_OF_MARCH.minusDays(1).plusDays(14), ChronoUnit.WEEKS, false), // Goal creation week
				Arguments.of(FIRST_MONDAY_OF_MARCH, FIRST_MONDAY_OF_MARCH.plusDays(19), FIRST_MONDAY_OF_MARCH.minusDays(1).plusDays(21), ChronoUnit.WEEKS, true) // Week after goal creation
				);
		// @formatter:on
	}
}
