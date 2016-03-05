package nu.yona.server.analysis.entities;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DayActivityRepository extends CrudRepository<DayActivity, UUID>
{
	@Query("select a from DayActivity a"
			+ " where a.userAnonymized.id = :userAnonymizedID and a.goal.id = :goalID and a.zonedStartTime = :zonedStartOfDay")
	DayActivity findOne(@Param("userAnonymizedID") UUID userAnonymizedID, @Param("goalID") UUID goalID,
			@Param("zonedStartOfDay") ZonedDateTime zonedStartOfDay);
}
