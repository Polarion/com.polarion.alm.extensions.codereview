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

import com.polarion.alm.tracker.ITrackerService;
import com.polarion.platform.ITransactionService;
import com.polarion.platform.context.IContextService;
import com.polarion.platform.jobs.GenericJobException;
import com.polarion.platform.jobs.IJobDescriptor;
import com.polarion.platform.jobs.IJobUnit;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.spi.BasicJobDescriptor;
import com.polarion.platform.jobs.spi.JobParameterPrimitiveType;
import com.polarion.platform.jobs.spi.SimpleJobParameter;
import com.polarion.platform.persistence.IDataService;
import com.polarion.platform.security.ISecurityService;
import com.polarion.platform.service.repository.IRepositoryService;

@SuppressWarnings("nls")
public class CodeReviewAssignerJobUnitFactory implements IJobUnitFactory {

    public static final String JOB_NAME = "codereview.assigner";

    @Override
    public String getName() {
        return JOB_NAME;
    }

    @Override
    public IJobDescriptor getJobDescriptor(IJobUnit jobUnit) {
        BasicJobDescriptor desc = new BasicJobDescriptor("Code Review Assigner", jobUnit);
        JobParameterPrimitiveType stringType = new JobParameterPrimitiveType("String of characters", String.class);
        JobParameterPrimitiveType booleanType = new JobParameterPrimitiveType("True/false", Boolean.class);
        desc.addParameter(new SimpleJobParameter(desc.getRootParameterGroup(), "reviewerRole", "Reviewer Role", stringType).setRequired(true));
        desc.addParameter(new SimpleJobParameter(desc.getRootParameterGroup(), "reviewedItemsQuery", "Reviewed Items Query", stringType).setRequired(true));
        desc.addParameter(new SimpleJobParameter(desc.getRootParameterGroup(), "toBeReviewedItemsQuery", "To Be Reviewed Items Query", stringType).setRequired(true));
        desc.addParameter(new SimpleJobParameter(desc.getRootParameterGroup(), "userAccountVaultKey", "User Account Vault Key", stringType).setRequired(false));
        desc.addParameter(new SimpleJobParameter(desc.getRootParameterGroup(), "debugMode", "Enable Debug Mode?", booleanType).setRequired(false));
        return desc;
    }

    @Override
    public IJobUnit createJobUnit(String name) throws GenericJobException {
        CodeReviewAssignerJobUnit jobUnit = new CodeReviewAssignerJobUnit(name, this);
        jobUnit.setDataService(dataService);
        jobUnit.setTrackerService(trackerService);
        jobUnit.setSecurityService(securityService);
        jobUnit.setContextService(contextService);
        jobUnit.setRepositoryService(repositoryService);
        jobUnit.setTransactionService(transactionService);
        return jobUnit;
    }

    private /*@NotNull*/ IDataService dataService;
    private /*@NotNull*/ ITrackerService trackerService;
    private /*@NotNull*/ ISecurityService securityService;
    private /*@NotNull*/ IContextService contextService;
    private /*@NotNull*/ IRepositoryService repositoryService;
    private /*@NotNull*/ ITransactionService transactionService;

    public void setDataService(@NotNull IDataService dataService) {
        this.dataService = dataService;
    }

    public void setTrackerService(@NotNull ITrackerService trackerService) {
        this.trackerService = trackerService;
    }

    public void setSecurityService(@NotNull ISecurityService securityService) {
        this.securityService = securityService;
    }

    public void setContextService(@NotNull IContextService contextService) {
        this.contextService = contextService;
    }

    public void setRepositoryService(@NotNull IRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    public void setTransactionService(@NotNull ITransactionService transactionService) {
        this.transactionService = transactionService;
    }

}
