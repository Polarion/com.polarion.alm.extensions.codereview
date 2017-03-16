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
package com.polarion.alm.extensions.codereview;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.polarion.alm.tracker.model.IComment;
import com.polarion.alm.tracker.model.IStatusOpt;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowAction;
import com.polarion.core.util.RunnableWEx;
import com.polarion.core.util.types.Text;
import com.polarion.platform.TransactionExecuter;
import com.polarion.platform.persistence.IEnumOption;
import com.polarion.platform.persistence.model.IPObjectList;
import com.polarion.subterra.base.location.ILocation;

@SuppressWarnings("nls")
public class Parameters {

    // URL parameters
    static final String PARAM_WORK_ITEM_ID = CodeReviewServlet.PARAM_ID;
    static final String PARAM_PROJECT_ID = "projectId";
    static final String PARAM_AGGREGATED_COMPARE = "aggregated";
    static final String PARAM_COMPARE_ALL = "compareAll";
    static final String PARAM_WORKFLOW_ACTION = "workflowAction";
    private static final String PARAM_REVIEW_COMMENT = "reviewComment";

    // configuration parameters
    private static final String CONFIG_LAST_REVIEWED_REVISION_FIELD = "lastReviewedRevisionField";
    private static final String CONFIG_REVIEWED_REVISIONS_FIELD = "reviewedRevisionsField";
    private static final String CONFIG_REVIEWER_FIELD = "reviewerField";
    private static final String CONFIG_IN_REVIEW_STATUS = "inReviewStatus";
    private static final String CONFIG_SUCCESSFUL_REVIEW_WF_ACTION = "successfulReviewWorkflowAction";
    private static final String CONFIG_SUCCESSFUL_REVIEW_RESOLUTION = "successfulReviewResolution";
    private static final String CONFIG_UNSUCCESSFUL_REVIEW_WF_ACTION = "unsuccessfulReviewWorkflowAction";
    private static final String CONFIG_FAST_TRACK_PERMITTED_LOCATION_PATTERN = "fastTrackPermittedLocationPattern";
    private static final String CONFIG_FAST_TRACK_REVIEWER = "fastTrackReviewer";
    private static final String CONFIG_UNRESOLVED_WORK_ITEM_WITH_REVISIONS_NEEDS_TIMEPOINT = "unresolvedWorkItemWithRevisionsNeedsTimePoint";
    private static final String CONFIG_REVIEWER_ROLE = "reviewerRole";
    private static final String CONFIG_PAST_REVIEWERS = "pastReviewers";
    private static final String CONFIG_PREVENT_REVIEW_CONFLICTS = "preventReviewConflicts";
    private static final String CONFIG_REVIEW_COMMENT_TITLE = "reviewCommentTitle";
    private static final String CONFIG_SUCCESSFUL_REVIEW_COMMENT_TITLE = "successfulReviewCommentTitle";
    private static final String CONFIG_UNSUCCESSFUL_REVIEW_COMMENT_TITLE = "unsuccessfulReviewCommentTitle";

    public static enum WorkflowAction {
        successfulReview, unsuccessfulReview;
    }

    private final @NotNull ParametersContext context;

    private final @NotNull IWorkItem workItem;
    private final boolean aggregatedCompare;
    private final boolean compareAll;
    private final @Nullable WorkflowAction workflowAction;

    private final @Nullable String lastReviewedRevisionField;
    private final @Nullable String reviewedRevisionsField;
    private final @Nullable String reviewerField;
    private final @Nullable String inReviewStatus;
    private final @Nullable String successfulReviewWorkflowAction;
    private final @Nullable String successfulReviewResolution;
    private final @Nullable String unsuccessfulReviewWorkflowAction;
    private final @Nullable Pattern fastTrackPermittedLocationPattern;
    private final @Nullable String fastTrackReviewer;
    private final @Nullable String successfulReviewCommentTitle;
    private final @Nullable String unsuccessfulReviewCommentTitle;
    private final @Nullable String reviewCommentTitle;
    private final @Nullable String commentText;
    private final boolean unresolvedWorkItemWithRevisionsNeedsTimePoint;
    private final @Nullable String reviewerRole;
    private final @NotNull Collection<String> pastReviewers;
    private final boolean preventReviewConflicts;

