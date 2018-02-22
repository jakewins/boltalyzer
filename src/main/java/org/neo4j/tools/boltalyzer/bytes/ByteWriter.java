package org.neo4j.tools.boltalyzer.bytes;

import java.nio.ByteBuffer;

public class ByteWriter
{
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        return bytesToHex( ByteBuffer.wrap( bytes ) );
    }
    public static String bytesToHex(ByteBuffer bytes) {
        char[] hexChars = new char[bytes.remaining() * 2];
        for ( int j = 0; j < bytes.remaining(); j++ ) {
            int v = bytes.get(j) & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
