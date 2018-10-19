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
import org.jetbrains.annotations.Nullable;

import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.workflow.IArguments;
import com.polarion.alm.tracker.workflow.ICallContext;
import com.polarion.alm.tracker.workflow.IWorkflowCondition;
import com.polarion.platform.persistence.model.IRevision;
import com.polarion.platform.service.repository.ILocationChangeMetaData;
import com.polarion.subterra.base.location.ILocation;

@SuppressWarnings("nls")
public class FastTrackReviewCondition implements IWorkflowCondition<IWorkItem> {

    private String check(@NotNull IWorkItem wi, @NotNull Parameters parameters) {
        for (IRevision revision : (List<IRevision>) wi.getLinkedRevisions()) {
            if (parameters.notFromIgnoredRepository(revision)) {
                List<ILocationChangeMetaData> changes = revision.getChangedLocations();
                for (ILocationChangeMetaData change : changes) {
                    ILocation location = change.getChangeLocationTo();
                    if (location != null) {
                        if (!parameters.isLocationPermittedForFastTrack(location)) {
                            return "At least one revision (" + revision.getName() + ") needs to be reviewed by real person";
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public boolean passesCondition(@NotNull ICallContext<IWorkItem> context, @NotNull IArguments arguments) {
        // unused
        return false;
    }

    @Override
    @Nullable
    public String passesConditionWithFailureMessage(@NotNull ICallContext<IWorkItem> context, @NotNull IArguments arguments) {
        return check(context.getTarget(), new Parameters(PlatformParametersContext.createFromPlatform(), context.getTarget()));
    }

}
