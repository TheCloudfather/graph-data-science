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
package org.neo4j.gds.similarity.filteredknn;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.IdFunction;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.extension.TestGraph;
import org.neo4j.gds.similarity.SimilarityResult;
import org.neo4j.gds.similarity.knn.ImmutableKnnContext;
import org.neo4j.gds.similarity.knn.KnnContext;
import org.neo4j.gds.similarity.knn.KnnNodePropertySpec;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@GdlExtension
class FilteredKnnTest {

    @GdlGraph
    private static final String DB_CYPHER =
        "CREATE" +
        "  (a { knn: 1.2, prop: 1.0 } )" +
        ", (b { knn: 1.1, prop: 5.0 } )" +
        ", (c { knn: 42.0, prop: 10.0 } )";
    @Inject
    private Graph graph;

    @GdlGraph(graphNamePrefix = "simThreshold")
    private static final String nodeCreateQuery =
        "CREATE " +
        "  (alice:Person {age: 23})" +
        " ,(carol:Person {age: 24})" +
        " ,(eve:Person {age: 34})" +
        " ,(bob:Person {age: 30})";
    @Inject
    private TestGraph simThresholdGraph;

    @GdlGraph(graphNamePrefix = "multPropMissing")
    private static final String nodeCreateMultipleQuery =
        "CREATE " +
        "  (a: P1 {prop1: 1.0, prop2: 5.0})" +
        " ,(b: P1 {prop2 : 5.0})" +
        " ,(c: P1 {prop2 : 10.0})" +
        " ,(d: P1 {prop3 : 10.0})";
    @Inject
    private TestGraph multPropMissingGraph;


    @Inject
    private IdFunction idFunction;

    @Test
    void shouldRunJustLikeKnnWhenYouDoNotSpecifySourceNodeFilterOrTargetNodeFilter() {
        var knnConfig = ImmutableFilteredKnnBaseConfig.builder()
            .nodeProperties(List.of(new KnnNodePropertySpec("knn")))
            .concurrency(1)
            .randomSeed(19L)
            .topK(1)
            .build();
        var knnContext = ImmutableKnnContext.builder().build();

        var knn = FilteredKnn.createWithoutSeeding(graph, knnConfig, knnContext);
        var result = knn.compute();

        assertThat(result).isNotNull();
        assertThat(result.similarityResultStream().count()).isEqualTo(3);

        long nodeAId = idFunction.of("a");
        long nodeBId = idFunction.of("b");
        long nodeCId = idFunction.of("c");

        assertCorrectNeighborList(result, nodeAId, nodeBId);
        assertCorrectNeighborList(result, nodeBId, nodeAId);
        assertCorrectNeighborList(result, nodeCId, nodeAId);
    }

    private void assertCorrectNeighborList(FilteredKnnResult result, long nodeId, long... expectedNeighbors) {
        List<SimilarityResult> similarityResults = result.similarityResultStream()
            .filter(sr -> sr.sourceNodeId() == nodeId)
            .collect(Collectors.toList());

        assertThat(similarityResults)
            .isSortedAccordingTo(Comparator.comparingDouble((s) -> -s.similarity));

        var justNeighbours = similarityResults.stream().mapToLong(SimilarityResult::targetNodeId).toArray();
        assertThat(justNeighbours)
            .doesNotContain(nodeId)
            .containsAnyOf(expectedNeighbors)
            .doesNotHaveDuplicates()
            .hasSizeLessThanOrEqualTo(expectedNeighbors.length);
    }

    @Nested
    class SourceNodeFilterTest {
        @GdlGraph
        private static final String DB_CYPHER =
            "CREATE" +
            "  (a { knn: 1.2 } )" +
            ", (b { knn: 1.1 } )" +
            ", (c { knn: 2.1 } )" +
            ", (d { knn: 3.1 } )" +
            ", (e { knn: 4.1 } )";

        @Test
        void shouldOnlyProduceResultsForFilteredSourceNode() {
            var filteredSourceNode = idFunction.of("a");
            var config = FilteredKnnBaseConfigImpl.builder()
                .nodeProperties(List.of("knn"))
                .topK(3)
                .randomJoins(0)
                .maxIterations(1)
                .randomSeed(20L)
                .concurrency(1)
                .sourceNodeFilter(filteredSourceNode)
                .build();
            var knnContext = KnnContext.empty();
            var knn = FilteredKnn.createWithoutSeeding(graph, config, knnContext);
            var result = knn.compute();

            assertThat(result.similarityResultStream()
                .map(sr -> sr.node1)
                .collect(Collectors.toSet())
            ).isEqualTo(Set.of(filteredSourceNode));
        }

        @Test
        void shouldOnlyProduceResultsForMultipleFilteredSourceNode() {
            var filteredNode1 = idFunction.of("a");
            var filteredNode2 = idFunction.of("b");
            var config = FilteredKnnBaseConfigImpl.builder()
                .nodeProperties("knn")
                .topK(3)
                .randomJoins(0)
                .maxIterations(1)
                .randomSeed(20L)
                .concurrency(1)
                .sourceNodeFilter(List.of(filteredNode1, filteredNode2))
                .build();
            var knnContext = KnnContext.empty();
            var knn = FilteredKnn.createWithoutSeeding(graph, config, knnContext);
            var result = knn.compute();

            assertThat(result.similarityResultStream()
                .map(sr -> sr.node1)
                .collect(Collectors.toSet())
            ).isEqualTo(Set.of(filteredNode1, filteredNode2));
        }
    }

