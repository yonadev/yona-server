package nu.yona.server.subscriptions.entities;

import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

public interface NewDeviceRequestRepository extends CrudRepository<NewDeviceRequest, UUID> {
	
}
