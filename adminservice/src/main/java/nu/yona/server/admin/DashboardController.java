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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import nu.yona.server.subscriptions.entities.UserAnonymizedRepository;
import nu.yona.server.subscriptions.entities.UserRepository;

@Controller
@RequestMapping(value = "/dashboard")
public class DashboardController
{
	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserAnonymizedRepository userAnonymizedRepository;

	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String getIndexPage(Model model)
	{
		List<Integer> intervalEndOffsets = Arrays.asList(1, 2, 7, 14, 30, 60);
		List<Interval> intervals = determineIntervals(intervalEndOffsets);

		model.addAttribute("totalNumOfUsers", userRepository.count());
		model.addAttribute("appOpened", calculateAppOpenedPercentages(intervals));
		model.addAttribute("lastMonitoredActivity", calculateLastMonitoredActivityPercentages(intervals));

		return "dashboard";
	}

	private List<Interval> determineIntervals(List<Integer> intervalEndOffsets)
	{
		Interval.setNextBegin(LocalDate.now());
		List<Interval> intervals = intervalEndOffsets.stream().map(ieo -> Interval.nextInstance(ieo))
				.collect(Collectors.toList());
		intervals.add(Interval.nextInstance(LocalDate.of(2017, 1, 1)));
		return intervals;
	}

	private List<Integer> calculateAppOpenedPercentages(List<Interval> intervals)
	{
		return calculatePercentages(intervals, (i) -> userRepository.countByAppLastOpenedDateBetween(i.begin, i.end), 0);
	}

	private List<Integer> calculateLastMonitoredActivityPercentages(List<Interval> intervals)
	{
		return calculatePercentages(intervals,
				(i) -> userAnonymizedRepository.countByLastMonitoredActivityDateBetween(i.begin, i.end),
				userAnonymizedRepository.countByLastMonitoredActivityDateIsNull());
	}

	private List<Integer> calculatePercentages(List<Interval> intervals, Function<Interval, Integer> countRetriever,
			int neverUsedCount)
	{
		List<Integer> appOpenedCounts = intervals.stream().map(countRetriever).collect(Collectors.toList());
		appOpenedCounts.add(neverUsedCount);

		return absoluteValuesToPercentages(appOpenedCounts);
	}

	private List<Integer> absoluteValuesToPercentages(List<Integer> values)
	{
		int sum = values.stream().reduce(0, (a, b) -> a + b);
		return values.stream().map(v -> Math.round(((float) v) / sum * 100)).collect(Collectors.toList());
	}

	@FunctionalInterface
	private interface Getter
	{
		int count(LocalDate begin, LocalDate end);
	}

	private static class Interval
	{
		final LocalDate begin;
		final LocalDate end;
		private static LocalDate nextBegin;

		Interval(LocalDate begin, LocalDate end)
		{
			this.begin = begin;
			this.end = end;
		}

		static Interval nextInstance(int endOffset)
		{
			LocalDate end = LocalDate.now().minusDays(endOffset - 1);
			return nextInstance(end);
		}

		private static Interval nextInstance(LocalDate end)
		{
			Interval newInterval = new Interval(nextBegin, end);
			nextBegin = newInterval.end.minusDays(1);
			return newInterval;
		}

		static void setNextBegin(LocalDate nextBegin)
		{
			Interval.nextBegin = nextBegin;
		}
	}
}
