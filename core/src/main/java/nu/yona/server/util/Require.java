/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.util;

import java.util.Optional;
import java.util.function.Supplier;

import nu.yona.server.exceptions.YonaException;

public class Require
{
	private Require()
	{
		// No instances
	}

	public static <E> void isNonNull(E object, Supplier<YonaException> exceptionSupplier)
	{
		if (object == null)
		{
			throw exceptionSupplier.get();
		}
	}

	public static <E> void isNull(E object, Supplier<YonaException> exceptionSupplier)
	{
		if (object != null)
		{
			throw exceptionSupplier.get();
		}
	}

	public static void that(boolean flag, Supplier<YonaException> exceptionSupplier)
	{
		if (!flag)
		{
			throw exceptionSupplier.get();
		}
	}

	public static <E> void isPresent(Optional<E> optional, Supplier<YonaException> exceptionSupplier)
	{
		if (!optional.isPresent())
		{
			throw exceptionSupplier.get();
		}
	}

	public static <E> void isNotPresent(Optional<E> optional, Supplier<YonaException> exceptionSupplier)
	{
		if (optional.isPresent())
		{
			throw exceptionSupplier.get();
		}
	}
}
