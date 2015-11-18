/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such user")
public class UserNotFoundException extends RuntimeException
{
    private static final long serialVersionUID = -4519219401062670885L;

    private UserNotFoundException(String msg)
    {
        super(msg);
    }

    public static UserNotFoundException notFoundByEmailAddress(String emailAddress)
    {
        return new UserNotFoundException("User with e-mail address '" + emailAddress + "' not found");
    }

    public static UserNotFoundException notFoundByMobileNumber(String mobileNumber)
    {
        return new UserNotFoundException("User with mobile number '" + mobileNumber + "' not found");
    }

    public static UserNotFoundException notFoundByID(UUID id)
    {
        return new UserNotFoundException("User with ID '" + id + "' not found");
    }
}
