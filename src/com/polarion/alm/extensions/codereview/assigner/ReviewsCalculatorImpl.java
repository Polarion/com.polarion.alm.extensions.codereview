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

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import com.polarion.alm.tracker.model.IWorkItem;

@SuppressWarnings("nls")
class ReviewsCalculatorImpl implements ReviewsCalculator {

    private final @NotNull LocalDate decisionDate;
    private final @NotNull ReviewsCalculatorContext context;

    ReviewsCalculatorImpl(@NotNull LocalDate decisionDate, @NotNull ReviewsCalculatorContext context) {
        this.decisionDate = decisionDate;
        this.context = context;
    }

    @Override
    public @NotNull Map<String, Integer> calculateReviews(@NotNull IWorkItem workItem, @NotNull Collection<String> targetReviewers) {
        context.log("Calculating reviews per reviewer on " + decisionDate + " for Work Item " + workItem.getId());
        Map<String, Integer> reviewsPerReviewer = new HashMap<>();
        WorkItemWithHistory workItemWithHistory = new WorkItemWithHistoryImpl(workItem, context);
        workItemWithHistory.forEachChangeFromNewestOnDate(decisionDate, change -> {
            context.log("... revision " + change);
            if (targetReviewers.contains(change.getChangeAuthor()) && wasReviewWorkflowActionTriggeredByReviewer(change)) {
                context.log("    ... had review workflow action triggered by reviewer");
                reviewsPerReviewer.merge(change.getChangeAuthor(), 1, (a, b) -> a + b);
            }
        });
        return reviewsPerReviewer;
    }

    private boolean wasReviewWorkflowActionTriggeredByReviewer(@NotNull WorkItemChange change) {
        IWorkItem workItem = change.getHistoricalWorkItem();
        if (!context.wasReviewWorkflowActionTriggered(workItem)) {
            return false;
        }
        return context.isReviewer(workItem, change.getChangeAuthor());
    }

}
