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


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.neo4j.tools.boltalyzer.bolt1.Dechunker;

import static org.neo4j.tools.boltalyzer.Dict.dict;

/**
 * Stateful object tracking an ongoing session, able to decode transport transmissions as they arrive and describe them in a helpful way.
 */
public class AnalyzedSession
{
    private final String name;
    private final long id;
    private final BoltMessageDescriber clientStreamDescriber;
    private final BoltMessageDescriber serverStreamDescriber;

    private final Dechunker clientStream;
    private final Dechunker serverStream;

    private int clientHandshakeRemaining = 16;
    private int serverHandshakeRemaining = 4;

    public AnalyzedSession(String name, long id)
    {
        this.name = name;
        this.id = id;
        this.clientStreamDescriber = new BoltMessageDescriber();
        this.serverStreamDescriber = new BoltMessageDescriber();
        this.clientStream = new Dechunker( clientStreamDescriber, () -> {} );
        this.serverStream = new Dechunker( serverStreamDescriber, () -> {} );
    }

    public String name()
    {
        return name;
    }

    public List<Dict> describeServerPayload( ByteBuffer payload ) throws IOException
    {
        ByteBuf data = Unpooled.wrappedBuffer( payload );
        LinkedList<Dict> out = new LinkedList<>();

        // TODO: Something more sophisticated than this
        if(serverHandshakeRemaining > 0 && data.readableBytes() > 0)
        {
            int toRead = Math.min( data.readableBytes(), serverHandshakeRemaining );
            serverHandshakeRemaining -= toRead;
            data.skipBytes( toRead );
            out.add( dict( Fields.Message.type, "<HANDSHAKE RESPONSE>" ) );
        }

        serverStream.handle( data );
        out.addAll(serverStreamDescriber.flushDescription());
        return out;
    }

    public List<Dict> describeClientPayload(ByteBuffer payload ) throws IOException
    {
        ByteBuf data = Unpooled.wrappedBuffer( payload );
        LinkedList<Dict> out = new LinkedList<>();

        if(clientHandshakeRemaining > 0 && data.readableBytes() > 0)
        {
            int toRead = Math.min( data.readableBytes(), clientHandshakeRemaining );
            clientHandshakeRemaining -= toRead;
            data.skipBytes( toRead );
            out.add( dict( Fields.Message.type, "<HANDSHAKE>" ) );
        }

        clientStream.handle( data );
        out.addAll(clientStreamDescriber.flushDescription());
        return out;
    }

    public long id()
    {
        return id;
    }
}