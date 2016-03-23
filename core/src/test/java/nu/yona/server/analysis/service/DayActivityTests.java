package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;

import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.DayActivity;

public class DayActivityTests
{
	private ZoneId testZone;

	@Before
	public void setUp()
	{
		testZone = ZoneId.of("Europe/Amsterdam");
	}

	private ZonedDateTime getZonedDateTime(int hour, int minute, int second)
	{
		return ZonedDateTime.of(2016, 3, 17, hour, minute, second, 0, testZone);
	}

	private DayActivity createDayActivity()
	{
		return DayActivity.createInstance(null, null, getZonedDateTime(0, 0, 0));
	}

	private Date getDate(int hour, int minute)
	{
		return getDate(hour, minute, 0);
	}

	private Date getDate(int hour, int minute, int second)
	{
		return Date.from(getZonedDateTime(hour, minute, second).toInstant());
	}

	@Test
	public void testSpreadSpan1()
	{
		DayActivity d = createDayActivity();
		d.addActivity(Activity.createInstance(getDate(19, 55), getDate(19, 59)));
		assertThat(d.getSpread().get(78), equalTo(0));
		assertThat(d.getSpread().get(79), equalTo(5));
		assertThat(d.getSpread().get(80), equalTo(0));
	}

	@Test
	public void testSpreadSpan2()
	{
		DayActivity d = createDayActivity();
		d.addActivity(Activity.createInstance(getDate(19, 55), getDate(20, 1)));
		assertThat(d.getSpread().get(78), equalTo(0));
		assertThat(d.getSpread().get(79), equalTo(5));
		assertThat(d.getSpread().get(80), equalTo(2));
		assertThat(d.getSpread().get(81), equalTo(0));
	}

	@Test
	public void testSpreadSpan2StartEdge()
	{
		DayActivity d = createDayActivity();
		d.addActivity(Activity.createInstance(getDate(19, 45), getDate(20, 1)));
		assertThat(d.getSpread().get(78), equalTo(0));
		assertThat(d.getSpread().get(79), equalTo(15));
		assertThat(d.getSpread().get(80), equalTo(2));
		assertThat(d.getSpread().get(81), equalTo(0));
	}

	@Test
	public void testSpreadSpan2EndEdge()
	{
		DayActivity d = createDayActivity();
		d.addActivity(Activity.createInstance(getDate(19, 55), getDate(20, 15)));
		assertThat(d.getSpread().get(78), equalTo(0));
		assertThat(d.getSpread().get(79), equalTo(5));
		assertThat(d.getSpread().get(80), equalTo(15));
		assertThat(d.getSpread().get(81), equalTo(1));
	}

	@Test
	public void testSpreadSpan1StartEndEdge()
	{
		DayActivity d = createDayActivity();
		d.addActivity(Activity.createInstance(getDate(19, 45, 00), getDate(19, 59, 59)));
		assertThat(d.getSpread().get(78), equalTo(0));
		assertThat(d.getSpread().get(79), equalTo(15));
		assertThat(d.getSpread().get(80), equalTo(0));
	}

	@Test
	public void testSpreadSpan3()
	{
		DayActivity d = createDayActivity();
		d.addActivity(Activity.createInstance(getDate(19, 55), getDate(20, 16)));
		assertThat(d.getSpread().get(78), equalTo(0));
		assertThat(d.getSpread().get(79), equalTo(5));
		assertThat(d.getSpread().get(80), equalTo(15));
		assertThat(d.getSpread().get(81), equalTo(2));
		assertThat(d.getSpread().get(82), equalTo(0));
	}

	@Test
	public void testSpreadMultipleActivitiesOverlappingUnsorted()
	{
		DayActivity d = createDayActivity();
		d.addActivity(Activity.createInstance(getDate(19, 48), getDate(19, 50)));
		d.addActivity(Activity.createInstance(getDate(19, 46), getDate(19, 59)));
		d.addActivity(Activity.createInstance(getDate(20, 1), getDate(20, 17)));
		assertThat(d.getSpread().get(78), equalTo(0));
		assertThat(d.getSpread().get(79), equalTo(14));
		assertThat(d.getSpread().get(80), equalTo(14));
		assertThat(d.getSpread().get(81), equalTo(3));
		assertThat(d.getSpread().get(82), equalTo(0));
	}
}
