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

import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.beta.filter.NodesFilter;
import org.neo4j.gds.beta.filter.expression.ExpressionParser;
import org.neo4j.gds.config.MutateLabelConfig;
import org.neo4j.gds.core.concurrency.Pools;
import org.neo4j.gds.core.utils.ProgressTimer;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.executor.ProcPreconditions;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;
import org.opencypher.v9_0.parser.javacc.ParseException;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;

public class GraphMutateNodeLabelProc extends CatalogProc {

    @Procedure(name = "gds.alpha.graph.nodeLabel.mutate", mode = READ)
    @Description("Mutates the in-memory graph with the given node Label.")
    public Stream<MutateLabelResult> mutate(
        @Name(value = "graphName") String graphName,
        @Name(value = "nodeLabel") String nodeLabel,
        @Name(value = "configuration") Map<String, Object> configuration
    ) throws ParseException {

        ProcPreconditions.check();

        var procedureConfig = MutateLabelConfig.of(configuration);
        var graphStore = graphStoreFromCatalog(graphName).graphStore();
        var filter = ExpressionParser.parse(procedureConfig.nodeFilter(), Map.of());
        var nodeLabelToMutate = NodeLabel.of(nodeLabel);

        var resultBuilder = MutateLabelResult.builder(graphName, nodeLabel).withConfig(procedureConfig.toMap());
        try (ProgressTimer ignored = ProgressTimer.start(resultBuilder::withMutateMillis)) {
            var filteredNodes = NodesFilter.filterNodes(
                graphStore,
                filter,
                procedureConfig.concurrency(),
                Map.of(),
                Pools.DEFAULT,
                ProgressTracker.NULL_TRACKER
            );

            var nodeCounter = new LongAdder();
            var idMap = filteredNodes.idMap();
            graphStore.addNodeLabel(nodeLabelToMutate);
            idMap.forEachNode(
                nodeId -> {
                    var originalNodeId = idMap.toOriginalNodeId(nodeId);
                    var mappedNodeId = graphStore.nodes().safeToMappedNodeId(originalNodeId);
                    graphStore.nodes().addNodeIdToLabel(mappedNodeId, nodeLabelToMutate);
                    nodeCounter.increment();
                    return true;
                }
            );

            resultBuilder
                .withNodeLabelsWritten(nodeCounter.longValue())
                .withNodeCount(graphStore.nodeCount());
        }


        return Stream.of(resultBuilder.build());
    }

}
