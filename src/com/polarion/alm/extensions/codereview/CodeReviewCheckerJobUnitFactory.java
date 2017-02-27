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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.ITrackerRevision;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.platform.announce.Announcement;
import com.polarion.platform.announce.IAnnouncerService;
import com.polarion.platform.context.IContextService;
import com.polarion.platform.jobs.GenericJobException;
import com.polarion.platform.jobs.IJobDescriptor;
import com.polarion.platform.jobs.IJobDescriptor.IJobParameterGroup;
import com.polarion.platform.jobs.IJobDescriptor.IJobParameterListType;
import com.polarion.platform.jobs.IJobDescriptor.IJobParameterType;
import com.polarion.platform.jobs.IJobStatus;
import com.polarion.platform.jobs.IJobUnit;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.IProgressMonitor;
import com.polarion.platform.jobs.spi.AbstractJobParameter;
import com.polarion.platform.jobs.spi.AbstractJobUnit;
import com.polarion.platform.jobs.spi.ArrayJobParameter;
import com.polarion.platform.jobs.spi.BasicJobDescriptor;
import com.polarion.platform.jobs.spi.JobParameterPrimitiveType;
import com.polarion.platform.jobs.spi.SimpleJobParameter;
import com.polarion.platform.persistence.IDataService;
import com.polarion.platform.persistence.model.IRevision;
import com.polarion.platform.repository.external.IExternalRepositoryProvider.IExternalRepository;
import com.polarion.platform.repository.external.IExternalRepositoryProviderRegistry;
import com.polarion.platform.security.ISecurityService;
import com.polarion.platform.service.repository.IRepositoryService;
import com.polarion.platform.service.repository.IRevisionMetaData;
import com.polarion.subterra.base.location.ILocation;
import com.polarion.subterra.base.location.Location;

@SuppressWarnings("nls")
public class CodeReviewCheckerJobUnitFactory implements IJobUnitFactory {

    public static final String JOB_NAME = "codereview.checker";

    @Override
    public String getName() {
        return JOB_NAME;
    }

    @Override
    public IJobDescriptor getJobDescriptor(IJobUnit jobUnit) {
        BasicJobDescriptor desc = new BasicJobDescriptor("Code Review Checker", jobUnit);
        JobParameterPrimitiveType stringType = new JobParameterPrimitiveType("String of characters", String.class);
        desc.addParameter(new ArrayJobParameter(desc.getRootParameterGroup(), "notificationReceivers", "Notification Receivers", stringType).setRequired(true));
        desc.addParameter(new SimpleJobParameter(desc.getRootParameterGroup(), "notificationSender", "Notification Sender", stringType).setRequired(true));
        desc.addParameter(new SimpleJobParameter(desc.getRootParameterGroup(), "notificationSubjectPrefix", "Notification Subject Prefix", stringType).setRequired(false));
        desc.addParameter(new LocationsJobParameter(desc.getRootParameterGroup(), "repositoryLocations", "Repository Locations").setRequired(true));
        desc.addParameter(new SimpleJobParameter(desc.getRootParameterGroup(), "permittedItemsQuery", "Permitted Items Query", stringType).setRequired(false));
        return desc;
    }

    private static final class LocationsJobParameter extends AbstractJobParameter {

        public LocationsJobParameter(IJobParameterGroup group, String name, String label) {
            super(group, name, label, new LocationsJobParameterType());
        }

        @Override
        public Object convertValue(Object value) {
            if (value instanceof List) {
                return ((List) value).stream().map(this::convertSingleItem).toArray(size -> new ILocation[size]);
            }
            if (value instanceof Map) {
                // list with one value is detected as map
                return new ILocation[] { convertSingleItem(((Map) value).values().iterator().next()) };
            }
            return new ILocation[] { convertSingleItem(value) };
        }

        private @Nullable ILocation convertSingleItem(@Nullable Object value) {
            if (value instanceof Map) {
                Map<String, Object> parameters = (Map<String, Object>) value;
                String repositoryName = Objects.toString(parameters.get("repositoryName"), IRepositoryService.DEFAULT);
                String locationPath = Objects.toString(parameters.get("locationPath"), "/");
                String revision = Objects.toString(parameters.get("revision"), null);
                return Location.getLocation(repositoryName, locationPath, revision);
            }
            throw new IllegalArgumentException("Unable to convert from " + value + " to ILocation class");
        }
    }

