/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.util;

import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

/*
 * Triggers the use of new subtransactions. See
 * http://stackoverflow.com/questions/15795985/spring-transaction-propagation-required-requires-new
 */
@Service
public class TransactionHelper
{
	@Transactional(value = TxType.REQUIRES_NEW)
	public <T> T executeInNewTransaction(Supplier<T> supplier)
	{
		return supplier.get();
	}

	@Transactional(value = TxType.REQUIRES_NEW)
	public void executeInNewTransaction(Runnable runnable)
	{
		runnable.run();
	}
}
