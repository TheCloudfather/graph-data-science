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
package org.neo4j.graphalgo.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.neo4j.gds.ml.nodemodels.logisticregression.NodeClassificationTrainConfig;
import org.neo4j.gds.ml.nodemodels.metrics.Metric;
import org.neo4j.gds.ml.util.ObjectMapperSingleton;
import org.neo4j.graphalgo.core.model.proto.TrainConfigsProto;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.neo4j.graphalgo.config.ConfigSerializers.serializableFeaturePropertiesConfig;
import static org.neo4j.graphalgo.config.ConfigSerializers.serializableModelConfig;

public final class NodeClassificationTrainConfigSerializer {

    private NodeClassificationTrainConfigSerializer() {}

    public static TrainConfigsProto.NodeClassificationTrainConfig toSerializable(NodeClassificationTrainConfig trainConfig) {
        var builder = TrainConfigsProto.NodeClassificationTrainConfig.newBuilder();

        builder
            .setModelConfig(serializableModelConfig(trainConfig))
            .setFeaturePropertiesConfig(serializableFeaturePropertiesConfig(trainConfig))
            .setHoldoutFraction(trainConfig.holdoutFraction())
            .setValidationFolds(trainConfig.validationFolds())
            .setTargetProperty(trainConfig.targetProperty());

        var randomSeedBuilder = TrainConfigsProto.RandomSeed
            .newBuilder()
            .setPresent(trainConfig.randomSeed().isPresent());
        trainConfig.randomSeed().ifPresent(randomSeedBuilder::setValue);
        builder.setRandomSeed(randomSeedBuilder);

        trainConfig.metrics()
            .stream()
            .map(Enum::name)
            .map(TrainConfigsProto.Metric::valueOf)
            .forEach(builder::addMetrics);

        trainConfig.params().forEach(paramsMap -> {
            try {
                var p = ObjectMapperSingleton.OBJECT_MAPPER.writeValueAsString(paramsMap);
                builder.addParams(p);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        });

        return builder.build();
    }

    public static NodeClassificationTrainConfig fromSerializable(TrainConfigsProto.NodeClassificationTrainConfig serializedTrainConfig) {
        var builder = NodeClassificationTrainConfig.builder();

        builder
            .modelName(serializedTrainConfig.getModelConfig().getModelName())
            .holdoutFraction(serializedTrainConfig.getHoldoutFraction())
            .validationFolds(serializedTrainConfig.getValidationFolds())
            .targetProperty(serializedTrainConfig.getTargetProperty())
            .featureProperties(serializedTrainConfig.getFeaturePropertiesConfig().getFeaturePropertiesList());

        var randomSeed = serializedTrainConfig.getRandomSeed();
        if (randomSeed.getPresent()) {
            builder.randomSeed(randomSeed.getValue());
        }

        var metrics = serializedTrainConfig
            .getMetricsList()
            .stream()
            .map(TrainConfigsProto.Metric::name)
            .map(Metric::valueOf)
            .collect(Collectors.toList());
        builder.metrics(metrics);

        List<Map<String, Object>> params = serializedTrainConfig
            .getParamsList()
            .stream()
            .map(NodeClassificationTrainConfigSerializer::protoToMap)
            .collect(Collectors.toList());
        builder.params(params);

        return builder.build();
    }

    private static Map<String, Object> protoToMap(String p) {
        try {
            return ObjectMapperSingleton.OBJECT_MAPPER.readValue(p, Map.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return Collections.emptyMap();
    }
}
