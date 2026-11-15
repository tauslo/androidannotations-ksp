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
import java.util.List;

/**
 * Description of a {@code @SharedPref} annotated interface and the preferences helper class to generate for it,
 * mirroring the APT {@code SharedPrefHolder}/{@code SharedPrefHandler} model.
 */
public final class SharedPrefModel {

	/** Mirrors {@code org.androidannotations.annotations.sharedpreferences.SharedPref.Scope}. */
	public enum Scope {
		APPLICATION_DEFAULT, ACTIVITY, ACTIVITY_DEFAULT, UNIQUE
	}

	/** One preference accessor method of the annotated interface and its generated field/editor-field pair. */
	public static final class PrefMethod {
		/** Method (and preference key) name. */
		public final String name;
		/** One of boolean, float, int, long, string, stringSet. */
		public final String typeKey;
		/** Simple name of the outer field class, e.g. IntPrefField. */
		public final String fieldClassSimple;
		/** Simple name of the editor field class, e.g. IntPrefEditorField. */
		public final String editorFieldClassSimple;
		/** SharedPreferencesHelper method to call, e.g. intField. */
		public final String helperMethodName;
		/** R.string id for the preference key or {@link AnnotationNames#RES_DEFAULT} when the method name is the key. */
		public final int keyResId;
		/** Whether the default value comes from a @DefaultRes resource. */
		public final boolean hasDefaultRes;
		/** R inner class of the default resource (bool, integer, string, array), only when {@link #hasDefaultRes}. */
		public final String defaultResType;
		/** Resource id of the default value or {@link AnnotationNames#RES_DEFAULT}, only when {@link #hasDefaultRes}. */
		public final int defaultResId;
		/** Resource name of the default value, only when {@link #hasDefaultRes}. */
		public final String defaultResName;
		/** Full Java expression for the default value argument of the field helper call. */
		public final String defaultExpr;
		/** Value used in the "Defaults to" javadoc line, or null when that line is omitted. */
		public final String defaultValueStr;
		/** Trimmed documentation comment of the method, or null when absent. */
		public final String doc;

		public PrefMethod(String name, String typeKey, String fieldClassSimple, String editorFieldClassSimple, String helperMethodName, int keyResId, boolean hasDefaultRes,
				String defaultResType, int defaultResId, String defaultResName, String defaultExpr, String defaultValueStr, String doc) {
			this.name = name;
			this.typeKey = typeKey;
			this.fieldClassSimple = fieldClassSimple;
			this.editorFieldClassSimple = editorFieldClassSimple;
			this.helperMethodName = helperMethodName;
			this.keyResId = keyResId;
			this.hasDefaultRes = hasDefaultRes;
			this.defaultResType = defaultResType;
			this.defaultResId = defaultResId;
			this.defaultResName = defaultResName;
			this.defaultExpr = defaultExpr;
			this.defaultValueStr = defaultValueStr;
			this.doc = doc;
		}
	}

	public final String packageName;
	public final String simpleName;
	public final String classSuffix;
	/** Qualified name of the enclosing type when this interface is nested; null for a top-level interface. */
	public final String enclosingQualifiedName;
	public final Scope scope;
	/** @SharedPref#name() when non-blank, otherwise the interface simple name. */
	public final String effectiveName;
	/** @SharedPref#mode(). */
	public final int mode;
	/** Whether the outer helper class declares a private Context context_ field. */
	public final boolean hasOuterContext;
	/** Whether the editor class takes and stores a Context (and edit() passes context_). */
	public final boolean hasEditorContext;
	public final List<PrefMethod> methods = new ArrayList<>();
	public final List<String> errors = new ArrayList<>();
	/** Set when this helper is emitted inside an enclosing generated component ({@code Outer_.Prefs_}). */
	private String nestedGeneratedQualifiedName;

	public SharedPrefModel(String packageName, String simpleName, String classSuffix, String enclosingQualifiedName, Scope scope, String effectiveName, int mode,
			boolean hasOuterContext, boolean hasEditorContext) {
		this.packageName = packageName;
		this.simpleName = simpleName;
		this.classSuffix = classSuffix;
		this.enclosingQualifiedName = enclosingQualifiedName;
		this.scope = scope;
		this.effectiveName = effectiveName;
		this.mode = mode;
		this.hasOuterContext = hasOuterContext;
		this.hasEditorContext = hasEditorContext;
	}

	public boolean isNested() {
		return enclosingQualifiedName != null;
	}

	/** Records that the helper is a static nested class of {@code enclosingGeneratedQualifiedName}. */
	public void nestInside(String enclosingGeneratedQualifiedName) {
		this.nestedGeneratedQualifiedName = enclosingGeneratedQualifiedName + "." + generatedSimpleName();
	}

	public String generatedSimpleName() {
		return simpleName + classSuffix;
	}

	public String editorSimpleName() {
		return simpleName + "Editor" + classSuffix;
	}

	public String qualifiedName() {
		if (enclosingQualifiedName != null) {
			return enclosingQualifiedName + "." + simpleName;
		}
		return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
	}

	public String generatedQualifiedName() {
		if (nestedGeneratedQualifiedName != null) {
			return nestedGeneratedQualifiedName;
		}
		return packageName.isEmpty() ? generatedSimpleName() : packageName + "." + generatedSimpleName();
	}
}
