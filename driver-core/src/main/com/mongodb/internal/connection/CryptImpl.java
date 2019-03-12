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

import com.mongodb.MongoException;
import com.mongodb.MongoInternalException;
import com.mongodb.MongoNamespace;
import com.mongodb.crypt.capi.MongoCrypt;
import com.mongodb.crypt.capi.MongoDecryptor;
import com.mongodb.crypt.capi.MongoEncryptor;
import com.mongodb.crypt.capi.MongoKeyBroker;
import com.mongodb.crypt.capi.MongoKeyDecryptor;
import org.bson.BsonDocument;
import org.bson.RawBsonDocument;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;

import static com.mongodb.assertions.Assertions.notNull;

class CryptImpl implements Crypt {

    private final MongoCrypt mongoCrypt;
    private final SchemaRetriever schemaRetriever;
    private final CommandMarker commandMarker;
    private final KeyVault keyVault;
    private final KeyManagementService keyManagementService;

    CryptImpl(final MongoCrypt mongoCrypt, final SchemaRetriever schemaRetriever, final CommandMarker commandMarker,
              final KeyVault keyVault, final KeyManagementService keyManagementService) {
        this.mongoCrypt = mongoCrypt;
        this.schemaRetriever = schemaRetriever;
        this.commandMarker = commandMarker;
        this.keyVault = keyVault;
        this.keyManagementService = keyManagementService;
    }

    @Override
    public RawBsonDocument encrypt(final String databaseName, final RawBsonDocument command) {
        notNull("databaseName", databaseName);
        notNull("command", command);

        MongoNamespace namespace = getNamespace(databaseName, command);
        MongoEncryptor encryptor = mongoCrypt.createEncryptor();
        MongoEncryptor.State state = encryptor.getState();

        try {
            while (true) {
                switch (state) {
                    case NEED_NS:
                        encryptor.addNamespace(namespace.getFullName());
                        break;
                    case NEED_SCHEMA:
                        BsonDocument schema = schemaRetriever.getSchema(namespace);
                        encryptor.addCollectionInfo(schema);  // TODO: resolve issue with schema v. collectionInfo
                        break;
                    case NEED_MARKINGS:
                        commandMarker.mark(encryptor.getSchema(), command);
                        break;
                    case NEED_KEYS:
                        MongoKeyBroker keyBroker = encryptor.getKeyBroker();
                        fetchKeys(keyBroker);
                        decryptKeys(keyBroker);
                        encryptor.keyBrokerDone();
                        break;
                    case NEED_ENCRYPTION:
                        encryptor.encrypt();
                        break;
                    case NO_ENCRYPTION_NEEDED:
                        return command;
                    case ENCRYPTED:
                        return (RawBsonDocument) encryptor.getEncryptedCommand(); // TODO: change type
                    default:
                        throw new MongoInternalException("Unsupported encryptor state + " + state);
                }
            }
        } finally {
            encryptor.close();
        }
    }

    @Override
    public RawBsonDocument decrypt(final RawBsonDocument commandResponse) {
        notNull("commandResponse", commandResponse);

        MongoDecryptor decryptor = mongoCrypt.createDecryptor();
        MongoDecryptor.State state = decryptor.getState();

        try {
            while (true) {
                switch (state) {
                    case NEED_DOC:
                        decryptor.addDocument(commandResponse);
                        break;
                    case NEED_KEYS:
                        MongoKeyBroker keyBroker = decryptor.getKeyBroker();
                        fetchKeys(keyBroker);
                        decryptKeys(keyBroker);
                        decryptor.keyBrokerDone();
                        break;
                    case NEED_DECRYPTION:
                        decryptor.decrypt();
                        break;
                    case NO_DECRYPTION_NEEDED:
                        return commandResponse;
                    case DECRYPTED:
                        return (RawBsonDocument) decryptor.getDecrypted(); //TODO: remove cast
                    default:
                        throw new MongoInternalException("Unsupported decryptor state + " + state);
                }
            }
        } finally {
            decryptor.close();
        }
    }

    private void fetchKeys(final MongoKeyBroker keyBroker) {
        Iterator<BsonDocument> iterator = keyVault.find(keyBroker.getKeyFilter());
        while (iterator.hasNext()) {
            keyBroker.addKey(iterator.next());
        }
        keyBroker.doneAddingKeys();
    }

    private void decryptKeys(final MongoKeyBroker keyBroker) {
        MongoKeyDecryptor keyDecryptor = keyBroker.nextDecryptor();
        while (keyDecryptor != null) {
            decryptKey(keyDecryptor);
            keyBroker.addDecryptedKey(keyDecryptor);
            keyDecryptor = keyBroker.nextDecryptor();
        }
    }

    private void decryptKey(final MongoKeyDecryptor keyDecryptor) {
        try {
            InputStream inputStream = keyManagementService.stream(keyDecryptor.getMessage());
            byte[] bytes = new byte[4096];

            int bytesNeeded = keyDecryptor.bytesNeeded(bytes.length);

            while (bytesNeeded > 0) {
                int bytesRead = inputStream.read(bytes, 0, bytesNeeded);
                keyDecryptor.feed(ByteBuffer.wrap(bytes, 0, bytesRead));
                bytesNeeded = keyDecryptor.bytesNeeded(bytes.length);
            }
        } catch (IOException e) {
            throw new MongoException("Exception decrypting key", e);  // TODO: type
        }
    }

    private MongoNamespace getNamespace(final String databaseName, final RawBsonDocument command) {
        return new MongoNamespace(databaseName, command.getString(command.getFirstKey()).getValue());
    }
}
