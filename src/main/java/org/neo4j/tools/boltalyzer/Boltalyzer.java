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


import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.helpers.Args;
import org.neo4j.helpers.collection.Pair;
import org.neo4j.tools.boltalyzer.serialize.Bolt2JSON;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.neo4j.helpers.collection.Pair.pair;
import static org.neo4j.tools.boltalyzer.Dict.dict;
import static org.neo4j.tools.boltalyzer.Fields.*;
import static org.neo4j.tools.boltalyzer.TimeMapper.modeForName;
import static org.neo4j.tools.boltalyzer.TimeMapper.unitForName;

public class Boltalyzer
{
    public static void main(String ... argv) throws Exception
    {
        Args args = Args.parse( argv );
        if ( argv.length == 0 || argv[0].equals( "-h" ) || argv[0].equals( "--help" ) || args.orphans().size() != 2 )
        {
            System.out.println(
                    "Usage: boltalyzer [--timemode <mode>] [--timeunit <unit>]\n" +
                    "                  [--serverport <port>] [--session <session id>]\n" +
                    "                  [--skip <n messages>]\n" +
                    "                  <command> <TCPDUMP_FILE>\n" +
                    "\n" +
                    "Commands:\n" +
                    "\n" +
                    "  boltalyzer [options] log <TCPDUMP_FILE>\n" +
                    "\n" +
                    "      Output a play-by-play of the Bolt traffic in TCPDUMP_FILE.\n" +
                    "\n" +
                    "  boltalyzer [options] replay <TCPDUMP_FILE> --target bolt://neo4j:neo4j@localhost:7687\n" +
                    "\n" +
                    "      Replay the traffic in TCPDUMP_FILE against the specified target.\n" +
                    "\n" +
                    "  boltalyzer [options] export <TCPDUMP_FILE> [--target path/to/export/to]\n" +
                    "\n" +
                    "      Write each query and its parameters to a dedicated JSON file,\n" +
                    "      prefixed by the time it was executed\n" +
                    "\n" +
                    "Options\n" +
                    "  --timemode [epoch | global-incremental | session-delta | iso8601]  (default: session-delta)\n" +
                    "  --timeunit [us | ms]  (default: us)\n" +
                    "  --serverport <port>  (default: 7687)\n" +
                    "  --session [<n> | all]  \n" +
                    "      Filter which sessions to show, session id is incrementally determined in order of sessions appearing in the data dump.  (default: all)\n" +
                    "  --skip <n>  Skip n packets before starting output    (default: 0)\n" +
                    "  -h  Print this message\n" +
                    "\n"
            );
            System.exit( 0 );
        }

        try (FileInputStream pcap = new FileInputStream(args.orphans().get(1)) )
        {
            // Implementation note: since this is in a sideline tool, I'm using it to play a bit.
            // The approach here uses maps instead of classes (a la http://confreaks.tv/videos/railsconf2012-keynote-simplicity-matters )
            // These maps are passed through the pipeline defined below, and are mutated as they pass through it.
            //
            // The idea here being that we will want a set of different pipelines for analyzing the stream from different perspectives.
            // Rather than having a dozen or so in-between-state-classes with specialized functions acting on them, we have generalized
            // functions acting on maps. This should put us in a situation where we can construct different pipelines from a common set of
            // pipeline functions.

            try(ClosableConsumer<Dict> mode = command(args)) {
                new PCAPParser()

                        // So, parse out a stream of packets from the pcap file
                        .parse(pcap)

                        // Modify the timestamps on those packets to fit the users chosen time mode
                        .map(modeForName(args.get("timemode", "session-delta")))

                        // And convert the timestamps to whatever units the user wants
                        .map(unitForName(args.get("timeunit", "us")))

                        // Decorate each packet with semantic information about what the actual bolt messages were,
                        // what the logical session and logical source of the message was
                        .map(new AddBoltDescription(
                                args.getNumber("serverport", 7687).intValue()))

                        // Now we can skip things (currently the step above needs to see all packets to maintain message framing alignment, so
                        // we can't skip until after the step above)
                        .skip(args.getNumber("skip", 0).intValue())

                        // Filter out to only look sessions the user cares about
                        .filter(sessionFilter(args.get("session", "all")))

                        // Do the thing the user asked for
                        .forEach(mode);
            }
        }
    }

