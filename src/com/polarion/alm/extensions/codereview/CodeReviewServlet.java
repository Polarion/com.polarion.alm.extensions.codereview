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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.polarion.alm.shared.api.SharedContext;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.alm.shared.api.utils.html.HtmlContentBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlFragmentBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlTagBuilder;
import com.polarion.alm.shared.api.utils.links.HtmlLinkFactory;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.ITrackerUser;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.persistence.model.IRevision;
import com.polarion.platform.repository.external.ExternalRepositoryUtils;
import com.polarion.platform.repository.external.ExternalRepositoryUtils.Action;
import com.polarion.platform.security.ISecurityService;
import com.polarion.platform.service.repository.ILocationChangeMetaData;
import com.polarion.platform.service.repository.IRepositoryReadOnlyConnection;
import com.polarion.platform.service.repository.IRepositoryService;
import com.polarion.platform.service.repository.RepositoryException;
import com.polarion.subterra.base.location.ILocation;
import com.polarion.subterra.base.location.Location;

@SuppressWarnings("nls")
public class CodeReviewServlet extends HttpServlet {

    private static final Logger logger = Logger.getLogger(CodeReviewServlet.class);

    private static final long serialVersionUID = -6763051117875062248L;

    private static IRepositoryService repoService = PlatformContext.getPlatform().lookupService(IRepositoryService.class);

    private static ITrackerService trackerService = PlatformContext.getPlatform().lookupService(ITrackerService.class);

    private static ISecurityService securityService = PlatformContext.getPlatform().lookupService(ISecurityService.class);

    static final Pattern fileExtension = Pattern.compile("\\.(java|groovy|sh|txt|properties|xml|classpath|js|css|project|jsp|vm|html|htm)$");

    static final String PARAM_ID = "id";
    static final String PARAM_REVIEW_SELECTED = "reviewSelected";
    static final String PARAM_REVISIONS_TO_MARK = "revisionsToMark";
    static final String PARAM_SET_CURRENT_REVIEWER = "setCurrentReviewer";

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String id = request.getParameter(PARAM_ID);

            if (id == null) {
                String uri = request.getRequestURI();
                String relativeUri = uri.substring("/polarion/".length());

                if (relativeUri.startsWith("codereview/compare/")) {
                    serveCompare(request, response, relativeUri.substring("codereview/compare/".length()));
                    return;
                }
                serveResource(response, relativeUri);
                return;
            }

