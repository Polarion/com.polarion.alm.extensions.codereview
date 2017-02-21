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

import java.io.IOException;
import java.io.InputStream;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.ITrackerUser;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowAction;
import com.polarion.platform.context.IContextService;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.security.ISecurityService;
import com.polarion.platform.service.repository.IRepositoryService;
import com.polarion.subterra.base.data.identification.IContextId;
import com.polarion.subterra.base.location.ILocation;

@SuppressWarnings("nls")
public class PlatformParametersContext implements ParametersContext {

    public PlatformParametersContext(@NotNull ISecurityService securityService, @NotNull ITrackerService trackerService, @NotNull IContextService contextService, @NotNull IRepositoryService repositoryService) {
        super();
        this.securityService = securityService;
        this.trackerService = trackerService;
        this.contextService = contextService;
        this.repositoryService = repositoryService;
    }

    public static @NotNull PlatformParametersContext createFromPlatform() {
        ISecurityService securityService = PlatformContext.getPlatform().lookupService(ISecurityService.class);
        ITrackerService trackerService = PlatformContext.getPlatform().lookupService(ITrackerService.class);
        IContextService contextService = PlatformContext.getPlatform().lookupService(IContextService.class);
        IRepositoryService repositoryService = PlatformContext.getPlatform().lookupService(IRepositoryService.class);
        return new PlatformParametersContext(securityService, trackerService, contextService, repositoryService);
    }

    private final @NotNull ISecurityService securityService;
    private final @NotNull ITrackerService trackerService;
    private final @NotNull IContextService contextService;
    private final @NotNull IRepositoryService repositoryService;
    private final Map<IContextId, Properties> configurationCache = new HashMap<>();

    @Override
    @Nullable
    public String getCurrentUser() {
        return securityService.getCurrentUser();
    }

    @Override
    @Nullable
    public String getFullNameOfUser(@NotNull String user) {
        ITrackerUser trackerUser = trackerService.getTrackerUser(user);
        if (trackerUser.isUnresolvable()) {
            return null;
        }
        return trackerUser.getName();
    }

    @Override
    public boolean hasRole(@NotNull String user, @NotNull String role, @NotNull IContextId contextId) {
        Collection<String> rolesForUser = securityService.getRolesForUser(user, contextId);
        return rolesForUser.contains(role);
    }

    @Override
    @Nullable
    public IWorkflowAction getAvailableWorkflowAction(@NotNull IWorkItem workItem, @NotNull String actionName) {
        IWorkflowAction[] availableActions = trackerService.getWorkflowManager().getAvailableActions(workItem);
        for (IWorkflowAction availableAction : availableActions) {
            if (actionName.equals(availableAction.getNativeActionId())) {
                return availableAction;
            }
        }
        return null;
    }

    @Override
    @NotNull
    public IWorkItem getWorkItem(@NotNull String projectId, @NotNull String id) {
        return trackerService.findWorkItem(projectId, id);
    }

    @Override
    @NotNull
    public Properties loadConfiguration(@NotNull IWorkItem workItem) {
        return configurationCache.computeIfAbsent(workItem.getContextId(), contextId -> loadConfigurationWithoutCaching(contextId));
    }

    private @NotNull Properties loadConfigurationWithoutCaching(@NotNull IContextId contextId) {
        ILocation location = contextService.getContextforId(contextId).getLocation();
        try {
            final Properties configuration = new Properties();
            location = Objects.requireNonNull(location).append(".polarion/codereview/codereview.properties");
            final ILocation f_location = location;
            securityService.doAsSystemUser(new PrivilegedExceptionAction<Void>() {
                @Override
                public Void run() throws IOException {
                    InputStream content = repositoryService.getReadOnlyConnection(f_location).getContent(f_location);
                    configuration.load(content);
                    return null;
                }
            });
            return configuration;
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error occurred while reading from location " + location + ": " + e.getMessage(), e);
        }
    }

}
