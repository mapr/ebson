/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.mongodb.internal.connection;

import org.bson.RawBsonDocument;

import java.io.Closeable;

/**
 * This class is NOT part of the public API.
 */
interface Crypt extends Closeable {
    /**
     * Encrypt the given command
     *
     * @param databaseName the database name
     * @param command      the uncrypted command
     * @return the encyrpted command
     */
    RawBsonDocument encrypt(String databaseName, RawBsonDocument command);

    /**
     * Decrypt the given command response
     *
     * @param commandResponse the encrypted command response
     * @return the decrypted command response
     */
    RawBsonDocument decrypt(RawBsonDocument commandResponse);

    @Override
    public void close();
}
