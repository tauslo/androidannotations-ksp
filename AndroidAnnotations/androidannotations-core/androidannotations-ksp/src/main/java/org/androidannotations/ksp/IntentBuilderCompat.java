/**
 * Copyright (C) 2010-2016 eBusiness Information, Excilys Group
 * Copyright (C) 2016-2020 the AndroidAnnotations project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed To in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.androidannotations.ksp;

import java.util.List;

import com.google.devtools.ksp.processing.Resolver;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.KSDeclaration;
import com.google.devtools.ksp.symbol.KSFunctionDeclaration;
import com.google.devtools.ksp.symbol.KSTypeReference;
import com.google.devtools.ksp.symbol.KSType;
import com.google.devtools.ksp.symbol.KSValueParameter;

import kotlin.sequences.SequencesKt;

/**
 * IntentBuilder branch selection for {@code @EActivity}, mirroring the APT
 * {@code ActivityIntentBuilder} classpath and minSdk probes: fragment overloads, activity options
 * ({@code lastOptions}) and the compat start-activity-for-result starter.
 */
public final class IntentBuilderCompat {

	public static final int MIN_SDK_WITH_FRAGMENT_SUPPORT = 11;
	public static final int MIN_SDK_WITH_ACTIVITY_OPTIONS = 16;

	private static final String FRAGMENT = "android.app.Fragment";
	private static final String ANDROIDX_FRAGMENT = "androidx.fragment.app.Fragment";
	private static final String SUPPORT_V4_FRAGMENT = "android.support.v4.app.Fragment";
	private static final String ANDROIDX_ACTIVITY_COMPAT = "androidx.core.app.ActivityCompat";
	private static final String ACTIVITY_COMPAT = "android.support.v4.app.ActivityCompat";
	private static final String BUNDLE = "android.os.Bundle";

	/** android.app.Fragment overloads: minSdk >= 11 and the class is on the compile classpath. */
	public final boolean fragment;
	/** androidx.fragment.app.Fragment overloads (independent of minSdk). */
	public final boolean androidxFragment;
	/** android.support.v4.app.Fragment overloads (independent of minSdk). */
	public final boolean supportFragment;
	/** Fragment declares startActivity(Intent, Bundle): the 3-arg calls pass {@code lastOptions}. */
	public final boolean activityOptionsInFragment;
	/** Compat starter class (androidx first), or null for the plain Activity/Context calls. */
	public final String activityCompat;
	/** minSdk < 16: wrap the options calls in a Build.VERSION.SDK_INT >= JELLY_BEAN guard. */
	public final boolean guardActivityOptions;

	private IntentBuilderCompat(boolean fragment, boolean androidxFragment, boolean supportFragment,
			boolean activityOptionsInFragment, String activityCompat, boolean guardActivityOptions) {
		this.fragment = fragment;
		this.androidxFragment = androidxFragment;
		this.supportFragment = supportFragment;
		this.activityOptionsInFragment = activityOptionsInFragment;
		this.activityCompat = activityCompat;
		this.guardActivityOptions = guardActivityOptions;
	}

	public static IntentBuilderCompat detect(Resolver resolver, int minSdkVersion) {
		boolean fragment = minSdkVersion >= MIN_SDK_WITH_FRAGMENT_SUPPORT && classExists(resolver, FRAGMENT);
		boolean androidxFragment = classExists(resolver, ANDROIDX_FRAGMENT);
		boolean supportFragment = classExists(resolver, SUPPORT_V4_FRAGMENT);
		boolean activityOptionsInFragment = fragment && hasActivityOptions(resolver, FRAGMENT, 1);
		String activityCompat = null;
		if (classExists(resolver, ANDROIDX_ACTIVITY_COMPAT)) {
			activityCompat = ANDROIDX_ACTIVITY_COMPAT;
		} else if (classExists(resolver, ACTIVITY_COMPAT)) {
			activityCompat = ACTIVITY_COMPAT;
		}
		return new IntentBuilderCompat(fragment, androidxFragment, supportFragment, activityOptionsInFragment,
				activityCompat, minSdkVersion < MIN_SDK_WITH_ACTIVITY_OPTIONS);
	}

	private static boolean classExists(Resolver resolver, String className) {
		return resolver.getClassDeclarationByName(resolver.getKSNameFromString(className)) != null;
	}

	/** APT parity: a {@code startActivity} method whose parameter at {@code optionsParamPosition} is a Bundle. */
	private static boolean hasActivityOptions(Resolver resolver, String className, int optionsParamPosition) {
		KSClassDeclaration declaration = resolver.getClassDeclarationByName(resolver.getKSNameFromString(className));
		if (declaration == null) {
			return false;
		}
		for (KSDeclaration member : SequencesKt.toList(declaration.getDeclarations())) {
			if (!(member instanceof KSFunctionDeclaration)) {
				continue;
			}
			KSFunctionDeclaration function = (KSFunctionDeclaration) member;
			if (!"startActivity".equals(function.getSimpleName().asString())) {
				continue;
			}
			List<KSValueParameter> parameters = function.getParameters();
			if (parameters.size() == optionsParamPosition + 1 && BUNDLE.equals(parameterType(parameters.get(optionsParamPosition)))) {
				return true;
			}
		}
		return false;
	}

	private static String parameterType(KSValueParameter parameter) {
		KSTypeReference typeRef = parameter.getType();
		if (typeRef == null) {
			return null;
		}
		KSType type = typeRef.resolve();
		KSDeclaration declaration = type.getDeclaration();
		if (declaration == null || declaration.getQualifiedName() == null) {
			return null;
		}
		return declaration.getQualifiedName().asString();
	}
}