    private static final class LocationsJobParameterType implements IJobParameterListType {

        private final @NotNull IJobParameterType itemType = new JobParameterPrimitiveType("Location", ILocation.class);
        private final @NotNull Class<?> arrayClass = new ILocation[0].getClass();

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public String getLabel() {
            return "Locations";
        }

        @Override
        public Class getTypeClass() {
            return arrayClass;
        }

        @Override
        public IJobParameterType getItemType() {
            return itemType;
        }

        @Override
        public Object createList(List items) {
            return items.toArray(new ILocation[items.size()]);
        }

    }

    @Override
    public IJobUnit createJobUnit(String name) throws GenericJobException {
        return new CodeReviewCheckerJobUnit(name, this);
    }

    private /*@NotNull*/ IDataService dataService;
    private /*@NotNull*/ ITrackerService trackerService;
    private /*@NotNull*/ IRepositoryService repoService;
    private /*@NotNull*/ IAnnouncerService announcerService;
    private /*@NotNull*/ IExternalRepositoryProviderRegistry externalRepositoryProviderRegistry;
    private /*@NotNull*/ ISecurityService securityService;
    private /*@NotNull*/ IContextService contextService;

    public void setDataService(@NotNull IDataService dataService) {
        this.dataService = dataService;
    }

    public void setTrackerService(@NotNull ITrackerService trackerService) {
        this.trackerService = trackerService;
    }

    public void setRepoService(@NotNull IRepositoryService repoService) {
        this.repoService = repoService;
    }

    public void setAnnouncerService(@NotNull IAnnouncerService announcerService) {
        this.announcerService = announcerService;
    }

    public void setExternalRepositoryProviderRegistry(@NotNull IExternalRepositoryProviderRegistry externalRepositoryProviderRegistry) {
        this.externalRepositoryProviderRegistry = externalRepositoryProviderRegistry;
    }

    public void setSecurityService(@NotNull ISecurityService securityService) {
        this.securityService = securityService;
    }

    public void setContextService(@NotNull IContextService contextService) {
        this.contextService = contextService;
    }

    public final class CodeReviewCheckerJobUnit extends AbstractJobUnit {

        public CodeReviewCheckerJobUnit(String name, IJobUnitFactory creator) {
            super(name, creator);
        }

        private final @NotNull ParametersContext parametersContext = new PlatformParametersContext(securityService, trackerService, contextService, repoService);
        private /*@NotNull*/ String[] notificationReceivers;
        private /*@NotNull*/ String notificationSender;
        private @Nullable String notificationSubjectPrefix;
        private /*@NotNull*/ ILocation[] repositoryLocations;
        private @Nullable String permittedItemsQuery;

        @Override
        public void activate() throws GenericJobException {
            super.activate();
            if (notificationReceivers == null) {
                throw new GenericJobException("notificationReceivers is required");
            }
            if (notificationSender == null) {
                throw new GenericJobException("notificationSender is required");
            }
            if (repositoryLocations == null) {
                throw new GenericJobException("repositoryLocations is required");
            }
        }

        public void setNotificationReceivers(@NotNull String... notificationReceivers) {
            this.notificationReceivers = notificationReceivers;
        }

        public void setNotificationSender(@NotNull String notificationSender) {
            this.notificationSender = notificationSender;
        }

        public void setNotificationSubjectPrefix(@Nullable String notificationSubjectPrefix) {
            this.notificationSubjectPrefix = notificationSubjectPrefix;
        }

        public void setRepositoryLocations(@NotNull ILocation... repositoryLocations) {
            this.repositoryLocations = repositoryLocations;
        }

