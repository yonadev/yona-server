package nu.yona.server.analysis.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalUnit;
import java.time.temporal.WeekFields;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.Translator;
import nu.yona.server.analysis.entities.WeekActivity;

@JsonRootName("weekActivity")
public class WeekActivityDTO extends IntervalActivityDTO
{
	private static final DateTimeFormatter ISO8601_WEEK_FORMATTER = new DateTimeFormatterBuilder().parseCaseInsensitive()
			.appendValue(IsoFields.WEEK_BASED_YEAR, 4, 10, SignStyle.EXCEEDS_PAD).appendLiteral("-W")
			.appendValue(IsoFields.WEEK_OF_WEEK_BASED_YEAR, 2)
			.parseDefaulting(WeekFields.ISO.dayOfWeek(), DayOfWeek.MONDAY.getValue()).toFormatter(Translator.EN_US_LOCALE);

	private final Map<DayOfWeek, DayActivityDTO> dayActivities;

	private WeekActivityDTO(UUID goalID, ZonedDateTime startTime, boolean shouldSerializeDate, List<Integer> spread,
			Optional<Integer> totalActivityDurationMinutes, Map<DayOfWeek, DayActivityDTO> dayActivities, boolean hasPrevious,
			boolean hasNext)
	{
		super(goalID, startTime, shouldSerializeDate, spread, totalActivityDurationMinutes, hasPrevious, hasNext);
		this.dayActivities = dayActivities;
	}

	@Override
	protected TemporalUnit getTimeUnit()
	{
		return ChronoUnit.WEEKS;
	}

	@Override
	protected String formatDateAsISO(LocalDate date)
	{
		return formatDate(date);
	}

	@JsonIgnore
	public Map<DayOfWeek, DayActivityDTO> getDayActivities()
	{
		return dayActivities;
	}

	public static LocalDate parseDate(String iso8601)
	{
		// ISO treats Monday as first day of the week, so our formatter defaults the day as Monday and here we subtract one day to
		// return the preceding Sunday.
		return LocalDate.parse(iso8601, ISO8601_WEEK_FORMATTER).minusDays(1);
	}

	public static String formatDate(LocalDate sundayDate)
	{
		// ISO treats Monday as first day of the week, so determine the week number based on the Monday rather than Sunday
		LocalDate mondayDate = sundayDate.plusDays(1);
		return ISO8601_WEEK_FORMATTER.format(mondayDate);
	}

	static WeekActivityDTO createInstance(WeekActivity weekActivity, LevelOfDetail levelOfDetail)
	{
		boolean includeDetail = levelOfDetail == LevelOfDetail.WeekDetail;
		return new WeekActivityDTO(weekActivity.getGoal().getID(), weekActivity.getStartTime(), includeDetail,
				includeDetail ? weekActivity.getSpread() : Collections.emptyList(),
				includeDetail ? Optional.of(weekActivity.getTotalActivityDurationMinutes()) : Optional.empty(),
				weekActivity.getDayActivities().stream()
						.collect(Collectors.toMap(dayActivity -> dayActivity.getDate().getDayOfWeek(),
								dayActivity -> DayActivityDTO.createInstance(dayActivity, levelOfDetail))),
				weekActivity.hasPrevious(), weekActivity.hasNext());
	}
}