    private Parameters(@NotNull ParametersContext context, @NotNull IWorkItem workItem, boolean aggregatedCompare, boolean compareAll, @Nullable WorkflowAction workflowAction, @Nullable String commentText) {
        super();
        this.context = context;
        this.workItem = workItem;
        this.aggregatedCompare = aggregatedCompare;
        this.compareAll = compareAll;
        this.workflowAction = workflowAction;
        Properties configuration = context.loadConfiguration(workItem);
        lastReviewedRevisionField = configuration.getProperty(CONFIG_LAST_REVIEWED_REVISION_FIELD);
        reviewedRevisionsField = configuration.getProperty(CONFIG_REVIEWED_REVISIONS_FIELD);
        reviewerField = configuration.getProperty(CONFIG_REVIEWER_FIELD);
        inReviewStatus = configuration.getProperty(CONFIG_IN_REVIEW_STATUS);
        successfulReviewWorkflowAction = configuration.getProperty(CONFIG_SUCCESSFUL_REVIEW_WF_ACTION);
        successfulReviewResolution = configuration.getProperty(CONFIG_SUCCESSFUL_REVIEW_RESOLUTION);
        unsuccessfulReviewWorkflowAction = configuration.getProperty(CONFIG_UNSUCCESSFUL_REVIEW_WF_ACTION);
        reviewCommentTitle = configuration.getProperty(CONFIG_REVIEW_COMMENT_TITLE);
        successfulReviewCommentTitle = configuration.getProperty(CONFIG_SUCCESSFUL_REVIEW_COMMENT_TITLE);
        unsuccessfulReviewCommentTitle = configuration.getProperty(CONFIG_UNSUCCESSFUL_REVIEW_COMMENT_TITLE);
        this.commentText = commentText;
        String fastTrackPermittedLocationPatternStr = configuration.getProperty(CONFIG_FAST_TRACK_PERMITTED_LOCATION_PATTERN);
        if (fastTrackPermittedLocationPatternStr != null) {
            fastTrackPermittedLocationPattern = Pattern.compile(fastTrackPermittedLocationPatternStr);
        } else {
            fastTrackPermittedLocationPattern = null;
        }
        fastTrackReviewer = configuration.getProperty(CONFIG_FAST_TRACK_REVIEWER);
        unresolvedWorkItemWithRevisionsNeedsTimePoint = Boolean.parseBoolean(configuration.getProperty(CONFIG_UNRESOLVED_WORK_ITEM_WITH_REVISIONS_NEEDS_TIMEPOINT));
        reviewerRole = configuration.getProperty(CONFIG_REVIEWER_ROLE);
        String pastReviewersString = configuration.getProperty(CONFIG_PAST_REVIEWERS);
        if (pastReviewersString != null) {
            pastReviewers = new HashSet(Arrays.asList(pastReviewersString.split("\\s+")));
        } else {
            pastReviewers = Collections.EMPTY_SET;
        }
        preventReviewConflicts = Boolean.parseBoolean(configuration.getProperty(CONFIG_PREVENT_REVIEW_CONFLICTS));
    }

    private static @Nullable WorkflowAction parseWorkflowAction(@Nullable String s) {
        if (s == null) {
            return null;
        }
        return WorkflowAction.valueOf(s);
    }

    public Parameters(@NotNull ParametersContext context, @NotNull HttpServletRequest request) {
        this(context, context.getWorkItem(request.getParameter(PARAM_PROJECT_ID), request.getParameter(PARAM_WORK_ITEM_ID)), Boolean.parseBoolean(request.getParameter(PARAM_AGGREGATED_COMPARE)),
                Boolean.parseBoolean(request.getParameter(PARAM_COMPARE_ALL)), parseWorkflowAction(request.getParameter(PARAM_WORKFLOW_ACTION)), request.getParameter(PARAM_REVIEW_COMMENT));
    }

    public Parameters(@NotNull ParametersContext context, @NotNull IWorkItem workItem) {
        this(context, workItem, false, false, null, null);
    }

    public @NotNull IWorkItem getWorkItem() {
        return workItem;
    }

    public boolean isAggregatedCompare() {
        return aggregatedCompare;
    }

