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
                            return FRAME;
                        } else {
                            ctx.endHeaderBytesSeen = 0;
                        }
                        break;
                    }
                }
                return UNINITIALIZED;
            }
        },

        FRAME {
            @Override
            public State handle( WebsocketDecoder ctx, ByteBuffer packet )
            {
                int chunk;

                chunk = packet.get() & 0xff;
                boolean fin = (chunk & 0b10000000) != 0;
                int opcode = (chunk & 0b00001111);

                chunk = packet.get() & 0xff;
                boolean masked = (chunk & 0b10000000) != 0;
                long payloadLen = (chunk & 0b01111111);

                if(payloadLen == 126) {
                    payloadLen = packet.getShort() & 0xffff;
                } else if(payloadLen == 127 ) {
                    payloadLen = packet.getLong();
                }

                byte[] maskingKey = new byte[4];
                if(masked) {
                    packet.get(maskingKey);
                }

                if(!fin) {
                    throw new UnsupportedOperationException( "Stream contains fragmented websocket frames, which is not yet supported by boltalyzer." );
                }

                if(payloadLen > Integer.MAX_VALUE) {
                    throw new UnsupportedOperationException( "Stream contains websocket frames larger than 1sGB, which is not yet supported by boltalyzer :(" );
                }

                byte[] payload = new byte[(int)payloadLen];
                packet.get( payload );

                // Unmask
                if(masked) {
                    int j;
                    for ( int i = 0; i < payload.length; i++ )
                    {
                        j = i % 4;
                        payload[i] = (byte)(payload[i] ^ maskingKey[j]);
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

                return FRAME;
            }
        }
        ;

        public abstract State handle( WebsocketDecoder ctx, ByteBuffer packet );
    }

    private State state = State.UNINITIALIZED;
    private ByteArrayOutputStream fragments = new ByteArrayOutputStream();
    private ByteArrayOutputStream outbound = new ByteArrayOutputStream();
    private int endHeaderBytesSeen = 0;

    @Override
    public ByteBuffer apply( ByteBuffer packet )
    {
        while(packet.remaining() > 0) {
            state = state.handle( this, packet );
        }

        ByteBuffer wrap = ByteBuffer.wrap( outbound.toByteArray() );
        outbound.reset();
        return wrap;
    }
}
