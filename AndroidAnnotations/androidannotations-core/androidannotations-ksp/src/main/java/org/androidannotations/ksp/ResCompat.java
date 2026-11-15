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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.androidannotations.ksp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.devtools.ksp.processing.Resolver;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.KSDeclaration;
import com.google.devtools.ksp.symbol.KSFunctionDeclaration;
import kotlin.sequences.SequencesKt;


/**
 * Resource annotation table and ContextCompat branch selection, mirroring the APT
 * {@code AndroidRes} enum and {@code ContextCompatAwareResHandler.getInstanceInvocation}.
 */
public final class ResCompat {

	/** One supported resource annotation, mirroring one {@code AndroidRes} entry. */
	public static final class Type {
		public final String annotationName;
		public final String simpleName;
		public final String rInnerClass;
		public final String method;
		public final List<String> allowedTypes;
		public final boolean contextCompatAware;
		public final int minSdkWithMethod;
		public final String minSdkPlatformName;
		/** Invocation style: RESOURCES / ANIMATION_UTILS / HTML_FROM_HTML (APT handler parity). */
		public final String invocation;

		Type(String annotationName, String simpleName, String rInnerClass, String method, List<String> allowedTypes,
			boolean contextCompatAware, int minSdkWithMethod, String minSdkPlatformName, String invocation) {
			this.annotationName = annotationName;
			this.simpleName = simpleName;
			this.rInnerClass = rInnerClass;
			this.method = method;
			this.allowedTypes = allowedTypes;
			this.contextCompatAware = contextCompatAware;
			this.minSdkWithMethod = minSdkWithMethod;
			this.minSdkPlatformName = minSdkPlatformName;
			this.invocation = invocation;
		}

		static Type of(String annotationName, String rInnerClass, String method, List<String> allowedTypes) {
			return new Type(annotationName, simpleOf(annotationName), rInnerClass, method, allowedTypes, false, -1, "", "RESOURCES");
		}

		static Type compatOf(String annotationName, String rInnerClass, String method, List<String> allowedTypes, int minSdkWithMethod, String minSdkPlatformName) {
			return new Type(annotationName, simpleOf(annotationName), rInnerClass, method, allowedTypes, true, minSdkWithMethod, minSdkPlatformName, "RESOURCES");
		}

		/** APT AnimationResHandler parity: {@code AnimationUtils.loadAnimation(context, R.anim.x)}. */
		static Type animationOf(String annotationName, List<String> allowedTypes) {
			return new Type(annotationName, simpleOf(annotationName), "anim", "getAnimation", allowedTypes, false, -1, "", "ANIMATION_UTILS");
		}

		/** APT HtmlResHandler parity: {@code Html.fromHtml(resources_.getString(R.string.x))}. */
		static Type htmlOf(String annotationName, List<String> allowedTypes) {
			return new Type(annotationName, simpleOf(annotationName), "string", "getString", allowedTypes, false, -1, "", "HTML_FROM_HTML");
		}

		private static String simpleOf(String annotationName) {
			int i = annotationName.lastIndexOf(".");
			return i == -1 ? annotationName : annotationName.substring(i + 1);
		}
	}

	private static final String RES_PKG = "org.androidannotations.annotations.res.";

