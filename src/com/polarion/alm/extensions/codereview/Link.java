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

import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.polarion.alm.extensions.codereview.Parameters.WorkflowAction;
import com.polarion.alm.shared.api.utils.links.HtmlLink;
import com.polarion.alm.shared.api.utils.links.HtmlLinkFactory;
import com.polarion.alm.tracker.model.IWorkItem;

@SuppressWarnings("nls")
final class Link {

    private final @NotNull IWorkItem workItem;
    private final boolean aggregatedCompare;
    private final boolean compareAll;
    private final @Nullable WorkflowAction workflowAction;
    private final @NotNull Parameter lastParameterInChain;

    public Link(@NotNull IWorkItem workItem, boolean aggregatedCompare, boolean compareAll, @Nullable WorkflowAction workflowAction) {
        this(workItem, aggregatedCompare, compareAll, workflowAction, new EmptyParameter());
    }

    public Link(@NotNull IWorkItem workItem, boolean aggregatedCompare, boolean compareAll, @Nullable WorkflowAction workflowAction, @NotNull Parameter lastParameterInChain) {
        this.workItem = workItem;
        this.aggregatedCompare = aggregatedCompare;
        this.compareAll = compareAll;
        this.workflowAction = workflowAction;
        this.lastParameterInChain = lastParameterInChain;
    }

    public @NotNull HtmlLink toHtmlLink() {
        StringBuilder link = new StringBuilder("/polarion/codereview?");
        link.append(Parameters.PARAM_WORK_ITEM_ID);
        link.append("=");
        link.append(workItem.getId());
        link.append("&");
        link.append(Parameters.PARAM_PROJECT_ID);
        link.append("=");
        link.append(workItem.getProjectId());
        if (aggregatedCompare) {
            link.append("&");
            link.append(Parameters.PARAM_AGGREGATED_COMPARE);
            link.append("=true");
        }
        if (compareAll) {
            link.append("&");
            link.append(Parameters.PARAM_COMPARE_ALL);
            link.append("=true");
        }
        if (workflowAction != null) {
            link.append("&");
            link.append(Parameters.PARAM_WORKFLOW_ACTION);
            link.append("=");
            link.append(workflowAction);
        }
        link.append(lastParameterInChain.queryString());
        return Objects.requireNonNull(HtmlLinkFactory.fromEncodedRelativeUrl(link.toString()));
    }

    public @NotNull Link withAggregatedCompare(boolean aggregatedCompare) {
        return new Link(workItem, aggregatedCompare, compareAll, workflowAction, lastParameterInChain);
    }

    public @NotNull Link withCompareAll(boolean compareAll) {
        return new Link(workItem, aggregatedCompare, compareAll, workflowAction, lastParameterInChain);
    }

    public @NotNull Link withWorkflowAction(@Nullable WorkflowAction workflowAction) {
        return new Link(workItem, aggregatedCompare, compareAll, workflowAction, lastParameterInChain);
    }

    public @NotNull Link withAdditionalParameter(@NotNull String name, @NotNull String value) {
        return new Link(workItem, aggregatedCompare, compareAll, workflowAction, new ParameterImpl(lastParameterInChain, name, value));
    }

    private interface Parameter {
        public @NotNull String queryString();
    }

    private static final class EmptyParameter implements Parameter {

        @Override
        public @NotNull String queryString() {
            return "";
        }

    }

    private static final class ParameterImpl implements Parameter {

        private final @NotNull Parameter previousParameter;
        private final @NotNull String name;
        private final @NotNull String value;

        public ParameterImpl(@NotNull Parameter previousParameter, @NotNull String name, @NotNull String value) {
            this.previousParameter = previousParameter;
            this.name = name;
            this.value = value;
        }

        @Override
        public @NotNull String queryString() {
            return previousParameter.queryString() + "&" + name + "=" + value;
        }

    }

}