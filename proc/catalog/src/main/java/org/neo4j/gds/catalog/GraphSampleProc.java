/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.catalog;

import org.neo4j.gds.ProcPreconditions;
import org.neo4j.gds.config.RandomWalkWithRestartsProcConfig;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.loading.GraphStoreCatalog;
import org.neo4j.gds.core.utils.ProgressTimer;
import org.neo4j.gds.graphsampling.GraphSampleConstructor;
import org.neo4j.gds.graphsampling.config.RandomWalkWithRestartsConfig;
import org.neo4j.gds.graphsampling.samplers.RandomWalkWithRestarts;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;

public class GraphSampleProc extends GraphProjectProc {

    private static final String DESCRIPTION = "Constructs a random subgraph based on random walks with restarts";

    @Procedure(name = "gds.alpha.graph.sample.rwr", mode = READ)
    @Description(DESCRIPTION)
    public Stream<GraphSampleResult> sampleRandomWalkWithRestarts(
        @Name(value = "graphName") String graphName,
        @Name(value = "fromGraphName") String fromGraphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        ProcPreconditions.check();
        validateGraphName(username(), graphName);

        try (var progressTimer = ProgressTimer.start()) {
            var fromGraphStore = graphStoreFromCatalog(fromGraphName);

            var cypherMap = CypherMapWrapper.create(configuration);
            var rwrConfig = RandomWalkWithRestartsConfig.of(cypherMap);

            var randomWalkWithRestarts = new RandomWalkWithRestarts(rwrConfig);
            var graphSampleConstructor = new GraphSampleConstructor(
                rwrConfig,
                fromGraphStore.graphStore(),
                randomWalkWithRestarts
            );
            var sampledGraphStore = graphSampleConstructor.construct();

            var rwrProcConfig = RandomWalkWithRestartsProcConfig.of(
                username(),
                graphName,
                fromGraphName,
                fromGraphStore.config(),
                cypherMap
            );

            GraphStoreCatalog.set(rwrProcConfig, sampledGraphStore);

            var projectMillis = progressTimer.stop().getDuration();
            return Stream.of(new GraphSampleResult(
                graphName,
                fromGraphName,
                sampledGraphStore.nodeCount(),
                sampledGraphStore.relationshipCount(),
                projectMillis
            ));
        }
    }


    public static class GraphSampleResult extends GraphProjectResult {
        public final String fromGraphName;

        GraphSampleResult(
            String graphName,
            String fromGraphName,
            long nodeCount,
            long relationshipCount,
            long projectMillis
        ) {
            super(graphName, nodeCount, relationshipCount, projectMillis);
            this.fromGraphName = fromGraphName;
        }
    }
}
