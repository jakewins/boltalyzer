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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.neo4j.bolt.v1.messaging.MessageHandler;
import org.neo4j.bolt.v1.runtime.spi.Record;
import org.neo4j.kernel.api.exceptions.Status;

import static org.neo4j.tools.boltalyzer.Dict.dict;

public class BoltMessageDescriber implements MessageHandler<RuntimeException>
{
    public static final String MSG_RECORD = "RECORD";
    private final List<Dict> messages = new ArrayList<>();

    @Override
    public void handleRunMessage( String statement, Map<String,Object> params ) throws RuntimeException
    {
        messages.add( dict(
                Fields.Message.type, "RUN",
                Fields.Message.statement, statement,
                Fields.Message.params, params ));
    }

    @Override
    public void handlePullAllMessage() throws RuntimeException
    {
        messages.add( dict( Fields.Message.type, "PULL_ALL" ) );
    }

    @Override
    public void handleDiscardAllMessage() throws RuntimeException
    {
        messages.add( dict( Fields.Message.type, "DISCARD_ALL" ) );
    }

    @Override
    public void handleResetMessage() throws RuntimeException
    {
        messages.add( dict( Fields.Message.type, "RESET" ) );
    }

    @Override
    public void handleAckFailureMessage() throws RuntimeException
    {
        messages.add( dict( Fields.Message.type, "ACK_FAILURE" ) );
    }

    @Override
    public void handleRecordMessage( Record item ) throws RuntimeException
    {
        messages.add( dict( Fields.Message.type, MSG_RECORD,
                Fields.Message.fields, item.fields() ));
    }

    @Override
    public void handleSuccessMessage( Map<String,Object> metadata ) throws RuntimeException
    {
        messages.add( dict( Fields.Message.type, "SUCCESS", Fields.Message.metadata, metadata) );
    }

    @Override
    public void handleFailureMessage( Status status, String message ) throws RuntimeException
    {
        messages.add( dict(
                Fields.Message.type, "FAILURE",
                Fields.Message.status, status,
                Fields.Message.message, message) );
    }

    @Override
    public void handleIgnoredMessage() throws RuntimeException
    {
        messages.add( dict( Fields.Message.type, "IGNORED" ) );
    }

    @Override
    public void handleInitMessage( String clientName, Map<String,Object> credentials ) throws RuntimeException
    {
        messages.add( dict( Fields.Message.type, "INIT" ) );
    }

    /** Return a description of all messages received since last time this method was called */
    public List<Dict> flushDescription()
    {
        List<Dict> out = new LinkedList<>();
        out.addAll(messages);
        messages.clear();
        return out;
    }
}