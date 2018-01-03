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

import java.util.HashMap;

/**
 * Just a decorated Hashmap. Adds chained 'put' calls, easy integration with {@link Field}
 * and makes it so that you don't have to write {@code HashMap<String, Object>} every 30 seconds.
 */
public class Dict extends HashMap<String, Object>
{
    public static Dict dict(Object ... alternatingKeyValue) {
        Dict d = new Dict();
        for (int i = 0; i < alternatingKeyValue.length; i+=2) {
            if(alternatingKeyValue[i] instanceof Field) {
                d.put((Field)alternatingKeyValue[i], alternatingKeyValue[i+1]);
            } else {
                d.put(alternatingKeyValue[i].toString(), alternatingKeyValue[i + 1]);
            }
        }
        return d;
    }

    public <T> Dict put( Field<T> field, T val )
    {
        field.put( this, val );
        return this;
    }

    public <T> T get( Field<T> field )
    {
        return field.get( this );
    }

    public <T> T get(Field<T> field, T defaultValue) {
        T val = get(field);
        if( val == null ) {
            return defaultValue;
        }
        return val;
    }
}
