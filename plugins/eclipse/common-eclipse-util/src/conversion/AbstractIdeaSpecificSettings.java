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
package org.jetbrains.idea.eclipse.conversion;

import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Element;
import org.jetbrains.idea.eclipse.IdeaXml;

import java.util.List;

/**
 * User: anna
 * Date: 11/8/12
 */
public abstract class AbstractIdeaSpecificSettings<T, C> {
  public void readIDEASpecific(final Element root, T model) throws InvalidDataException {
    expandElement(root, model);

    readLanguageLevel(root, model);

    setupCompilerOutputs(root, model);

    final List entriesElements = root.getChildren(IdeaXml.CONTENT_ENTRY_TAG);
    if (!entriesElements.isEmpty()) {
      for (Object o : entriesElements) {
        readContentEntry((Element)o, createContentEntry(model, ((Element)o).getAttributeValue(IdeaXml.URL_ATTR)), model);
      }
    } else {
      final C[] entries = getEntries(model);//todo
      if (entries.length > 0) {
        readContentEntry(root, entries[0], model);
      }
    }

    setupJdk(root, model);
    setupLibraryRoots(root, model);
    overrideModulesScopes(root, model);
  }

  protected abstract C[] getEntries(T model);

  protected abstract C createContentEntry(T model, String url);

  protected abstract void setupLibraryRoots(Element root, T model);

  protected abstract void setupJdk(Element root, T model);

  protected abstract void setupCompilerOutputs(Element root, T model);

  protected abstract void readLanguageLevel(Element root, T model) throws InvalidDataException;

  protected abstract void expandElement(Element root, T model);

  protected abstract void overrideModulesScopes(Element root, T model);

  public abstract void readContentEntry(Element root, C entry, T model);
}
