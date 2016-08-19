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

import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.polarion.alm.extensions.codereview.TextDiffMatchPatch.Diff;
import com.polarion.alm.shared.api.utils.html.HtmlContentBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlTagBuilder;
import com.polarion.platform.service.repository.IRepositoryReadOnlyConnection;
import com.polarion.subterra.base.location.ILocation;

@SuppressWarnings("nls")
public class FileCompareRenderer {

    @NotNull
    private final IRepositoryReadOnlyConnection connection;
    @NotNull
    private final HtmlContentBuilder builder;

    public FileCompareRenderer(@NotNull IRepositoryReadOnlyConnection connection, @NotNull HtmlContentBuilder builder) {
        this.connection = connection;
        this.builder = builder;
    }

    public void append(@NotNull ILocation first, @NotNull ILocation second) {
        String firstContent = CodeReviewServlet.getStringContent(connection, first);
        String secondContent = CodeReviewServlet.getStringContent(connection, second);
        HtmlTagBuilder compareContainer = builder.tag().div();
        compareContainer.attributes().className("cr_file_content cr_file_content_modify");
        String compareFiles = compareFiles(firstContent, secondContent);

        HtmlTagBuilder pre = compareContainer.append().tag().pre();
        HtmlTagBuilder code = pre.append().tag().byName("code");
        CodeReviewServlet.appendStyle(first, code);
        code.append().html(compareFiles);
    }

    private String compareFiles(@NotNull String first, @NotNull String second) {
        TextDiffMatchPatch textDiffMatchPatch = new TextDiffMatchPatch();

        first = first.replace("\n", "\r\n").replace("\r\r", "\r");
        second = second.replace("\n", "\r\n").replace("\r\r", "\r");

        List<Diff> diffs = textDiffMatchPatch.diffMainAtLineLevel(first, second);
        StringBuilder html = new StringBuilder();
        for (Diff aDiff : diffs) {
            String text = aDiff.getText();
            text = CodeReviewServlet.escapeHTML(text).replace("\r", "\n");
            switch (aDiff.getOperation()) {
            case INSERT:
                html.append("<span class=\"change\" style=\"background:#e6ffe6;\">").append(text)
                        .append("</span>");
                break;
            case DELETE:
                html.append("<span class=\"change\" style=\"background:#ffe6e6;\">").append(text)
                        .append("</span>");
                break;
            case EQUAL:
                html.append(text);
                break;
            }
        }
        return html.toString();
    }

}
