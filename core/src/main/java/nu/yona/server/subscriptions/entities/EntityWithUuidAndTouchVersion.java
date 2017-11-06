package nu.yona.server.subscriptions.entities;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;

import nu.yona.server.entities.EntityWithUuid;

@MappedSuperclass
public abstract class EntityWithUuidAndTouchVersion extends EntityWithUuid
{
	@Column(nullable = true)
	private int touchVersion;

	public EntityWithUuidAndTouchVersion(UUID id)
	{
		super(id);
	}

	public EntityWithUuidAndTouchVersion touch()
	{
		touchVersion++;
		return this;
	}

}