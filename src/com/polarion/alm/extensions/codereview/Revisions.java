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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.polarion.alm.extensions.codereview.Parameters.Link;
import com.polarion.alm.extensions.codereview.Parameters.WorkflowAction;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.shared.api.utils.html.HtmlContentBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlFragmentBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlTagBuilder;
import com.polarion.alm.shared.api.utils.links.HtmlLinkFactory;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.persistence.model.IRevision;
import com.polarion.platform.service.repository.IRepositoryService;

@SuppressWarnings("nls")
public final class Revisions {

    private static final @NotNull String REVIEWED_REVISIONS_DELIMITER = ",";
    private static final @NotNull String REPOSITORY_AND_REVISION_DELIMITER = "/";
    private static final @NotNull String REVIEWER_DELIMITER = "\\";

    private final @NotNull List<RevisionModel> revisionModels = new ArrayList<>();

    private static final class RevisionModel {

        public final @NotNull IRevision revision;
        public final boolean defaultRepository;
        public boolean reviewed;
        public boolean suitableForCompare;
        public @Nullable String reviewer;

        private RevisionModel(@NotNull IRevision revision) {
            this.revision = revision;
            defaultRepository = IRepositoryService.DEFAULT.equals(revision.getRepositoryName());
        }

        private @NotNull RevisionModel lastReviewedRevision(@Nullable Integer lastReviewedRevision) {
            if (IRepositoryService.DEFAULT.equals(revision.getRepositoryName())) {
                try {
                    int revisionInt = CodeReviewServlet.getRevision(revision);
                    if (lastReviewedRevision != null && revisionInt <= lastReviewedRevision) {
                        reviewed = true;
                    }
                    suitableForCompare = !revision.getChangedLocations().isEmpty();
                } catch (NumberFormatException e) {
                    // ignored
                }
            }
            return this;
        }

        private @NotNull RevisionModel reviewedRevisions(@NotNull Map<String, String> reviewedRevisions) {
            String key = getKey();
            reviewed = reviewedRevisions.containsKey(key);
            reviewer = reviewedRevisions.get(key);
            if (IRepositoryService.DEFAULT.equals(revision.getRepositoryName())) {
                suitableForCompare = !revision.getChangedLocations().isEmpty();
            }
            return this;
        }

        private @NotNull String getKey() {
            return revision.getRepositoryName() + REPOSITORY_AND_REVISION_DELIMITER + revision.getName();
        }

        public @NotNull String getReviewedRevisionsRecord() {
            return getKey() + ((reviewer != null) ? REVIEWER_DELIMITER + reviewer : "");
        }

    }

    public Revisions(@NotNull List<IRevision> linkedRevisions, @Nullable Integer lastReviewedRevision) {
        for (IRevision revision : linkedRevisions) {
            revisionModels.add(new RevisionModel(revision).lastReviewedRevision(lastReviewedRevision));
        }
    }

    public Revisions(@NotNull List<IRevision> linkedRevisions, @Nullable String reviewedRevisionsFieldValue) {
        Map<String, String> reviewedRevisions = new HashMap<>();
        parseReviewedRevisions(reviewedRevisionsFieldValue, reviewedRevisions);
        for (IRevision revision : linkedRevisions) {
            revisionModels.add(new RevisionModel(revision).reviewedRevisions(reviewedRevisions));
        }
    }

    private void parseReviewedRevisions(@Nullable String s, @NotNull Map<String, String> reviewedRevisions) {
        if (s != null) {
            for (String record : s.split(REVIEWED_REVISIONS_DELIMITER)) {
                String key = record;
                String reviewer = null;
                int i = record.lastIndexOf(REVIEWER_DELIMITER);
                if (i > -1) {
                    key = record.substring(0, i);
                    reviewer = record.substring(i + 1);
                }
                reviewedRevisions.put(key, reviewer);
            }
        }
    }

    public @NotNull Revisions markReviewed(@NotNull Predicate<String> shouldMark, @NotNull String reviewer) {
        for (RevisionModel revisionModel : revisionModels) {
            if (!revisionModel.reviewed && shouldMark.test(revisionModel.getKey())) {
                revisionModel.reviewed = true;
                revisionModel.reviewer = reviewer;
            }
        }
        return this;
    }

    public @NotNull Revisions markReviewed(@NotNull Collection<String> revisionsToMarkAsReviewed, @NotNull String reviewer) {
        return markReviewed(revision -> revisionsToMarkAsReviewed.contains(revision), reviewer);
    }

    public @NotNull String getReviewedRevisionsFieldValue() {
        StringJoiner value = new StringJoiner(REVIEWED_REVISIONS_DELIMITER);
        for (RevisionModel revisionModel : revisionModels) {
            if (revisionModel.reviewed) {
                value.add(revisionModel.getReviewedRevisionsRecord());
            }
        }
        return value.toString();
    }

    public @NotNull List<IRevision> getComparableRevisionsToReview() {
        List<IRevision> revisions = new ArrayList<>();
        for (RevisionModel revisionModel : revisionModels) {
            if (!revisionModel.reviewed && revisionModel.suitableForCompare) {
                revisions.add(revisionModel.revision);
            }
        }
        return revisions;
    }

    public boolean hasRevisionsToReview() {
        for (RevisionModel revisionModel : revisionModels) {
            if (!revisionModel.reviewed) {
                return true;
            }
        }
        return false;
    }

