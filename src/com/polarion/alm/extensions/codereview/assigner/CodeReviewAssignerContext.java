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
package com.polarion.alm.extensions.codereview.assigner;

import org.jetbrains.annotations.NotNull;

import com.polarion.alm.tracker.model.IWorkItem;

interface CodeReviewAssignerContext {

    void log(@NotNull String message);

    boolean hasRevisionsToReviewAuthoredByUser(@NotNull IWorkItem workItem, @NotNull String user);

    void assignReviewerAndSave(@NotNull IWorkItem workItem, @NotNull String reviewer);

}