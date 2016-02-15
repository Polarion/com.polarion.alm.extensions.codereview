/*
 * Copyright (C) 2004-2016 Polarion Software
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

import java.io.IOException;
import java.io.InputStream;
import java.security.PrivilegedExceptionAction;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.polarion.alm.shared.api.utils.links.HtmlLink;
import com.polarion.alm.shared.api.utils.links.HtmlLinkFactory;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.IStatusOpt;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowAction;
import com.polarion.core.util.RunnableWEx;
import com.polarion.platform.TransactionExecuter;
import com.polarion.platform.context.IContextService;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.persistence.model.IPObjectList;
import com.polarion.platform.security.ISecurityService;
import com.polarion.platform.service.repository.IRepositoryService;
import com.polarion.subterra.base.data.identification.IContextId;
import com.polarion.subterra.base.location.ILocation;

@SuppressWarnings("nls")
public class Parameters {

    private static final Logger log = Logger.getLogger(Parameters.class);

    private static @NotNull IContextService contextService = PlatformContext.getPlatform().lookupService(IContextService.class);
    private static @NotNull IRepositoryService repositoryService = PlatformContext.getPlatform().lookupService(IRepositoryService.class);
    private static @NotNull ISecurityService securityService = PlatformContext.getPlatform().lookupService(ISecurityService.class);
    private static @NotNull ITrackerService trackerService = PlatformContext.getPlatform().lookupService(ITrackerService.class);

    // URL parameters
    private static final String PARAM_WORK_ITEM_ID = CodeReviewServlet.PARAM_ID;
    private static final String PARAM_PROJECT_ID = "projectId";
    private static final String PARAM_AGGREGATED_COMPARE = "aggregated";
    private static final String PARAM_COMPARE_ALL = "compareAll";
    private static final String PARAM_WORKFLOW_ACTION = "workflowAction";

    // configuration parameters
    private static final String CONFIG_LAST_REVIEWED_REVISION_FIELD = "lastReviewedRevisionField";
    private static final String CONFIG_REVIEWED_REVISIONS_FIELD = "reviewedRevisionsField";
    private static final String CONFIG_REVIEWER_FIELD = "reviewerField";
    private static final String CONFIG_IN_REVIEW_STATUS = "inReviewStatus";
    private static final String CONFIG_SUCCESSFUL_REVIEW_WF_ACTION = "successfulReviewWorkflowAction";
    private static final String CONFIG_SUCCESSFUL_REVIEW_RESOLUTION = "successfulReviewResolution";
    private static final String CONFIG_FAST_TRACK_PERMITTED_LOCATION_PATTERN = "fastTrackPermittedLocationPattern";
    private static final String CONFIG_FAST_TRACK_REVIEWER = "fastTrackReviewer";
    private static final String CONFIG_UNRESOLVED_WORK_ITEM_WITH_REVISIONS_NEEDS_TIMEPOINT = "unresolvedWorkItemWithRevisionsNeedsTimePoint";

    public static enum WorkflowAction {
        successfulReview;
    }

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
    private final @Nullable Pattern fastTrackPermittedLocationPattern;
    private final @Nullable String fastTrackReviewer;
    private final boolean unresolvedWorkItemWithRevisionsNeedsTimePoint;

    private Parameters(@NotNull IWorkItem workItem, boolean aggregatedCompare, boolean compareAll, @Nullable WorkflowAction workflowAction, @NotNull Function<IWorkItem, Properties> configurationLoader) {
        super();
        this.workItem = workItem;
        this.aggregatedCompare = aggregatedCompare;
        this.compareAll = compareAll;
        this.workflowAction = workflowAction;
        Properties configuration = configurationLoader.apply(workItem);
        lastReviewedRevisionField = configuration.getProperty(CONFIG_LAST_REVIEWED_REVISION_FIELD);
        reviewedRevisionsField = configuration.getProperty(CONFIG_REVIEWED_REVISIONS_FIELD);
        reviewerField = configuration.getProperty(CONFIG_REVIEWER_FIELD);
        inReviewStatus = configuration.getProperty(CONFIG_IN_REVIEW_STATUS);
        successfulReviewWorkflowAction = configuration.getProperty(CONFIG_SUCCESSFUL_REVIEW_WF_ACTION);
        successfulReviewResolution = configuration.getProperty(CONFIG_SUCCESSFUL_REVIEW_RESOLUTION);
        String fastTrackPermittedLocationPatternStr = configuration.getProperty(CONFIG_FAST_TRACK_PERMITTED_LOCATION_PATTERN);
        fastTrackPermittedLocationPattern = Pattern.compile(fastTrackPermittedLocationPatternStr);
        fastTrackReviewer = configuration.getProperty(CONFIG_FAST_TRACK_REVIEWER);
        unresolvedWorkItemWithRevisionsNeedsTimePoint = Boolean.parseBoolean(configuration.getProperty(CONFIG_UNRESOLVED_WORK_ITEM_WITH_REVISIONS_NEEDS_TIMEPOINT));
    }

    public static @NotNull Function<IWorkItem, Properties> repositoryConfigurationLoader() {
        return wi -> loadConfiguration(wi);
    }

    public static @NotNull Function<IWorkItem, Properties> perContextCachingConfigurationLoader(@NotNull Function<IWorkItem, Properties> loader) {
        Map<IContextId, Properties> cache = new HashMap<>();
        return wi -> {
            IContextId contextId = wi.getContextId();
            Properties properties = cache.get(contextId);
            if (properties == null) {
                properties = loader.apply(wi);
                cache.put(contextId, properties);
            }
            return properties;
        };
    }

    private static @NotNull Properties loadConfiguration(@NotNull IWorkItem workItem) {
        ILocation location = contextService.getContextforId(workItem.getContextId()).getLocation();
        try {
            final Properties configuration = new Properties();
            location = Objects.requireNonNull(location).append(".polarion/codereview/codereview.properties");
            final ILocation f_location = location;
            securityService.doAsSystemUser(new PrivilegedExceptionAction<Void>() {
                @Override
                public Void run() throws IOException {
                    InputStream content = repositoryService.getReadOnlyConnection(f_location).getContent(f_location);
                    configuration.load(content);
                    return null;
                }
            });
            return configuration;
        } catch (Exception e) {
            log.error("Unexpected error occurred while reading from location " + location, e);
            throw new RuntimeException(e);
        }
    }

    private static @Nullable WorkflowAction parseWorkflowAction(@Nullable String s) {
        if (s == null) {
            return null;
        }
        return WorkflowAction.valueOf(s);
    }

    public Parameters(@NotNull HttpServletRequest request, @NotNull Function<IWorkItem, Properties> configurationLoader) {
        this(trackerService.findWorkItem(request.getParameter(PARAM_PROJECT_ID), request.getParameter(PARAM_WORK_ITEM_ID)), Boolean.parseBoolean(request.getParameter(PARAM_AGGREGATED_COMPARE)),
                Boolean.parseBoolean(request.getParameter(PARAM_COMPARE_ALL)), parseWorkflowAction(request.getParameter(PARAM_WORKFLOW_ACTION)), configurationLoader);
    }

    public Parameters(@NotNull IWorkItem workItem, @NotNull Function<IWorkItem, Properties> configurationLoader) {
        this(workItem, false, false, null, configurationLoader);
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
            lastReviewedRevision = Integer.valueOf((String) lastReviewedRevisionValue);
        }
        return lastReviewedRevision;
    }

    public @Nullable String getReviewedRevisions() {
        return (reviewedRevisionsField != null) ? (String) workItem.getValue(reviewedRevisionsField) : null;
    }

    public class Link {

        private boolean linkAggregatedCompare;
        private boolean linkCompareAll;
        private @Nullable WorkflowAction linkWorkflowAction;
        private @NotNull List<SimpleEntry<String, String>> additionalParameters = new ArrayList<>();

        public Link() {
            linkAggregatedCompare = aggregatedCompare;
            linkCompareAll = compareAll;
            linkWorkflowAction = workflowAction;
        }

        public @NotNull HtmlLink toHtmlLink() {
            StringBuilder link = new StringBuilder("/polarion/codereview?");
            link.append(PARAM_WORK_ITEM_ID);
            link.append("=");
            link.append(workItem.getId());
            link.append("&");
            link.append(PARAM_PROJECT_ID);
            link.append("=");
            link.append(workItem.getProjectId());
            if (linkAggregatedCompare) {
                link.append("&");
                link.append(PARAM_AGGREGATED_COMPARE);
                link.append("=true");
            }
            if (linkCompareAll) {
                link.append("&");
                link.append(PARAM_COMPARE_ALL);
                link.append("=true");
            }
            if (linkWorkflowAction != null) {
                link.append("&");
                link.append(PARAM_WORKFLOW_ACTION);
                link.append("=");
                link.append(linkWorkflowAction);
            }
            for (Map.Entry<String, String> additionalParameterEntry : additionalParameters) {
                link.append("&");
                link.append(additionalParameterEntry.getKey());
                link.append("=");
                link.append(additionalParameterEntry.getValue());
            }
            return Objects.requireNonNull(HtmlLinkFactory.fromEncodedRelativeUrl(link.toString()));
        }

        public @NotNull Link withAggregatedCompare(boolean aggregatedCompare) {
            linkAggregatedCompare = aggregatedCompare;
            return this;
        }

        public @NotNull Link withCompareAll(boolean compareAll) {
            linkCompareAll = compareAll;
            return this;
        }

        public @NotNull Link withWorkflowAction(@Nullable WorkflowAction workflowAction) {
            linkWorkflowAction = workflowAction;
            return this;
        }

        public @NotNull Link withAdditionalParameter(@NotNull String name, @NotNull String value) {
            additionalParameters.add(new SimpleEntry<String, String>(name, value));
            return this;
        }

    }

    public @NotNull Link link() {
        return new Link();
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
        IWorkflowAction[] availableActions = trackerService.getWorkflowManager().getAvailableActions(workItem);
        for (IWorkflowAction availableAction : availableActions) {
            if (actionName.equals(availableAction.getNativeActionId())) {
                for (String requiredFeature : availableAction.getRequiredFeatures()) {
                    if (IWorkItem.KEY_RESOLUTION.equals(requiredFeature) && successfulReviewResolution != null) {
                        workItem.setEnumerationValue(IWorkItem.KEY_RESOLUTION, successfulReviewResolution);
                    }
                }
                workItem.performAction(availableAction.getActionId());
                return;
            }
        }
    }

    public @NotNull Parameters updateWorkItem(@Nullable String newReviewedRevisions, @Nullable String newReviewer, boolean permittedToPerformWFAction) {
        if (newReviewedRevisions != null) {
            workItem.setValue(reviewedRevisionsField, newReviewedRevisions);
        }
        if (newReviewer != null) {
            workItem.setValue(reviewerField, workItem.getEnumerationOptionForField(reviewerField, newReviewer));
        }
        if (permittedToPerformWFAction && workflowAction != null) {
            switch (workflowAction) {
            case successfulReview:
                performWFAction(Objects.requireNonNull(successfulReviewWorkflowAction));
                break;
            }
        }
        return this;
    }

    public @NotNull Parameters storeWorkItem(@Nullable final String newReviewedRevisions, @Nullable final String newReviewer, final boolean permittedToPerformWFAction) {
        TransactionExecuter.execute(new RunnableWEx<Void>() {
            @Override
            public Void runWEx() throws Exception {
                updateWorkItem(newReviewedRevisions, newReviewer, permittedToPerformWFAction);
                workItem.save();
                return null;
            }
        });
        return this;
    }

    public boolean isInReviewStatus() {
        if (inReviewStatus == null) {
            return true;
        }
        IStatusOpt status = workItem.getStatus();
        if (inReviewStatus != null && status != null) {
            return inReviewStatus.equals(status.getId());
        }
        return false;
    }

    public boolean isWorkflowActionConfigured() {
        return successfulReviewWorkflowAction != null;
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

}
