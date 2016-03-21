/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.function.Supplier;

import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;

import org.springframework.stereotype.Service;

/*
 * Triggers the use of new subtransactions. See
 * http://stackoverflow.com/questions/15795985/spring-transaction-propagation-required-requires-new
 */
@Service
class TransactionHelper
{
	@Transactional(value = TxType.REQUIRES_NEW)
	public <T> T executeInNewTransaction(Supplier<T> supplier)
	{
		return supplier.get();
	}
}
