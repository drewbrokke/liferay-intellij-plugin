/**
 * SPDX-FileCopyrightText: (c) 2023 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ide.idea.server;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.application.BaseJavaApplicationCommandLineState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.PathsList;

import com.liferay.ide.idea.server.portal.PortalBundle;
import com.liferay.ide.idea.server.portal.PortalBundleFactory;
import com.liferay.ide.idea.util.FileUtil;
import com.liferay.ide.idea.util.PortalPropertiesConfiguration;
import com.liferay.ide.idea.util.ServerUtil;

import java.io.File;
import java.io.InputStream;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import org.jetbrains.annotations.NotNull;

/**
 * @author Terry Jia
 * @author Simon Jiang
 */
public class LiferayServerCommandLineState extends BaseJavaApplicationCommandLineState<LiferayServerConfiguration> {

	public LiferayServerCommandLineState(
		ExecutionEnvironment executionEnvironment, @NotNull LiferayServerConfiguration liferayServerConfiguration) {

		super(executionEnvironment, liferayServerConfiguration);
	}

	@Override
	protected JavaParameters createJavaParameters() throws ExecutionException {
		JavaParameters javaParameters = new JavaParameters();

		LiferayServerConfiguration liferayServerConfiguration = getConfiguration();

		String alternativeJre = null;

		if (liferayServerConfiguration.isAlternativeJrePathEnabled()) {
			alternativeJre = liferayServerConfiguration.getAlternativeJrePath();
		}

		Path jrePath = Paths.get(alternativeJre);

		if (!jrePath.isAbsolute()) {
			Sdk[] allJdks = ProjectJdkTable.getInstance(
			).getAllJdks();

			for (Sdk sdk : allJdks) {
				if (Objects.equals(sdk.getName(), alternativeJre)) {
					alternativeJre = sdk.getHomePath();
				}
			}
		}

		Map<String, String> userEnvironmentMap = new LinkedHashMap<>();

		if (liferayServerConfiguration.isPassParentEnvs()) {
			userEnvironmentMap.putAll(System.getenv());
		}

		userEnvironmentMap.putAll(liferayServerConfiguration.getEnvs());

		javaParameters.setEnv(userEnvironmentMap);

		Sdk projectJdk = JavaParametersUtil.createProjectJdk(liferayServerConfiguration.getProject(), alternativeJre);

		if (JavaSdkVersionUtil.isAtLeast(projectJdk, JavaSdkVersion.JDK_11)) {
			javaParameters.addEnv("JDK_JAVA_OPTIONS", _getJdkJavaOptionsString(javaParameters));
		}

		javaParameters.setJdk(projectJdk);

		String bundleLocation = liferayServerConfiguration.getBundleLocation();

		PortalBundleFactory bundleFactory = ServerUtil.getPortalBundleFactory(
			liferayServerConfiguration.getBundleType());

		Path appServerPath = bundleFactory.findAppServerPath(Paths.get(bundleLocation));

		if (appServerPath == null) {
			throw new ExecutionException("Liferay bundle location is invalid.  " + bundleLocation);
		}

		final PortalBundle portalBundle = bundleFactory.create(appServerPath);

		ParametersList programParametersList = javaParameters.getProgramParametersList();

		Stream.of(
			portalBundle.getRuntimeStartProgArgs()
		).forEach(
			programParametersList::add
		);

		PathsList classPath = javaParameters.getClassPath();

		Stream.of(
			portalBundle.getRuntimeClasspath()
		).map(
			Path::toFile
		).forEach(
			classPath::add
		);

		javaParameters.setMainClass(portalBundle.getMainClass());

		ParametersList vmParametersList = javaParameters.getVMParametersList();

		Stream.of(
			portalBundle.getRuntimeStartVMArgs(projectJdk)
		).forEach(
			vmParametersList::add
		);

		List<String> vmParametersStrings = vmParametersList.getList();

		String serverConfigurationVMParameterString = liferayServerConfiguration.getVMParameters();

		List<String> serverConfigurationVMParameters = List.of(StringUtils.split(serverConfigurationVMParameterString));

		serverConfigurationVMParameters.stream(
		).filter(
			parameter -> !vmParametersStrings.contains(parameter)
		).forEach(
			parameter -> vmParametersList.add(parameter)
		);

		setupJavaParameters(javaParameters);

		try {
			_configureDeveloperMode(liferayServerConfiguration);
		}
		catch (Exception exception) {
			throw new ExecutionException(exception);
		}

		return javaParameters;
	}

	private void _configureDeveloperMode(LiferayServerConfiguration configuration) throws Exception {
		PortalBundleFactory bundleFactory = ServerUtil.getPortalBundleFactory(configuration.getBundleType());

		Path appServerPath = bundleFactory.findAppServerPath(Paths.get(configuration.getBundleLocation()));

		File file = appServerPath.toFile();

		File portalExtPropertiesFile = new File(file.getParentFile(), "portal-ext.properties");

		if (configuration.getDeveloperMode()) {
			if (!portalExtPropertiesFile.exists()) {
				portalExtPropertiesFile.createNewFile();
			}

			PortalPropertiesConfiguration portalPropertiesConfiguration = new PortalPropertiesConfiguration();

			try (InputStream inputStream = Files.newInputStream(portalExtPropertiesFile.toPath())) {
				portalPropertiesConfiguration.load(inputStream);
			}

			String[] properties = portalPropertiesConfiguration.getStringArray("include-and-override");

			boolean existing = false;

			for (String prop : properties) {
				if (prop.equals("portal-developer.properties")) {
					existing = true;

					break;
				}
			}

			if (!existing) {
				portalPropertiesConfiguration.addProperty("include-and-override", "portal-developer.properties");
			}

			portalPropertiesConfiguration.save(portalExtPropertiesFile);
		}
		else if (portalExtPropertiesFile.exists()) {
			String contents = FileUtil.readContents(portalExtPropertiesFile, true);

			FileUtils.write(
				portalExtPropertiesFile, contents.replace("include-and-override=portal-developer.properties", ""),
				Charset.defaultCharset());
		}
	}

	@NotNull
	private String _getJdkJavaOptionsString(JavaParameters javaParameters) {
		List<String> opens = new ArrayList<>();

		Map<String, String> env = javaParameters.getEnv();

		if (env.containsKey("JDK_JAVA_OPTIONS")) {
			opens.add(env.get("JDK_JAVA_OPTIONS"));
		}

		opens.add("--add-opens=java.base/java.io=ALL-UNNAMED");
		opens.add("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED");
		opens.add("--add-opens=java.base/java.lang.ref=ALL-UNNAMED");
		opens.add("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED");
		opens.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
		opens.add("--add-opens=java.base/java.net=ALL-UNNAMED");
		opens.add("--add-opens=java.base/java.nio.charset=ALL-UNNAMED");
		opens.add("--add-opens=java.base/java.nio=ALL-UNNAMED");
		opens.add("--add-opens=java.base/java.text=ALL-UNNAMED");
		opens.add("--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED");
		opens.add("--add-opens=java.base/java.util.concurrent=ALL-UNNAMED");
		opens.add("--add-opens=java.base/java.util=ALL-UNNAMED");
		opens.add("--add-opens=java.base/sun.nio.ch=ALL-UNNAMED");
		opens.add("--add-opens=java.base/sun.nio.cs=ALL-UNNAMED");
		opens.add("--add-opens=java.xml/com.sun.org.apache.xerces.internal.util=ALL-UNNAMED");

		return String.join(" ", opens);
	}

}