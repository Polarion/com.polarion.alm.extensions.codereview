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

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

class ProbabilityMapImpl<T> implements ProbabilityMap<T> {

    private final @NotNull Map<T, Double> innerProbabilityMap;

    ProbabilityMapImpl(@NotNull Map<T, Integer> frequencies) {
        this.innerProbabilityMap = convertFrequenciesToProbabilities(frequencies);
    }

    private @NotNull Map<T, Double> convertFrequenciesToProbabilities(@NotNull Map<T, Integer> frequencies) {
        int totalSum = frequencies.values().stream().mapToInt(Integer::intValue).sum();
        int numberOfKeys = frequencies.size();
        return frequencies.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> calculateProbability(e.getValue(), totalSum, numberOfKeys)));
    }

    private double calculateProbability(int frequency, int totalSum, int numberOfKeys) {
        if (totalSum == 0) {
            return (double) 1 / (double) numberOfKeys;
        }
        int invertedReviewsCount = totalSum - frequency;
        int invertedTotalReviews = totalSum * (numberOfKeys - 1);
        return (double) invertedReviewsCount / (double) invertedTotalReviews;
    }

    @Override
    public @NotNull Optional<T> selectRandomly() {
        return select(Math.random());
    }

    @Override
    public @NotNull Optional<T> select(double targetProbability) {
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