        public void setPermittedItemsQuery(@Nullable String permittedItemsQuery) {
            this.permittedItemsQuery = permittedItemsQuery;
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
            getLogger().info("notification receivers: " + Arrays.asList(notificationReceivers));
            getLogger().info("notification sender: " + notificationSender);
            getLogger().info("notification subject prefix: " + notificationSubjectPrefix);
            getLogger().info("repository locations: " + Arrays.asList(repositoryLocations));
            getLogger().info("permitted items query: " + permittedItemsQuery);

            if (progress.isCanceled()) {
                return;
            }

            List<ITrackerRevision> orphanedRevisions = new ArrayList<>();
            Set<IWorkItem> processedWIs = new HashSet<>();
            Set<IWorkItem> wisToBeReviewed = new HashSet<>();
            Predicate<IWorkItem> isForbiddenWorkItem = (permittedItemsQuery == null) ? (wi -> false)
                    : (wi -> trackerService.queryWorkItems("(" + permittedItemsQuery + ") AND id:(" + wi.getProjectId() + "/" + wi.getId() + ")", null).isEmpty());

            try {

                for (ILocation repositoryLocation : repositoryLocations) {
                    processLocation(progress, repositoryLocation, orphanedRevisions, processedWIs, wisToBeReviewed, isForbiddenWorkItem);
                }

                if (progress.isCanceled()) {
                    return;
                }

                if (!orphanedRevisions.isEmpty()) {
                    getLogger().info("orphaned revisions:");
                    orphanedRevisions.forEach(revision -> getLogger().info(String.format("- r%s | %s | %s | %s", revision.getName(), revision.getStringAuthor(), revision.getCreated(), revision.getMessage())));
                } else {
                    getLogger().info("no orphaned revisions");
                }

                if (progress.isCanceled()) {
                    return;
                }

                if (!wisToBeReviewed.isEmpty()) {
                    getLogger().info("Work Items to be reviewed again:");
                    wisToBeReviewed.forEach(wi -> getLogger().info(String.format("- %s %s", wi.getId(), wi.getTitle())));
                } else {
                    getLogger().info("no Work Items to be reviewed again");
                }

                if (progress.isCanceled()) {
                    return;
                }

                if (!orphanedRevisions.isEmpty() || !wisToBeReviewed.isEmpty()) {
                    sendNotification(orphanedRevisions, wisToBeReviewed);
                }

            } finally {
                // cleanup
                orphanedRevisions.forEach(revision -> revision.forget());
                processedWIs.forEach(wi -> wi.forget());
                wisToBeReviewed.forEach(wi -> wi.forget());
            }
        }

        private void processLocation(@NotNull IProgressMonitor progress, @NotNull ILocation location, @NotNull List<ITrackerRevision> orphanedRevisions, @NotNull Set<IWorkItem> processedWIs, @NotNull Set<IWorkItem> wisToBeReviewed, @NotNull Predicate<IWorkItem> isForbiddenWorkItem) {
            if (progress.isCanceled()) {
                return;
            }
            getLogger().info("revisions for location " + location + ":");

            if (repoService.getRepository(location.getRepositoryName()) != null) {
                processInternalLocation(progress, location, orphanedRevisions, processedWIs, wisToBeReviewed, isForbiddenWorkItem);
            } else {
                processExternalLocation(progress, location, orphanedRevisions, processedWIs, wisToBeReviewed, isForbiddenWorkItem);
            }
        }

        private void processInternalLocation(@NotNull IProgressMonitor progress, @NotNull ILocation location, @NotNull List<ITrackerRevision> orphanedRevisions, @NotNull Set<IWorkItem> processedWIs, @NotNull Set<IWorkItem> wisToBeReviewed, @NotNull Predicate<IWorkItem> isForbiddenWorkItem) {
            String startRevision = location.getRevision();
            if (startRevision == null) {
                startRevision = repoService.getReadOnlyConnection(location).getFirstRevision(location);
            }
            getLogger().info("startRevision: " + startRevision);

            String endRevision = repoService.getReadOnlyConnection(location).getLastRevision(location.removeRevision());
            getLogger().info("endRevision: " + endRevision);

            List<IRevisionMetaData> revisionsMetaData = repoService.getReadOnlyConnection(location).getRevisionsMetaData(location.setRevision(startRevision), endRevision, false);
            revisionsMetaData.stream()
                    .map(revisionMetaData -> dataService.getRevision(location.getRepositoryName(), revisionMetaData.getName()))
                    .map(revision -> trackerService.getTrackerRevision(revision))
                    .forEach(trackerRevision -> processRevision(progress, trackerRevision, orphanedRevisions, processedWIs, wisToBeReviewed, isForbiddenWorkItem));
        }

