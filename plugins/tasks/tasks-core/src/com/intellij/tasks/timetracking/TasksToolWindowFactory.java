package com.intellij.tasks.timetracking;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;

/**
 * User: evgeny.zakrevsky
 * Date: 11/8/12
 */
public class TasksToolWindowFactory implements ToolWindowFactory, Condition<Project>, DumbAware {

  @Override
  public boolean value(final Project project) {
    final TaskManager manager = TaskManager.getManager(project);
    final LocalTask activeTask = manager.getActiveTask();
    final boolean isNotUsed =
      activeTask.isDefault() && manager.getLocalTasks().size() == 1 && Comparing.equal(activeTask.getCreated(), activeTask.getUpdated());
    return !isNotUsed;
  }

  @Override
  public void createToolWindowContent(final Project project, final ToolWindow toolWindow) {
    final ContentManager contentManager = toolWindow.getContentManager();
    final Content content = ContentFactory.SERVICE.getInstance().createContent(new TasksToolWindowPanel(project), null, false);
    contentManager.addContent(content);
  }
}
