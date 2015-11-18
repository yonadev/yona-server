/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.subscriptions.service.BuddyDTO;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.UserDTO;

@JsonRootName("goalConflictMessage")
public class GoalConflictMessageDTO extends MessageDTO
{
    private final String nickname;
    private final String goalName;
    private final String url;

    private GoalConflictMessageDTO(UUID id, String nickname, String goalName, String url)
    {
        super(id);
        this.nickname = nickname;
        this.goalName = goalName;
        this.url = url;
    }

    @Override
    public Set<String> getPossibleActions()
    {
        return new HashSet<>();
    }

    public String getNickname()
    {
        return nickname;
    }

    public String getGoalName()
    {
        return goalName;
    }

    public String getUrl()
    {
        return url;
    }

    public static GoalConflictMessageDTO createInstance(UserDTO actingUser, GoalConflictMessage messageEntity, String nickname)
    {
        return new GoalConflictMessageDTO(messageEntity.getID(), nickname, messageEntity.getGoal().getName(),
                messageEntity.getURL());
    }

    @Component
    private static class Manager implements DTOManager
    {
        @Autowired
        private TheDTOManager theDTOFactory;

        @Autowired
        private BuddyService buddyService;

        @PostConstruct
        private void init()
        {
            theDTOFactory.addManager(GoalConflictMessage.class, this);
        }

        @Override
        public MessageDTO createInstance(UserDTO actingUser, Message messageEntity)
        {
            return GoalConflictMessageDTO.createInstance(actingUser, (GoalConflictMessage) messageEntity,
                    getNickname(actingUser, (GoalConflictMessage) messageEntity));
        }

        @Override
        public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
                MessageActionDTO requestPayload)
        {
            throw new IllegalArgumentException("Action '" + action + "' is not supported");
        }

        private String getNickname(UserDTO actingUser, GoalConflictMessage messageEntity)
        {
            UUID loginID = messageEntity.getRelatedLoginID();
            if (actingUser.getPrivateData().getVpnProfile().getLoginID().equals(loginID))
            {
                return "<self>";
            }

            Set<BuddyDTO> buddies = buddyService.getBuddies(actingUser.getPrivateData().getBuddyIDs());
            for (BuddyDTO buddy : buddies)
            {
                if (loginID.equals(buddy.getLoginID()))
                {
                    return buddy.getNickName();
                }
            }
            throw new IllegalStateException("Cannot find buddy for login ID '" + loginID + "'");
        }
    }
}