        private void processExternalLocation(@NotNull IProgressMonitor progress, @NotNull ILocation location, @NotNull List<ITrackerRevision> orphanedRevisions, @NotNull Set<IWorkItem> processedWIs, @NotNull Set<IWorkItem> wisToBeReviewed, @NotNull Predicate<IWorkItem> isForbiddenWorkItem) {
            String repositoryName = location.getRepositoryName();
            IExternalRepository repository = externalRepositoryProviderRegistry.getRepositoryByUniqueId(repositoryName);
            if (repository == null) {
                throw new IllegalArgumentException("Unknown repository '" + repositoryName + "'");
            }
            if ((location.getLocationPath() != null && !location.getLocationPath().equals("/")) || location.getRevision() != null) {
                throw new IllegalArgumentException("External location " + location + " may not specify path nor revision");
            }

            List<IRevision> revisions = dataService.searchInstances(IRevision.PROTO, IRevision.KEY_REPOSITORY_NAME + ":\"" + repositoryName + "\"", IRevision.KEY_CREATED);
            revisions.stream()
                    .map(revision -> trackerService.getTrackerRevision(revision))
                    .forEach(trackerRevision -> processRevision(progress, trackerRevision, orphanedRevisions, processedWIs, wisToBeReviewed, isForbiddenWorkItem));
        }

        private void processRevision(@NotNull IProgressMonitor progress, @NotNull ITrackerRevision revision, @NotNull List<ITrackerRevision> orphanedRevisions, @NotNull Set<IWorkItem> processedWIs, @NotNull Set<IWorkItem> wisToBeReviewed, @NotNull Predicate<IWorkItem> isForbiddenWorkItem) {
            if (progress.isCanceled()) {
                return;
            }
            getLogger().info("- revision " + revision.getName());
            List<IWorkItem> wis = ((List<IWorkItem>) revision.getLinkedWIs()).stream().filter(wi -> wi.getContextId().equals(getScope().getId()) && wi.getLinkedRevisions().contains(revision)).collect(Collectors.toList());
            if (wis.isEmpty()) {
                getLogger().info("  ... is orphaned");
                orphanedRevisions.add(revision);
            } else {
                getLogger().info("  ... is linked to:");
                wis.forEach(wi -> processWorkItem(progress, wi, processedWIs, wisToBeReviewed, isForbiddenWorkItem));
            }
        }

        private void processWorkItem(@NotNull IProgressMonitor progress, @NotNull IWorkItem wi, @NotNull Set<IWorkItem> processedWIs, @NotNull Set<IWorkItem> wisToBeReviewed, @NotNull Predicate<IWorkItem> isForbiddenWorkItem) {
            if (progress.isCanceled()) {
                return;
            }
            if (processedWIs.contains(wi)) {
                getLogger().info("      already processed " + wi.getId());
                return;
            }
            processedWIs.add(wi);
            boolean needsReview = false;
            if (isForbiddenWorkItem.test(wi)) {
                getLogger().info("      forbidden " + wi.getId());
                needsReview = true;
            } else {
                Parameters parameters = new Parameters(parametersContext, wi);
                Revisions revisions = parameters.createRevisions();
                if (wi.getResolution() == null) {
                    if (parameters.unresolvedWorkItemWithRevisionsNeedsTimePoint()) {
                        if (wi.getTimePoint() == null) {
                            getLogger().info("      unresolved " + wi.getId() + " without timepoint");
                            needsReview = true;
                        } else {
                            getLogger().info("      unresolved " + wi.getId() + " with timepoint " + wi.getTimePoint().getName());
                        }
                    } else {
                        getLogger().info("      unresolved " + wi.getId());
                    }
                } else {
                    getLogger().info("      resolved " + wi.getId());
                    if (revisions.hasRevisionsToReview()) {
                        getLogger().info("      ... has revisions to review");
                        needsReview = true;
                    }
                }
                if (revisions.hasRevisionsReviewedByNonReviewers(parameters)) {
                    getLogger().info("      ... has revisions reviewed by non-reviewers");
                    needsReview = true;
                }
                if (revisions.hasSelfReviewedRevisions(parameters)) {
                    getLogger().info("      ... has self-reviewed revisions");
                    needsReview = true;
                }
            }
            if (needsReview) {
                getLogger().info("      ... needs to be reviewed again");
                wisToBeReviewed.add(wi);
            }
        }

