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

final class MvpModel {

	enum Kind {
		ACTIVITY, BEAN
	}

	static final class Param {
		final String name;
		final String typeName;

		Param(String name, String typeName) {
			this.name = name;
			this.typeName = typeName;
		}
	}

	static final class FieldInjection {
		final String propertyName;
		final String typeName;
		final boolean jvmField;
		final int resId;
		final String resName;

		FieldInjection(String propertyName, String typeName, boolean jvmField, int resId, String resName) {
			this.propertyName = propertyName;
			this.typeName = typeName;
			this.jvmField = jvmField;
			this.resId = resId;
			this.resName = resName;
		}
	}

	static final class MethodInfo {
		final String name;
		final List<Param> params;
		final String id;
		final long delay;
		final String serial;
		final boolean reusePropagation;

		MethodInfo(String name, List<Param> params, String id, long delay, String serial, boolean reusePropagation) {
			this.name = name;
			this.params = params;
			this.id = id;
			this.delay = delay;
			this.serial = serial;
			this.reusePropagation = reusePropagation;
		}
	}

	final Kind kind;
	final String packageName;
	final String simpleName;
	final String qualifiedName;
	final int layoutId;
	final String layoutResName;
	final List<FieldInjection> viewByIds = new ArrayList<>();
	final List<FieldInjection> stringRes = new ArrayList<>();
	final List<FieldInjection> extras = new ArrayList<>();
	final List<String> afterViews = new ArrayList<>();
	final List<MethodInfo> backgrounds = new ArrayList<>();
	final List<MethodInfo> uiThreads = new ArrayList<>();

	MvpModel(Kind kind, String packageName, String simpleName, String qualifiedName, int layoutId, String layoutResName) {
		this.kind = kind;
		this.packageName = packageName;
		this.simpleName = simpleName;
		this.qualifiedName = qualifiedName;
		this.layoutId = layoutId;
		this.layoutResName = layoutResName;
	}

	String generatedSimpleName() {
		return simpleName + "_";
	}

	String generatedQualifiedName() {
		if (packageName == null || packageName.isEmpty()) {
			return generatedSimpleName();
		}
		return packageName + "." + generatedSimpleName();
	}
}
