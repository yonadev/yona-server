package nu.yona.server.subscriptions.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.service.MessageDestinationDTO;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.entities.UserAnonymized;

public class UserAnonymizedDTO
{
	private UUID id;
	private Set<Goal> goals;
	private MessageDestinationDTO anonymousMessageDestination;
	private Set<UUID> buddyAnonymizedIDs;

	public UserAnonymizedDTO(UUID id, Set<Goal> goals, MessageDestinationDTO anonymousMessageDestination,
			Set<UUID> buddyAnonymizedIDs)
	{
		this.id = id;
		this.goals = new HashSet<>(goals);
		this.anonymousMessageDestination = anonymousMessageDestination;
		this.buddyAnonymizedIDs = buddyAnonymizedIDs;
	}

	public static UserAnonymizedDTO createInstance(UserAnonymized entity)
	{
		return new UserAnonymizedDTO(entity.getID(), entity.getGoals(),
				MessageDestinationDTO.createInstance(entity.getAnonymousDestination()), entity.getBuddiesAnonymized().stream()
						.map(buddyAnonymized -> buddyAnonymized.getID()).collect(Collectors.toSet()));
	}

	public UUID getID()
	{
		return id;
	}

	public Set<Goal> getGoals()
	{
		return goals;
	}

	public String getTimeZoneId()
	{
		return "Europe/Amsterdam";
	}

	public MessageDestinationDTO getAnonymousDestination()
	{
		return anonymousMessageDestination;
	}

	public Set<MessageDestinationDTO> getBuddyDestinations()
	{
		return buddyAnonymizedIDs.stream().map(id -> BuddyAnonymized.getRepository().findOne(id))
				.filter(ba -> ba.getSendingStatus() == Status.ACCEPTED)
				.map(ba -> MessageDestinationDTO.createInstance(ba.getUserAnonymized().getAnonymousDestination()))
				.collect(Collectors.toSet());
	}

	public BuddyAnonymized getBuddyAnonymized(UUID fromUserAnonymizedID)
	{
		return buddyAnonymizedIDs.stream().map(id -> BuddyAnonymized.getRepository().findOne(id))
				.filter(ba -> ba.getUserAnonymizedID().equals(fromUserAnonymizedID)).findAny().orElse(null);
	}
}