        private String generateNotificationContent(@NotNull List<ITrackerRevision> orphanedRevisions, @NotNull Set<IWorkItem> wisToBeReviewed) {
            String summary = orphanedRevisions.size() + " orphaned revisions, ";
            if (!wisToBeReviewed.isEmpty()) {
                summary += "<a href=\"" + System.getProperty("base.url") + "/polarion/redirect/workitems?query=id:(" + wisToBeReviewed.stream().map(wi -> wi.getProject().getId() + "/" + wi.getId()).collect(Collectors.joining(" ")) + ")\">";
            }
            summary += wisToBeReviewed.size();
            if (!wisToBeReviewed.isEmpty()) {
                summary += "</a>";
            }
            summary += " Work Items to be reviewed again";
            StringBuilder details = new StringBuilder();

            details.append("Orphaned revisions:\n\n");
            orphanedRevisions.forEach(revision -> {
                details.append("<a href=\"");
                details.append(getRevisionViewAbsoluteURL(revision.getViewURL()));
                details.append("\">");
                details.append(revision.getName());
                details.append("</a> ");
                details.append(CodeReviewServlet.escapeHTML(String.format("| %s | %s | %s", revision.getStringAuthor(), revision.getCreated(), revision.getMessage())));
                details.append('\n');
            });

            details.append('\n');

            details.append("Work Items to be reviewed again:\n\n");
            wisToBeReviewed.stream().sorted(Comparator.comparing(wi -> wi.getId())).forEachOrdered(wi -> {
                details.append("<a href=\"");
                details.append(System.getProperty("base.url"));
                details.append("/polarion/redirect/project/" + wi.getProject().getId() + "/workitem?id=" + wi.getId());
                details.append("\">");
                details.append(CodeReviewServlet.escapeHTML(wi.getId()));
                details.append("</a> ");
                details.append(CodeReviewServlet.escapeHTML(wi.getTitle()));
                details.append('\n');
            });

            return "<html><body><b>Summary:</b><br/><br/>" + summary.toString() + "<br/><br/>\r\n" + "<b>Details:</b> <pre>" + details.toString() + "</pre></body></html>";
        }

        private @NotNull String getRevisionViewAbsoluteURL(@NotNull String viewUrl) {
            URI uri = null;
            try {
                uri = new URI(viewUrl);
            } catch (Exception e) {
                //do nothing
            }
            if (uri != null && !uri.isAbsolute()) {
                viewUrl = System.getProperty("base.url") + viewUrl;
            }
            return viewUrl;
        }

        private void sendNotification(@NotNull List<ITrackerRevision> orphanedRevisions, @NotNull Set<IWorkItem> wisToBeReviewed) {
            if (notificationReceivers.length == 0) {
                return;
            }
            String sender = notificationSender;
            String subjectPrefix = notificationSubjectPrefix;
            if (subjectPrefix != null) {
                subjectPrefix = subjectPrefix + " ";
            } else {
                subjectPrefix = "";
            }

            Announcement announcement = new Announcement();
            announcement.setSender(sender);
            announcement.setReceivers(notificationReceivers);
            announcement.setContentType("text/html");

            announcement.setSubject(subjectPrefix + "Code Review Checker Results");
            announcement.setContent(generateNotificationContent(orphanedRevisions, wisToBeReviewed));

            try {
                announcerService.sendAnnouncement(IAnnouncerService.SMTP_TRANSPORT, announcement);
            } catch (Exception e) {
                getLogger().error("Unable to send notification mail: " + e.getLocalizedMessage(), e);
            }
        }
    }

}
