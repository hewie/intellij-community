/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 16, 2001
 * Time: 12:50:45 AM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.util.PlatformIcons;

import javax.swing.*;

public class RefProjectImpl extends RefEntityImpl implements RefProject {
  public RefProjectImpl(RefManager refManager) {
    super(refManager.getProject().getName(), refManager);
  }

  public boolean isValid() {
    return true;
  }

  public Icon getIcon(final boolean expanded) {
    return PlatformIcons.PROJECT_ICON;
  }
}
