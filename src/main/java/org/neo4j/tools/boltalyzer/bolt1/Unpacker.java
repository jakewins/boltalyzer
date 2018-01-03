package org.neo4j.tools.boltalyzer.bolt1;

import org.neo4j.bolt.v1.messaging.BoltIOException;
import org.neo4j.bolt.v1.messaging.Neo4jPack;
import org.neo4j.bolt.v1.messaging.PathPack;
import org.neo4j.bolt.v1.messaging.infrastructure.ValueNode;
import org.neo4j.bolt.v1.messaging.infrastructure.ValuePath;
import org.neo4j.bolt.v1.messaging.infrastructure.ValueRelationship;
import org.neo4j.bolt.v1.messaging.infrastructure.ValueUnboundRelationship;
import org.neo4j.bolt.v1.packstream.PackInput;
import org.neo4j.bolt.v1.packstream.PackStream;
import org.neo4j.bolt.v1.packstream.PackType;
import org.neo4j.kernel.api.exceptions.Status;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.neo4j.bolt.v1.messaging.Neo4jPack.*;
import static org.neo4j.bolt.v1.packstream.PackStream.UNKNOWN_SIZE;

public class Unpacker extends Neo4jPack.Unpacker {
    private PathPack.Unpacker pathUnpacker = new PathPack.Unpacker();

    public Unpacker( PackInput input )
    {
        super( input );
    }

    @Override
    public Object unpack() throws IOException
    {
        PackType valType = peekNextType();
        switch ( valType )
        {
            case STRING:
                return unpackString();
            case INTEGER:
                return unpackLong();
            case FLOAT:
                return unpackDouble();
            case BOOLEAN:
                return unpackBoolean();
            case NULL:
                // still need to move past the null value
                unpackNull();
                return null;
            case LIST:
            {
                return unpackList();
            }
            case MAP:
            {
                return unpackMap();
            }
            case STRUCT:
            {
                unpackStructHeader();
                char signature = unpackStructSignature();
                switch ( signature )
                {
                    case NODE:
                    {
                        return ValueNode.unpackFields(this);
                    }
                    case RELATIONSHIP:
                    {
                        return ValueRelationship.unpackFields(this);
                    }
                    case UNBOUND_RELATIONSHIP:
                    {
                        return ValueUnboundRelationship.unpackFields(this);
                    }
                    case PATH:
                    {
                        return new PathPack.Unpacker().unpackFields(this);
                    }
                    default:
                        throw new BoltIOException( Status.Request.InvalidFormat,
                                "Unknown struct type: " + Integer.toHexString( signature ) );
                }
            }
            case END_OF_STREAM:
            {
                unpackEndOfStream();
                return null;
            }
            default:
                throw new BoltIOException( Status.Request.InvalidFormat,
                        "Unknown value type: " + valType );
        }
    }

    public List<Object> unpackList() throws IOException
    {
        int size = (int) unpackListHeader();
        if ( size == 0 )
        {
            return EMPTY_LIST;
        }
        ArrayList<Object> list;
        if ( size == UNKNOWN_SIZE )
        {
            list = new ArrayList<>();
            boolean more = true;
            while ( more )
            {
                PackType keyType = peekNextType();
                switch ( keyType )
                {
                    case END_OF_STREAM:
                        unpack();
                        more = false;
                        break;
                    default:
                        list.add( unpack() );
                }
            }
        }
        else
        {
            list = new ArrayList<>( size );
            for ( int i = 0; i < size; i++ )
            {
                list.add( unpack() );
            }
        }
        return list;
    }

    public Map<String, Object> unpackMap() throws IOException
    {
        int size = (int) unpackMapHeader();
        if ( size == 0 )
        {
            return EMPTY_MAP;
        }
        Map<String, Object> map;
        if ( size == UNKNOWN_SIZE ) {
            map = new HashMap<>();
            boolean more = true;
            while ( more )
            {
                PackType keyType = peekNextType();
                String key;
                Object val;
                switch ( keyType )
                {
                    case END_OF_STREAM:
                        unpack();
                        more = false;
                        break;
                    case STRING:
                        key = unpackString();
                        val = unpack();
                        if( map.put( key, val ) != null )
                        {
                            throw new BoltIOException( Status.Request.Invalid, "Duplicate map key `" + key + "`." );
                        }
                        break;
                    case NULL:
                        throw new BoltIOException( Status.Request.Invalid, "Value `null` is not supported as key in maps, must be a non-nullable string." );
                    default:
                        throw new PackStream.PackStreamException( "Bad key type" );
                }
            }
        }
        else
        {
            map = new HashMap<>( size, 1 );
            for ( int i = 0; i < size; i++ )
            {
                PackType type = peekNextType();
                String key;
                switch ( type )
                {
                    case NULL:
                        throw new BoltIOException( Status.Request.Invalid, "Value `null` is not supported as key in maps, must be a non-nullable string." );
                    case STRING:
                        key = unpackString();
                        break;
                    default:
                        throw new PackStream.PackStreamException( "Bad key type: " + type );
                }

                Object val = unpack();
                if( map.put( key, val ) != null )
                {
                    throw new BoltIOException( Status.Request.Invalid, "Duplicate map key `" + key + "`." );
                }
            }
        }
        return map;
    }
}
