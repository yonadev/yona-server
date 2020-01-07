/*******************************************************************************
 * Copyright (c) 2019, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import org.springframework.context.annotation.Scope;

/**
 * This type defines a Spring bean scope. Some standard bean scopes exist (e.g. singleton, request, session) and it is possible to
 * create custom ones, which then have to be configured through CustomScopeConfigurer.
 */
@Scope("thread")
public @interface ThreadScope
{

}
