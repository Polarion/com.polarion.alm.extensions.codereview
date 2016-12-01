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
import java.time.ZoneId;
import java.util.Date;
import java.util.Iterator;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.platform.persistence.model.IRevision;

final class WorkItemWithHistoryImpl implements WorkItemWithHistory {
    private final @NotNull IWorkItem workItem;
    private final @NotNull ReviewsCalculatorContext context;

    WorkItemWithHistoryImpl(@NotNull IWorkItem workItem, @NotNull ReviewsCalculatorContext context) {
        this.workItem = workItem;
        this.context = context;
    }

    @Override
    public void forEachChangeFromDate(@NotNull LocalDate date, @NotNull Consumer<WorkItemChange> action) {
        Iterator<IWorkItem> historyIterator = context.getHistoryOfWorkItemFromNewest(workItem);
        while (historyIterator.hasNext()) {
            IWorkItem historicalWI = historyIterator.next();
            try {
                WorkItemChange change = createWorkItemChange(historicalWI);
                if (change.wasChangedOn(date)) {
                    action.accept(change);
                } else if (change.wasChangedEarlierThan(date)) {
                    break;
                }
            } finally {
                historicalWI.forget();
            }
        }
    }

    private @NotNull WorkItemChange createWorkItemChange(IWorkItem historicalWI) {
        IRevision revision = context.getRevisionOfHistoricalWorkItem(historicalWI);
        try {
            return new WorkItemChange(historicalWI, revision.getName(), dateToLocalDate(revision.getCreated()), revision.getStringAuthor());
        } finally {
            revision.forget();
        }
    }

    private @Nullable LocalDate dateToLocalDate(@Nullable Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

}