    public boolean hasComparableRevisionsToReview() {
        for (RevisionModel revisionModel : revisionModels) {
            if (!revisionModel.reviewed && revisionModel.suitableForCompare) {
                return true;
            }
        }
        return false;
    }

    public boolean hasNonDefaultRevisionsToReview() {
        for (RevisionModel revisionModel : revisionModels) {
            if (!revisionModel.reviewed && !revisionModel.defaultRepository) {
                return true;
            }
        }
        return false;
    }

    public boolean hasComparableRevisions() {
        for (RevisionModel revisionModel : revisionModels) {
            if (revisionModel.suitableForCompare) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return revisionModels.isEmpty();
    }

    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private @NotNull String formatDate(@Nullable Date date) {
        if (date == null) {
            return "?";
        }
        return dateFormat.format(date);
    }

    private @NotNull IProjectService getProjectService() {
        return PlatformContext.getPlatform().lookupService(IProjectService.class);
    }

    private @NotNull String getUserLabel(@Nullable String userId, @NotNull String nullUserLabel) {
        if (userId == null) {
            return nullUserLabel;
        }
        return getProjectService().getUser(userId).getLabel();
    }

    private @NotNull HtmlTagBuilder nowrapTD(@NotNull HtmlTagBuilder tr) {
        HtmlTagBuilder td = tr.append().tag().td();
        td.attributes().style("white-space: nowrap");
        return td;
    }

    public void asHTMLTable(@NotNull HtmlFragmentBuilder builder, @NotNull Parameters parameters) {
        String refreshCall = "setTimeout(function() { var refreshes = document.querySelectorAll('[src*=refreshBtn]'); refreshes[refreshes.length - 1].parentNode.click(); }, 500);";
        HtmlContentBuilder form = builder.html(
                "<form method=\"post\" action=\"" + builder.target().toEncodedUrl(parameters.link().toHtmlLink())
                        + "\" onsubmit=\"" + refreshCall + "\">");
        HtmlTagBuilder table = form.tag().table();
        HtmlTagBuilder header = table.append().tag().tr();
        header.append().tag().th();
        header.append().tag().th().append().text("Revision");
        header.append().tag().th().append().text("Date");
        header.append().tag().th().append().text("Author");
        header.append().tag().th().append().text("Reviewer");
        header.append().tag().th().append().text("Message");
        boolean inReviewStatus = parameters.isInReviewStatus();
        for (RevisionModel revisionModel : revisionModels) {
            HtmlTagBuilder tr = table.append().tag().tr();
            tr.append().tag().td().append().html("<input type=\"checkbox\" name=\"" + CodeReviewServlet.PARAM_REVISIONS_TO_MARK + "\" value=\"" + revisionModel.getKey() + "\"" + (revisionModel.reviewed ? " checked disabled" : "")
                    + (inReviewStatus ? "" : " disabled") + ">");
            HtmlTagBuilder revisionTD = nowrapTD(tr);
            if (!revisionModel.defaultRepository) {
                HtmlTagBuilder span = revisionTD.append().tag().span();
                span.attributes().title("Non-default repository " + revisionModel.revision.getRepositoryName());
                span.append().text("(N) ");
            }
            revisionTD.append().decoratedLabel().link(HtmlLinkFactory.fromEncodedUrl(revisionModel.revision.getViewURL()), true).append().text(revisionModel.revision.getName());
            nowrapTD(tr).append().text(formatDate(revisionModel.revision.getCreated()));
            nowrapTD(tr).append().text(getUserLabel(revisionModel.revision.getStringAuthor(), "?"));
            nowrapTD(tr).append().text(getUserLabel(revisionModel.reviewer, revisionModel.reviewed ? "?" : ""));
            HtmlTagBuilder messageTD = nowrapTD(tr);
            String message = revisionModel.revision.getMessage();
            if (message != null) {
                messageTD.attributes().title(message);
                messageTD.append().text(message.substring(0, Math.min(message.length(), 200)));
            }
        }
        if (inReviewStatus) {
            form.html("<input type=\"submit\" name=\"" + CodeReviewServlet.PARAM_REVIEW_SELECTED + "\" value=\"Review selected\">");
            form.nbsp();
            Link link = parameters.link().withAdditionalParameter(CodeReviewServlet.PARAM_REVIEW_SELECTED, "1");
            for (RevisionModel revisionModel : revisionModels) {
                if (!revisionModel.reviewed) {
                    link.withAdditionalParameter(CodeReviewServlet.PARAM_REVISIONS_TO_MARK, revisionModel.getKey());
                }
            }
            HtmlTagBuilder reviewAll = form.tag().b().append().tag().a();
            reviewAll.attributes().href(link.toHtmlLink());
            reviewAll.attributes().onClick(refreshCall);
            reviewAll.append().text("[ Review all ]");
            if (parameters.isWorkflowActionConfigured()) {
                form.nbsp();
                HtmlTagBuilder reviewAllSuccess = form.tag().b().append().tag().a();
                reviewAllSuccess.attributes().href(link.withWorkflowAction(WorkflowAction.successfulReview).toHtmlLink());
                reviewAllSuccess.attributes().onClick(refreshCall);
                reviewAllSuccess.append().text("[ Review all & advance ]");
            }
        }
        form.html("</form>");
    }

}