package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.time.DayOfWeek;

import org.junit.Test;

public class WeekActivityTests
{
	@Test
	public void testParse()
	{
		assertThat(WeekActivityDTO.parseDate("2016-W02").getDayOfWeek(), equalTo(DayOfWeek.SUNDAY));
		assertThat(WeekActivityDTO.parseDate("2016-W02").getYear(), equalTo(2016));
	}
}
