/**
 * SPDX-FileCopyrightText: (c) 2023 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ide.idea.util;

import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.util.SystemProperties;
import com.intellij.util.lang.JavaVersion;

import java.io.File;

import org.jetbrains.jps.model.java.JdkVersionDetector;

/**
 * @author Simon Jiang
 */
public class SdkUtil {

	public static Sdk getTestJdk() {
		File javaHome = new File(SystemProperties.getJavaHome());

		if (JdkUtil.checkForJre(javaHome.toPath()) && !JdkUtil.checkForJdk(javaHome.toPath())) {
			File javaHomeParent = javaHome.getParentFile();

			if ((javaHomeParent != null) && JdkUtil.checkForJre(javaHomeParent.toPath()) &&
				JdkUtil.checkForJdk(javaHomeParent.toPath())) {

				javaHome = javaHomeParent;
			}
		}

		String versionName = JdkVersionDetector.formatVersionString(JavaVersion.current());

		JavaSdk javaSdk = JavaSdk.getInstance();

		return javaSdk.createJdk(versionName, javaHome.getAbsolutePath(), !JdkUtil.checkForJdk(javaHome.toPath()));
	}

	public static void maybeAddSdk(Sdk sdk) {
		ProjectJdkTable projectJdkTable = ProjectJdkTable.getInstance();

		SdkTypeId sdkType = sdk.getSdkType();

		Sdk jdk = projectJdkTable.findJdk(sdk.getName(), sdkType.getName());

		if (jdk != null) {
			return;
		}

		projectJdkTable.addJdk(sdk);
	}

}