    public @Nullable Integer getLastReviewedRevision() {
        Object lastReviewedRevisionValue = lastReviewedRevisionField != null && !lastReviewedRevisionField.isEmpty() ? workItem.getValue(lastReviewedRevisionField) : null;
        Integer lastReviewedRevision = null;
        if (lastReviewedRevisionValue instanceof Integer) {
            lastReviewedRevision = (Integer) lastReviewedRevisionValue;
        } else if (lastReviewedRevisionValue instanceof String) {
            try {
                lastReviewedRevision = Integer.valueOf((String) lastReviewedRevisionValue);
            } catch (NumberFormatException e) {
                // ignored
            }
        }
        return lastReviewedRevision;
    }

    public @Nullable String getReviewedRevisions() {
        return (reviewedRevisionsField != null) ? (String) workItem.getValue(reviewedRevisionsField) : null;
    }

    public @NotNull Link link() {
        return new Link(workItem, aggregatedCompare, compareAll, workflowAction);
    }

    public @NotNull Revisions createRevisions() {
        IPObjectList linkedRevisions = workItem.getLinkedRevisions();
        if (compareAll) {
            return new Revisions(linkedRevisions, (Integer) null);
        }
        Integer lastReviewedRevision = getLastReviewedRevision();
        String reviewedRevisions = getReviewedRevisions();
        if (reviewedRevisions != null) {
            return new Revisions(linkedRevisions, reviewedRevisions);
        } else {
            return new Revisions(linkedRevisions, lastReviewedRevision);
        }
    }

    private void performWFAction(@NotNull String actionName) {
        IWorkflowAction workflowAction = context.getAvailableWorkflowAction(workItem, actionName);
        if (workflowAction != null) {
            for (String requiredFeature : workflowAction.getRequiredFeatures()) {
                if (IWorkItem.KEY_RESOLUTION.equals(requiredFeature) && successfulReviewResolution != null) {
                    workItem.setEnumerationValue(IWorkItem.KEY_RESOLUTION, successfulReviewResolution);
                }
            }
            workItem.performAction(workflowAction.getActionId());
        }
    }

    public @NotNull Parameters updateWorkItem(@Nullable String newReviewedRevisions, @Nullable String newReviewer, boolean permittedToPerformWFAction) {
        if (newReviewedRevisions != null) {
            workItem.setValue(reviewedRevisionsField, newReviewedRevisions);
        }
        if (newReviewer != null) {
            workItem.setValue(reviewerField, workItem.getEnumerationOptionForField(reviewerField, newReviewer));
        }
        String commentTitle = reviewCommentTitle;
        if (permittedToPerformWFAction && workflowAction != null) {
            switch (workflowAction) {
            case successfulReview:
                commentTitle = successfulReviewCommentTitle != null ? successfulReviewCommentTitle : "";
                performWFAction(Objects.requireNonNull(successfulReviewWorkflowAction));
                break;
            case unsuccessfulReview:
                commentTitle = unsuccessfulReviewCommentTitle != null ? unsuccessfulReviewCommentTitle : "";
                performWFAction(Objects.requireNonNull(unsuccessfulReviewWorkflowAction));
                break;
            }
        }
        if (commentText != null && !commentText.isEmpty()) {
            IComment comment = workItem.createComment(Text.plain(commentText), commentTitle, null);
            comment.save();
        }
        return this;
    }

    public @NotNull Parameters assignReviewerAndSave(@Nullable String reviewer) {
        return updateWorkItemAndSave(null, reviewer, false);
    }

    public @NotNull Parameters assignReviewerAndSaveInTX(@Nullable String reviewer) {
        return updateWorkItemAndSaveInTX(null, reviewer, false);
    }

    public @NotNull Parameters updateWorkItemAndSaveInTX(@Nullable final String newReviewedRevisions, @Nullable final String newReviewer, final boolean permittedToPerformWFAction) {
        return TransactionExecuter.execute(new RunnableWEx<Parameters>() {
            @Override
            public Parameters runWEx() throws Exception {
                return updateWorkItemAndSave(newReviewedRevisions, newReviewer, permittedToPerformWFAction);
            }
        });
    }

