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

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

import com.polarion.alm.tracker.model.IWorkItem;

@SuppressWarnings("nls")
public class CodeReviewAssigner {

    private final @NotNull Collection<String> targetReviewers;
    private final @NotNull Collection<IWorkItem> reviewedWorkItems;
    private final @NotNull Collection<IWorkItem> workItemsToBeReviewed;
    private final @NotNull ReviewsCalculator reviewsCalculator;
    private final @NotNull CodeReviewAssignerContext context;
    private final @NotNull Function<Map<String, Integer>, ProbabilityMap> probabilityMapFactory;

    CodeReviewAssigner(@NotNull Collection<String> targetReviewers, @NotNull Collection<IWorkItem> reviewedWorkItems, @NotNull Collection<IWorkItem> workItemsToBeReviewed, @NotNull ReviewsCalculator reviewsCalculator, @NotNull CodeReviewAssignerContext context, @NotNull Function<Map<String, Integer>, ProbabilityMap> probabilityMapFactory) {
        super();
        this.targetReviewers = targetReviewers;
        this.reviewedWorkItems = reviewedWorkItems;
        this.workItemsToBeReviewed = workItemsToBeReviewed;
        this.reviewsCalculator = reviewsCalculator;
        this.context = context;
        this.probabilityMapFactory = probabilityMapFactory;
    }

    public void execute() {
        Map<String, Integer> todaysReviewsPerReviewer = calculateReviewsPerReviewer();
        for (String reviewer : targetReviewers) {
            todaysReviewsPerReviewer.putIfAbsent(reviewer, 0);
        }
        context.log("Reviews per reviewer: " + todaysReviewsPerReviewer);
        assignReviewers(todaysReviewsPerReviewer);
    }

    private @NotNull Map<String, Integer> calculateReviewsPerReviewer() {
        return reviewedWorkItems.stream()
                .flatMap(wi -> calculateReviewsPerReviewer(wi).entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a + b));
    }

    private @NotNull Map<String, Integer> calculateReviewsPerReviewer(@NotNull IWorkItem wi) {
        return reviewsCalculator.calculateReviews(wi, targetReviewers);
    }

    private void assignReviewers(@NotNull Map<String, Integer> reviewsPerReviewer) {
        workItemsToBeReviewed.forEach(wi -> assignReviewerRandomly(wi, reviewsPerReviewer));
    }

    private void assignReviewerRandomly(@NotNull IWorkItem wi, @NotNull Map<String, Integer> reviewsPerReviewer) {
        try {
            context.log("Assigning reviewer for Work Item " + wi.getId());
            Map<String, Integer> reviewsPerPossibleReviewer = calculateReviewsPerPossibleReviewer(wi, reviewsPerReviewer);
            ProbabilityMap<String> reviewProbabilityPerReviewer = probabilityMapFactory.apply(reviewsPerPossibleReviewer);
            context.log("... review probability per reviewer " + reviewProbabilityPerReviewer);
            Optional<String> randomlySelectedReviewer = reviewProbabilityPerReviewer.selectRandomly();
            randomlySelectedReviewer.ifPresent(reviewer -> context.assignReviewerAndSave(wi, reviewer));
            context.log("... randomly selected reviewer: " + randomlySelectedReviewer);
        } finally {
            wi.forget();
        }
    }

    private @NotNull Map<String, Integer> calculateReviewsPerPossibleReviewer(@NotNull IWorkItem workItem, @NotNull Map<String, Integer> reviewsPerReviewer) {
        Map<String, Integer> reviewsPerPossibleReviewer = reviewsPerReviewer.entrySet().stream()
                .filter(e -> !context.hasRevisionsToReviewAuthoredByUser(workItem, e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        context.log("... reviews per possible reviewer: " + reviewsPerPossibleReviewer);
        return reviewsPerPossibleReviewer;
    }

}
