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

import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.polarion.alm.extensions.codereview.Parameters.UserIdentity;
import com.polarion.platform.persistence.model.IRevision;
import com.polarion.platform.service.repository.IRepositoryService;

@SuppressWarnings("nls")
final class RevisionModel {

    public final @NotNull IRevision revision;
    public final boolean defaultRepository;
    public boolean reviewed;
    public boolean suitableForCompare;
    public @Nullable String reviewer;

    RevisionModel(@NotNull IRevision revision) {
        this.revision = revision;
        defaultRepository = IRepositoryService.DEFAULT.equals(revision.getRepositoryName());
    }

    @NotNull
    RevisionModel lastReviewedRevision(@Nullable Integer lastReviewedRevision) {
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

    @NotNull
    RevisionModel reviewedRevisions(@NotNull Map<String, String> reviewedRevisions) {
        String key = getKey();
        reviewed = reviewedRevisions.containsKey(key);
        reviewer = reviewedRevisions.get(key);
        if (IRepositoryService.DEFAULT.equals(revision.getRepositoryName())) {
            suitableForCompare = !revision.getChangedLocations().isEmpty();
        }
        return this;
    }

    @NotNull
    String getKey() {
        return revision.getRepositoryName() + Revisions.REPOSITORY_AND_REVISION_DELIMITER + revision.getName();
    }

    @NotNull
    String getReviewedRevisionsRecord() {
        return getKey() + ((reviewer != null) ? Revisions.REVIEWER_DELIMITER + reviewer : "");
    }

    boolean isAuthoredByUser(@NotNull UserIdentity userIdentity) {
        String author = revision.getStringAuthor();
        if (author == null) {
            return false;
        }
        return defaultRepository ? userIdentity.hasId(author) : userIdentity.hasIdOrName(author);
    }

}