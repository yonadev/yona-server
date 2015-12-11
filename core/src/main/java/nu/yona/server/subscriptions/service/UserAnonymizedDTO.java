package nu.yona.server.subscriptions.service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.service.MessageDestinationDTO;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
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
		// hard to cache because the availability of a buddy connection may change
		return UserAnonymized
				.mapAvailableDestinations(buddyAnonymizedIDs.stream().map(id -> BuddyAnonymized.getRepository().findOne(id)))
				.map(messageDestination -> MessageDestinationDTO.createInstance(messageDestination)).collect(Collectors.toSet());
	}

	public BuddyAnonymized getBuddyAnonymized(UUID fromUserVPNLoginID)
	{
		return buddyAnonymizedIDs.stream().map(id -> BuddyAnonymized.getRepository().findOne(id))
				.filter(ba -> ba.getVPNLoginID().equals(fromUserVPNLoginID)).findAny().orElse(null);
	}
}
