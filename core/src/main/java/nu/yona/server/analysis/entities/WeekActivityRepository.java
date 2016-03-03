package nu.yona.server.analysis.entities;

import java.util.UUID;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WeekActivityRepository extends CrudRepository<WeekActivity, UUID>
{

}
