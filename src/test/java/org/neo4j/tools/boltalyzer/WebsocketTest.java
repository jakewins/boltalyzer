package org.neo4j.tools.boltalyzer;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

public class WebsocketTest
{
    @Test
    public void shouldPrintLogForWebsocket() throws Exception
    {
        // Given
        String file = getClass().getClassLoader().getResource("boltalyzer/websocket.pcap").getFile();

        // When
        OutputCapture capturer = new OutputCapture();
        try(AutoCloseable ignore = capturer.capture(System.out))
        {
            Boltalyzer.main( "log", file );
        }

        // Then
        assert capturer.captured().contains(
            "0000000190\tsession-011\tClient\t{\"type\":\"INIT\"}\n" +
            "0000000764\tsession-011\tServer\t{\"type\":\"<HANDSHAKE RESPONSE>\"}\n" +
            "0000002275\tsession-011\tServer\t{\"meta\":{\"server\":\"Neo4j/3.3.3\"},\"type\":\"SUCCESS\"}\n" +
            "0000000065\tsession-011\tClient\t\n" +
            "0000012725\tsession-011\tClient\t{\"statement\":\"CALL db.indexes()\",\"type\":\"RUN\",\"params\":null}\n" +
            "{\"type\":\"PULL_ALL\"}\n" +
            "0000007864\tsession-011\tServer\t{\"meta\":{\"fields\":[\"description\",\"label\",\"properties\",\"state\",\"type\",\"provider\"],\"result_available_after\":4},\"type\":\"SUCCESS\"}\n" +
            "{\"meta\":{\"result_consumed_after\":0,\"type\":\"r\"},\"type\":\"SUCCESS\"}\n" +
            "0000007481\tsession-011\tClient\t{\"type\":\"RESET\"}\n" +
            "0000002338\tsession-011\tClient\t{\"statement\":\"CREATE ({hello:\\\"World\\\"})\",\"type\":\"RUN\",\"params\":null}\n" +
            "{\"type\":\"PULL_ALL\"}\n" + "0000000177\tsession-011\tServer\t{\"meta\":{},\"type\":\"SUCCESS\"}" );
    }

    static class OutputCapture {

        private ByteArrayOutputStream capture;

        public AutoCloseable capture(PrintStream target) {
            capture = new ByteArrayOutputStream();
            if(target == System.out) {
                PrintStream original = System.out;
                System.setOut( new PrintStream( capture ) );
                return () -> System.setOut( original );
            } else if( target == System.err ) {
                PrintStream original = System.err;
                System.setErr( new PrintStream( capture ) );
                return () -> System.setErr( original );
            } else {
                throw new RuntimeException( "Unknown" );
            }
        }

        public String captured() {
            try
            {
                return capture.toString("UTF-8");
            }
            catch ( UnsupportedEncodingException e )
            {
                throw new RuntimeException( e );
            }
        }
    }
}
