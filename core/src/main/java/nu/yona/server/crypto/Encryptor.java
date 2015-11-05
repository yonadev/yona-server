/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import java.util.Set;
import java.util.UUID;

public interface Encryptor {
	byte[] encrypt(byte[] plaintext);

	byte[] encrypt(String plaintext);

	byte[] encrypt(UUID plaintext);

	byte[] encrypt(long plaintext);

	byte[] encrypt(Set<UUID> plaintext);

}
