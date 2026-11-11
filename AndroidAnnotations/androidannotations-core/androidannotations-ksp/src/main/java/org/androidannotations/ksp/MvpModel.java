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

import java.util.ArrayList;
import java.util.List;

public final class MvpModel {

	public enum Kind {
		ACTIVITY, BEAN
	}

	public static final class Param {
		public final String name;
		public final String typeName;

		public Param(String name, String typeName) {
			this.name = name;
			this.typeName = typeName;
		}
	}

	public static final class FieldInjection {
		public final String propertyName;
		public final String typeName;
		public final boolean jvmField;
		public final int resId;
		public final String resName;
		/** @Extra only: Java canonical type incl. generic args (null for non-extra injections). */
		public final String extraFullType;
		/** @Extra only: write-side template for the IntentBuilder_ fluent arg, {arg} = parameter name. */
		public final String extraWriteExpr;
		/** @Extra only: read-side template for Bundle restore, {key} = constant name. */
		public final String extraReadExpr;
		/** @Extra only: restore needs @SuppressWarnings("unchecked") (generic cast). */
		public final boolean extraUnchecked;

		public FieldInjection(String propertyName, String typeName, boolean jvmField, int resId, String resName) {
			this(propertyName, typeName, jvmField, resId, resName, null, "{arg}", "(java.lang.Object) extras_.get({key})", false);
		}

		public FieldInjection(String propertyName, String typeName, boolean jvmField, int resId, String resName,
				String extraFullType, String extraWriteExpr, String extraReadExpr, boolean extraUnchecked) {
			this.propertyName = propertyName;
			this.typeName = typeName;
			this.jvmField = jvmField;
			this.resId = resId;
			this.resName = resName;
			this.extraFullType = extraFullType;
			this.extraWriteExpr = extraWriteExpr;
			this.extraReadExpr = extraReadExpr;
			this.extraUnchecked = extraUnchecked;
		}
	}

	public static final class MethodInfo {
		public final String name;
		public final List<Param> params;
		public final String id;
		public final long delay;
		public final String serial;
		public final boolean reusePropagation;
		/** Java visibility modifier emitted for the generated override: "public" / "protected" / "private" / "" (package-private). */
		public final String visibility;

		public MethodInfo(String name, List<Param> params, String id, long delay, String serial, boolean reusePropagation, String visibility) {
			this.name = name;
			this.params = params;
			this.id = id;
			this.delay = delay;
			this.serial = serial;
			this.reusePropagation = reusePropagation;
			this.visibility = visibility;
		}
	}

	public static final class BeanTarget {
		public final String name;
		/** METHOD form only: local declaration type of the injected parameter (full Java name incl. generics). */
		public final String declaredType;
		public final String beanGeneratedQualifiedName;
		/** True when getInstance_ takes the (context, rootFragment) overload: scope is Default or Fragment. */
		public final boolean withRootFragmentArg;

		public BeanTarget(String name, String declaredType, String beanGeneratedQualifiedName, boolean withRootFragmentArg) {
			this.name = name;
			this.declaredType = declaredType;
			this.beanGeneratedQualifiedName = beanGeneratedQualifiedName;
			this.withRootFragmentArg = withRootFragmentArg;
		}
	}

	public static final class BeanInjection {
		public enum Form {
			FIELD, METHOD
		}

		public final Form form;
		public final String name;
		/** FIELD form only: @JvmField means direct assignment instead of a setter call. */
		public final boolean jvmField;
		public final List<BeanTarget> targets;

		public BeanInjection(Form form, String name, boolean jvmField, List<BeanTarget> targets) {
			this.form = form;
			this.name = name;
			this.jvmField = jvmField;
			this.targets = targets;
		}
	}

	public final Kind kind;
	public final String packageName;
	public final String simpleName;
	public final String qualifiedName;
	public final String classSuffix;
	public final int layoutId;
	public final String layoutResName;
	public final List<FieldInjection> viewByIds = new ArrayList<>();
	public final List<FieldInjection> stringRes = new ArrayList<>();
	public final List<FieldInjection> extras = new ArrayList<>();
	public final List<String> afterViews = new ArrayList<>();
	public final List<String> afterExtras = new ArrayList<>();
	public final List<BeanInjection> beans = new ArrayList<>();
	public final List<String> afterInject = new ArrayList<>();
	/** Validation failures recorded by the handler (it has no logger); the processor logs each and skips generation. */
	public final List<String> errors = new ArrayList<>();
	public final List<MethodInfo> backgrounds = new ArrayList<>();
	public final List<MethodInfo> uiThreads = new ArrayList<>();
	/** True when the generated class is a static nested class inside its enclosing annotated class's generated class. */
	public final boolean nested;
	/** Directly nested annotated models, inlined into this model's generated source (set by the processor). */
	public final List<MvpModel> nestedModels = new ArrayList<>();
	/** @EBean scope name: Default / Activity / Fragment / Singleton ("Default" when unspecified). */
	public final String scope;

	public MvpModel(Kind kind, String packageName, String simpleName, String qualifiedName, String classSuffix, int layoutId, String layoutResName, boolean nested, String scope) {
		this.kind = kind;
		this.packageName = packageName;
		this.simpleName = simpleName;
		this.qualifiedName = qualifiedName;
		this.classSuffix = classSuffix;
		this.layoutId = layoutId;
		this.layoutResName = layoutResName;
		this.nested = nested;
		this.scope = scope;
	}

	public String generatedSimpleName() {
		return simpleName + classSuffix;
	}

	public String generatedQualifiedName() {
		if (packageName == null || packageName.isEmpty()) {
			return generatedSimpleName();
		}
		return packageName + "." + generatedSimpleName();
	}
}
