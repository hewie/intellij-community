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
package com.intellij.core;

import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobLauncher;
import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderFactoryImpl;
import com.intellij.mock.MockApplication;
import com.intellij.mock.MockFileDocumentManagerImpl;
import com.intellij.mock.MockReferenceProvidersRegistry;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ExtensionAreas;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.StaticGetter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.openapi.vfs.impl.CoreVirtualFilePointerManager;
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl;
import com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.openapi.vfs.newvfs.FileSystemPersistence;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.PsiReferenceServiceImpl;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.stubs.BinaryFileStubBuilders;
import com.intellij.psi.stubs.CoreStubTreeLoader;
import com.intellij.psi.stubs.StubTreeLoader;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.messages.impl.MessageBusImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.MutablePicoContainer;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author yole
 */
public class CoreApplicationEnvironment {
  private final CoreFileTypeRegistry myFileTypeRegistry;
  private final CoreEncodingRegistry myEncodingRegistry;
  protected final MockApplication myApplication;
  private final CoreLocalFileSystem myLocalFileSystem;
  protected final VirtualFileSystem myJarFileSystem;
  private final Disposable myParentDisposable;

  public CoreApplicationEnvironment(Disposable parentDisposable) {
    myParentDisposable = parentDisposable;
    Extensions.cleanRootArea(myParentDisposable);

    myFileTypeRegistry = new CoreFileTypeRegistry();
    myEncodingRegistry = new CoreEncodingRegistry();

    myApplication = new MockApplication(myParentDisposable);
    ApplicationManager.setApplication(myApplication,
                                      new StaticGetter<FileTypeRegistry>(myFileTypeRegistry),
                                      new StaticGetter<EncodingRegistry>(myEncodingRegistry),
                                      myParentDisposable);
    myLocalFileSystem = createLocalFileSystem();
    myJarFileSystem = createJarFileSystem();

    Extensions.registerAreaClass(ExtensionAreas.IDEA_PROJECT, null);

    final MutablePicoContainer appContainer = myApplication.getPicoContainer();
    registerComponentInstance(appContainer, FileDocumentManager.class, new MockFileDocumentManagerImpl(new Function<CharSequence, Document>() {
      @Override
      public Document fun(CharSequence charSequence) {
        return new DocumentImpl(charSequence);
      }
    }, null));

    FileSystemPersistence fileSystemPersistence = new FileSystemPersistence() {
      @Override
      public void refresh(boolean asynchronous, Runnable postAction, @NotNull ModalityState modalityState) {
      }

      @Override
      public int getCheapFileSystemModificationCount() {
        return 0;
      }

      @Nullable
      @Override
      public VirtualFile findFileById(int id) {
        return null;
      }
    };
    VirtualFileManagerImpl virtualFileManager = new VirtualFileManagerImpl(new VirtualFileSystem[]{myLocalFileSystem, myJarFileSystem},
                                                                       new MessageBusImpl(myApplication, null),
                                                                       fileSystemPersistence
    );
    registerComponentInstance(appContainer, VirtualFileManager.class, virtualFileManager
    );
    myApplication.registerService(VirtualFilePointerManager.class, new CoreVirtualFilePointerManager());

    myApplication.registerService(DefaultASTFactory.class, new CoreASTFactory());
    myApplication.registerService(PsiBuilderFactory.class, new PsiBuilderFactoryImpl());
    myApplication.registerService(ReferenceProvidersRegistry.class, new MockReferenceProvidersRegistry());
    myApplication.registerService(StubTreeLoader.class, new CoreStubTreeLoader());
    myApplication.registerService(PsiReferenceService.class, new PsiReferenceServiceImpl());

    registerApplicationExtensionPoint(ContentBasedFileSubstitutor.EP_NAME, ContentBasedFileSubstitutor.class);
    registerExtensionPoint(Extensions.getRootArea(), BinaryFileStubBuilders.EP_NAME, FileTypeExtensionPoint.class);

    ProgressIndicatorProvider.ourInstance = createProgressIndicatorProvider();

    myApplication.registerService(JobLauncher.class, new JobLauncher() {
      @Override
      public <T> boolean invokeConcurrentlyUnderProgress(@NotNull List<T> things,
                                                         ProgressIndicator progress,
                                                         boolean failFastOnAcquireReadAction,
                                                         @NotNull Processor<T> thingProcessor) throws ProcessCanceledException {
        for (T thing : things) {
          if (!thingProcessor.process(thing))
            return false;
        }
        return true;
      }

      @Override
      public Job<Void> submitToJobThread(int priority, @NotNull Runnable action, Consumer<Future> onDoneCallback) {
        action.run();
        if (onDoneCallback != null)
          onDoneCallback.consume(new Future() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
              return false;
            }

            @Override
            public boolean isCancelled() {
              return false;
            }

            @Override
            public boolean isDone() {
              return true;
            }

            @Override
            public Object get() throws InterruptedException, ExecutionException {
              return null;
            }

            @Override
            public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
              return null;
            }
          });
        return null;
      }
    });

  }

  protected ProgressIndicatorProvider createProgressIndicatorProvider() {
    return new ProgressIndicatorProvider() {
      @Override
      public ProgressIndicator getProgressIndicator() {
        return new EmptyProgressIndicator();
      }

      @Override
      protected void doCheckCanceled() throws ProcessCanceledException {
      }

      @Override
      public NonCancelableSection startNonCancelableSection() {
        return new NonCancelableSection() {
          @Override
          public void done() {
          }
        };
      }
    };
  }

  protected VirtualFileSystem createJarFileSystem() {
    return new CoreJarFileSystem();
  }

  protected CoreLocalFileSystem createLocalFileSystem() {
    return new CoreLocalFileSystem();
  }

  public MockApplication getApplication() {
    return myApplication;
  }

  public Disposable getParentDisposable() {
    return myParentDisposable;
  }

  public  <T> void registerApplicationComponent(final Class<T> interfaceClass, final T implementation) {
    registerComponentInstance(myApplication.getPicoContainer(), interfaceClass, implementation);
  }

  public void registerFileType(FileType fileType, String extension) {
    myFileTypeRegistry.registerFileType(fileType, extension);
  }

  public void registerParserDefinition(ParserDefinition definition) {
    addExplicitExtension(LanguageParserDefinitions.INSTANCE, definition.getFileNodeType().getLanguage(), definition);
  }

  public static <T> void registerComponentInstance(final MutablePicoContainer container, final Class<T> key, final T implementation) {
    container.unregisterComponent(key);
    container.registerComponentInstance(key, implementation);
  }

  protected <T> void addExplicitExtension(final LanguageExtension<T> instance, final Language language, final T object) {
    instance.addExplicitExtension(language, object);
    Disposer.register(myParentDisposable, new Disposable() {
      @Override
      public void dispose() {
        instance.removeExplicitExtension(language, object);
      }
    });
  }

  protected <T> void addExplicitExtension(final FileTypeExtension<T> instance, final FileType fileType, final T object) {
    instance.addExplicitExtension(fileType, object);
    Disposer.register(myParentDisposable, new Disposable() {
      @Override
      public void dispose() {
        instance.removeExplicitExtension(fileType, object);
      }
    });
  }

  protected <T> void addExtension(ExtensionPointName<T> name, final T extension) {
    final ExtensionPoint<T> extensionPoint = Extensions.getRootArea().getExtensionPoint(name);
    extensionPoint.registerExtension(extension);
    Disposer.register(myParentDisposable, new Disposable() {
      @Override
      public void dispose() {
        extensionPoint.unregisterExtension(extension);
      }
    });
  }


  public static <T> void registerExtensionPoint(final ExtensionsArea area, final ExtensionPointName<T> extensionPointName,
                                                   final Class<? extends T> aClass) {
    final String name = extensionPointName.getName();
    registerExtensionPoint(area, name, aClass);
  }

  public static <T> void registerExtensionPoint(ExtensionsArea area, String name, Class<? extends T> aClass) {
    if (!area.hasExtensionPoint(name)) {
      ExtensionPoint.Kind kind = aClass.isInterface() || (aClass.getModifiers() & Modifier.ABSTRACT) != 0 ? ExtensionPoint.Kind.INTERFACE : ExtensionPoint.Kind.BEAN_CLASS;
      area.registerExtensionPoint(name, aClass.getName(), kind);
    }
  }

  public static <T> void registerApplicationExtensionPoint(final ExtensionPointName<T> extensionPointName, final Class<? extends T> aClass) {
    final String name = extensionPointName.getName();
    registerExtensionPoint(Extensions.getRootArea(), name, aClass);
  }

  public CoreLocalFileSystem getLocalFileSystem() {
    return myLocalFileSystem;
  }

  public VirtualFileSystem getJarFileSystem() {
    return myJarFileSystem;
  }
}
