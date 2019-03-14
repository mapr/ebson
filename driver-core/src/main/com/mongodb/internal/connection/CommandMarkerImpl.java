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

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.binding.ClusterBinding;
import com.mongodb.connection.Cluster;
import com.mongodb.operation.CommandReadOperation;
import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.BsonDocument;
import org.bson.BsonElement;
import org.bson.RawBsonDocument;
import org.bson.codecs.RawBsonDocumentCodec;
import org.bson.io.BasicOutputBuffer;

import static java.util.Collections.singletonList;

public class CommandMarkerImpl implements CommandMarker {
    private final Cluster cluster;

    public CommandMarkerImpl(final Cluster cluster) {
        this.cluster = cluster;
    }

    @Override
    public BsonDocument mark(final String databaseName, final BsonDocument schema, final RawBsonDocument command) {
        BasicOutputBuffer buffer = new BasicOutputBuffer(command.getByteBuffer().remaining());

        ElementExtendingBsonWriter elementExtendingBsonWriter = new ElementExtendingBsonWriter(
                new BsonBinaryWriter(buffer), singletonList(new BsonElement("jsonSchema", schema)));

        BsonBinaryReader bsonBinaryReader = new BsonBinaryReader(command.getByteBuffer().asNIO());

        elementExtendingBsonWriter.pipe(bsonBinaryReader);

        RawBsonDocument markableCommand = new RawBsonDocument(buffer.getInternalBuffer(), 0, buffer.getSize());

        return new CommandReadOperation<RawBsonDocument>(databaseName, markableCommand, new RawBsonDocumentCodec())
                .execute(new ClusterBinding(cluster, ReadPreference.primary(), ReadConcern.DEFAULT));
    }
}