    private static ClosableConsumer<Dict> command(Args args) throws IOException {
        String command = args.orphans().get(0);
        if(command.equalsIgnoreCase("querydump")) {
            return queryDumper(args.get("dir", "dump"));
        }
        if(command.equalsIgnoreCase("log")) {
            Function<Dict, String> describe = describer();
            return (p) -> System.out.println(describe.apply(p));
        }
        if(command.equalsIgnoreCase("replay")) {
            if(!args.has("target")) {
                System.err.println("Must specify --target to replay; eg. '--target bolt://neo4j:neo4j@localhost:7687'");
                System.exit(1);
            }
            return replay(args.get("target"));
        }

        System.err.println("Unknown command: " + command);
        System.exit(1);
        return null;
    }

    private static Function<Dict, String> describer() {
        return (p) -> {
            String messages = p.get(description, emptyList()).stream().map(m -> {
                try {
                    return Bolt2JSON.mapper().writeValueAsString(m); // todo
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.joining("\n"));

            return String.format("%s\t%s\t%s\t%s",
                    p.get(timeString),
                    p.get(session).name(),
                    p.get(logicalSource),
                    messages);
        };
    }

    private static ClosableConsumer<Dict> queryDumper(String path) throws IOException {
        if(path.equals("")) {
            return d -> {};
        }

        Map<String, AtomicLong> queryIds = new HashMap<>();
        Path out = Paths.get(path);
        Files.createDirectories(out);
        return p -> {
            p.get(Fields.description, emptyList())
                    .forEach(m -> {
                        if(m.get(Message.type).equals("RUN")) {
                            String session = p.get(Fields.session).name();
                            Path qpath = out.resolve(String.format("%s-%s-Q%d.json", p.get(timeString), session,
                                    queryIds.computeIfAbsent(session, s -> new AtomicLong()).getAndIncrement()));
                            try {
                                Bolt2JSON.mapper().writeValue(qpath.toFile(), dict(
                                    "statement", m.get(Message.statement),
                                    "params", m.get(Message.params),
                                    "time", p.get(Fields.timestamp)
                                ));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
        };
    }

    interface ClosableConsumer<T> extends AutoCloseable, Consumer<T> {
        @Override
        default void close() throws Exception {}
    }

    private static ClosableConsumer<Dict> replay(String connectionString) throws IOException
    {
        System.out.println("Replaying against " + connectionString);

        Driver driver = GraphDatabase.driver(connectionString);
        Function<String, Pair<Session, ExecutorService>> newSession = (s) -> pair(driver.session(), newSingleThreadExecutor());
        Map<String, Pair<Session, ExecutorService>> sessions = new HashMap<>();

        long replayStartUs = System.nanoTime() / 1000;
        AtomicLong streamStartTimeUs = new AtomicLong(-1);

        return new ClosableConsumer<Dict>() {
            @Override
            public void close() throws Exception {
                sessions.values().forEach(p -> {
                    ExecutorService other = p.other();
                    other.shutdown();
                    try {
                        other.awaitTermination(1, TimeUnit.MINUTES);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override
            public void accept(Dict p) {
                if(streamStartTimeUs.get() == -1)
                {
                    streamStartTimeUs.set(p.get(Fields.timestamp));
                }
                p.get(Fields.description, emptyList())
                        .forEach(m -> {
                            if(m.get(Message.type).equals("RUN")) {
                                String sessionName = p.get(Fields.session).name();

                                Pair<Session, ExecutorService> worker = sessions.computeIfAbsent(sessionName, newSession);
                                worker.other().execute(() -> {
                                    try {
                                        long currentDeltaUs = System.nanoTime() / 1000 - replayStartUs;
                                        long messageDeltaUs = p.get(Fields.timestamp) - streamStartTimeUs.get();

                                        if (currentDeltaUs < messageDeltaUs) {
                                            // Pace requests to roughly match the original load
                                            Thread.sleep((messageDeltaUs - currentDeltaUs) / 1000);
                                        }

                                        // TODO Note that this doesn't properly pipeline the way the original did,
                                        // should revise this to act on the connection level.

                                        String abbreviatedStatement = ellipsis(m.get(Message.statement), 80);
                                        System.out.println(String.format("%s: %s", sessionName, abbreviatedStatement));
                                        worker.first().run(m.get(Message.statement), m.get(Message.params)).consume();
                                    }
                                    catch(Exception e)
                                    {
                                        e.printStackTrace();
                                    }
                                });
                            }
                        });
            }
        };
    }

    public static String ellipsis(String input, int maxCharacters) {
        if (input.length() < maxCharacters) {
            return input;
        }
        return input.substring(0, maxCharacters - 3) + "...";
    }

    private static Predicate<Dict> sessionFilter( String name )
    {
        switch( name )
        {
        case "all": return (p) -> true;
        default:
            long sessionId = Long.parseLong( name );
            return (p) -> p.get( session ).id() == sessionId;
        }
    }

    public static class SessionRepository
    {
        private final Map<Object,AnalyzedSession> openSessions = new HashMap<>();
        private int sessionCount = 0;

        public AnalyzedSession session( Object connectionKey )
        {
            AnalyzedSession session = openSessions.get( connectionKey );
            if( session == null )
            {
                int sid = sessionCount++;
                session = new AnalyzedSession(  String.format("session-%03d", sid ), sid);
                openSessions.put( connectionKey, session );
            }
            return session;
        }
    }

    /** Adds a description of the messages in each packet, plus semantic info about who is sending it and attaches a session object to it */
    public static class AddBoltDescription implements Function<Dict, Dict>
    {
        private final SessionRepository sessions;
        private final int serverPort;

        public AddBoltDescription( int serverPort )
        {
            this.serverPort = serverPort;
            this.sessions = new SessionRepository();
        }

        @Override
        public Dict apply( Dict packet )
        {
            AnalyzedSession sess = sessions.session( packet.get( connectionKey ) );
            packet.put( session, sess );
            if ( packet.get( dstPort ) == serverPort )
            {
                packet.put( description, describeClientPayload( packet.get( payload ), sess ) );
                packet.put( logicalSource, "Client" );
            }
            else
            {
                packet.put( description, describeServerPayload( packet.get( payload ), sess ) );
                packet.put( logicalSource, "Server" );
            }
            return packet;
        }

        private List<Dict> describeClientPayload(ByteBuffer packet, AnalyzedSession sess ) {
            try
            {
                return sess.describeClientPayload( packet );
            }
            catch ( IOException e )
            {
                return singletonList(
                        dict(
                                Message.type, "<UNPARSEABLE>",
                                Message.message, String.format("(%s) %s", e.getMessage(), bytesToHex( packet ))));
            }
        }

        private List<Dict> describeServerPayload( ByteBuffer packet, AnalyzedSession sess ) {
            try
            {
                return sess.describeServerPayload( packet );
            }
            catch ( IOException e )
            {
                return singletonList(
                        dict(
                                Message.type, "<UNPARSEABLE>",
                                Message.message, String.format("(%s) %s", e.getMessage(), bytesToHex( packet ))));
            }
        }

        private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
        private static String bytesToHex(ByteBuffer bytes) {
            char[] hexChars = new char[bytes.remaining() * 2];
            for ( int j = 0; j < bytes.remaining(); j++ ) {
                int v = bytes.get(j) & 0xFF;
                hexChars[j * 2] = hexArray[v >>> 4];
                hexChars[j * 2 + 1] = hexArray[v & 0x0F];
            }
            return new String(hexChars);
        }
    }
}