            if (request.getParameter(PARAM_REVIEW_SELECTED) != null) {
                doReviewSelected(request, response);
            } else if (request.getParameter(PARAM_SET_CURRENT_REVIEWER) != null) {
                doSetCurrentReviewer(request, response);
            } else {
                serveMain(request, response);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void doReviewSelected(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
        Collection<String> revisionsToMark = new HashSet<>();
        String[] revisionsToMarkArray = request.getParameterValues(PARAM_REVISIONS_TO_MARK);
        if (revisionsToMarkArray != null) {
            revisionsToMark.addAll(Arrays.asList(revisionsToMarkArray));
        }

        final Parameters parameters = createParameters(request);
        if (parameters.canReview()) {
            String currentUser = securityService.getCurrentUser();
            Revisions revisions = parameters.createRevisions();
            String reviewedRevisions = revisions.markReviewed(revisionsToMark, currentUser, parameters).getReviewedRevisionsFieldValue();
            parameters.updateWorkItemAndSaveInTX(reviewedRevisions, currentUser, !revisions.hasRevisionsToReview());
        }

        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private @NotNull Parameters createParameters(@NotNull HttpServletRequest request) {
        return new Parameters(PlatformParametersContext.createFromPlatform(), request);
    }

    private void doSetCurrentReviewer(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
        final Parameters parameters = createParameters(request);
        if (parameters.mustStartReview()) {
            String currentUser = securityService.getCurrentUser();
            parameters.assignReviewerAndSaveInTX(currentUser);
        }
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private void serveCompare(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull String path) {
        TransactionalExecutor.executeSafelyInReadOnlyTransaction(transaction -> {
            try {
                serveCompareInternal(transaction.context(), request, response, path);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
            return null;
        });
    }

    private void serveCompareInternal(@NotNull SharedContext context, @NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull String path) throws IOException {
        String content;
        try {
            String revision = getRevisionParameter(request, "revision");
            String revision2 = getRevisionParameter(request, "revision2");

            ILocation repositoryLocation = Location.getLocationWithRepository(IRepositoryService.DEFAULT, "/"); //$NON-NLS-1$
            IRepositoryReadOnlyConnection connection = repoService.getReadOnlyConnection(repositoryLocation);

            path = URLDecoder.decode(path, StandardCharsets.UTF_8.name());
            ILocation fileLocation = repositoryLocation.append(path);

            HtmlFragmentBuilder builder = context.createHtmlFragmentBuilderFor().gwt();
            if (revision == null) {
                appendContent(builder, connection, fileLocation);
            } else {
                if (revision2 == null) {
                    revision2 = revision;
                    revision = connection.getPreviousState(revision);
                }
                ILocation location1 = fileLocation.setRevision(revision);
                ILocation location2 = fileLocation.setRevision(revision2);

                if (connection.isFile(location1) && connection.isFile(location2)) {
                    new FileCompareRenderer(connection, builder).append(location1, location2);
                } else if (connection.isFile(location1)) {
                    appendContent(builder, connection, location1).attributes().style("background:#ffe6e6;");
                } else {
                    appendContent(builder, connection, location2).attributes().style("background:#e6ffe6;");
                }
            }
            content = createPageHtml(builder.toString());
        } catch (RepositoryException e) {
            content = e.getMessage();
        }
        OutputStream out = response.getOutputStream();
        try {

            serveContent(response, out, content);
        } finally {
            out.close();
        }
    }

    private @NotNull HtmlTagBuilder appendContent(@NotNull HtmlFragmentBuilder builder, @NotNull IRepositoryReadOnlyConnection connection, @NotNull ILocation location) {
        HtmlTagBuilder compareContainer = builder.tag().div();
        compareContainer.attributes().className("cr_file_content");

        HtmlTagBuilder pre = compareContainer.append().tag().pre();
        HtmlTagBuilder code = pre.append().tag().byName("code");
        appendStyle(location, code);
        code.append().text(getStringContent(connection, location));
        return compareContainer;
    }

    private @Nullable String getRevisionParameter(@NotNull HttpServletRequest request, @NotNull String key) {
        String value = request.getParameter(key);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value;
    }

    private void serveMain(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
        TransactionalExecutor.executeSafelyInReadOnlyTransaction(transaction -> {
            try {
                OutputStream out = response.getOutputStream();
                try {
                    String content = render(transaction.context(), createParameters(request));
                    serveContent(response, out, content);
                } finally {
                    out.close();
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
            return null;
        });
    }

    private void serveContent(@NotNull HttpServletResponse response, @NotNull OutputStream out, @NotNull String content) throws UnsupportedEncodingException, IOException {
        response.setContentType("text/html;charset=utf-8");
        byte[] bout = content.getBytes(StandardCharsets.UTF_8.name());
        response.setIntHeader("Content-Length", bout.length);
        response.setContentType("text/html");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        out.write(bout);
        out.flush();
    }

    private void serveResource(@NotNull HttpServletResponse response, @NotNull String uri) {
        try {
            setContentType(uri, response);
            if (uri.startsWith("codereview/")) {
                uri = uri.substring("codereview/".length());
            }
            copyResource(getServletContext().getResourceAsStream(uri), response.getOutputStream());
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private static void setContentType(@NotNull String uri, @NotNull HttpServletResponse response) {
        if (uri.endsWith(".js")) {
            response.setContentType("text/javascript");
        } else if (uri.endsWith(".html")) {
            response.setContentType("text/html");
        } else if (uri.endsWith(".png")) {
            response.setContentType("image/png");
        } else if (uri.endsWith("css")) {
            response.setContentType("text/css");
        }
    }

    private static void copyResource(@NotNull InputStream is, @NotNull OutputStream os) throws IOException {
        try {
            try {
                byte[] buf = new byte[64 * 1024];
                int len;
                while ((len = is.read(buf)) != -1) {
                    os.write(buf, 0, len);
                }
                os.flush();
            } finally {
                os.close();
            }
        } finally {
            is.close();
        }
    }

    private @NotNull String render(@NotNull SharedContext context, @NotNull Parameters parameters) {
        IWorkItem workItem = parameters.getWorkItem();
        boolean aggregated = parameters.isAggregatedCompare();

        HtmlFragmentBuilder builder = context.createHtmlFragmentBuilderFor().gwt();

        HtmlTagBuilder nav = builder.tag().div();
        nav.attributes().className("cr_nav");
        HtmlTagBuilder fileInfo = builder.tag().div();

        if (aggregated) {
            HtmlTagBuilder link = nav.append().tag().a();
            link.attributes().href(parameters.link().withAggregatedCompare(false).toHtmlLink());
            link.append().text("Show Regular View");
        } else {
            HtmlTagBuilder link = nav.append().tag().a();
            link.attributes().href(parameters.link().withAggregatedCompare(true).toHtmlLink());
            link.append().text("Show Aggregated View");
        }

        if (workItem.isPersisted()) {
            HtmlTagBuilder container = appendMainContainer(builder);

            ILocation repoLocation = Location.getLocationWithRepository(IRepositoryService.DEFAULT, "/"); //$NON-NLS-1$
            IRepositoryReadOnlyConnection connection = repoService.getReadOnlyConnection(repoLocation);

            List<IRevision> revisions = parameters.createRevisions().getComparableRevisionsToReview();

            if (aggregated) {
                for (IRevision revision : revisions) {
                    for (ILocationChangeMetaData metaData : revision.getChangedLocations()) {
                        if (metaData.isModified()) {
                            if (!wasModifiedAfter(metaData, revision, revisions)) {
                                try {
                                    processLocationMetaDataAggregated(container.append(), connection, metaData, revision, revisions, fileInfo);
                                } catch (Exception e) {
                                    logger.error(e.getMessage(), e);
                                }
                            }
                        } else {
                            try {
                                processLocationMetaDataAggregated(container.append(), connection, metaData, revision, revisions, fileInfo);
                            } catch (Exception e) {
                                logger.error(e.getMessage(), e);
                            }
                        }
                    }
                }

            } else {
                for (IRevision revision : revisions) {
                    appendRevisionLabel(container, revision);
                    for (ILocationChangeMetaData metaData : revision.getChangedLocations()) {
                        try {
                            processLocationMetaData(container.append(), connection, metaData, revision, fileInfo);
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                }
            }

            workItem.forget();
        }
        builder.finished();
        String pageContent = builder.toString();

        return createPageHtml(pageContent);
    }

    private @NotNull String createPageHtml(@NotNull String pageContent) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html><head><title>Code Review</title><link rel=\"shortcut icon\" href=\"/polarion/ria/images/favicon.ico\" />" +
                "<link rel=\"stylesheet\" href=\"/polarion/codereview/styles/styles.css\" type=\"text/css\">" +
                " <script src=\"/polarion/codereview/highlight.pack.js\"></script>" +
                " <script src=\"/polarion/codereview/script.js\"></script>" +
                " <script src=\"/polarion/codereview/jquery-3.0.0.min.js\"></script>" +
                " <script src=\"/polarion/codereview/sticky-kit.min.js\"></script>" +
                "<link href=\"/polarion/codereview/styles/mono-blue.css\" rel=\"stylesheet\" type=\"text/css\">" +
                "</head><body>" +
                pageContent + "<script type=\"text/javascript\">hljs.initHighlightingOnLoad();</script>" + "</body></html>";
    }

    private boolean wasModifiedAfter(@NotNull ILocationChangeMetaData metaData, @NotNull IRevision revisionMetaData, @NotNull List<IRevision> allRevisions) {
        for (IRevision revision : allRevisions) {
            if (getRevision(revision) > getRevision(revisionMetaData)) {
                Collection<ILocationChangeMetaData> locationMetadatas = revision.getChangedLocations();
                for (ILocationChangeMetaData locMetadata : locationMetadatas) {
                    if (locMetadata.isModified()) {
                        ILocation changeLocationTo = metaData.getChangeLocationTo().removeRevision();
                        ILocation changeLocationTo2 = locMetadata.getChangeLocationTo().removeRevision();
                        if (Objects.equals(changeLocationTo, changeLocationTo2)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private @NotNull ILocation getFirstPrevState(@NotNull ILocationChangeMetaData metaData, @NotNull List<IRevision> allRevisions) {
        for (IRevision revision : allRevisions) {
            Collection<ILocationChangeMetaData> locationMetadatas = revision.getChangedLocations();
            for (ILocationChangeMetaData locMetadata : locationMetadatas) {
                if (locMetadata.isModified()) {
                    ILocation changeLocationTo = metaData.getChangeLocationTo().removeRevision();
                    ILocation changeLocationTo2 = locMetadata.getChangeLocationTo().removeRevision();
                    if (Objects.equals(changeLocationTo, changeLocationTo2)) {
                        return getPreviousState(locMetadata.getChangeLocationTo());
                    }
                }
            }
        }
        return getPreviousState(metaData.getChangeLocationTo());
    }

    static int getRevision(@NotNull IRevision revision) {
        return Integer.parseInt(revision.getName());
    }

    private void appendRevisionLabel(@NotNull HtmlTagBuilder container, @NotNull IRevision revision) {
        HtmlTagBuilder revisionLabel = container.append().tag().div();
        revisionLabel.attributes().className("cr_revision_label");
        HtmlTagBuilder revisionLink = revisionLabel.append().tag().a();
        revisionLink.attributes().href(HtmlLinkFactory.fromEncodedRelativeUrl(revision.getViewURL()));
        revisionLink.attributes().target("_blank");
        revisionLink.append().text(revision.getName() + " - " + revision.getMessage());
        String author = revision.getStringAuthor();
        if (author != null && !author.isEmpty()) {
            ITrackerUser trackerUser = trackerService.getTrackerUser(author);
            String name = trackerUser.isUnresolvable() ? trackerUser.getId() : trackerUser.getName();
            revisionLink.append().text(" (by " + name + ")");
        }
    }

    private @NotNull HtmlTagBuilder appendMainContainer(@NotNull HtmlFragmentBuilder builder) {
        HtmlTagBuilder container = builder.tag().div();
        container.attributes().id("codeReviewContainer").className("cr_main_container");
        return container;
    }

    private void processLocationMetaData(@NotNull HtmlContentBuilder builder, @NotNull IRepositoryReadOnlyConnection connection, @NotNull ILocationChangeMetaData metaData, @NotNull IRevision revision, @NotNull HtmlTagBuilder fileInfo) {
        ILocation changeLocationTo = metaData.getChangeLocationTo();
        HtmlTagBuilder div = builder.tag().div();
        div.attributes().className("change");
        div.attributes().id(createElementIdFromLocation(changeLocationTo));

        HtmlContentBuilder boxBuilder = div.append();
        if (metaData.isCopied() && (metaData.isRemoved() || metaData.isCreated())) {
            //rename?
        } else if (metaData.isCreated() || metaData.isCopied()) {
            appendFileInfo(boxBuilder, changeLocationTo, metaData, revision);
            appendFileInfoLite(fileInfo.append(), changeLocationTo, metaData, revision);
            if (isValidFileForCompare(changeLocationTo, connection)) {
                appendHTMLContent(boxBuilder, connection, changeLocationTo, true);
            } else {
                appendNotTextFileWarning(boxBuilder);
            }
        } else if (metaData.isRemoved()) {
            ILocation previousState = getPreviousState(changeLocationTo);
            appendFileInfo(boxBuilder, previousState, metaData, revision);
            appendFileInfoLite(fileInfo.append(), previousState, metaData, revision);
            if (connection.exists(previousState)) {
                if (isValidFileForCompare(previousState, connection)) {
                    appendHTMLContent(boxBuilder, connection, previousState, false);
                } else {
                    appendNotTextFileWarning(boxBuilder);
                }
            } else {
                appendFileRemovedDuringCopy(builder);
            }
        } else if (metaData.isModified()) {
            ILocation previousState = getPreviousState(changeLocationTo);
            appendFileInfo(boxBuilder, changeLocationTo, metaData, revision);
            appendFileInfoLite(fileInfo.append(), changeLocationTo, metaData, revision);
            if (isValidFileForCompare(changeLocationTo, connection)) {
                new FileCompareRenderer(connection, boxBuilder).append(previousState, changeLocationTo);
            } else {
                appendNotTextFileWarning(boxBuilder);
            }
        }
    }

    private void processLocationMetaDataAggregated(@NotNull HtmlContentBuilder builder, @NotNull IRepositoryReadOnlyConnection connection, @NotNull ILocationChangeMetaData metaData, @NotNull IRevision revision, @NotNull List<IRevision> allRevisions, @NotNull HtmlTagBuilder fileInfo) {
        ILocation changeLocationTo = metaData.getChangeLocationTo();
        HtmlTagBuilder div = builder.tag().div();
        div.attributes().className("change");
        div.attributes().id(createElementIdFromLocation(changeLocationTo));

        HtmlContentBuilder boxBuilder = div.append();
        if (metaData.isCopied() && (metaData.isRemoved() || metaData.isCreated())) {
            //rename?
        } else if (metaData.isCreated() || metaData.isCopied()) {
            appendFileInfo(boxBuilder, changeLocationTo, metaData, revision);
            appendFileInfoLite(fileInfo.append(), changeLocationTo, metaData, revision);
            if (isValidFileForCompare(changeLocationTo, connection)) {
                appendHTMLContent(boxBuilder, connection, changeLocationTo, true);
            } else {
                appendNotTextFileWarning(boxBuilder);
            }
        } else if (metaData.isRemoved()) {
            ILocation previousState = getPreviousState(changeLocationTo);
            appendFileInfo(boxBuilder, previousState, metaData, revision);
            appendFileInfoLite(fileInfo.append(), previousState, metaData, revision);
            if (connection.exists(previousState)) {
                if (isValidFileForCompare(previousState, connection)) {
                    appendHTMLContent(boxBuilder, connection, previousState, false);
                } else {
                    appendNotTextFileWarning(boxBuilder);
                }
            } else {
                appendFileRemovedDuringCopy(builder);
            }
        } else if (metaData.isModified()) {
            ILocation previousState = getFirstPrevState(metaData, allRevisions);
            appendFileInfo(boxBuilder, changeLocationTo, metaData, revision);
            appendFileInfoLite(fileInfo.append(), changeLocationTo, metaData, revision);
            if (isValidFileForCompare(changeLocationTo, connection)) {
                new FileCompareRenderer(connection, boxBuilder).append(previousState, changeLocationTo);
            } else {
                appendNotTextFileWarning(boxBuilder);
            }
        }
    }

    private void appendFileRemovedDuringCopy(@NotNull HtmlContentBuilder builder) {
        builder.tag().div().append().text("File was removed during copy operation.");
    }

    private void appendNotTextFileWarning(@NotNull HtmlContentBuilder builder) {
        builder.tag().div().append().text("This is not a text file.");
    }

    private boolean isValidFileForCompare(@NotNull ILocation location, @NotNull IRepositoryReadOnlyConnection connection) {
        if (connection.isFolder(location)) {
            return false;
        }
        String fileName = location.getLastComponent().toLowerCase();
        if (fileExtension.matcher(fileName).find() || "readme".equalsIgnoreCase(fileName)) {
            return true;
        }
        String mimeType = connection.getProperty(location, "svn:mime-type");
        return (mimeType != null) && mimeType.startsWith("text/");
    }

    private void appendFileInfo(@NotNull HtmlContentBuilder builder, @NotNull ILocation changeLocationTo, @NotNull ILocationChangeMetaData metaData, @NotNull IRevision revision) {
        HtmlTagBuilder locationLabel = builder.tag().div();
        locationLabel.attributes().className("cr_file_label");

        Action action = ExternalRepositoryUtils.getAction(metaData);
        if (action != null) {
            locationLabel.append().tag().img().attributes().src(HtmlLinkFactory.fromEncodedRelativeUrl("/polarion/ria/images/revision/" + action.id + ".png")).className("cr_fileImg").title(action.label);
        }

        HtmlTagBuilder fileLink = locationLabel.append().tag().a();
        fileLink.attributes().href(HtmlLinkFactory.fromEncodedRelativeUrl(revision.getLocationDiffURL(metaData)));
        fileLink.attributes().target("_blank");

        fileLink.append().text(changeLocationTo.getLastComponent());
        fileLink.append().text(" (");
        fileLink.append().text(changeLocationTo.getLocationPath());
        fileLink.append().text(")");
    }

    private void appendFileInfoLite(@NotNull HtmlContentBuilder builder, @NotNull ILocation changeLocationTo, @NotNull ILocationChangeMetaData metaData, @NotNull IRevision revision) {
        HtmlTagBuilder locationLabel = builder.tag().div();
        locationLabel.attributes().className("cr_file_label_lite");

        Action action = ExternalRepositoryUtils.getAction(metaData);
        if (action != null) {
            locationLabel.append().tag().img().attributes().src(HtmlLinkFactory.fromEncodedRelativeUrl("/polarion/ria/images/revision/" + action.id + ".png")).className("cr_fileImg").title(action.label);
        }

        HtmlTagBuilder fileLink = locationLabel.append().tag().a();
        fileLink.attributes().onClick("scrollToNode('" + createElementIdFromLocation(changeLocationTo) + "')");
        fileLink.append().tag().span().append().text(changeLocationTo.getLastComponent());
        fileLink.append().text(" r.");
        fileLink.append().text(revision.getName());
        fileLink.append().text(" (");
        fileLink.append().text(changeLocationTo.getLocationPath());
        fileLink.append().text(")");
    }

    @NotNull
    private String createElementIdFromLocation(@NotNull ILocation changeLocationTo) {
        return changeLocationTo.serialize().replace("#", "_");
    }

    private @NotNull ILocation getPreviousState(@NotNull ILocation location) {
        Integer toRevision = getPrevRevision(location.getRevision());
        return location.setRevision(toRevision.toString());
    }

    private @NotNull Integer getPrevRevision(@NotNull String revision) {
        Integer toRevision = Integer.valueOf(revision);
        if (toRevision > 0) {
            toRevision -= 1;
        }
        return toRevision;
    }

    private void appendHTMLContent(@NotNull HtmlContentBuilder builder, @NotNull IRepositoryReadOnlyConnection connection, @NotNull ILocation location, boolean create) {
        if (!create && !connection.exists(location)) {
            builder.tag().div().append().text("File was removed during copy operation.");
        } else {
            String content = getStringContent(connection, location);
            HtmlTagBuilder compareContainer = builder.tag().div();
            compareContainer.attributes().className("cr_file_content " + (create ? "cr_file_content_add" : "cr_file_content_delete"));

            HtmlTagBuilder pre = compareContainer.append().tag().pre();
            HtmlTagBuilder code = pre.append().tag().byName("code");
            appendStyle(location, code);
            code.append().text(content);

        }
    }

    static void appendStyle(@NotNull ILocation first, @NotNull HtmlTagBuilder code) {
        if (first.getLastComponent().endsWith(".java") || first.getLastComponent().endsWith(".jsp")) {
            code.attributes().className("java language-java");
        } else if (first.getLastComponent().endsWith("README") || first.getLastComponent().endsWith(".properties") || first.getLastComponent().endsWith(".txt") || first.getLastComponent().endsWith(".classpath")
                || first.getLastComponent().endsWith(".project")) {
            code.attributes().className("no-highlight");
        }
    }

    static @NotNull String getStringContent(@NotNull IRepositoryReadOnlyConnection connection, @NotNull ILocation location) {
        String result = "";
        try {
            try (BufferedReader buffer = new BufferedReader(new InputStreamReader(connection.getContent(location), StandardCharsets.UTF_8))) {
                result = buffer.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return result;
    }

    static @NotNull String escapeHTML(@NotNull String str) {
        return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

}
