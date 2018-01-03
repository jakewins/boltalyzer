package org.neo4j.tools.boltalyzer.bolt1;

import io.netty.buffer.ByteBuf;
import org.neo4j.bolt.v1.messaging.MessageHandler;
import org.neo4j.bolt.v1.transport.ChunkedInput;

import java.io.IOException;

public class Dechunker {
    private final ChunkedInput input;
    private final MessageFormat.Reader unpacker;
    private final MessageHandler<RuntimeException> onMessage;
    private final Runnable onMessageStarted;

    public enum State
    {
        AWAITING_CHUNK,
        IN_CHUNK,
        IN_HEADER,
        CLOSED
    }

    private State state = State.AWAITING_CHUNK;
    private int chunkSize = 0;

    public Dechunker( MessageHandler<RuntimeException> messageHandler, Runnable onMessageStarted )
    {
        this.onMessage = messageHandler;
        this.onMessageStarted = onMessageStarted;
        this.input = new ChunkedInput();
        this.unpacker = new MessageFormat.Reader( new Unpacker( input ) );
    }

    public void handle( ByteBuf data ) throws IOException
    {
        while ( data.readableBytes() > 0 )
        {
            switch ( state )
            {
                case AWAITING_CHUNK:
                {
                    if ( data.readableBytes() >= 2 )
                    {
                        // Whole header available, read that
                        chunkSize = data.readUnsignedShort();
                        handleHeader();
                    }
                    else
                    {
                        // Only one byte available, read that and wait for the second byte
                        chunkSize = data.readUnsignedByte() << 8;
                        state = State.IN_HEADER;
                    }
                    break;
                }
                case IN_HEADER:
                {
                    // First header byte read, now we read the next one
                    chunkSize = chunkSize | data.readUnsignedByte();
                    handleHeader();
                    break;
                }
                case IN_CHUNK:
                {
                    if ( chunkSize < data.readableBytes() )
                    {
                        // Current packet is larger than current chunk, slice of the chunk
                        input.append( data.readSlice( chunkSize ) );
                        state = State.AWAITING_CHUNK;
                    }
                    else if ( chunkSize == data.readableBytes() )
                    {
                        // Current packet perfectly maps to current chunk
                        input.append( data );
                        state = State.AWAITING_CHUNK;
                        return;
                    }
                    else
                    {
                        // Current packet is smaller than the chunk we're reading, split the current chunk itself up
                        chunkSize -= data.readableBytes();
                        input.append( data );
                        return;
                    }
                    break;
                }
                case CLOSED:
                {
                    // No-op
                    return;
                }
            }
        }
    }

    private void handleHeader() throws IOException
    {
        if(chunkSize == 0)
        {
            // Message boundary
            try
            {
                onMessageStarted.run();
                unpacker.read( onMessage );
            }
            finally
            {
                input.clear();
            }
            state = State.AWAITING_CHUNK;
        }
        else
        {
            state = State.IN_CHUNK;
        }
    }

}
