package nu.yona.server.analysis.entities;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DayActivityRepository extends CrudRepository<DayActivity, UUID>
{
	@Query("select a from DayActivity a"
			+ " where a.userAnonymizedID = :userAnonymizedID and a.goalID = :goalID and a.localDate = :forDate")
	DayActivity findDayActivity(@Param("userAnonymizedID") UUID userAnonymizedID, @Param("goalID") UUID goalID,
			@Param("forDate") LocalDate forDate);
}
