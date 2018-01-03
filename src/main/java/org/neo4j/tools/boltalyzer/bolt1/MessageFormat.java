package org.neo4j.tools.boltalyzer.bolt1;

import org.neo4j.bolt.v1.messaging.BoltIOException;
import org.neo4j.bolt.v1.messaging.MessageBoundaryHook;
import org.neo4j.bolt.v1.messaging.MessageHandler;
import org.neo4j.bolt.v1.messaging.Neo4jPack;
import org.neo4j.bolt.v1.messaging.message.Message;
import org.neo4j.bolt.v1.packstream.PackStream;
import org.neo4j.bolt.v1.runtime.spi.Record;
import org.neo4j.kernel.api.exceptions.Status;

import java.io.IOException;
import java.util.Map;

import static org.neo4j.bolt.v1.runtime.internal.Neo4jError.codeFromString;
import static org.neo4j.bolt.v1.runtime.spi.Records.record;

public class MessageFormat {
    public static final int VERSION = 1;

    public int version()
    {
        return VERSION;
    }

    public interface MessageTypes
    {
        byte MSG_INIT = 0x01;
        byte MSG_ACK_FAILURE = 0x0E;
        byte MSG_RESET = 0x0F;

        byte MSG_RUN = 0x10;
        byte MSG_DISCARD_ALL = 0x2F;
        byte MSG_PULL_ALL = 0x3F;

        byte MSG_RECORD = 0x71;
        byte MSG_SUCCESS = 0x70;
        byte MSG_IGNORED = 0x7E;
        byte MSG_FAILURE = 0x7F;
    }

    static String messageTypeName( int type )
    {
        switch( type )
        {
            case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_INIT:        return "MSG_INIT";
            case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_ACK_FAILURE: return "MSG_ACK_FAILURE";
            case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_RESET:       return "MSG_RESET";
            case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_RUN:         return "MSG_RUN";
            case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_DISCARD_ALL: return "MSG_DISCARD_ALL";
            case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_PULL_ALL:    return "MSG_PULL_ALL";
            case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_RECORD:      return "MSG_RECORD";
            case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_SUCCESS:     return "MSG_SUCCESS";
            case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_IGNORED:     return "MSG_IGNORED";
            case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_FAILURE:     return "MSG_FAILURE";
            default: return "0x" + Integer.toHexString(type);
        }
    }

    public static class Reader implements org.neo4j.bolt.v1.messaging.MessageFormat.Reader
    {
        private final Unpacker unpacker;

        public Reader( Unpacker unpacker )
        {
            this.unpacker = unpacker;
        }

        @Override
        public boolean hasNext() throws IOException
        {
            return unpacker.hasNext();
        }

        /**
         * Parse a single message into the given consumer.
         */
        @Override
        public <E extends Exception> void read( MessageHandler<E> output ) throws IOException, E
        {
            try
            {
                unpacker.unpackStructHeader();
                int type = (int) unpacker.unpackLong();

                try
                {
                    switch ( type )
                    {
                        case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_RUN:
                            unpackRunMessage( output );
                            break;
                        case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_DISCARD_ALL:
                            unpackDiscardAllMessage( output );
                            break;
                        case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_PULL_ALL:
                            unpackPullAllMessage( output );
                            break;
                        case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_RECORD:
                            unpackRecordMessage( output );
                            break;
                        case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_SUCCESS:
                            unpackSuccessMessage( output );
                            break;
                        case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_FAILURE:
                            unpackFailureMessage( output );
                            break;
                        case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_IGNORED:
                            unpackIgnoredMessage( output );
                            break;
                        case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_INIT:
                            unpackInitMessage( output );
                            break;
                        case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_RESET:
                            output.handleResetMessage();
                            break;
                        case org.neo4j.bolt.v1.messaging.PackStreamMessageFormatV1.MessageTypes.MSG_ACK_FAILURE:
                            output.handleAckFailureMessage();
                            break;
                        default:
                            throw new BoltIOException( Status.Request.Invalid,
                                    "0x" + Integer.toHexString(type) + " is not a valid message type." );
                    }
                }
                catch( PackStream.PackStreamException e )
                {
                    throw new BoltIOException( Status.Request.InvalidFormat,
                            "Unable to read " + messageTypeName (type) + " message. " +
                                    "Error was: " + e.getMessage(), e );
                }
            }
            catch( PackStream.PackStreamException e )
            {
                throw new BoltIOException( Status.Request.InvalidFormat, "Unable to read message type. " +
                        "Error was: " + e.getMessage(), e );
            }
        }

        private <E extends Exception> void unpackSuccessMessage( MessageHandler<E> output )
                throws E, IOException
        {
            Map<String,Object> map = unpacker.unpackMap();
            output.handleSuccessMessage( map );
        }

        private <E extends Exception> void unpackFailureMessage( MessageHandler<E> output )
                throws E, IOException
        {
            Map<String,Object> map = unpacker.unpackMap();

            String codeStr = map.containsKey( "code" ) ?
                    (String) map.get( "code" ) :
                    Status.General.UnknownError.name();

            String msg = map.containsKey( "message" ) ?
                    (String) map.get( "message" ) :
                    "<No message supplied>";

            output.handleFailureMessage( codeFromString( codeStr ), msg );
        }

        private <E extends Exception> void unpackIgnoredMessage( MessageHandler<E> output )
                throws E
        {
            output.handleIgnoredMessage();
        }

        private <E extends Exception> void unpackRecordMessage( MessageHandler<E> output )
                throws E, IOException
        {
            long length = unpacker.unpackListHeader();
            final Object[] fields = new Object[(int) length];
            for ( int i = 0; i < length; i++ )
            {
                fields[i] = unpacker.unpack();
            }
            output.handleRecordMessage( record( fields ) );
        }

        private <E extends Exception> void unpackRunMessage( MessageHandler<E> output )
                throws E, IOException
        {
            String statement = unpacker.unpackString();
            Map<String,Object> params = unpacker.unpackMap();
            output.handleRunMessage( statement, params );
        }

        private <E extends Exception> void unpackDiscardAllMessage( MessageHandler<E> output )
                throws E, IOException
        {
            output.handleDiscardAllMessage();
        }

        private <E extends Exception> void unpackPullAllMessage( MessageHandler<E> output )
                throws E, IOException
        {
            output.handlePullAllMessage();
        }

        private <E extends Exception> void unpackInitMessage( MessageHandler<E> output ) throws IOException, E
        {
            String clientName = unpacker.unpackString();
            Map<String,Object> credentials = unpacker.unpackMap();
            output.handleInitMessage( clientName, credentials );
        }

    }
}
