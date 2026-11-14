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
		ACTIVITY, APPLICATION, BEAN, FRAGMENT, SERVICE
	}

	public static final class Param {
		/** @NotNull/@Nullable synthesized from Kotlin nullability for .kt sources; null otherwise. */
		public final String nullAnnotation;

		public final String name;
		public final String typeName;

		public Param(String name, String typeName, String nullAnnotation) {
			this.name = name;
			this.typeName = typeName;
			this.nullAnnotation = nullAnnotation;
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

	public static final class ResField {
		public final String propertyName;
		public final String typeName;
		public final boolean jvmField;
		public final int resId;
		public final String resName;
		/** R inner class of the resource (color, drawable, bool, integer, array, string, dimen). */
		public final String rInnerClass;
		/** Resources lookup method (getColor, getBoolean, getStringArray, ...). */
		public final String method;
		/** Invocation style from ResCompat.Type: RESOURCES / ANIMATION_UTILS / HTML_FROM_HTML. */
		public final String invocation;

		public ResField(String propertyName, String typeName, boolean jvmField, int resId, String resName, String rInnerClass, String method, String invocation) {
			this.propertyName = propertyName;
			this.typeName = typeName;
			this.jvmField = jvmField;
			this.resId = resId;
			this.resName = resName;
			this.rInnerClass = rInnerClass;
			this.method = method;
			this.invocation = invocation;
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

	/** APT ViewByIdHandler parity (method-injection form): one injected parameter of a @ViewById method. */
	public static final class ViewMethodParam {
		/** Local variable name in the generated braced block; also the call argument. */
		public final String paramName;
		/** Declared type of the parameter, same-package refs resolved (full Java name incl. generics). */
		public final String typeName;
		public final int resId;
		public final String resName;
		/** R.id fallback key: METHOD form = method name, PARAM form = parameter name. */
		public final String idKey;

		public ViewMethodParam(String paramName, String typeName, int resId, String resName, String idKey) {
			this.paramName = paramName;
			this.typeName = typeName;
			this.resId = resId;
			this.resName = resName;
			this.idKey = idKey;
		}
	}

	/** APT ViewByIdHandler parity: field form or method-injection form of @ViewById. */
	public static final class ViewInjection {
		public enum Form {
			FIELD, METHOD
		}

		public final Form form;
		/** FIELD form only. */
		public final FieldInjection field;
		/** METHOD form only: method name to invoke. */
		public final String methodName;
		/** METHOD form only: injected parameters (method form = 1, param form = N). */
		public final List<ViewMethodParam> params;

		private ViewInjection(Form form, FieldInjection field, String methodName, List<ViewMethodParam> params) {
			this.form = form;
			this.field = field;
			this.methodName = methodName;
			this.params = params;
		}

		public static ViewInjection field(FieldInjection field) {
			return new ViewInjection(Form.FIELD, field, null, null);
		}

		public static ViewInjection method(String methodName, List<ViewMethodParam> params) {
			return new ViewInjection(Form.METHOD, null, methodName, params);
		}
	}

	/** APT AppHandler parity: one @App injection target (no value param; type must be @EApplication). */
	public static final class AppTarget {
		public final String name;
		/** METHOD form only: local declaration type of the injected parameter (full Java name incl. generics). */
		public final String declaredType;
		public final String appGeneratedQualifiedName;

		public AppTarget(String name, String declaredType, String appGeneratedQualifiedName) {
			this.name = name;
			this.declaredType = declaredType;
			this.appGeneratedQualifiedName = appGeneratedQualifiedName;
		}
	}

	public static final class AppInjection {
		public enum Form {
			FIELD, METHOD
		}

		public final Form form;
		public final String name;
		/** FIELD form only: @JvmField means direct assignment instead of a setter call. */
		public final boolean jvmField;
		public final List<AppTarget> targets;

		public AppInjection(Form form, String name, boolean jvmField, List<AppTarget> targets) {
			this.form = form;
			this.name = name;
			this.jvmField = jvmField;
			this.targets = targets;
		}
	}

	/** APT SystemServiceHandler parity: one getSystemService() lookup target in an injection statement or block. */
	public static final class SystemServiceTarget {
		public final String name; // field/param name (local decl + call arg)
		/** Full Java type name (cast + local declaration). */
		public final String declaredType;
		/** e.g. android.content.Context.WIFI_SERVICE. */
		public final String serviceConst;
		/** True when getSystemService must run on the application context (WifiManager/AudioManager). */
		public final boolean appContext;

		public SystemServiceTarget(String name, String declaredType, String serviceConst, boolean appContext) {
			this.name = name;
			this.declaredType = declaredType;
			this.serviceConst = serviceConst;
			this.appContext = appContext;
		}
	}

	public static final class SystemServiceInjection {
		public enum Form {
			FIELD, METHOD
		}

		public final Form form;
		public final String name; // field name (FIELD) / method name (METHOD)
		/** FIELD form only: direct assignment (lateinit/@JvmField) instead of a setter call. */
		public final boolean jvmField;
		public final List<SystemServiceTarget> targets; // FIELD=1; METHOD/param=N (all parameters)

		public SystemServiceInjection(Form form, String name, boolean jvmField, List<SystemServiceTarget> targets) {
			this.form = form;
			this.name = name;
			this.jvmField = jvmField;
			this.targets = targets;
		}
	}

	public static final class FragmentInjection {
		public final String propertyName;
		public final String typeName;
		public final int resId;
		public final String resName;
		public final boolean childFragment;

		public FragmentInjection(String propertyName, String typeName, int resId, String resName, boolean childFragment) {
			this.propertyName = propertyName; this.typeName = typeName; this.resId = resId;
			this.resName = resName; this.childFragment = childFragment;
		}
	}

	/** APT InstanceStateHandler parity: one @InstanceState property with its pre-built save/restore statements. */
	public static final class InstanceState {
		public final String propertyName;
		/** Statement for the onSaveInstanceState body, e.g. bundle_.putString("n", n); */
		public final String saveStmt;
		/** Statement for the restoreSavedInstanceState_ body, e.g. n = savedInstanceState.getString("n"); */
		public final String restoreStmt;
		/** True when the restore statement needs @SuppressWarnings("unchecked") on the method. */
		public final boolean unchecked;

		public InstanceState(String propertyName, String saveStmt, String restoreStmt, boolean unchecked) {
			this.propertyName = propertyName;
			this.saveStmt = saveStmt;
			this.restoreStmt = restoreStmt;
			this.unchecked = unchecked;
		}
	}

	public static final class ViewListener {
		public final boolean longClick;
		public final String methodName;
		public final List<Param> params;
		public final boolean returnsBoolean;
		public final List<Integer> valueIds;
		public final List<String> nameIds;

		public ViewListener(boolean longClick, String methodName, List<Param> params,
			boolean returnsBoolean, List<Integer> valueIds, List<String> nameIds) {
			this.longClick = longClick;
			this.methodName = methodName;
			this.params = params;
			this.returnsBoolean = returnsBoolean;
			this.valueIds = valueIds;
			this.nameIds = nameIds;
		}
	}

/** APT OptionsMenuHandler parity: class-level @OptionsMenu menu resource refs. */
	public static final class OptionsMenuRef {
		public final int resId;
		public final String resName;

		public OptionsMenuRef(int resId, String resName) {
			this.resId = resId;
			this.resName = resName;
		}
	}

	/** APT OptionsItemHandler parity: @OptionsItem method with its resolved ids. */
	public static final class OptionsItem {
		public final String methodName;
		public final boolean returnsBoolean;
		public final boolean hasMenuItemParam;
		public final List<Integer> valueIds;
		public final List<String> nameIds;

		public OptionsItem(String methodName, boolean returnsBoolean, boolean hasMenuItemParam,
				List<Integer> valueIds, List<String> nameIds) {
			this.methodName = methodName;
			this.returnsBoolean = returnsBoolean;
			this.hasMenuItemParam = hasMenuItemParam;
			this.valueIds = valueIds;
			this.nameIds = nameIds;
		}
	}

	/** APT InjectHelper parity: one @InjectMenu or @OptionsMenuItem injection (field, method or param form). */
	public static final class MenuInjection {
		public final boolean fieldForm;
		public final String name;
		public final int resId;
		public final String resName;
		public final List<Param> params;
		public final List<List<Integer>> valueIds;
		public final List<List<String>> nameIds;
		public final boolean allParamsAnnotated;

		public MenuInjection(boolean fieldForm, String name, int resId, String resName, List<Param> params,
				List<List<Integer>> valueIds, List<List<String>> nameIds, boolean allParamsAnnotated) {
			this.fieldForm = fieldForm;
			this.name = name;
			this.resId = resId;
			this.resName = resName;
			this.params = params;
			this.valueIds = valueIds;
			this.nameIds = nameIds;
			this.allParamsAnnotated = allParamsAnnotated;
		}
	}

	/** APT OnActivityResultHandler parity: one @OnActivityResult method with its resolved invocation args and extra reads. */
	public static final class ActivityResultMethod {
		public final String methodName;
		/** Invocation argument expressions in source parameter order (data / resultCode / extra local names). */
		public final List<String> argExprs = new ArrayList<>();
		/** Full local-declaration statements for @Extra params, in source parameter order. */
		public final List<String> extraDecls = new ArrayList<>();

		public ActivityResultMethod(String methodName) {
			this.methodName = methodName;
		}
	}

	/** APT OnActivityResultDelegate parity: one switch case grouping all methods sharing a requestCode. */
	public static final class ActivityResultGroup {
		public final int requestCode;
		/** Member methods in source order. */
		public final List<ActivityResultMethod> methods = new ArrayList<>();

		public ActivityResultGroup(int requestCode) {
			this.requestCode = requestCode;
		}
	}

	/** APT ExtraParameterHandler.getStaticExtraField parity: one generated @OnActivityResult extra key constant. */
	public static final class ExtraConstant {
		public final String constantName;
		public final String key;

		public ExtraConstant(String constantName, String key) {
			this.constantName = constantName;
			this.key = key;
		}
	}

	public final Kind kind;
	public final String packageName;
	public final String simpleName;
	public final String qualifiedName;
	public final String classSuffix;
	public final int layoutId;
	public final String layoutResName;
	public final List<ViewInjection> viewInjections = new ArrayList<>();
	public final List<FieldInjection> stringRes = new ArrayList<>();
	public final List<ResField> resFields = new ArrayList<>();
	public final List<FieldInjection> extras = new ArrayList<>();
	public final List<FieldInjection> fragmentArgs = new ArrayList<>();
	public final List<InstanceState> instanceStates = new ArrayList<>();
	public final List<FragmentInjection> fragmentByTags = new ArrayList<>();
	public final List<FragmentInjection> fragmentByIds = new ArrayList<>();
	public final List<ViewListener> clickListeners = new ArrayList<>();
	public final List<ViewListener> longClickListeners = new ArrayList<>();
	public final List<OptionsMenuRef> optionsMenus = new ArrayList<>();
	public final List<OptionsItem> optionsItems = new ArrayList<>();
	public final List<MenuInjection> injectMenuInjections = new ArrayList<>();
	public final List<MenuInjection> optionsMenuItemInjections = new ArrayList<>();
	public final List<ActivityResultGroup> activityResults = new ArrayList<>();
	public final List<ExtraConstant> oarExtraConstants = new ArrayList<>();
	/** OR of per-extra unchecked-cast flags: method-level @SuppressWarnings("unchecked") on the generated onActivityResult. */
	public boolean activityResultUnchecked = false;
	public final List<String> afterViews = new ArrayList<>();
	public final List<String> afterExtras = new ArrayList<>();
	public final List<BeanInjection> beans = new ArrayList<>();
	public final List<AppInjection> apps = new ArrayList<>();
	public final List<SystemServiceInjection> systemServices = new ArrayList<>();
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
	/** True when @EFragment(forceLayoutInjection=true): onCreateView inflates even if the host already created a view. */
	public boolean forceLayoutInjection = false;

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