    private @NotNull Parameters updateWorkItemAndSave(@Nullable String newReviewedRevisions, @Nullable String newReviewer, boolean permittedToPerformWFAction) {
        updateWorkItem(newReviewedRevisions, newReviewer, permittedToPerformWFAction);
        workItem.save();
        return this;
    }

    private boolean isInReviewStatus() {
        if (inReviewStatus == null) {
            return true;
        }
        IStatusOpt status = workItem.getStatus();
        if (inReviewStatus != null && status != null) {
            return inReviewStatus.equals(status.getId());
        }
        return false;
    }

    public @Nullable String getInReviewStatus() {
        return inReviewStatus;
    }

    public boolean isSuccessfulWorkflowActionConfigured() {
        return successfulReviewWorkflowAction != null;
    }

    public boolean isUnsuccessfulWorkflowActionConfigured() {
        return unsuccessfulReviewWorkflowAction != null;
    }

    public boolean isLocationPermittedForFastTrack(@NotNull ILocation location) {
        String path = location.getLocationPath();
        if (path != null) {
            return Objects.requireNonNull(fastTrackPermittedLocationPattern).matcher(path).matches();
        }
        return true;
    }

    public @Nullable String getFastTrackReviewer() {
        return fastTrackReviewer;
    }

    public boolean unresolvedWorkItemWithRevisionsNeedsTimePoint() {
        return unresolvedWorkItemWithRevisionsNeedsTimePoint;
    }

    private boolean hasReviewerRole() {
        if (reviewerRole == null) {
            return true;
        }
        String currentUser = context.getCurrentUser();
        if (currentUser == null) {
            return false;
        }
        return context.hasRole(currentUser, Objects.requireNonNull(reviewerRole), workItem.getContextId());
    }

    private boolean canStartReview() {
        return isInReviewStatus() && hasReviewerRole();
    }

    public boolean mustStartReview() {
        return canStartReview() && isCurrentReviewerEmptyAndNeedsToBeSet();
    }

    public boolean canReview() {
        return canStartReview() && isCurrentUserSetAsCurrentReviewerOrDoesNotHaveTo();
    }

    private boolean isCurrentUserSetAsCurrentReviewerOrDoesNotHaveTo() {
        if (reviewerField == null || !preventReviewConflicts) {
            return true;
        }
        String currentUser = context.getCurrentUser();
        return isUserSetAsCurrentReviewer(currentUser);
    }

    private boolean isUserSetAsCurrentReviewer(@Nullable String user) {
        IEnumOption currentReviewerOption = (IEnumOption) workItem.getValue(reviewerField);
        if (currentReviewerOption == null) {
            return false;
        }
        String currentReviewer = currentReviewerOption.getId();
        return currentReviewer.equals(user);
    }

    private boolean isCurrentReviewerEmptyAndNeedsToBeSet() {
        if (reviewerField == null || !preventReviewConflicts) {
            return false;
        }
        IEnumOption currentReviewerOption = (IEnumOption) workItem.getValue(reviewerField);
        return currentReviewerOption == null;
    }

    public boolean isOrWasPermittedReviewer(@Nullable String user) {
        if (user == null) {
            return true;
        }
        if (reviewerRole == null) {
            return true;
        }
        if (user.equals(fastTrackReviewer)) {
            return true;
        }
        if (pastReviewers.contains(user)) {
            return true;
        }
        return context.hasRole(user, Objects.requireNonNull(reviewerRole), workItem.getContextId());
    }

    public final class UserIdentity {

        private @Nullable String id = null;
        private @Nullable String name = null;

        UserIdentity(@Nullable String id) {
            if (id == null) {
                return;
            }
            this.id = id;
            name = context.getFullNameOfUser(id);
        }

        public boolean hasId(@NotNull String id) {
            return id.equals(this.id);
        }

        public boolean hasName(@NotNull String name) {
            return name.equals(this.name);
        }

        public boolean hasIdOrName(@NotNull String idOrName) {
            return hasId(idOrName) || hasName(idOrName);
        }

        public String label() {
            return name != null ? name : id;
        }

    }

    public @NotNull UserIdentity identityForUser(@Nullable String user) {
        return new UserIdentity(user);
    }

    public @NotNull UserIdentity identityForCurrentUser() {
        return identityForUser(context.getCurrentUser());
    }

}
