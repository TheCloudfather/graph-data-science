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
package org.neo4j.graphalgo.pagerank;

import com.carrotsearch.hppc.LongScatterSet;
import com.carrotsearch.hppc.LongSet;
import org.neo4j.graphalgo.api.NodeMapping;
import org.neo4j.graphalgo.api.nodeproperties.ValueType;
import org.neo4j.graphalgo.beta.pregel.Messages;
import org.neo4j.graphalgo.beta.pregel.PregelComputation;
import org.neo4j.graphalgo.beta.pregel.PregelSchema;
import org.neo4j.graphalgo.beta.pregel.Reducer;
import org.neo4j.graphalgo.beta.pregel.context.ComputeContext;
import org.neo4j.graphalgo.beta.pregel.context.InitContext;

import java.util.Optional;
import java.util.function.LongToDoubleFunction;

public final class PageRankPregel implements PregelComputation<PageRankPregelConfig> {

    static final String PAGE_RANK = "pagerank";

    private final boolean hasSourceNodes;
    private final LongSet sourceNodes;
    private final LongToDoubleFunction degreeFunction;

    private final double dampingFactor;
    private final double tolerance;
    private final double alpha;
    private final double deltaCoefficient;

    public PageRankPregel(
        NodeMapping nodeMapping,
        PageRankPregelConfig config,
        LongToDoubleFunction degreeFunction,
        double deltaCoefficient
    ) {
        this.dampingFactor = config.dampingFactor();
        this.tolerance = config.tolerance();
        this.deltaCoefficient = deltaCoefficient;
        this.alpha = 1 - this.dampingFactor;
        this.sourceNodes = new LongScatterSet();
        config.sourceNodeIds().map(nodeMapping::toMappedNodeId).forEach(sourceNodes::add);
        this.hasSourceNodes = !sourceNodes.isEmpty();
        this.degreeFunction = degreeFunction;
    }

    @Override
    public PregelSchema schema(PageRankPregelConfig config) {
        return new PregelSchema.Builder().add(PAGE_RANK, ValueType.DOUBLE).build();
    }

    @Override
    public void init(InitContext<PageRankPregelConfig> context) {
        context.setNodeValue(PAGE_RANK, initialValue(context));
    }

    private double initialValue(InitContext<PageRankPregelConfig> context) {
        if (!hasSourceNodes || sourceNodes.contains(context.nodeId())) {
            return alpha;
        }
        return 0;
    }

    @Override
    public void compute(ComputeContext<PageRankPregelConfig> context, Messages messages) {
        double rank = context.doubleNodeValue(PAGE_RANK);
        double delta = rank;

        if (!context.isInitialSuperstep()) {
            double sum = 0;
            for (var message : messages) {
                sum += message;
            }

            delta = dampingFactor * deltaCoefficient * sum;
            context.setNodeValue(PAGE_RANK, rank + delta);
        }

        if (delta > tolerance || context.isInitialSuperstep()) {
            context.sendToNeighbors(delta / degreeFunction.applyAsDouble(context.nodeId()));
        } else {
            context.voteToHalt();
        }
    }

    @Override
    public Optional<Reducer> reducer() {
        return Optional.of(new Reducer.Sum());
    }

    @Override
    public double applyRelationshipWeight(double nodeValue, double relationshipWeight) {
        return nodeValue * relationshipWeight;
    }

}
