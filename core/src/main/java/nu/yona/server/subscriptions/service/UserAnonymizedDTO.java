package nu.yona.server.subscriptions.service;

import java.util.Set;
import java.util.stream.Collectors;

import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.service.MessageDestinationDTO;
import nu.yona.server.subscriptions.entities.UserAnonymized;

public class UserAnonymizedDTO
{
	private Set<String> goals;
	private MessageDestinationDTO anonymousMessageDestination;
	private Set<MessageDestinationDTO> buddyMessageDestinations;

	private UserAnonymizedDTO(Set<Goal> goals, MessageDestinationDTO anonymousMessageDestination,
			Set<MessageDestinationDTO> buddyMessageDestinations)
	{
		this.goals = goals.stream().map(goal -> goal.getName()).collect(Collectors.toSet());
		this.anonymousMessageDestination = anonymousMessageDestination;
		this.buddyMessageDestinations = buddyMessageDestinations;
	}

	public static UserAnonymizedDTO createInstance(UserAnonymized entity)
	{
		return new UserAnonymizedDTO(entity.getGoals(), MessageDestinationDTO.createInstance(entity.getAnonymousDestination()),
				entity.getBuddyDestinations().stream()
						.map(buddyDestination -> MessageDestinationDTO.createInstance(buddyDestination))
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
		return buddyMessageDestinations;
	}
}
