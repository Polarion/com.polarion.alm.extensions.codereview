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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.polarion.alm.extensions.codereview.Parameters.UserIdentity;
import com.polarion.platform.persistence.model.IRevision;

@SuppressWarnings("nls")
public final class Revisions {

    private static final char REVIEWED_REVISIONS_DELIMITER = ',';
    private static final @NotNull String REVIEWED_REVISIONS_DELIMITER_REGEX = "\\s*" + REVIEWED_REVISIONS_DELIMITER + "\\s*";
    private static final @NotNull String REVIEWED_REVISIONS_DELIMITER_OUTPUT = REVIEWED_REVISIONS_DELIMITER + " ";
    static final char REPOSITORY_AND_REVISION_DELIMITER = '/';
    static final char REVIEWER_DELIMITER = '\\';

    private final @NotNull List<RevisionModel> revisionModels = new ArrayList<>();

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
        return hasRevisionsToReviewAuthoredByUser(userIdentity);
    }

    public boolean hasRevisionsToReviewAuthoredByUser(@NotNull UserIdentity userIdentity) {
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

    void forEachRevision(@NotNull Consumer<RevisionModel> consumer) {
        revisionModels.forEach(consumer);
    }

    void forEachRevisionToReview(@NotNull Consumer<RevisionModel> consumer) {
        for (RevisionModel revisionModel : revisionModels) {
            if (!revisionModel.reviewed) {
                consumer.accept(revisionModel);
            }
        }
    }

    public @NotNull RevisionsRenderer render() {
        return new RevisionsRenderer(this);
    }

}