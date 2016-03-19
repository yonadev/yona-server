package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.subscriptions.service.UserService;

@Service
public class ActivityService
{
	@Autowired
	private UserService userService;

	public Page<WeekActivityDTO> getWeekActivity(UUID userID, Pageable pageable)
	{
		UUID userAnonymizedID = userService.getPrivateUser(userID).getPrivateData().getUserAnonymizedID();
		Page<WeekActivity> weekActivityEntitiesPage = WeekActivity.getRepository().findAll(userAnonymizedID, pageable);
		return new PageImpl<WeekActivityDTO>(weekActivityEntitiesPage.getContent().stream()
				.map(a -> WeekActivityDTO.createInstance(a)).collect(Collectors.toList()), pageable,
				weekActivityEntitiesPage.getTotalElements());
	}

	public Page<DayActivityDTO> getDayActivity(UUID userID, Pageable pageable)
	{
		UUID userAnonymizedID = userService.getPrivateUser(userID).getPrivateData().getUserAnonymizedID();
		Page<DayActivity> dayActivityEntitiesPage = DayActivity.getRepository().findAll(userAnonymizedID, pageable);
		return new PageImpl<DayActivityDTO>(dayActivityEntitiesPage.getContent().stream()
				.map(a -> DayActivityDTO.createInstance(a)).collect(Collectors.toList()), pageable,
				dayActivityEntitiesPage.getTotalElements());
	}

	public WeekActivityDTO getWeekActivity(UUID userID, LocalDate date, UUID goalID)
	{
		UUID userAnonymizedID = userService.getPrivateUser(userID).getPrivateData().getUserAnonymizedID();
		return WeekActivityDTO.createInstance(WeekActivity.getRepository().findOne(userAnonymizedID, date, goalID));
	}

	public DayActivityDTO getDayActivity(UUID userID, LocalDate date, UUID goalID)
	{
		UUID userAnonymizedID = userService.getPrivateUser(userID).getPrivateData().getUserAnonymizedID();
		return DayActivityDTO.createInstance(DayActivity.getRepository().findOne(userAnonymizedID, date, goalID));
	}
}
