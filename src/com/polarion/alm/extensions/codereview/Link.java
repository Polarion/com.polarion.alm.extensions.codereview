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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private final @NotNull List<Parameter> additionalParameters;

    Link(@NotNull IWorkItem workItem, boolean aggregatedCompare, boolean compareAll, @Nullable WorkflowAction workflowAction) {
        this(workItem, aggregatedCompare, compareAll, workflowAction, Collections.emptyList());
    }

    private Link(@NotNull IWorkItem workItem, boolean aggregatedCompare, boolean compareAll, @Nullable WorkflowAction workflowAction, @NotNull List<Parameter> additionalParameters) {
        this.workItem = workItem;
        this.aggregatedCompare = aggregatedCompare;
        this.compareAll = compareAll;
        this.workflowAction = workflowAction;
        this.additionalParameters = additionalParameters;
    }

    public @NotNull HtmlLink htmlLink() {
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
        for (Parameter additionalParameter : additionalParameters) {
            link.append(additionalParameter.queryString());
        }
        return Objects.requireNonNull(HtmlLinkFactory.fromEncodedRelativeUrl(link.toString()));
    }

    public @NotNull Link withAggregatedCompare(boolean aggregatedCompare) {
        return new Link(workItem, aggregatedCompare, compareAll, workflowAction, additionalParameters);
    }

    public @NotNull Link withCompareAll(boolean compareAll) {
        return new Link(workItem, aggregatedCompare, compareAll, workflowAction, additionalParameters);
    }

    public @NotNull Link withWorkflowAction(@Nullable WorkflowAction workflowAction) {
        return new Link(workItem, aggregatedCompare, compareAll, workflowAction, additionalParameters);
    }

    public @NotNull Link withAdditionalParameter(@NotNull String name, @NotNull String value) {
        return withAdditionalParameters(new ParameterImpl(name, value));
    }

    public @NotNull Link withAdditionalParameters(@NotNull Parameter... additionalParameters) {
        List<Parameter> joinedAdditionalParameters = new ArrayList<>(this.additionalParameters);
        Collections.addAll(joinedAdditionalParameters, additionalParameters);
        return new Link(workItem, aggregatedCompare, compareAll, workflowAction, joinedAdditionalParameters);
    }

    public interface Parameter {
        @NotNull
        String queryString();
    }

    public static final class ParameterImpl implements Parameter {

        private final @NotNull String name;
        private final @NotNull String value;

        public ParameterImpl(@NotNull String name, @NotNull String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public @NotNull String queryString() {
            return "&" + name + "=" + value;
        }

    }

}