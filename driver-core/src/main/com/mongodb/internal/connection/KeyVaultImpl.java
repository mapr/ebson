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

import com.mongodb.MongoNamespace;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.binding.ClusterBinding;
import com.mongodb.connection.Cluster;
import com.mongodb.operation.BatchCursor;
import com.mongodb.operation.FindOperation;
import org.bson.BsonDocument;
import org.bson.codecs.BsonDocumentCodec;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class KeyVaultImpl implements KeyVault {
    private final Cluster cluster;
    private final MongoNamespace namespace;

    KeyVaultImpl(final Cluster cluster, final MongoNamespace namespace) {
        this.cluster = cluster;
        this.namespace = namespace;
    }

    @Override
    public Iterator<BsonDocument> find(final BsonDocument keyFilter) {
        BatchCursor<BsonDocument> batchCursor = new FindOperation<BsonDocument>(namespace, new BsonDocumentCodec())
                .filter(keyFilter)
                .execute(new ClusterBinding(cluster, ReadPreference.primaryPreferred(), ReadConcern.DEFAULT));

        List<BsonDocument> keys = new ArrayList<BsonDocument>();
        try {
            while (batchCursor.hasNext()) {
                keys.addAll(batchCursor.next());
            }
        } finally {
            batchCursor.close();
        }

        return keys.iterator();
    }
}
