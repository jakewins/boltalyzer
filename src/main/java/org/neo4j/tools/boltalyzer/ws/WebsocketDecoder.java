package org.neo4j.tools.boltalyzer.ws;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Function;

/** Strips off websocket framing */
public class WebsocketDecoder implements Function<ByteBuffer,ByteBuffer>
{
    enum State
    {
        UNINITIALIZED
        {
            @Override
            public State handle( WebsocketDecoder ctx, ByteBuffer packet )
            {
                // WS opens with a regular HTTP exchange,
                // so discard that by reading until 0D0A 0D0A
                while(packet.remaining() > 0) {
                    int b = packet.get() & 0xff;
                    switch(ctx.endHeaderBytesSeen)
                    {
                    case 0:
                        if(b == 0x0D) {
                            ctx.endHeaderBytesSeen++;
                        } else {
                            ctx.endHeaderBytesSeen = 0;
                        }
                        break;
                    case 1:
                        if(b == 0x0A) {
                            ctx.endHeaderBytesSeen++;
                        } else {
                            ctx.endHeaderBytesSeen = 0;
                        }
                        break;
                    case 2:
                        if(b == 0x0D) {
                            ctx.endHeaderBytesSeen++;
                        } else {
                            ctx.endHeaderBytesSeen = 0;
                        }
                        break;
                    case 3:
                        if(b == 0x0A) {
                            return FIRST_BYTE;
                        } else {
                            ctx.endHeaderBytesSeen = 0;
                        }
                        break;
                    }
                }
                return UNINITIALIZED;
            }
        },

        FIRST_BYTE
                {
            @Override
            public State handle( WebsocketDecoder ctx, ByteBuffer packet )
            {
                int chunk;

                chunk = packet.get() & 0xff;
                boolean fin = (chunk & 0b10000000) != 0;
                int opcode = (chunk & 0b00001111);

//                if(opcode != 2 && opcode != 0 && opcode != 4 && opcode != 9 && opcode != 10) {
//                    throw new UnsupportedOperationException( "Boltalyzer can't handler websocket opcode: " + opcode );
//                }

                return SECOND_BYTE;
            }
        },

        SECOND_BYTE
        {
            @Override
            public State handle( WebsocketDecoder ctx, ByteBuffer packet )
            {
                int chunk;

                chunk = packet.get() & 0xff;
                ctx.masked = (chunk & 0b10000000) != 0;
                long payloadLen = (chunk & 0b01111111);

                if(payloadLen == 126) {
                    ctx.payloadLen = packet.getShort() & 0xffff;
                } else if(payloadLen == 127 ) {
                    ctx.payloadLen = packet.getLong();
                } else {
                    ctx.payloadLen = payloadLen;
                }

                if(ctx.payloadLen > Integer.MAX_VALUE) {
                    throw new UnsupportedOperationException( "Stream contains websocket frames larger than 1sGB, which is not yet supported by boltalyzer :(" );
                }

                if(ctx.masked) {
                    return MASK;
                }

                return PAYLOAD;
            }
        },

        MASK
        {
            @Override
            public State handle( WebsocketDecoder ctx, ByteBuffer packet )
            {
                if(packet.remaining() < 4) {
                    // Wait for more data
                    return MASK;
                }
                if(ctx.masked) {
                    packet.get(ctx.maskingKey);
                }

                return PAYLOAD;
            }
        },

        PAYLOAD
        {
            @Override
            public State handle( WebsocketDecoder ctx, ByteBuffer packet )
            {
                int toRead = Math.min( (int)ctx.payloadLen, packet.remaining() );
                byte[] payload = new byte[toRead];
                packet.get( payload );

                // Unmask
                if(ctx.masked) {
                    int j;
                    for ( int i = 0; i < payload.length; i++ )
                    {
                        j = i % 4;
                        payload[i] = (byte)(payload[i] ^ ctx.maskingKey[j]);
                    }
                }

                try
                {
                    ctx.outbound.write( payload );
                }
                catch ( IOException e )
                {
                    throw new RuntimeException( e );
                }

                if(toRead < ctx.payloadLen) {
                    ctx.payloadLen -= toRead;
                    return PAYLOAD;
                }

                return FIRST_BYTE;
            }
        },
        ;

        public abstract State handle( WebsocketDecoder ctx, ByteBuffer packet );
    }

    private State state = State.UNINITIALIZED;
    private byte[] savedPartial;
    private ByteArrayOutputStream outbound = new ByteArrayOutputStream();
    private int endHeaderBytesSeen = 0;
    private long payloadLen = 0;
    private boolean masked;
    private byte[] maskingKey = new byte[4];

    @Override
    public ByteBuffer apply( ByteBuffer packet )
    {
        if(savedPartial != null) {
            ByteBuffer combinedPacket = ByteBuffer.allocate( savedPartial.length + packet.remaining() );
            combinedPacket.put( savedPartial );
            combinedPacket.put( packet );
            combinedPacket.position( 0 );
            packet = combinedPacket;
            savedPartial = null;
        }
        while(packet.remaining() > 0) {
            int start = packet.position();
            state = state.handle( this, packet );
            if(start == packet.position()) {
                // Nothing read, need more data to make progress
                savedPartial = new byte[packet.remaining()];
                break;
            }
        }

        ByteBuffer wrap = ByteBuffer.wrap( outbound.toByteArray() );
        outbound.reset();
        return wrap;
    }
}
