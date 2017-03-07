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

import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.polarion.alm.extensions.codereview.utils.DateUtils;
import com.polarion.alm.shared.api.utils.html.HtmlTagBuilder;
import com.polarion.alm.shared.api.utils.links.HtmlLinkFactory;

@SuppressWarnings("nls")
public class RevisionModelRenderer {

    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final @NotNull RevisionModel revisionModel;

    public RevisionModelRenderer(@NotNull RevisionModel revisionModel) {
        this.revisionModel = revisionModel;
    }

    public void asHTMLTableRow(@NotNull HtmlTagBuilder table, @NotNull Parameters parameters) {
        HtmlTagBuilder tr = table.append().tag().tr();
        boolean checked = false;
        boolean disabled = false;
        String tooltip = null;
        if (revisionModel.reviewed) {
            checked = disabled = true;
            tooltip = "Revision already reviewed";
        } else if (!parameters.canReview()) {
            disabled = true;
            tooltip = "You are not allowed to do review";
        } else if (revisionModel.isAuthoredByUser(parameters.identityForCurrentUser())) {
            disabled = true;
            tooltip = "Self-review is not permitted";
        }
        tooltip = (tooltip != null) ? " title=\"" + tooltip + "\"" : "";
        tr.append().tag().td().append()
                .html("<input type=\"checkbox\" name=\"" + CodeReviewServlet.PARAM_REVISIONS_TO_MARK + "\" value=\"" + revisionModel.getKey() + "\"" + (checked ? " checked" : "") + (disabled ? " disabled" : "") + tooltip + ">");
        HtmlTagBuilder revisionTD = nowrapTD(tr);
        if (!revisionModel.defaultRepository) {
            HtmlTagBuilder span = revisionTD.append().tag().span();
            span.attributes().title("Non-default repository " + revisionModel.revision.getRepositoryName());
            span.append().text("(N) ");
        }
        revisionTD.append().decoratedLabel().link(HtmlLinkFactory.fromEncodedUrl(revisionModel.revision.getViewURL()), true).append().text(revisionModel.revision.getName());
        nowrapTD(tr).append().text(formatDate(revisionModel.revision.getCreated()));
        nowrapTD(tr).append().text(getUserLabel(revisionModel.revision.getStringAuthor(), "?", parameters));
        nowrapTD(tr).append().text(getUserLabel(revisionModel.reviewer, revisionModel.reviewed ? "?" : "", parameters));
        HtmlTagBuilder messageTD = nowrapTD(tr);
        String message = revisionModel.revision.getMessage();
        if (message != null) {
            messageTD.attributes().title(message);
            messageTD.append().text(message.substring(0, Math.min(message.length(), 200)));
        }
    }

    private @NotNull HtmlTagBuilder nowrapTD(@NotNull HtmlTagBuilder tr) {
        HtmlTagBuilder td = tr.append().tag().td();
        td.attributes().style("white-space: nowrap");
        return td;
    }

    private @NotNull String formatDate(@Nullable Date date) {
        if (date == null) {
            return "?";
        }
        return dateTimeFormatter.format(DateUtils.dateToLocalDateTime(date));
    }

    private @NotNull String getUserLabel(@Nullable String userId, @NotNull String nullUserLabel, @NotNull Parameters parameters) {
        if (userId == null) {
            return nullUserLabel;
        }
        return parameters.identityForUser(userId).label();
    }

}
