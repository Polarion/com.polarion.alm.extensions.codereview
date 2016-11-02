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

import java.util.Map;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import com.polarion.alm.shared.api.SharedContext;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.alm.shared.api.utils.html.HtmlFragmentBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlTagBuilder;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.ui.server.forms.extensions.IFormExtension;
import com.polarion.platform.persistence.model.IPObject;

@SuppressWarnings("nls")
public class CodeReviewExtension implements IFormExtension {

    public static final String ID = "codereview";

    private static final Logger log = Logger.getLogger(CodeReviewExtension.class);

    @Override
    public String render(IPObject object, Map<String, String> attributes) {
        return TransactionalExecutor.executeSafelyInReadOnlyTransaction(transaction -> renderInternal(transaction.context(), object));
    }

    public String renderInternal(@NotNull SharedContext context, @NotNull IPObject object) {
        HtmlFragmentBuilder builder = context.createHtmlFragmentBuilderFor().gwt();

        try {
            if (object.isPersisted() && object instanceof IWorkItem) {
                IWorkItem workItem = (IWorkItem) object;
                Parameters parameters = new Parameters(workItem, Parameters.repositoryConfigurationLoader());
                Revisions revisions = parameters.createRevisions();
                boolean separatorNeeded = false;
                if (revisions.hasRevisionsToReview()) {
                    appendSomethingWaiting(builder, revisions.hasComparableRevisionsToReview(), revisions.hasNonDefaultRevisionsToReview(), parameters);
                    separatorNeeded = true;
                }
                if (revisions.hasComparableRevisions()) {
                    appendCompareAll(builder, parameters);
                    separatorNeeded = true;
                }
                if (!revisions.isEmpty()) {
                    if (separatorNeeded) {
                        builder.tag().hr();
                    }
                    appendRevisionsTable(builder, revisions, parameters);
                }
                appendReviewAll(builder, revisions, parameters);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            builder.tag().div().append().tag().b().append().text("Uknown error - see server log for more information.");
        }
        builder.finished();
        return builder.toString();
    }

    private void appendReviewAll(HtmlFragmentBuilder builder, Revisions revisions, Parameters parameters) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException();
    }

    private void appendSomethingWaiting(@NotNull HtmlFragmentBuilder builder, boolean comparableRevisionToReview, boolean nonDefaultRevisionsToReview, @NotNull Parameters parameters) {
        HtmlTagBuilder infoBox = builder.tag().div();
        HtmlTagBuilder toReview = infoBox.append().tag().span();
        toReview.append().html("Something to code review is waiting...&nbsp;");

        if (comparableRevisionToReview) {
            HtmlTagBuilder link = infoBox.append().tag().a();
            link.attributes().href(parameters.link().toHtmlLink());
            link.attributes().target("_blank");
            if (nonDefaultRevisionsToReview) {
                link.append().html("<b>Open for default repository</b>");
                infoBox.append().html(" and find a suitable compare for non-default repositories yourself");
            } else {
                link.append().html("<b>Open</b>");
            }
        } else if (nonDefaultRevisionsToReview) {
            infoBox.append().html("but you have to find a suitable compare for non-default repositories yourself!");
        }
    }

    private void appendRevisionsTable(@NotNull HtmlFragmentBuilder builder, @NotNull Revisions revisions, @NotNull Parameters parameters) {
        revisions.asHTMLTable(builder, parameters);
    }

    private void appendCompareAll(@NotNull HtmlFragmentBuilder builder, @NotNull Parameters parameters) {
        HtmlTagBuilder infoBox = builder.tag().div();

        HtmlTagBuilder link = infoBox.append().tag().a();
        link.attributes().target("_blank");
        link.attributes().href(parameters.link().withCompareAll(true).toHtmlLink());
        link.append().html("<b>Open compare of all revisions from default repository</b>");
    }

}