    @Nested
    class TargetNodeFiltering {
        @GdlGraph
        private static final String DB_CYPHER =
            "CREATE" +
            "  (a { knn: 1.2 } )" +
            ", (b { knn: 1.1 } )" +
            ", (c { knn: 2.1 } )" +
            ", (d { knn: 3.1 } )" +
            ", (e { knn: 4.1 } )";

        @Test
        void shouldOnlyProduceResultsForFilteredTargetNode() {
            var targetNode = idFunction.of("a");
            var config = FilteredKnnBaseConfigImpl.builder()
                .nodeProperties(List.of("knn"))
                .topK(3)
                .randomJoins(0)
                .maxIterations(1)
                .randomSeed(20L)
                .concurrency(1)
                .targetNodeFilter(targetNode)
                .build();
            var knnContext = KnnContext.empty();
            var knn = FilteredKnn.createWithoutSeeding(graph, config, knnContext);
            var result = knn.compute();

            assertThat(result.similarityResultStream()
                .map(SimilarityResult::targetNodeId)
                .collect(Collectors.toSet())
            ).isEqualTo(Set.of(targetNode));
        }

        @Test
        void shouldOnlyProduceResultsForFilteredTargetNodes() {
            var targetNode1 = idFunction.of("a");
            var targetNode2 = idFunction.of("b");
            var config = FilteredKnnBaseConfigImpl.builder()
                .nodeProperties("knn")
                .topK(3)
                .randomJoins(0)
                .maxIterations(1)
                .randomSeed(20L)
                .concurrency(1)
                .targetNodeFilter(List.of(targetNode1, targetNode2))
                .build();
            var knnContext = KnnContext.empty();
            var knn = FilteredKnn.createWithoutSeeding(graph, config, knnContext);
            var result = knn.compute();

            assertThat(result.similarityResultStream()
                .map(SimilarityResult::targetNodeId)
                .collect(Collectors.toSet())
            ).isEqualTo(Set.of(targetNode1, targetNode2));
        }
    }

    @Nested
    class TargetNodeFilteringAndDuplicates {
        @GdlGraph
        private static final String DB_CYPHER =
            "CREATE" +
            "  (a { knn: 1.2 } )" +
            ", (b { knn: 1.1 } )" +
            ", (c { knn: 2.1 } )" +
            ", (d { knn: 3.1 } )" +
            ", (e { knn: 4.1 } )";

        @Test
        void shouldIgnoreDuplicates() {
            var config = FilteredKnnBaseConfigImpl.builder()
                .nodeProperties(List.of("knn"))
                .topK(42)
                .build();
            var knnContext = KnnContext.empty();
            var knn = FilteredKnn.createWithoutSeeding(graph, config, knnContext);
            var result = knn.compute();

            /*
             * Ok we want to express that, for each source node, the target nodes found have no duplicates.
             * First, group the results
             */
            Map<Long, List<SimilarityResult>> resultsPerSourceNode = result
                .similarityResultStream()
                .collect(Collectors.groupingBy(SimilarityResult::sourceNodeId));

            // now for each result, see that there are no duplicates
            resultsPerSourceNode
                .values()
                .forEach(similarityResultList -> assertThat(similarityResultList
                    .stream()
                    .mapToLong(SimilarityResult::targetNodeId)).doesNotHaveDuplicates());
        }
    }

    @Nested
    class TargetNodeFilteringAndSeeding {
        @GdlGraph
        private static final String DB_CYPHER =
            "CREATE" +
            "  (x { knn: 100.2 } )" +
            "  (y { knn: 1000.2 } )" +
            "  (z { knn: 10000.2 } )" +
            "  (a { knn: 1.2 } )" +
            ", (b { knn: 1.1 } )" +
            ", (c { knn: 2.1 } )" +
            ", (d { knn: 3.1 } )" +
            ", (e { knn: 4.1 } )";

        /**
         * Testing seeding at this level is difficult, you rely on a leap and a prayer. Here is a stab at it. If this
         * becomes unmaintainable, rely on just {@link org.neo4j.gds.similarity.filteredknn.TargetNodeFilteringTest}
         * instead.
         */
        @Test
        void shouldSeedResultSet() {
            /*
             * This is a bit convoluted: seeding will take the first k nodes regardless of score. Consider node a, it
             * will score very poorly with nodes x, y and z, but x, y and z will be its seeds nonetheless.
             *
             * So here we confirm the first three nodes are the undesirables ones, and tacit knowledge says they will
             * form the seed.
             */
            var targetNodeX = idFunction.of("x");
            var targetNodeY = idFunction.of("y");
            var targetNodeZ = idFunction.of("z");
            var targetNodeA = idFunction.of("a");
            assertThat(targetNodeX).isLessThan(targetNodeY).isLessThan(targetNodeZ).isLessThan(targetNodeA);

            // no target node filter specified -> everything is a target node
            var config = FilteredKnnBaseConfigImpl.builder()
                .nodeProperties(List.of("knn"))
                .topK(4)
                .randomSeed(87L)
                .concurrency(1)
                .build();
            var knnContext = KnnContext.empty();
            var knn = FilteredKnn.createWithDefaultSeeding(graph, config, knnContext);
            var result = knn.compute();

            /*
             * Now let's look at a and it's highest scoring neighbours. They should _not_ be x, y or z because we know
             * those will score very poorly, even if those were the seed nodes.
             */
            Stream<Long> highestScoringNeighboursOfNodeA = result.similarityResultStream()
                .filter(sr -> sr.sourceNodeId() == targetNodeA)
                .map(SimilarityResult::targetNodeId);
            assertThat(highestScoringNeighboursOfNodeA).doesNotContain(targetNodeX, targetNodeY, targetNodeZ);
        }
    }
}
