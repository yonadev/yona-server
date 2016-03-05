package nu.yona.server.analysis.entities;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WeekActivityRepository extends CrudRepository<WeekActivity, UUID>
{
	@Query("select a from WeekActivity a"
			+ " where a.userAnonymized.id = :userAnonymizedID and a.goal.id = :goalID and a.startTime = :startOfWeek")
	WeekActivity findOne(@Param("userAnonymizedID") UUID userAnonymizedID, @Param("goalID") UUID goalID,
			@Param("startOfWeek") ZonedDateTime startOfWeek);
}
