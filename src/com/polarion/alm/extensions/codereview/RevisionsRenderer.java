/*
 * Copyright 2017 Polarion AG
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.polarion.alm.extensions.codereview.Parameters.Link;
import com.polarion.alm.shared.api.utils.html.HtmlContentBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlFragmentBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlTagBuilder;
import com.polarion.alm.shared.api.utils.links.HtmlLinkFactory;

@SuppressWarnings("nls")
public class RevisionsRenderer {

    private final @NotNull Revisions revisions;

    RevisionsRenderer(@NotNull Revisions revisions) {
        this.revisions = revisions;
    }

    private static final String COMMENT_TEXT_AREA_ID = "commentTextArea";
    private static final String REVIEW_ALL_ID = "reviewAll";
    private static final String REVIEW_ALL_ADVANCE_ID = "reviewAllAdvance";
    private static final String REVIEW_ALL_REOPEN_ID = "reviewAllReopen";
    private static final String HIDDEN_ID = "hidden";

    private static final @NotNull String refreshCall = "setTimeout(function() { var refreshes = document.querySelectorAll('[src*=refreshBtn]'); refreshes[refreshes.length - 1].parentNode.click(); }, 500);";
    private static final @NotNull String showAreaCall = "(function () {var commentTextArea = document.getElementById('" + COMMENT_TEXT_AREA_ID + "'); commentTextArea.style.display='block'; }());";

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
        revisions.forEachRevision(revisionModel -> revisionModel.render().asHTMLTableRow(table, parameters));
        if (parameters.mustStartReview()) {
            appendStartReviewButton(parameters, form);
        }
        if (parameters.canReview()) {
            appendButtons(parameters, form);
            form.tag().br();
            form.html("<textarea name='reviewComment' placeholder='Type your comment' id='" + COMMENT_TEXT_AREA_ID + "' style='display:none; resize:both !important; overflow:auto' rows='8' cols='140'></textarea>");
        }
        form.html("</form>");
    }

    private void appendButtons(@NotNull Parameters parameters, @NotNull HtmlContentBuilder form) {
        form.html("<input type='submit' name='" + CodeReviewServlet.PARAM_REVIEW_SELECTED + "' value='Review selected' >");

        Link link = parameters.link().withAdditionalParameter(CodeReviewServlet.PARAM_REVIEW_SELECTED, "1");
        addReviewButton(form, REVIEW_ALL_ID, "[ Review all ]", null);

        if (parameters.isSuccessfulWorkflowActionConfigured() && !revisions.hasRevisionsToReviewAuthoredByCurrentUser(parameters)) {
            addReviewButton(form, REVIEW_ALL_ADVANCE_ID, "[ Review all & advance ]", Parameters.WorkflowAction.successfulReview);
        }
        if (parameters.isUnsuccessfulWorkflowActionConfigured()) {
            addReviewButton(form, REVIEW_ALL_REOPEN_ID, "[ Review all & reopen ]", Parameters.WorkflowAction.unsuccessfulReview);
        }
        addButton(form, null, null, "Add Comment", showAreaCall, "color:#369;font-size:12px;");

        addButton(form, link, HIDDEN_ID, "", "", "display:block");
    }

    private void addReviewButton(@NotNull HtmlContentBuilder form, @NotNull String id, @NotNull String text, @Nullable Parameters.WorkflowAction workflowAction) {
        addButton(form, null, id, text, reviewButtonCallback(workflowAction), null);
    }

    private void addButton(@NotNull HtmlContentBuilder form, @Nullable Link link, @Nullable String id, @NotNull String text, @NotNull String js, @Nullable String style) {
        form.nbsp();
        HtmlTagBuilder button = form.tag().b().append().tag().a();
        button.attributes().href(link != null ? link.toHtmlLink() : HtmlLinkFactory.fromEncodedUrl("javascript:void(0);"));
        button.attributes().id(id);
        button.attributes().onClick(js);
        button.attributes().style(style);
        button.append().text(text);
    }

    private void appendStartReviewButton(@NotNull Parameters parameters, @NotNull HtmlContentBuilder form) {
        Link link = parameters.link().withAdditionalParameter(CodeReviewServlet.PARAM_SET_CURRENT_REVIEWER, "1");
        addButton(form, link, "startReview", "[ Start review ]", refreshCall, null);
    }

    private @NotNull String reviewButtonCallback(@Nullable Parameters.WorkflowAction workflowAction) {
        String workflowActionJS = "";
        if (workflowAction != null) {
            workflowActionJS = " + '&workflowAction=" + workflowAction + "'";
        }
        return "(function () {var commentText = document.getElementById('" + COMMENT_TEXT_AREA_ID + "').value;"
                + " window.location = document.getElementById('" + HIDDEN_ID + "').href + '&reviewComment=' + encodeURIComponent(commentText)" + workflowActionJS + ";" + refreshCall + "}());";
    }

}
