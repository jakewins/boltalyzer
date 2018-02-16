package org.neo4j.tools.boltalyzer.serialize;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.Version;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.module.SimpleModule;

import java.io.IOException;
import java.util.Arrays;

import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;

public class Bolt2JSON {
    private final static ObjectMapper mapper = new ObjectMapper().withModule(create());

    public static ObjectMapper mapper() {
        return mapper;
    }

    public static SimpleModule create()
    {
        SimpleModule module = new SimpleModule("BoltSerialization", Version.unknownVersion());
        module.addSerializer(new LabelSerializer());
        module.addSerializer(new NodeSerializer());
        module.addSerializer(new RelationshipSerializer());
        module.addSerializer(new RelationshipTypeSerializer());
        return module;
    }
}

class LabelSerializer extends JsonSerializer<Label> {
    @Override
    public Class<Label> handledType() {
        return Label.class;
    }

    @Override
    public void serialize(Label value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
        jgen.writeString(value.name());
    }
}


class RelationshipTypeSerializer extends JsonSerializer<RelationshipType> {
    @Override
    public Class<RelationshipType> handledType() {
        return RelationshipType.class;
    }

    @Override
    public void serialize(RelationshipType value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
        jgen.writeString(value.name());
    }
}


class NodeSerializer extends JsonSerializer<Node> {
    @Override
    public Class<Node> handledType() {
        return Node.class;
    }

    @Override
    public void serialize(Node value, JsonGenerator g, SerializerProvider provider) throws IOException, JsonProcessingException {
        g.writeStartArray();
        g.writeString("NODE");
        g.writeNumber(value.getId());
        g.writeObject(value.getLabels());
        g.writeObject(value.getAllProperties());
        g.writeEndArray();
    }
}

class RelationshipSerializer extends JsonSerializer<Relationship> {
    @Override
    public Class<Relationship> handledType() {
        return Relationship.class;
    }

    @Override
    public void serialize(Relationship value, JsonGenerator g, SerializerProvider provider) throws IOException, JsonProcessingException {
        g.writeStartArray();
        g.writeString("RELATIONSHIP");
        g.writeNumber(value.getId());
        g.writeObject(Arrays.asList(value.getStartNode().getId(), value.getType().name(), value.getEndNode().getId()));
        g.writeObject(value.getAllProperties());
        g.writeEndArray();
    }
}