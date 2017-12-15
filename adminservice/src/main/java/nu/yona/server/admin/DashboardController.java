/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.admin;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.UserAnonymizedRepository;
import nu.yona.server.subscriptions.entities.UserRepository;

@Controller
@RequestMapping(value = "/dashboard")
public class DashboardController
{
	private static final LocalDate LONG_AGO = LocalDate.of(2000, 1, 1);

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserAnonymizedRepository userAnonymizedRepository;

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private BuildProperties buildProperties;

	@RequestMapping(value = "/", method = RequestMethod.GET)
	@Transactional
	public String getIndexPage(Model model)
	{
		List<Integer> intervalEndOffsets = Arrays.asList(1, 2, 7, 14, 30, 60);
		List<HistoryInterval> intervals = determineIntervals(intervalEndOffsets);

		List<Integer> appOpenedCounts = calculateAppOpenedCounts(intervals);
		List<Integer> lastMonitoredActivityCounts = calculateLastMonitoredActivityCounts(intervals);
		model.addAttribute("maxNumOfUsers", yonaProperties.getMaxUsers());
		model.addAttribute("totalNumOfUsers", userRepository.count());
		model.addAttribute("numOfUsersWithConfirmedNumbers", userRepository.countByMobileNumberConfirmationCodeIsNull());
		model.addAttribute("numOfUsersWithUnconfirmedNumbers", userRepository.countByMobileNumberConfirmationCodeIsNotNull());
		model.addAttribute("numOfUsersWithUnconfirmedNumbersInvitedOnBuddyRequest",
				userRepository.countByMobileNumberConfirmationCodeIsNotNullAndIsCreatedOnBuddyRequest(true));
		model.addAttribute("numOfUsersWithUnconfirmedNumbersFreeSignUp",
				userRepository.countByMobileNumberConfirmationCodeIsNotNullAndIsCreatedOnBuddyRequest(false));
		model.addAttribute("appOpenedCounts", appOpenedCounts);
		model.addAttribute("appOpenedPercentages", absoluteValuesToPercentages(appOpenedCounts));
		model.addAttribute("lastMonitoredActivityCounts", lastMonitoredActivityCounts);
		model.addAttribute("lastMonitoredActivityPercentages", absoluteValuesToPercentages(lastMonitoredActivityCounts));
		model.addAttribute("buildNumber", buildProperties.get("buildNumber"));

		return "dashboard";
	}

	private List<HistoryInterval> determineIntervals(List<Integer> intervalEndOffsets)
	{
		HistoryInterval.setNextStart(LocalDate.now());
		List<HistoryInterval> intervals = intervalEndOffsets.stream().map(HistoryInterval::nextInstance)
				.collect(Collectors.toList());
		intervals.add(HistoryInterval.nextInstance(LONG_AGO));
		return intervals;
	}

	private List<Integer> calculateAppOpenedCounts(List<HistoryInterval> intervals)
	{
		return calculateCounts(intervals, i -> userRepository.countByAppLastOpenedDateBetween(i.end, i.start),
				userRepository.countByAppLastOpenedDateIsNull());
	}

	private List<Integer> calculateLastMonitoredActivityCounts(List<HistoryInterval> intervals)
	{
		return calculateCounts(intervals, i -> userAnonymizedRepository.countByLastMonitoredActivityDateBetween(i.end, i.start),
				userAnonymizedRepository.countByLastMonitoredActivityDateIsNull());
	}

	private List<Integer> calculateCounts(List<HistoryInterval> intervals, Function<HistoryInterval, Integer> countRetriever,
			int neverUsedCount)
	{
		List<Integer> appOpenedCounts = intervals.stream().map(countRetriever).collect(Collectors.toList());
		appOpenedCounts.add(neverUsedCount);

		return appOpenedCounts;
	}

	private List<Integer> absoluteValuesToPercentages(List<Integer> values)
	{
		int sum = values.stream().reduce(0, (a, b) -> a + b);
		return values.stream().map(v -> Math.round(((float) v) / sum * 100)).collect(Collectors.toList());
	}

	@FunctionalInterface
	private interface Getter
	{
		int count(LocalDate start, LocalDate end);
	}

	/**
	 * This interval is used to reason backward in time, so the end is more recent than the start. Note that the interval is used
	 * for "between" queries, which include both start and end, so an interval with an equal start and end date covers just one
	 * day.
	 */
	private static class HistoryInterval
	{
		final LocalDate start;
		final LocalDate end;
		private static LocalDate nextStart;

		HistoryInterval(LocalDate start, LocalDate end)
		{
			this.start = start;
			this.end = end;
		}

		static HistoryInterval nextInstance(int endOffset)
		{
			LocalDate end = LocalDate.now().minusDays(endOffset - 1L);
			return nextInstance(end);
		}

		private static HistoryInterval nextInstance(LocalDate end)
		{
			HistoryInterval newInterval = new HistoryInterval(nextStart, end);
			nextStart = newInterval.end.minusDays(1);
			return newInterval;
		}

		static void setNextStart(LocalDate nextStart)
		{
			HistoryInterval.nextStart = nextStart;
		}
	}
}
