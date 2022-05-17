/*******************************************************************************
 * Copyright (c) 2017, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.admin;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.UserAnonymizedRepository;
import nu.yona.server.subscriptions.entities.UserRepository;

@Controller
@RequestMapping(value = "/dashboard")
public class DashboardController
{
	private static final LocalDate LONG_AGO = LocalDate.of(2000, 1, 1);

	private static final List<Integer> DAYS_APP_OPENED_SCALE = Arrays.asList(0, 1, 2, 3, 4, 30, 90, 180, 365, Integer.MAX_VALUE);

	private static final List<String> DAYS_APP_OPENED_LABELS = Arrays
			.asList("Never opened", "On day of installation", "1 day", "2 days", "3 days", "4 to 30 days", "31 to 90 days",
					"91 to 180 days", "181 to 365 days", "More than 365 days");

	private static final List<Integer> HISTORY_SCALE = Arrays.asList(1, 2, 7, 14, 30, 60);

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserAnonymizedRepository userAnonymizedRepository;

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private BuildProperties buildProperties;

	@GetMapping(value = "/")
	@Transactional
	public String getIndexPage(Model model)
	{
		List<HistoryInterval> intervals = determineIntervals(HISTORY_SCALE);

		List<Integer> appOpenedCounts = calculateAppOpenedCounts(intervals);
		List<Integer> lastMonitoredActivityCounts = calculateLastMonitoredActivityCounts(intervals);
		List<Integer> daysAppOpenedCounts = calculateDaysAppOpenedCounts();
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
		model.addAttribute("appOpenedSumLast30Days",
				userRepository.countByAppLastOpenedDateBetween(LocalDate.now().minusDays(29), LocalDate.now()));
		model.addAttribute("lastMonitoredActivityCounts", lastMonitoredActivityCounts);
		model.addAttribute("lastMonitoredActivityPercentages", absoluteValuesToPercentages(lastMonitoredActivityCounts));
		model.addAttribute("lastMonitoredActivitySumLast30Days",
				userAnonymizedRepository.countByLastMonitoredActivityDateBetween(LocalDate.now().minusDays(29), LocalDate.now()));
		model.addAttribute("buildId", buildProperties.get("buildId"));
		model.addAttribute("daysAppOpenedLabels", DAYS_APP_OPENED_LABELS);
		model.addAttribute("daysAppOpenedCounts", daysAppOpenedCounts);
		model.addAttribute("daysAppOpenedPercentages", absoluteValuesToPercentages(daysAppOpenedCounts));
		model.addAttribute("daysAppOpenedCumulativePercentages",
				cumulativeValues(absoluteValuesToPercentages(daysAppOpenedCounts)));

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

	private List<Integer> calculateDaysAppOpenedCounts()
	{
		List<Integer> counts = IntStream.range(0, DAYS_APP_OPENED_SCALE.size() - 1).mapToObj(i -> userRepository
				.countByNumberOfDaysAppOpenedAfterInstallation(DAYS_APP_OPENED_SCALE.get(i), DAYS_APP_OPENED_SCALE.get(i + 1)))
				.collect(Collectors.toList());
		int neverOpenedCount = userRepository.countByAppLastOpenedDateIsNull();
		counts.add(0, neverOpenedCount);
		return counts;
	}

	private List<Integer> calculateCounts(List<HistoryInterval> intervals, Function<HistoryInterval, Integer> countRetriever,
			int neverUsedCount)
	{
		List<Integer> counts = intervals.stream().map(countRetriever).collect(Collectors.toList());
		counts.add(neverUsedCount);

		return counts;
	}

	private List<Integer> cumulativeValues(List<Integer> values)
	{
		List<Integer> results = new ArrayList<>();
		int sum = 0;
		for (int i = 0; i < values.size(); i++)
		{
			sum += values.get(i);
			results.add(sum);
		}
		return results;
	}

	private List<Integer> absoluteValuesToPercentages(List<Integer> values)
	{
		int sum = values.stream().reduce(0, (a, b) -> a + b);
		return values.stream().map(v -> absoluteValueToPercentage(sum, v)).collect(Collectors.toList());
	}

	private int absoluteValueToPercentage(int sum, Integer value)
	{
		int percentage = Math.round(((float) value) / sum * 100);
		return value > 0 ? Math.max(percentage, 1) : percentage;
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
