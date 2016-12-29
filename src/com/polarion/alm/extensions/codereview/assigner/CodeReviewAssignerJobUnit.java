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
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.polarion.alm.extensions.codereview.Parameters;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.RunnableWEx;
import com.polarion.platform.TransactionExecuter;
import com.polarion.platform.jobs.GenericJobException;
import com.polarion.platform.jobs.IJobStatus;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.IProgressMonitor;
import com.polarion.platform.jobs.spi.AbstractJobUnit;
import com.polarion.platform.persistence.IDataService;
import com.polarion.platform.persistence.model.IRevision;
import com.polarion.platform.security.ISecurityService;

@SuppressWarnings("nls")
final class CodeReviewAssignerJobUnit extends AbstractJobUnit {

    CodeReviewAssignerJobUnit(String name, IJobUnitFactory creator) {
        super(name, creator);
    }

    private final @NotNull Function<IWorkItem, Properties> configurationLoader = Parameters.perContextCachingConfigurationLoader(Parameters.repositoryConfigurationLoader());
    private /*@NotNull*/ String reviewerRole;
    private /*@NotNull*/ String reviewedItemsQuery;
    private /*@NotNull*/ String toBeReviewedItemsQuery;
    private /*@NotNull*/ IDataService dataService;
    private /*@NotNull*/ ITrackerService trackerService;
    private /*@NotNull*/ ISecurityService securityService;

    @Override
    public void activate() throws GenericJobException {
        super.activate();
        if (reviewerRole == null) {
            throw new GenericJobException("reviewerRole is required");
        }
        if (reviewedItemsQuery == null) {
            throw new GenericJobException("reviewedItemsQuery is required");
        }
        if (toBeReviewedItemsQuery == null) {
            throw new GenericJobException("toBeReviewedItemsQuery is required");
        }
        if (dataService == null) {
            throw new GenericJobException("dataService is required");
        }
        if (trackerService == null) {
            throw new GenericJobException("trackerService is required");
        }
        if (securityService == null) {
            throw new GenericJobException("securityService is required");
        }
    }

    public void setReviewerRole(@NotNull String reviewerRole) {
        this.reviewerRole = reviewerRole;
    }

    public void setReviewedItemsQuery(@NotNull String reviewedItemsQuery) {
        this.reviewedItemsQuery = reviewedItemsQuery;
    }

    public void setToBeReviewedItemsQuery(@NotNull String toBeReviewedItemsQuery) {
        this.toBeReviewedItemsQuery = toBeReviewedItemsQuery;
    }

    public void setDataService(@NotNull IDataService dataService) {
        this.dataService = dataService;
    }

    public void setTrackerService(@NotNull ITrackerService trackerService) {
        this.trackerService = trackerService;
    }

    public void setSecurityService(@NotNull ISecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    protected IJobStatus runInternal(IProgressMonitor progress) {
        try {
            execute(progress);
            return progress.isCanceled() ? getStatusCancelled(null) : getStatusOK(null);
        } catch (Exception e) {
            return getStatusFailed(e.getMessage(), e);
        } finally {
            progress.done();
        }
    }

    private void execute(@NotNull IProgressMonitor progress) {
        getLogger().info("scope: " + getScope());
        getLogger().info("reviewer role: " + reviewerRole);
        getLogger().info("reviewed items query: " + reviewedItemsQuery);
        getLogger().info("to be reviewed items query: " + toBeReviewedItemsQuery);

        if (progress.isCanceled()) {
            return;
        }

        TransactionExecuter.execute(new RunnableWEx<Void>() {
            @Override
            public Void runWEx() throws Exception {
                Collection<String> reviewers = securityService.getUsersForContextRole(reviewerRole, getScope().getId());
                getLogger().info("Reviewers to assign: " + reviewers);
                Collection<IWorkItem> reviewedWorkItems = trackerService.queryWorkItems(reviewedItemsQuery, null);
                Collection<IWorkItem> workItemsToBeReviewed = trackerService.queryWorkItems(toBeReviewedItemsQuery, null);
                ReviewsCalculator reviewsCalculator = new ReviewsCalculatorImpl(LocalDate.now(), new JobReviewsCalculatorContext(), WorkItemWithHistoryImpl::new);
                JobCodeReviewAssignerContext codeReviewAssignerContext = new JobCodeReviewAssignerContext();
                CodeReviewAssigner codeReviewAssigner = new CodeReviewAssigner(reviewers, reviewedWorkItems, workItemsToBeReviewed, reviewsCalculator, codeReviewAssignerContext, ProbabilityMapImpl::new);
                codeReviewAssigner.execute();
                return null;
            }
        });
    }

    private final class JobReviewsCalculatorContext implements ReviewsCalculatorContext {

        @Override
        @NotNull
        public IRevision getRevisionOfHistoricalWorkItem(@NotNull IWorkItem historicalWorkItem) {
            return dataService.getRevision(historicalWorkItem.getContextId(), historicalWorkItem.getDataRevision());
        }

        @Override
        @NotNull
        public Iterator<IWorkItem> getHistoryOfWorkItemFromNewest(@NotNull IWorkItem workItem) {
            List<IWorkItem> objectHistory = dataService.getObjectHistory(workItem);
            return new Iterator<IWorkItem>() {

                private int currentIndex = objectHistory.size();

                @Override
                public IWorkItem next() {
                    return objectHistory.get(--currentIndex);
                }

                @Override
                public boolean hasNext() {
                    return currentIndex > 0;
                }
            };
        }

        @Override
        public boolean wasReviewWorkflowActionTriggered(@NotNull IWorkItem workItem) {
            Parameters parameters = new Parameters(workItem, configurationLoader);
            return parameters.wasReviewWorkflowActionTriggered();
        }

        @Override
        public boolean isReviewer(@NotNull IWorkItem contextWorkItem, @Nullable String user) {
            if (user == null) {
                return false;
            }
            Collection<String> rolesForUser = securityService.getRolesForUser(user, contextWorkItem.getContextId());
            return rolesForUser.contains(reviewerRole);
        }

        @Override
        public void log(@NotNull String message) {
            getLogger().info(message);
        }

    }

    private final class JobCodeReviewAssignerContext implements CodeReviewAssignerContext {

        @Override
        public void log(@NotNull String message) {
            getLogger().info(message);
        }

        private @NotNull Parameters getParameters(@NotNull IWorkItem workItem) {
            return new Parameters(workItem, configurationLoader);
        }

        @Override
        public boolean hasRevisionsToReviewAuthoredByUser(@NotNull IWorkItem workItem, @NotNull String user) {
            Parameters parameters = getParameters(workItem);
            return parameters.createRevisions().hasRevisionsToReviewAuthoredByUser(parameters.identityForUser(user));
        }

        @Override
        public void assignReviewerAndSave(@NotNull IWorkItem workItem, @NotNull String reviewer) {
            getParameters(workItem).assignReviewerAndSave(reviewer);
        }

    }

}