	/** All supported resource annotations (StringRes is handled separately as in P0a). */
	public static final List<Type> ALL;
	static {
		List<Type> list = new ArrayList<Type>();
		list.add(Type.compatOf(RES_PKG + "ColorRes", "color", "getColor", Arrays.asList("int", "java.lang.Integer"), 23, "M"));
		list.add(Type.of(RES_PKG + "BooleanRes", "bool", "getBoolean", Arrays.asList("java.lang.Boolean", "boolean")));
		list.add(Type.compatOf(RES_PKG + "ColorStateListRes", "color", "getColorStateList", Arrays.asList("android.content.res.ColorStateList"), 23, "M"));
		list.add(Type.of(RES_PKG + "DimensionRes", "dimen", "getDimension", Arrays.asList("java.lang.Float", "float")));
		list.add(Type.of(RES_PKG + "DimensionPixelOffsetRes", "dimen", "getDimensionPixelOffset", Arrays.asList("java.lang.Integer", "int")));
		list.add(Type.of(RES_PKG + "DimensionPixelSizeRes", "dimen", "getDimensionPixelSize", Arrays.asList("java.lang.Integer", "int")));
		list.add(Type.compatOf(RES_PKG + "DrawableRes", "drawable", "getDrawable", Arrays.asList("android.graphics.drawable.Drawable"), 21, "LOLLIPOP"));
		list.add(Type.of(RES_PKG + "IntegerRes", "integer", "getInteger", Arrays.asList("java.lang.Integer", "int")));
		list.add(Type.of(RES_PKG + "IntArrayRes", "array", "getIntArray", Arrays.asList("int[]")));
		list.add(Type.of(RES_PKG + "StringArrayRes", "array", "getStringArray", Arrays.asList("java.lang.String[]")));
		list.add(Type.of(RES_PKG + "TextRes", "string", "getText", Arrays.asList("java.lang.CharSequence")));
		list.add(Type.of(RES_PKG + "TextArrayRes", "array", "getTextArray", Arrays.asList("java.lang.CharSequence")));
		list.add(Type.animationOf(RES_PKG + "AnimationRes", Arrays.asList("android.content.res.XmlResourceParser", "android.view.animation.Animation")));
		list.add(Type.htmlOf(RES_PKG + "HtmlRes", Arrays.asList("java.lang.CharSequence", "android.text.Spanned")));
		list.add(Type.of(RES_PKG + "LayoutRes", "layout", "getLayout", Arrays.asList("android.content.res.XmlResourceParser")));
		list.add(Type.of(RES_PKG + "MovieRes", "movie", "getMovie", Arrays.asList("android.graphics.Movie")));
		ALL = Collections.unmodifiableList(list);
	}

	/** How a ContextCompat-aware resource must be read; mirrors the APT branch order. */
	public static final class Mode {
		/** ANDROIDX_COMPAT / SUPPORT_COMPAT / CONTEXT_METHOD / IF_GUARD / RESOURCES. */
		public final String kind;
		/** IF_GUARD only: the Build.VERSION_CODES constant name (M, LOLLIPOP, ...). */
		public final String platformName;

		Mode(String kind, String platformName) {
			this.kind = kind;
			this.platformName = platformName;
		}
	}

	private ResCompat() {
	}

	/**
	 * Branch selection of APT {@code ContextCompatAwareResHandler.getInstanceInvocation}:
	 * androidx ContextCompat, then support ContextCompat, then the plain context method when the
	 * minSdk is high enough, then an SDK_INT guard when the platform method exists on the compile
	 * classpath, and finally the plain resources call.
	 */
	public static Mode modeFor(Resolver resolver, int minSdkVersion, Type type) {
		if (!type.contextCompatAware) {
			return new Mode("RESOURCES", "");
		}
		if (hasMethod(resolver, "androidx.core.content.ContextCompat", type.method)) {
			return new Mode("ANDROIDX_COMPAT", "");
		}
		if (hasMethod(resolver, "android.support.v4.content.ContextCompat", type.method)) {
			return new Mode("SUPPORT_COMPAT", "");
		}
		if (minSdkVersion >= type.minSdkWithMethod) {
			return new Mode("CONTEXT_METHOD", "");
		}
		if (hasMethod(resolver, "android.content.Context", type.method)) {
			return new Mode("IF_GUARD", type.minSdkPlatformName);
		}
		return new Mode("RESOURCES", "");
	}

	private static boolean hasMethod(Resolver resolver, String className, String methodName) {
		KSClassDeclaration declaration = resolver.getClassDeclarationByName(resolver.getKSNameFromString(className));
		if (declaration == null) {
			return false;
		}
		for (KSDeclaration member : SequencesKt.toList(declaration.getDeclarations())) {
			if (member instanceof KSFunctionDeclaration && methodName.equals(member.getSimpleName().asString())) {
				return true;
			}
		}
		return false;
	}
}
