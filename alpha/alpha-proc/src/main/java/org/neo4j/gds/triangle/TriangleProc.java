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
package org.neo4j.gds.triangle;

import org.neo4j.gds.AlgoBaseProc;
import org.neo4j.gds.GraphAlgorithmFactory;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.concurrency.Pools;
import org.neo4j.gds.core.utils.TerminationFlag;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.impl.triangle.TriangleStream;
import org.neo4j.gds.validation.BeforeLoadValidation;
import org.neo4j.gds.validation.GraphCreateConfigValidations;
import org.neo4j.gds.validation.ValidationConfiguration;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;

public class TriangleProc extends AlgoBaseProc<TriangleStream, Stream<TriangleStream.Result>, TriangleCountBaseConfig> {

    private static final String DESCRIPTION = "Triangles streams the nodeIds of each triangle in the graph.";

    @Procedure(name = "gds.alpha.triangles", mode = READ)
    @Description(DESCRIPTION)
    public Stream<TriangleStream.Result> stream(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        ComputationResult<TriangleStream, Stream<TriangleStream.Result>, TriangleCountBaseConfig> computationResult =
            compute(graphName, configuration, false, false);

        Graph graph = computationResult.graph();

        if (graph.isEmpty()) {
            graph.release();
            return Stream.empty();
        }

        var resultStream = computationResult.result();
        try(var statement = transaction.acquireStatement()) {
            statement.registerCloseableResource(resultStream);
        }
        return resultStream;
    }

    @Override
    protected TriangleCountBaseConfig newConfig(String username, CypherMapWrapper config) {
        return TriangleCountBaseConfig.of(config);
    }

    @Override
    public ValidationConfiguration<TriangleCountBaseConfig> getValidationConfig() {
        return new ValidationConfiguration<>() {
            @Override
            public List<BeforeLoadValidation<TriangleCountBaseConfig>> beforeLoadValidations() {
                return List.of(new GraphCreateConfigValidations.UndirectedGraphValidation<>());
            }
        };
    }

    @Override
    protected GraphAlgorithmFactory<TriangleStream, TriangleCountBaseConfig> algorithmFactory() {
        return new GraphAlgorithmFactory<>() {
            @Override
            protected String taskName() {
                return "TriangleStream";
            }

            @Override
            protected TriangleStream build(
                Graph graph,
                TriangleCountBaseConfig configuration,
                AllocationTracker allocationTracker,
                ProgressTracker progressTracker
            ) {
                return TriangleStream.create(graph, Pools.DEFAULT, configuration.concurrency())
                    .withTerminationFlag(TerminationFlag.wrap(transaction));
            }
        };
    }
}
