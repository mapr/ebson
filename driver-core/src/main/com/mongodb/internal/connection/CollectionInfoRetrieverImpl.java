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

import com.mongodb.ReadPreference;
import com.mongodb.binding.ClusterBinding;
import com.mongodb.connection.Cluster;
import com.mongodb.operation.BatchCursor;
import com.mongodb.operation.ListCollectionsOperation;
import org.bson.BsonDocument;
import org.bson.codecs.BsonDocumentCodec;

// TODO: should this even be done in core?  It means, for example, no session support
public class CollectionInfoRetrieverImpl implements CollectionInfoRetriever {

    private volatile Cluster cluster;

    public CollectionInfoRetrieverImpl() {
    }

    @Override
    public BsonDocument filter(final String databaseName, final BsonDocument filter) {
        BatchCursor<BsonDocument> batchCursor = new ListCollectionsOperation<BsonDocument>(databaseName, new BsonDocumentCodec())
                .filter(filter)
                .execute(new ClusterBinding(cluster, ReadPreference.primaryPreferred()));

        try {
            if (!batchCursor.hasNext()) {
                return null;
            }

            return batchCursor.next().get(0);
        } finally {
            batchCursor.close();
        }
    }

    @Override
    public void init(final Cluster cluster) {
        this.cluster = cluster;
    }
}
