package com.intellij.ide.projectWizard;

import com.intellij.ide.actions.ImportModuleAction;
import com.intellij.ide.actions.ImportProjectAction;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.newProjectWizard.SelectTemplateStep;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.actions.NewModuleAction;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectTemplate;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 10/29/12
 */
public abstract class ProjectWizardTestCase extends PlatformTestCase {

  protected final List<Sdk> mySdks = new ArrayList<Sdk>();
  protected TestWizard myWizard;
  @Nullable
  private Project myCreatedProject;

  protected Project createProjectFromTemplate(String group, String name, @Nullable Consumer<ModuleWizardStep> adjuster) throws IOException {
    runWizard(group, name, null, adjuster);
    try {
      myCreatedProject = NewProjectUtil.createFromWizard(myWizard, null);
    }
    catch (Throwable e) {
      myCreatedProject = ContainerUtil.find(myProjectManager.getOpenProjects(), new Condition<Project>() {
        @Override
        public boolean value(Project project) {
          return myWizard.getProjectName().equals(project.getName());
        }
      });
      throw new RuntimeException(e);
    }
    assertNotNull(myCreatedProject);
    UIUtil.dispatchAllInvocationEvents();

    Project[] projects = myProjectManager.getOpenProjects();
    assertEquals(2, projects.length);
    return myCreatedProject;
  }

  @Nullable
  protected Module createModuleFromTemplate(String group, String name, @Nullable Consumer<ModuleWizardStep> adjuster) throws IOException {
    runWizard(group, name, getProject(), adjuster);
    return createModuleFromWizard();
  }

  protected Module createModuleFromWizard() {
    return new NewModuleAction().createModuleFromWizard(myProject, null, myWizard);
  }

  protected void runWizard(String group, String name, Project project, @Nullable Consumer<ModuleWizardStep> adjuster) throws IOException {

    createWizard(project);
    SelectTemplateStep step = (SelectTemplateStep)myWizard.getCurrentStepObject();
    boolean condition = step.setSelectedTemplate(group, name);
    if (!condition) {
      throw new IllegalArgumentException(group + "/" + name + " template not found");
    }
    ProjectTemplate template = step.getSelectedTemplate();
    assertNotNull(template);

    if (adjuster != null) {
      adjuster.consume(step);
    }

    runWizard(adjuster);
  }

  protected void createWizard(Project project) throws IOException {
    File directory = FileUtil.createTempDirectory(getName(), "new", false);
    myFilesToDelete.add(directory);
    myWizard = new TestWizard(project, directory.getPath());
    UIUtil.dispatchAllInvocationEvents(); // to make default selection applied
  }

  protected void runWizard(Consumer<ModuleWizardStep> adjuster) {
    while (!myWizard.isLast()) {
      myWizard.doNextAction();
      if (adjuster != null) {
        adjuster.consume(myWizard.getCurrentStepObject());
      }
    }
    myWizard.doOk();
  }

  @Override
  public void tearDown() throws Exception {
    if (myWizard != null) {
      Disposer.dispose(myWizard.getDisposable());
    }
    if (myCreatedProject != null) {
      myProjectManager.closeProject(myCreatedProject);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          Disposer.dispose(myCreatedProject);
        }
      });
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (Sdk sdk : mySdks) {
          ProjectJdkTable.getInstance().removeJdk(sdk);
        }
      }
    });
    super.tearDown();
  }

  protected Module importModuleFrom(ProjectImportProvider provider, String path) {
    return importFrom(path, getProject(), provider);
  }

  protected Module importProjectFrom(String path, ProjectImportProvider... providers) {
    Module module = importFrom(path, null, providers);
    if (module != null) {
      myCreatedProject = module.getProject();
    }
    return module;
  }

  private Module importFrom(String path, @Nullable Project project, final ProjectImportProvider... providers) {
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    assertNotNull("Can't find " + path, file);
    assertTrue(providers[0].canImport(file, project));
    ImportModuleAction action = new ImportModuleAction() {
      @Override
      protected AddModuleWizard createWizard(Project project, List<ProjectImportProvider> available, String path) {
        myWizard = new TestWizard(project, path, providers);
        return myWizard;
      }

      @Override
      protected boolean processWizard(AddModuleWizard wizard) {
        runWizard(null);
        return true;
      }

      @Override
      public List<Module> createFromWizard(Project project, AddModuleWizard wizard) {
        return project == null ? new ImportProjectAction().createFromWizard(project, wizard) : super.createFromWizard(project, wizard);
      }
    };

    List<Module> modules = action.doImport(project, file);
    return modules == null || modules.isEmpty() ? null : modules.get(0);
  }

  protected static class TestWizard extends AddModuleWizard {

    public TestWizard(@Nullable Project project, String defaultPath) {
      super(project, DefaultModulesProvider.createForProject(project), defaultPath);
    }

    public TestWizard(Project project, String filePath, ProjectImportProvider... importProvider) {
      super(project, filePath, importProvider);
    }

    void doOk() {
      doOKAction();
    }

    boolean isLast() {
      return isLastStep();
    }

    void commit() {
      commitStepData(getCurrentStepObject());
    }
  }

}
