/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.tools.boltalyzer;

import org.neo4j.kernel.api.exceptions.Status;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.neo4j.tools.boltalyzer.Field.field;

public class Fields
{
    public static final Field<Long> timestamp = field( "ts" );
    public static final Field<String> timeString = field( "tss" );
    public static final Field<InetAddress> src = field( "src" );
    public static final Field<Integer> srcPort = field( "srcPort" );
    public static final Field<InetAddress> dst = field( "dst" );
    public static final Field<Integer> dstPort = field( "dstPort" );
    public static final Field<ByteBuffer> payload = field( "raw" );
    public static final Field<String> connectionKey = field( "connectionKey" );
    public static final Field<List<Dict>> description = field( "description" );
    public static final Field<AnalyzedSession> session = field( "session" );
    public static final Field<String> logicalSource = field( "logicalSource" );

    public static class Message {
        public static final Field<String> type = field("type");
        public static final Field<String> statement = field("statement");
        public static final Field<Map<String, Object>> params = field("params");
        public static final Field<Object[]> fields = field("fields");
        public static final Field<Map<String, Object>> metadata = field("meta");
        public static final Field<Status> status = field("status");
        public static final Field<String> message = field("message");
    }
}
