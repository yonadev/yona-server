package nu.yona.server.analysis.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.WeekFields;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.WeekActivity;

@JsonRootName("weekActivity")
public class WeekActivityDTO extends IntervalActivityDTO
{
	static final DateTimeFormatter ISO8601_WEEK_FORMATTER = new DateTimeFormatterBuilder().appendPattern("YYYY-'W'w")
			.parseDefaulting(WeekFields.ISO.dayOfWeek(), DayOfWeek.SUNDAY.getValue()).toFormatter();

	private Map<DayOfWeek, DayActivityDTO> dayActivities;

	private WeekActivityDTO(UUID goalID, ZonedDateTime startTime, boolean shouldSerializeDate, List<Integer> spread,
			Optional<Integer> totalActivityDurationMinutes, Map<DayOfWeek, DayActivityDTO> dayActivities)
	{
		super(goalID, startTime, shouldSerializeDate, spread, Collections.emptyList(), totalActivityDurationMinutes);
		this.dayActivities = dayActivities;
	}

	@Override
	public DateTimeFormatter getDateFormatter()
	{
		return ISO8601_WEEK_FORMATTER;
	}

	@JsonIgnore
	public Map<DayOfWeek, DayActivityDTO> getDayActivities()
	{
		return dayActivities;
	}

	public static LocalDate parseDate(String iso8601)
	{
		return LocalDate.parse(iso8601, ISO8601_WEEK_FORMATTER);
	}

	static WeekActivityDTO createInstance(WeekActivity weekActivity, LevelOfDetail levelOfDetail)
	{
		boolean includeDetail = levelOfDetail == LevelOfDetail.WeekDetail;
		return new WeekActivityDTO(weekActivity.getGoal().getID(), weekActivity.getStartTime(), includeDetail,
				includeDetail ? weekActivity.getSpread() : Collections.emptyList(),
				includeDetail ? Optional.of(weekActivity.getTotalActivityDurationMinutes()) : Optional.empty(),
				weekActivity.getDayActivities().stream()
						.collect(Collectors.toMap(dayActivity -> dayActivity.getDate().getDayOfWeek(),
								dayActivity -> DayActivityDTO.createInstance(dayActivity, levelOfDetail))));
	}
}
