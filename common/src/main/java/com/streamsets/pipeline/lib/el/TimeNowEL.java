/**
 * Copyright 2015 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.lib.el;

import com.streamsets.pipeline.api.ElFunction;
import com.streamsets.pipeline.api.ElParam;
import com.streamsets.pipeline.api.el.ELEval;
import com.streamsets.pipeline.api.el.ELVars;
import com.streamsets.pipeline.api.impl.Utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeNowEL {

  public static final String TIME_CONTEXT_VAR = "time";
  public static final String TIME_NOW_CONTEXT_VAR = "time_now";

  private TimeNowEL() {}

  @ElFunction(prefix = TIME_CONTEXT_VAR, name = "now", description = "Creates a Datetime object set to the current " +
      "time.")
  public static Date getTimeNowFunc() {
    Date now = (Date) ELEval.getVariablesInScope().getContextVariable(TIME_NOW_CONTEXT_VAR);
    if(null == now) {
      now = new Date();
    }
    return now;
  }

  @ElFunction(prefix = TIME_CONTEXT_VAR, name = "trimDate", description = "Set date portion of datetime expression to January 1, 1970")
  @SuppressWarnings("deprecation")
  public static Date trimDate(@ElParam("datetime") Date in) {
    if(in == null) {
      return null;
    }

    Date ret = new Date(in.getTime());
    ret.setYear(70);
    ret.setMonth(0);
    ret.setDate(1);
    return ret;
  }

  @ElFunction(prefix = TIME_CONTEXT_VAR, name = "trimTime", description = "Set time portion of datetime expression to 00:00:00")
  @SuppressWarnings("deprecation")
  public static Date trimTime(@ElParam("datetime") Date in) {
    if(in == null) {
      return null;
    }

    Date ret = new Date(in.getTime());
    ret.setHours(0);
    ret.setMinutes(0);
    ret.setSeconds(0);
    return ret;
  }

  public static void setTimeNowInContext(ELVars variables, Date now) {
    Utils.checkNotNull(variables, "variables");
    variables.addContextVariable(TIME_NOW_CONTEXT_VAR, now);
  }

  @ElFunction(prefix = TIME_CONTEXT_VAR,
      name = "millisecondsToDateTime",
      description = "Convert epoch in milliseconds to DateTime")
  public static Date millisecondsToDateTime(@ElParam("long") long in) {
    return new Date(in);

  }

  @ElFunction(prefix = TIME_CONTEXT_VAR,
      name = "dateTimeToMilliseconds",
      description = "Convert DateTime to epoch in milliseconds")
  public static long dateTimeToMilliseconds(@ElParam("datetime") Date in) {
    if (in == null) {
      return 0;
    }
    return in.getTime();
  }

  @ElFunction(prefix = TIME_CONTEXT_VAR,
      name = "extractStringFromDate",
      description = "Format a date into a string, based on an output format specification")
  public static String millisecondsToStringDate(@ElParam("datetime") Date in, @ElParam("string") String outputFormat) {
    if(in == null || outputFormat == null || outputFormat.isEmpty()) {
      return "";
    }

    SimpleDateFormat formatter = new SimpleDateFormat(outputFormat);
    return formatter.format(in);
  }

  @ElFunction(prefix = TIME_CONTEXT_VAR,
      name = "extractLongFromDate",
      description = "Format a date into a long, based on an output format specification")
  public static long milliSecondsToLongDate(@ElParam("datetime") Date in, @ElParam("string") String outputFormat)
      throws NumberFormatException {
    if(in == null || outputFormat == null || outputFormat.isEmpty()) {
      return 0;
    }

    SimpleDateFormat formatter = new SimpleDateFormat(outputFormat);
    String str = formatter.format(in);

    String value = str.replaceAll("[^0-9]","");
    return Long.parseLong(value);
  }
}
