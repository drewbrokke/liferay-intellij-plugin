/**
 * SPDX-FileCopyrightText: (c) 2023 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ide.idea.language.service;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import com.liferay.ide.idea.util.SdkUtil;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;

import org.junit.Test;

/**
 * @author Dominik Marks
 * @author Gregory Amerson
 */
public class LiferayServiceJavaImplLineMarkerProviderTest extends LightJavaCodeInsightFixtureTestCase {

	@SuppressWarnings({"rawtypes", "unchecked"})
	@Test
	public void testExceptionNameInspection() {
		myFixture.configureByFiles("com/liferay/ide/model/impl/MyModelImpl.java", "service.xml");

		AtomicBoolean lineMarkerFound = new AtomicBoolean(false);

		List<GutterMark> allGutterMarks = myFixture.findAllGutters();

		Stream<GutterMark> s = allGutterMarks.stream();

		s.filter(
			gutterMark -> gutterMark instanceof LineMarkerInfo.LineMarkerGutterIconRenderer
		).map(
			gutterMark -> {
				LineMarkerInfo.LineMarkerGutterIconRenderer lineMarkerGutterIconRenderer =
					(LineMarkerInfo.LineMarkerGutterIconRenderer)gutterMark;

				return lineMarkerGutterIconRenderer.getLineMarkerInfo();
			}
		).filter(
			lineMarkerInfo -> lineMarkerInfo instanceof RelatedItemLineMarkerInfo
		).flatMap(
			lineMarkerInfo -> {
				RelatedItemLineMarkerInfo relatedItemLineMarkerInfo = (RelatedItemLineMarkerInfo)lineMarkerInfo;

				Collection<GotoRelatedItem> items = relatedItemLineMarkerInfo.createGotoRelatedItems();

				return items.stream();
			}
		).map(
			GotoRelatedItem::getElement
		).filter(
			Objects::nonNull
		).findAny(
		).ifPresent(
			e -> lineMarkerFound.set(true)
		);

		assertTrue("Java Implementation line marker not found", lineMarkerFound.get());
	}

	@NotNull
	@Override
	protected LightProjectDescriptor getProjectDescriptor() {
		return _MY_PROJECT_DESCRIPTOR;
	}

	@Override
	protected String getTestDataPath() {
		return "testdata/com/liferay/ide/idea/language/service/LiferayServiceJavaImplLineMarkerProviderTest";
	}

	private static final LightProjectDescriptor _MY_PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {

		@Override
		public void configureModule(
			@NotNull Module module, @NotNull ModifiableRootModel modifiableRootModel,
			@NotNull ContentEntry contentEntry) {

			LanguageLevelModuleExtension languageLevelModuleExtension = modifiableRootModel.getModuleExtension(
				LanguageLevelModuleExtension.class);

			if (languageLevelModuleExtension != null) {
				languageLevelModuleExtension.setLanguageLevel(LanguageLevel.JDK_1_8);
			}

			Sdk testJdk = SdkUtil.getTestJdk();

			SdkUtil.maybeAddSdk(testJdk);

			modifiableRootModel.setSdk(testJdk);
		}

		@Override
		public Sdk getSdk() {
			return IdeaTestUtil.getMockJdk18();
		}

	};

}