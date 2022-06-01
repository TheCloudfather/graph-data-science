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
package org.neo4j.gds.similarity.filterednodesim;

import org.neo4j.gds.AlgoBaseProc;
import org.neo4j.gds.AlgorithmFactory;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.executor.ComputationResultConsumer;
import org.neo4j.gds.executor.ExecutionMode;
import org.neo4j.gds.executor.GdsCallable;
import org.neo4j.gds.executor.MemoryEstimationExecutor;
import org.neo4j.gds.executor.ProcedureExecutor;
import org.neo4j.gds.results.MemoryEstimateResult;
import org.neo4j.gds.similarity.SimilarityMutateResult;
import org.neo4j.gds.similarity.nodesim.NodeSimilarity;
import org.neo4j.gds.similarity.nodesim.NodeSimilarityFactory;
import org.neo4j.gds.similarity.nodesim.NodeSimilarityResult;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.gds.similarity.filterednodesim.FilteredNodeSimilarityStreamProc.DESCRIPTION;
import static org.neo4j.procedure.Mode.READ;

@GdsCallable(name = "gds.alpha.nodeSimilarity.filtered.mutate", description = DESCRIPTION, executionMode = ExecutionMode.STREAM)
public class FilteredNodeSimilarityMutateProc  extends AlgoBaseProc<
    NodeSimilarity,
    NodeSimilarityResult,
    FilteredNodeSimilarityMutateConfig,
    SimilarityMutateResult
    > {

    @Procedure(name = "gds.alpha.nodeSimilarity.filtered.mutate", mode = READ)
    @Description(DESCRIPTION)
    public Stream<SimilarityMutateResult> mutate(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return new ProcedureExecutor<>(
            new FilteredNodeSimilarityMutateSpec(),
            executionContext()
        ).compute(graphName, configuration, true, true);
    }

    @Procedure(name = "gds.alpha.nodeSimilarity.filtered.mutate.estimate", mode = READ)
    @Description(ESTIMATE_DESCRIPTION)
    public Stream<MemoryEstimateResult> estimate(
        @Name(value = "graphNameOrConfiguration") Object graphNameOrConfiguration,
        @Name(value = "algoConfiguration") Map<String, Object> algoConfiguration
    ) {
        return new MemoryEstimationExecutor<>(
            new FilteredNodeSimilarityMutateSpec(),
            executionContext()
        ).computeEstimate(graphNameOrConfiguration, algoConfiguration);
    }

    @Override
    public AlgorithmFactory<?, NodeSimilarity, FilteredNodeSimilarityMutateConfig> algorithmFactory() {
        return new NodeSimilarityFactory<>();
    }

    @Override
    public ComputationResultConsumer<NodeSimilarity, NodeSimilarityResult, FilteredNodeSimilarityMutateConfig, Stream<SimilarityMutateResult>> computationResultConsumer() {
            return new FilteredNodeSimilarityMutateSpec().computationResultConsumer();
    }

    @Override
    protected FilteredNodeSimilarityMutateConfig newConfig(String username, CypherMapWrapper userInput) {
            return FilteredNodeSimilarityMutateConfig.of(userInput);
    }
}
