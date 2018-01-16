package org.neo4j.tools.boltalyzer;

import org.junit.Rule;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.harness.junit.Neo4jRule;

import java.util.Iterator;

public class ReplayTest
{
    @Rule
    public Neo4jRule neo4j = new Neo4jRule();

    @Test
    public void shouldReplayLoad() throws Exception
    {
        // Given
        String file = getClass().getClassLoader().getResource("boltalyzer/CreateNode.pcap").getFile();

        // When
        Boltalyzer.main(
                "--mode", "replay",
                "--target", neo4j.boltURI().toASCIIString(),
                file);

        // Then the node should've gotten created
        GraphDatabaseService db = neo4j.getGraphDatabaseService();
        try(Transaction ignored = db.beginTx()) {
            Iterator<Node> nodes = db.getAllNodes().iterator();

            assert nodes.hasNext() : "Should have created one node";
            assert nodes.next().hasLabel(Label.label("ReplayTest")) : "Should have created a node with label ReplayTest";
            assert !nodes.hasNext() : "Should just have created a single node.";
        }
    }
}
