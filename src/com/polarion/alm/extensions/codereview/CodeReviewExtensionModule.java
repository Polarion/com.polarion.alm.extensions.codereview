/*
 * Copyright (C) 2004-2016 Polarion Software
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

import com.google.inject.AbstractModule;
import com.polarion.alm.ui.server.forms.extensions.FormExtensionContribution;
import com.polarion.platform.guice.internal.Contributions;

/**
 * Guice module for this plugin. Must be referenced in the MANIFETS.MF in section Guice-Modules.
 */
public class CodeReviewExtensionModule extends AbstractModule {

    @Override
    protected void configure() {
        Contributions<FormExtensionContribution> contributions = new Contributions<FormExtensionContribution>(binder(), FormExtensionContribution.class);
        contributions.addBinding().toInstance(new FormExtensionContribution(CodeReviewExtension.class, CodeReviewExtension.ID));
    }

}
