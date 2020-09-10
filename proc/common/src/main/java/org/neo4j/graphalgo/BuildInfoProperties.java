/*
 * Copyright (c) 2017-2020 "Neo4j,"
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
package org.neo4j.graphalgo;

import org.neo4j.graphalgo.annotation.ValueClass;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

@ValueClass
public abstract class BuildInfoProperties {

    public abstract String gdsVersion();

    public abstract String buildDate();

    public abstract String buildJdk();

    public abstract String buildJavaVersion();

    public abstract String buildHash();

    public abstract String minimumRequiredJavaVersion();

    public static BuildInfoProperties from(Properties properties) {
        return ImmutableBuildInfoProperties.builder()
            .gdsVersion(properties.getProperty("Implementation-Version"))
            .buildDate(properties.getProperty("Build-Date", "unknown"))
            .buildJdk(properties.getProperty("Created-By", "unknown"))
            .buildJavaVersion(properties.getProperty("Build-Java-Version", "unknown"))
            .buildHash(properties.getProperty("Full-Change", "unknown"))
            .minimumRequiredJavaVersion(properties.getProperty("X-Compile-Target-JDK", "unknown"))
            .build();
    }

    public static BuildInfoProperties get() throws IOException {
        return LoadInfoProperties.infoProperties();
    }

    public static BuildInfoProperties require() {
        try {
            return get();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // nested static class so that we don't load the properties when the proc class
    // is initialized, but only on first request, and then we cache it
    private static final class LoadInfoProperties {

        private static BuildInfoProperties infoProperties() throws IOException {
            var properties = INFO_PROPERTIES;
            if (properties instanceof BuildInfoProperties) {
                return (BuildInfoProperties) properties;
            }
            throw (IOException) properties;
        }

        private static final String INFO_FILE = "META-INF/info.properties";
        private static final Object INFO_PROPERTIES = loadProperties();

        private static Object loadProperties() {
            var properties = new Properties();
            var classLoader = Thread.currentThread().getContextClassLoader();
            try (var infoStream = classLoader.getResourceAsStream(INFO_FILE)) {
                if (infoStream != null) {
                    try (var infoReader = new InputStreamReader(infoStream, StandardCharsets.UTF_8)) {
                        properties.load(infoReader);
                        return from(properties);
                    }
                }
            } catch (IOException exception) {
                return exception;
            }
            return properties;
        }
    }
}
