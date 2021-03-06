/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.codeStyle.arrangement.group;

import com.intellij.psi.codeStyle.arrangement.order.ArrangementEntryOrderType;
import org.jetbrains.annotations.NotNull;

/**
 * Encapsulates information about grouping rules to use during arrangement.
 * <p/>
 * E.g. a rule might look like 'keep together class methods which implement methods from particular interface'.
 * 
 * @author Denis Zhdanov
 * @since 9/18/12 8:50 AM
 */
public class ArrangementGroupingRule {

  @NotNull private final ArrangementGroupingType   myGroupingType;
  @NotNull private final ArrangementEntryOrderType myOrderType;

  public ArrangementGroupingRule(@NotNull ArrangementGroupingType groupingType) {
    this(groupingType, ArrangementEntryOrderType.KEEP);
  }
  
  public ArrangementGroupingRule(@NotNull ArrangementGroupingType groupingType, @NotNull ArrangementEntryOrderType orderType) {
    myGroupingType = groupingType;
    myOrderType = orderType;
  }

  @NotNull
  public ArrangementGroupingType getRule() {
    return myGroupingType;
  }

  @NotNull
  public ArrangementEntryOrderType getOrderType() {
    return myOrderType;
  }

  @Override
  public int hashCode() {
    int result = myGroupingType.hashCode();
    result = 31 * result + myOrderType.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ArrangementGroupingRule rule = (ArrangementGroupingRule)o;

    if (myOrderType != rule.myOrderType) return false;
    if (!myGroupingType.equals(rule.myGroupingType)) return false;

    return true;
  }

  @Override
  public String toString() {
    return String.format("(%s, %s)", myGroupingType, myOrderType);
  }
}
