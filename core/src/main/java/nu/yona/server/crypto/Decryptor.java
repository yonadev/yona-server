/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import java.util.Set;
import java.util.UUID;

public interface Decryptor
{
    byte[] decrypt(byte[] ciphertext);

    String decryptString(byte[] ciphertext);

    UUID decryptUUID(byte[] ciphertext);

    long decryptLong(byte[] ciphertext);

    Set<UUID> decryptUUIDSet(byte[] ciphertext);
}
