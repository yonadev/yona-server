package nu.yona.server.subscriptions.entities;

import java.time.ZonedDateTime;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Table;

import nu.yona.server.entities.EntityWithID;

@Entity
@Table(name = "CONFIRMATION_CODES")
public class ConfirmationCode extends EntityWithID
{
	private ZonedDateTime creationTime;
	private String confirmationCode;
	private int attempts;

	// Default constructor is required for JPA
	public ConfirmationCode()
	{
		super(null);
	}

	private ConfirmationCode(UUID id, ZonedDateTime creationTime, String confirmationCode)
	{
		super(id);
		this.creationTime = creationTime;
		this.confirmationCode = confirmationCode;
	}

	public String getConfirmationCode()
	{
		return confirmationCode;
	}

	public void setConfirmationCode(String confirmationCode)
	{
		this.confirmationCode = confirmationCode;
	}

	public ZonedDateTime getCreationTime()
	{
		return creationTime;
	}

	public int getAttempts()
	{
		return attempts;
	}

	public void incrementAttempts()
	{
		this.attempts++;
	}

	public static ConfirmationCode createInstance(String confirmationCode)
	{
		return new ConfirmationCode(UUID.randomUUID(), ZonedDateTime.now(), confirmationCode);
	}
}
