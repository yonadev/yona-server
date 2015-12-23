package nu.yona.server.subscriptions.service;

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
	private Set<String> goals;
	private MessageDestinationDTO anonymousMessageDestination;
	private Set<UUID> buddyAnonymizedIDs;

	private UserAnonymizedDTO(Set<Goal> goals, MessageDestinationDTO anonymousMessageDestination, Set<UUID> buddyAnonymizedIDs)
	{
		this.goals = goals.stream().map(goal -> goal.getName()).collect(Collectors.toSet());
		this.anonymousMessageDestination = anonymousMessageDestination;
		this.buddyAnonymizedIDs = buddyAnonymizedIDs;
	}

	public static UserAnonymizedDTO createInstance(UserAnonymized entity)
	{
		return new UserAnonymizedDTO(entity.getGoals(), MessageDestinationDTO.createInstance(entity.getAnonymousDestination()),
				entity.getBuddiesAnonymized().stream().map(buddyAnonymized -> buddyAnonymized.getID())
						.collect(Collectors.toSet()));
	}

	public Set<String> getGoals()
	{
		return goals;
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
