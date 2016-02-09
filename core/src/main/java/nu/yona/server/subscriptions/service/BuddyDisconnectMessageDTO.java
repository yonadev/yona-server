package nu.yona.server.subscriptions.service;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.messaging.service.MessageServiceException;
import nu.yona.server.subscriptions.entities.BuddyDisconnectMessage;
import nu.yona.server.subscriptions.service.BuddyService.DropBuddyReason;

@JsonRootName("buddyDisconnectMessage")
public class BuddyDisconnectMessageDTO extends BuddyMessageDTO
{
	private static final String PROCESS = "process";
	private DropBuddyReason reason;
	private boolean isProcessed;

	private BuddyDisconnectMessageDTO(UUID id, Date creationTime, UserDTO user, UUID loginID, String nickname, String message,
			DropBuddyReason reason, boolean isProcessed)
	{
		super(id, creationTime, user, nickname, message);
		this.reason = reason;
		this.isProcessed = isProcessed;
	}

	public DropBuddyReason getReason()
	{
		return reason;
	}

	public boolean isProcessed()
	{
		return isProcessed;
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = new HashSet<>();
		if (!isProcessed)
		{
			possibleActions.add(PROCESS);
		}
		return possibleActions;
	}

	@Override
	public boolean canBeDeleted()
	{
		return this.isProcessed;
	}

	public static BuddyDisconnectMessageDTO createInstance(UserDTO actingUser, BuddyDisconnectMessage messageEntity)
	{
		return new BuddyDisconnectMessageDTO(messageEntity.getID(), messageEntity.getCreationTime(),
				UserDTO.createInstanceIfNotNull(messageEntity.getUser()), messageEntity.getRelatedUserAnonymizedID(),
				messageEntity.getNickname(), messageEntity.getMessage(), messageEntity.getReason(), messageEntity.isProcessed());
	}

	@Component
	private static class Factory implements DTOManager
	{
		@Autowired
		private TheDTOManager theDTOFactory;

		@Autowired
		private BuddyService buddyService;

		@PostConstruct
		private void init()
		{
			theDTOFactory.addManager(BuddyDisconnectMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(UserDTO actingUser, Message messageEntity)
		{
			return BuddyDisconnectMessageDTO.createInstance(actingUser, (BuddyDisconnectMessage) messageEntity);
		}

		@Override
		public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
				MessageActionDTO requestPayload)
		{
			switch (action)
			{
				case PROCESS:
					return handleAction_Process(actingUser, (BuddyDisconnectMessage) messageEntity, requestPayload);
				default:
					throw MessageServiceException.actionNotSupported(action);
			}
		}

		private MessageActionDTO handleAction_Process(UserDTO actingUser, BuddyDisconnectMessage messageEntity,
				MessageActionDTO requestPayload)
		{
			buddyService.removeBuddyAfterBuddyRemovedConnection(actingUser.getID(), messageEntity.getUserID());

			messageEntity = updateMessageStatusAsProcessed(messageEntity);

			return MessageActionDTO.createInstanceActionDone(theDTOFactory.createInstance(actingUser, messageEntity));
		}

		private BuddyDisconnectMessage updateMessageStatusAsProcessed(BuddyDisconnectMessage messageEntity)
		{
			messageEntity.setProcessed();
			return Message.getRepository().save(messageEntity);
		}
	}
}
