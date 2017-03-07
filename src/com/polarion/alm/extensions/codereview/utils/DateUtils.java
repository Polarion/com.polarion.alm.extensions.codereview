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
package com.polarion.alm.extensions.codereview.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DateUtils {

    public static @Nullable LocalDate dateToLocalDate(@Nullable Date date) {
        if (date == null) {
            return null;
        }
        return dateToZonedDateTime(date).toLocalDate();
    }

    public static @Nullable LocalDateTime dateToLocalDateTime(@Nullable Date date) {
        if (date == null) {
            return null;
        }
        return dateToZonedDateTime(date).toLocalDateTime();
    }

    private static @NotNull ZonedDateTime dateToZonedDateTime(@NotNull Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault());
    }

}
