/**
 * SPDX-FileCopyrightText: (c) 2023 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ide.idea.ui.actions;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.execution.ParametersListUtil;

import com.liferay.ide.idea.core.LiferayIcons;
import com.liferay.ide.idea.server.LiferayDockerServerConfigurationProducer;
import com.liferay.ide.idea.server.LiferayDockerServerConfigurationType;
import com.liferay.ide.idea.util.GradleUtil;
import com.liferay.ide.idea.util.LiferayWorkspaceSupport;
import com.liferay.ide.idea.util.ListUtil;

import java.util.List;
import java.util.Objects;

import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.tooling.model.GradleProject;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * @author Simon Jiang
 * @author Seiphon Wang
 */
public class InitDockerBundleAction extends AbstractLiferayGradleTaskAction {

	public InitDockerBundleAction() {
		super("InitDockerBundle", "Run init docker Bundle task", LiferayIcons.LIFERAY_ICON, "createDockerContainer");
	}

	@Override
	protected void afterTask(Project project) {
		List<RunConfigurationProducer<?>> producers = LiferayDockerServerConfigurationProducer.getProducers(project);

		for (RunConfigurationProducer producer : producers) {
			ConfigurationType configurationType = producer.getConfigurationType();

			if (Objects.equals(LiferayDockerServerConfigurationType.id, configurationType.getId())) {
				RunManager runManager = RunManager.getInstance(project);

				RunnerAndConfigurationSettings configuration = runManager.findConfigurationByTypeAndName(
					configurationType, project.getName() + "-docker-server");

				if (configuration == null) {
					List<RunConfiguration> configurationList = runManager.getAllConfigurationsList();

					for (RunConfiguration runConfiguration : configurationList) {
						ConfigurationType type = runConfiguration.getType();

						if (Objects.equals(LiferayDockerServerConfigurationType.id, type.getId())) {
							configuration = runManager.findSettings(runConfiguration);

							break;
						}
					}

					if (configuration == null) {
						configuration = runManager.createConfiguration(
							project.getName() + "-docker-server", producer.getConfigurationFactory());

						runManager.addConfiguration(configuration);
					}
				}

				runManager.setSelectedConfiguration(configuration);
			}
		}
	}

	@Override
	protected void beforeTask(Project project) {
		try {
			ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();

			CommandLineParser gradleCmdParser = new CommandLineParser();

			ParsedCommandLine parsedCommandLine = gradleCmdParser.parse(
				ParametersListUtil.parse("removeDockerContainer", true));

			settings.setExternalProjectPath(project.getBasePath());
			settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.toString());
			settings.setTaskNames(parsedCommandLine.getExtraArguments());

			ExternalSystemUtil.runTask(
				settings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID,
				new TaskCallback() {

					@Override
					public void onFailure() {
					}

					@Override
					public void onSuccess() {
						ExternalSystemUtil.runTask(
							externalTaskExecutionInfo.getSettings(), externalTaskExecutionInfo.getExecutorId(), project,
							GradleConstants.SYSTEM_ID,
							new TaskCallback() {

								@Override
								public void onFailure() {
								}

								@Override
								public void onSuccess() {
									afterTask(project);
								}

							},
							ProgressExecutionMode.IN_BACKGROUND_ASYNC, true);
					}

				},
				ProgressExecutionMode.IN_BACKGROUND_ASYNC, true);
		}
		catch (Exception exception) {
			_logger.error(exception);
		}
	}

	@Override
	protected void doExecute(
		AnActionEvent anActionEvent, RunnerAndConfigurationSettings runnerAndConfigurationSettings) {

		Project project = anActionEvent.getData(CommonDataKeys.PROJECT);

		if (project == null) {
			return;
		}

		beforeTask(project);
	}

	@Override
	protected String getScriptParameters() {
		return _scriptParameters;
	}

	@Override
	protected boolean isEnabledAndVisible(AnActionEvent anActionEvent) {
		if (super.isEnabledAndVisible(anActionEvent)) {
			VirtualFile baseDir = LiferayWorkspaceSupport.getWorkspaceVirtualFile(anActionEvent.getProject());

			if (baseDir == null) {
				return false;
			}

			if (baseDir.equals(getVirtualFile(anActionEvent))) {
				return true;
			}
		}

		return false;
	}

	@Nullable
	@Override
	protected RunnerAndConfigurationSettings processRunnerConfiguration(AnActionEvent anActionEvent) {
		List<Module> warCoreExtProjects = LiferayWorkspaceSupport.getWarCoreExtModules(anActionEvent.getProject());

		StringBuilder scriptParameters = new StringBuilder();

		if (ListUtil.isNotEmpty(warCoreExtProjects)) {
			for (Module module : warCoreExtProjects) {
				GradleProject gradleProject = GradleUtil.getGradleProject(module);

				if (Objects.nonNull(gradleProject)) {
					scriptParameters.append("-x ");
					scriptParameters.append(gradleProject.getPath());
					scriptParameters.append(":buildExtInfo");
					scriptParameters.append(" -x ");
					scriptParameters.append(gradleProject.getPath());
					scriptParameters.append(":deploy");
					scriptParameters.append(" -x ");
					scriptParameters.append(gradleProject.getPath());
					scriptParameters.append(":dockerDeploy ");
				}
			}
		}

		_scriptParameters = scriptParameters.toString();

		return super.processRunnerConfiguration(anActionEvent);
	}

	private static final Logger _logger = Logger.getInstance(InitDockerBundleAction.class);

	private String _scriptParameters = null;

}