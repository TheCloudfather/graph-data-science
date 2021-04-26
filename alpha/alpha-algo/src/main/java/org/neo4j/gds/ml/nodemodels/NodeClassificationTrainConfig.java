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
package org.neo4j.gds.ml.nodemodels;

import org.immutables.value.Value;
import org.neo4j.gds.ml.nodemodels.metrics.MetricSpecification;
import org.neo4j.graphalgo.annotation.Configuration;
import org.neo4j.graphalgo.annotation.ValueClass;
import org.neo4j.graphalgo.config.AlgoBaseConfig;
import org.neo4j.graphalgo.config.ConcurrencyConfig;
import org.neo4j.graphalgo.config.FeaturePropertiesConfig;
import org.neo4j.graphalgo.config.GraphCreateConfig;
import org.neo4j.graphalgo.config.ModelConfig;
import org.neo4j.graphalgo.core.CypherMapWrapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ValueClass
@Configuration
@SuppressWarnings("immutables:subtype")
public interface NodeClassificationTrainConfig extends AlgoBaseConfig, FeaturePropertiesConfig, ModelConfig {

    long serialVersionUID = 0x42L;

    Optional<Long> randomSeed();

    @Configuration.ConvertWith("org.neo4j.gds.ml.nodemodels.metrics.MetricSpecification#parse")
    @Configuration.ToMapValue("org.neo4j.gds.ml.nodemodels.metrics.MetricSpecification#specificationsToString")
    List<MetricSpecification> metrics();

    @Configuration.DoubleRange(min = 0, max = 1)
    double holdoutFraction();

    @Configuration.IntegerRange(min = 2)
    int validationFolds();

    @Configuration.ConvertWith("org.apache.commons.lang3.StringUtils#trimToNull")
    String targetProperty();

    List<Map<String, Object>> params();

    @Value.Derived
    @Configuration.Ignore
    default List<ParamConfig> paramsConfig() {
        return params()
            .stream()
            .map(ParamConfig::of)
            .collect(Collectors.toList());
    }

    static NodeClassificationTrainConfig of(
        Optional<String> graphName,
        Optional<GraphCreateConfig> maybeImplicitCreate,
        String username,
        CypherMapWrapper config
    ) {
        return new NodeClassificationTrainConfigImpl(
            graphName,
            maybeImplicitCreate,
            username,
            config
        );
    }

    static ImmutableNodeClassificationTrainConfig.Builder builder() {
        return ImmutableNodeClassificationTrainConfig.builder();
    }

    @ValueClass
    @Configuration
    interface ParamConfig extends ConcurrencyConfig {
        double penalty();

        @Configuration.IntegerRange(min = 1)
        default int batchSize() {
            return 100;
        }

        @Configuration.IntegerRange(min = 1)
        default int minEpochs() {
            return 1;
        }

        @Configuration.IntegerRange(min = 1)
        default int maxEpochs() {
            return 100;
        }

        @Configuration.IntegerRange(min = 0)
        default int patience() {
            return 1;
        }

        @Configuration.DoubleRange(min = 0)
        default double tolerance() {
            return 0.001D;
        }

        default boolean sharedUpdater() {
            return false;
        }

        static ParamConfig of(Map<String, Object> rawParams) {
            return new ParamConfigImpl(CypherMapWrapper.create(rawParams));
        }
    }
}
