/*
 * Copyright 2016 Polarion AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.polarion.alm.extensions.codereview.assigner;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("nls")
class ProbabilityMapImpl<T> implements ProbabilityMap<T> {

    private final @NotNull Map<T, Double> innerProbabilityMap;

    ProbabilityMapImpl(@NotNull Map<T, Integer> frequencies) {
        this.innerProbabilityMap = convertFrequenciesToProbabilities(frequencies);
    }

    private @NotNull Map<T, Double> convertFrequenciesToProbabilities(@NotNull Map<T, Integer> frequencies) {
        if (frequencies.values().stream().anyMatch(frequency -> frequency < 0)) {
            throw new IllegalArgumentException("Frequency less than zero supplied: " + frequencies);
        }
        frequencies = normalizeFrequencies(frequencies);
        int totalSum = frequencies.values().stream().mapToInt(Integer::intValue).sum();
        int numberOfKeys = frequencies.size();
        return frequencies.entrySet().stream().sequential()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> calculateProbability(e.getValue(), totalSum, numberOfKeys),
                        (a, b) -> {
                            throw new IllegalStateException("Duplicate frequency key");
                        },
                        LinkedHashMap::new));
    }

    private @NotNull Map<T, Integer> normalizeFrequencies(Map<T, Integer> frequencies) {
        Map<T, Integer> normalizedFrequencies = new LinkedHashMap<>(frequencies);
        normalizedFrequencies.replaceAll((key, value) -> value + 1);
        return normalizedFrequencies;
    }

    private double calculateProbability(int frequency, int totalSum, int numberOfKeys) {
        int invertedFrequency = totalSum - frequency;
        int invertedTotalSum = totalSum * (numberOfKeys - 1);
        return (double) invertedFrequency / (double) invertedTotalSum;
    }

    @Override
    public @NotNull Optional<T> selectRandomly() {
        return select(Math.random());
    }

    @Override
    public @NotNull Optional<T> select(double targetProbability) {
        if (targetProbability < 0 || targetProbability >= 1) {
            throw new IllegalArgumentException("Invalid target probability: " + targetProbability);
        }
        double accumulatedProbability = 0;
        T lastKey = null;
        for (Map.Entry<T, Double> innerProbabilityEntry : innerProbabilityMap.entrySet()) {
            T key = innerProbabilityEntry.getKey();
            double probability = innerProbabilityEntry.getValue();
            accumulatedProbability += probability;
            if (targetProbability < accumulatedProbability) {
                return Optional.of(key);
            }
            lastKey = key;
        }
        return Optional.ofNullable(lastKey);
    }

    @Override
    public String toString() {
        return innerProbabilityMap.toString();
    }

}
