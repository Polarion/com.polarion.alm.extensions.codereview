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
import com.polarion.alm.extensions.codereview.Parameters.UserIdentity;
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

    private static final char REVIEWED_REVISIONS_DELIMITER = ',';
    private static final @NotNull String REVIEWED_REVISIONS_DELIMITER_REGEX = "\\s*" + REVIEWED_REVISIONS_DELIMITER + "\\s*";
    private static final @NotNull String REVIEWED_REVISIONS_DELIMITER_OUTPUT = REVIEWED_REVISIONS_DELIMITER + " ";
    private static final char REPOSITORY_AND_REVISION_DELIMITER = '/';
    private static final char REVIEWER_DELIMITER = '\\';

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

        public boolean isAuthoredByUser(@NotNull UserIdentity userIdentity) {
            String author = revision.getStringAuthor();
            if (author == null) {
                return false;
            }
            return defaultRepository ? userIdentity.hasId(author) : userIdentity.hasIdOrName(author);
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
            for (String record : s.trim().split(REVIEWED_REVISIONS_DELIMITER_REGEX)) {
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

    public @NotNull Revisions markReviewed(@NotNull Predicate<String> shouldMark, @NotNull String reviewer, @NotNull Parameters parameters) {
        UserIdentity userIdentity = parameters.identityForUser(reviewer);
        for (RevisionModel revisionModel : revisionModels) {
            if (!revisionModel.reviewed && shouldMark.test(revisionModel.getKey()) && !revisionModel.isAuthoredByUser(userIdentity)) {
                revisionModel.reviewed = true;
                revisionModel.reviewer = reviewer;
            }
        }
        return this;
    }

    public @NotNull Revisions markReviewed(@NotNull Collection<String> revisionsToMarkAsReviewed, @NotNull String reviewer, @NotNull Parameters parameters) {
        return markReviewed(revision -> revisionsToMarkAsReviewed.contains(revision), reviewer, parameters);
    }

    public @NotNull String getReviewedRevisionsFieldValue() {
        StringJoiner value = new StringJoiner(REVIEWED_REVISIONS_DELIMITER_OUTPUT);
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

    public boolean hasRevisionsReviewedByNonReviewers(@NotNull Parameters parameters) {
        for (RevisionModel revisionModel : revisionModels) {
            if (!parameters.isOrWasPermittedReviewer(revisionModel.reviewer)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasRevisionsToReviewAuthoredByCurrentUser(@NotNull Parameters parameters) {
        UserIdentity userIdentity = parameters.identityForCurrentUser();
        for (RevisionModel revisionModel : revisionModels) {
            if (!revisionModel.reviewed && revisionModel.isAuthoredByUser(userIdentity)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasSelfReviewedRevisions(@NotNull Parameters parameters) {
        for (RevisionModel revisionModel : revisionModels) {
            if (revisionModel.reviewed) {
                UserIdentity userIdentity = parameters.identityForUser(revisionModel.reviewer);
                if (revisionModel.isAuthoredByUser(userIdentity)) {
                    return true;
                }
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

    private static final String COMMENT_TEXT_AREA_ID = "commentTextArea";
    private static final String REVIEW_ALL_ID = "reviewAll";
    private static final String REVIEW_ALL_ADVANCE_ID = "reviewAllAdvance";
    private static final String REVIEW_ALL_REOPEN_ID = "reviewAllReopen";
    private static final String HIDDEN_ID = "hidden";

    private static final @NotNull String refreshCall = "setTimeout(function() { var refreshes = document.querySelectorAll('[src*=refreshBtn]'); refreshes[refreshes.length - 1].parentNode.click(); }, 500);";
    private static final @NotNull String showAreaCall = "(function () {var commentTextArea = document.getElementById('" + COMMENT_TEXT_AREA_ID + "'); commentTextArea.style.display='block'; }());";
    private static final @NotNull String setupAllCall = "(function () {var commentText = document.getElementById('" + COMMENT_TEXT_AREA_ID + "').value; var reviewAll = document.getElementById('" + REVIEW_ALL_ID + "');"
            + " reviewAll.href = document.getElementById('" + HIDDEN_ID + "').href + '&reviewComment=' + escape(commentText); reviewAll.click();" + refreshCall + "}());";
    private static final @NotNull String setupSuccessfulCall = "(function () {var commentText = document.getElementById('" + COMMENT_TEXT_AREA_ID + "').value; var reviewAllAdvance = document.getElementById('" + REVIEW_ALL_ADVANCE_ID + "');"
            + " reviewAllAdvance.href = document.getElementById('" + HIDDEN_ID + "').href + '&reviewComment=' + escape(commentText) + '&workflowAction=" + Parameters.WorkflowAction.successfulReview + "'; reviewAllAdvance.click();"
            + refreshCall + "}());";
    private static final @NotNull String setupUnsuccessfulCall = "(function () {var commentText = document.getElementById('" + COMMENT_TEXT_AREA_ID + "').value; var reviewAllReopen = document.getElementById('" + REVIEW_ALL_REOPEN_ID + "');"
            + " reviewAllReopen.href = document.getElementById('" + HIDDEN_ID + "').href + '&reviewComment=' + escape(commentText) + '&workflowAction=" + Parameters.WorkflowAction.unsuccessfulReview + "'; reviewAllReopen.click();"
            + refreshCall + "}());";

    public void asHTMLTable(@NotNull HtmlFragmentBuilder builder, @NotNull Parameters parameters) {
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
        boolean canReview = parameters.canReview();
        for (RevisionModel revisionModel : revisionModels) {
            appendRevisionRow(table, canReview, revisionModel);
        }
        if (parameters.mustStartReview()) {
            appendStartReviewButton(parameters, form);
        }
        if (canReview) {
            appendButtons(parameters, form);
            form.tag().br();
            form.html("<textarea name='reviewComment' placeholder='Type your comment' id='" + COMMENT_TEXT_AREA_ID + "' style='display:none' rows='8' cols='140'></textarea>");
        }
        form.html("</form>");
    }

    private void appendRevisionRow(@NotNull HtmlTagBuilder table, boolean editable, @NotNull RevisionModel revisionModel) {
        HtmlTagBuilder tr = table.append().tag().tr();
        tr.append().tag().td().append().html("<input type=\"checkbox\" name=\"" + CodeReviewServlet.PARAM_REVISIONS_TO_MARK + "\" value=\"" + revisionModel.getKey() + "\"" + (revisionModel.reviewed ? " checked disabled" : "")
                + (editable ? "" : " disabled") + ">");
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

    private void appendButtons(@NotNull Parameters parameters, @NotNull HtmlContentBuilder form) {
        form.html("<input type='submit' name='" + CodeReviewServlet.PARAM_REVIEW_SELECTED + "' value='Review selected' >");

        Link link = parameters.link().withAdditionalParameter(CodeReviewServlet.PARAM_REVIEW_SELECTED, "1");
        for (RevisionModel revisionModel : revisionModels) {
            if (!revisionModel.reviewed) {
                link.withAdditionalParameter(CodeReviewServlet.PARAM_REVISIONS_TO_MARK, revisionModel.getKey());
            }
        }
        addButton(form, null, REVIEW_ALL_ID, "[ Review all ]", setupAllCall, null);

        if (parameters.isSuccessfulWorkflowActionConfigured() && !hasRevisionsToReviewAuthoredByCurrentUser(parameters)) {
            addButton(form, null, REVIEW_ALL_ADVANCE_ID, "[ Review all & advance ]", setupSuccessfulCall, null);
        }
        if (parameters.isUnsuccessfulWorkflowActionConfigured()) {
            addButton(form, null, REVIEW_ALL_REOPEN_ID, "[ Review all & reopen]", setupUnsuccessfulCall, null);
        }
        addButton(form, null, null, "Add Comment", showAreaCall, "color:#369;font-size:12px;");

        addButton(form, link, HIDDEN_ID, "", "", "display:block");
    }

    private void addButton(@NotNull HtmlContentBuilder form, @Nullable Link link, @Nullable String id, @NotNull String text, @NotNull String js, @Nullable String style) {
        form.nbsp();
        HtmlTagBuilder button = form.tag().b().append().tag().a();
        button.attributes().href(link != null ? link.toHtmlLink() : HtmlLinkFactory.fromEncodedUrl("javascript:void(0)}"));
        button.attributes().id(id);
        button.attributes().onClick(js);
        button.attributes().style(style);
        button.append().text(text);
    }

    private void appendStartReviewButton(@NotNull Parameters parameters, @NotNull HtmlContentBuilder form) {
        Link link = parameters.link().withAdditionalParameter(CodeReviewServlet.PARAM_SET_CURRENT_REVIEWER, "1");
        addButton(form, link, "startReview", "[ Start review ]", refreshCall, null);
    }
}