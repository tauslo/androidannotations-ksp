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
		ACTIVITY, APPLICATION, BEAN, FRAGMENT, SERVICE, VIEW, VIEW_GROUP,
		RECEIVER, INTENT_SERVICE, PROVIDER
	}

	/** Which view callback a {@link ViewListener} wires. Click and long-click stay on their own lists. */
	public enum ListenerKind {
		CLICK, LONG_CLICK, TOUCH, FOCUS_CHANGE, CHECKED_CHANGE, EDITOR_ACTION,
		ITEM_CLICK, ITEM_LONG_CLICK, ITEM_SELECT,
		TEXT_CHANGE, BEFORE_TEXT_CHANGE, AFTER_TEXT_CHANGE,
		SEEK_PROGRESS, SEEK_START, SEEK_STOP,
		PAGE_SELECTED, PAGE_SCROLLED, PAGE_SCROLL_STATE
	}

	/** Which {@code KeyEvent.Callback} method a {@link KeyBinding} contributes a switch case to. */
	public enum KeyKind {
		DOWN, UP, LONG_PRESS, MULTIPLE
	}

	/**
	 * How a user parameter maps onto the generated listener method. Text-change ints are matched by name
	 * ({@code start}/{@code count}/{@code before}/{@code after}), not by position.
	 */
	public enum ParamRole {
		VIEW, MOTION_EVENT, BOOLEAN, TEXT_VIEW, INT, KEY_EVENT, ITEM,
		CHAR_SEQUENCE, START, COUNT, BEFORE, AFTER, EDITABLE,
		SEEK_BAR, PROGRESS, FROM_USER, VIEW_PAGER, POSITION, POSITION_OFFSET, OFFSET_PIXELS, SCROLL_STATE
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

		/**
		 * Set when the same method is also {@code @WakeLock}. WakeLockHandler runs before UiThread/Background,
		 * so the lock wraps the super call inside the posted task.
		 */
		public WakeLockSpec wakeLock;
		/** Set when the same method is also {@code @Trace} and the {@code trace} option is on. Trace is inside WakeLock. */
		public TraceSpec trace;
		/** Set when the same method is also {@code @Suppose*}. Suppose runs last, so the check is outside the task. */
		public SupposeSpec suppose;

		/**
		 * Set when the same method is also {@code @IgnoreWhen}. UiThread/Background wrap the guard inside the
		 * posted task (APT runs IgnoreWhenHandler before those handlers).
		 */
		public String ignoreWhenState;
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

		/** APT BeanHandler: a field that is also {@code @NonConfigurationInstance} is assigned only when still null. */
		public boolean assignOnlyIfNull;
		/** Generated {@code @EBean} type used for {@code rebind} when restoring a non-singleton bean. */
		public String rebindGeneratedName;
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

		/** False when the field type is an AndroidX or support-v4 Fragment rather than {@code android.app.Fragment}. */
		public boolean nativeFragment = true;
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
		public final ListenerKind kind;
		public final String methodName;
		public final List<Param> params;
		/** Parallel to {@link #params}. Empty for a no-arg listener. */
		public final List<ParamRole> roles;
		public final boolean returnsBoolean;
		public final List<Integer> valueIds;
		public final List<String> nameIds;

		public ViewListener(ListenerKind kind, String methodName, List<Param> params, List<ParamRole> roles,
			boolean returnsBoolean, List<Integer> valueIds, List<String> nameIds) {
			this.kind = kind;
			this.methodName = methodName;
			this.params = params;
			this.roles = roles;
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

	/** APT PrefHandler parity: one {@code @Pref} field assigned in {@code init_} to {@code new Helper_(context)}. */
	public static final class PrefInjection {
		public final String propertyName;
		/** Direct field write ({@code lateinit} / {@code @JvmField} / Java field) rather than a setter call. */
		public final boolean jvmField;
		/** Simple name of the declared field type (generated helper, or the {@code @SharedPref} interface). */
		public final String typeSimpleName;
		/** Qualified name when the type resolved; null while it is still an error type. */
		public final String typeQualifiedName;
		/** Generated prefs helper to instantiate. Filled before generation. */
		public String generatedQualifiedName;

		public PrefInjection(String propertyName, boolean jvmField, String typeSimpleName, String typeQualifiedName) {
			this.propertyName = propertyName;
			this.jvmField = jvmField;
			this.typeSimpleName = typeSimpleName;
			this.typeQualifiedName = typeQualifiedName;
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

	/** APT ViewsByIdHandler parity: one List (or array) of views filled in {@code onViewChanged}. */
	public static final class ViewsByIdInjection {
		public enum Form {
			FIELD, METHOD
		}

		public final Form form;
		public final String name;
		public final boolean jvmField;
		/** Element type of the list or array, for example {@code android.widget.TextView}. */
		public final String viewType;
		/** True when the declared type is an array rather than a {@code List}. */
		public final boolean array;
		public final List<Integer> valueIds;
		public final List<String> nameIds;

		public ViewsByIdInjection(Form form, String name, boolean jvmField, String viewType, boolean array,
				List<Integer> valueIds, List<String> nameIds) {
			this.form = form;
			this.name = name;
			this.jvmField = jvmField;
			this.viewType = viewType;
			this.array = array;
			this.valueIds = valueIds;
			this.nameIds = nameIds;
		}
	}

	/** APT FromHtmlHandler parity: {@code setText(Html.fromHtml(context.getString(R.string.*)))} after view injection. */
	public static final class FromHtmlField {
		public final String propertyName;
		public final int resId;
		public final String resName;

		public FromHtmlField(String propertyName, int resId, String resName) {
			this.propertyName = propertyName;
			this.resId = resId;
			this.resName = resName;
		}
	}

	/** APT NonConfigurationInstanceHandler parity: one retained activity field. */
	public static final class NonConfigField {
		public final String propertyName;
		public final String typeName;
		public final boolean jvmField;
		/** Generated bean class for {@code rebind}, or null when the field is not a non-singleton {@code @Bean}. */
		public final String rebindGeneratedName;

		public NonConfigField(String propertyName, String typeName, boolean jvmField, String rebindGeneratedName) {
			this.propertyName = propertyName;
			this.typeName = typeName;
			this.jvmField = jvmField;
			this.rebindGeneratedName = rebindGeneratedName;
		}
	}

	/** One user constructor copied onto an {@code @EView} / {@code @EViewGroup} subclass. */
	public static final class ViewCtor {
		public final List<Param> params;

		public ViewCtor(List<Param> params) {
			this.params = params;
		}
	}

	/** APT ReceiverHandler parity: one {@code @Receiver} method and the arguments forwarded from {@code onReceive}. */
	public static final class ReceiverArg {
		public enum Role {
			CONTEXT, INTENT, EXTRA
		}

		public final Role role;
		public final String name;
		public final String typeName;
		/** EXTRA only: expression that reads the value from {@code extras_}. */
		public final String readExpr;

		public ReceiverArg(Role role, String name, String typeName, String readExpr) {
			this.role = role;
			this.name = name;
			this.typeName = typeName;
			this.readExpr = readExpr;
		}
	}

	public static final class ReceiverBinding {
		public final String methodName;
		/** {@code OnCreateOnDestroy} / {@code OnStartOnStop} / {@code OnResumeOnPause} / {@code OnAttachOnDetach}. */
		public final String registerAt;
		public final boolean local;
		/** FQCN of LocalBroadcastManager when {@code local} is true. */
		public final String localManager;
		public final List<String> actions;
		public final List<String> dataSchemes;
		public final List<ReceiverArg> args;
		public final boolean unchecked;

		public ReceiverBinding(String methodName, String registerAt, boolean local, String localManager,
				List<String> actions, List<String> dataSchemes, List<ReceiverArg> args, boolean unchecked) {
			this.methodName = methodName;
			this.registerAt = registerAt;
			this.local = local;
			this.localManager = localManager;
			this.actions = actions;
			this.dataSchemes = dataSchemes;
			this.args = args;
			this.unchecked = unchecked;
		}
	}

	/** APT ReceiverActionHandler: one {@code @ReceiverAction} dispatched from {@code onReceive}. */
	public static final class ReceiverActionBinding {
		public final String methodName;
		public final List<String> actions;
		public final List<String> dataSchemes;
		public final List<ReceiverArg> args;
		public final boolean unchecked;
		public final String actionsField;
		public final boolean actionsList;
		public final String schemesField;
		public final boolean schemesList;

		public ReceiverActionBinding(String methodName, List<String> actions, List<String> dataSchemes, List<ReceiverArg> args,
				boolean unchecked, String actionsField, boolean actionsList, String schemesField, boolean schemesList) {
			this.methodName = methodName;
			this.actions = actions;
			this.dataSchemes = dataSchemes;
			this.args = args;
			this.unchecked = unchecked;
			this.actionsField = actionsField;
			this.actionsList = actionsList;
			this.schemesField = schemesField;
			this.schemesList = schemesList;
		}
	}

	/** One {@code @ServiceAction} parameter restored from the intent extras and written by the intent builder. */
	public static final class ServiceActionParam {
		public final String name;
		public final String typeName;
		public final String constantName;
		public final String readExpr;
		public final String writeExpr;

		public ServiceActionParam(String name, String typeName, String constantName, String readExpr, String writeExpr) {
			this.name = name;
			this.typeName = typeName;
			this.constantName = constantName;
			this.readExpr = readExpr;
			this.writeExpr = writeExpr;
		}
	}

	/** APT ServiceActionHandler: one action method plus its intent-builder overload. */
	public static final class ServiceActionBinding {
		public final String methodName;
		public final String actionValue;
		public final String actionField;
		public final List<ServiceActionParam> params;
		public final boolean unchecked;

		public ServiceActionBinding(String methodName, String actionValue, String actionField, List<ServiceActionParam> params, boolean unchecked) {
			this.methodName = methodName;
			this.actionValue = actionValue;
			this.actionField = actionField;
			this.params = params;
			this.unchecked = unchecked;
		}
	}

	/** One {@code @KeyDown}/{@code @KeyUp}/{@code @KeyLongPress}/{@code @KeyMultiple} method. */
	public static final class KeyBinding {
		public final KeyKind kind;
		public final String methodName;
		public final boolean returnsBoolean;
		public final List<Integer> keyCodes;
		public final List<ParamRole> roles;

		public KeyBinding(KeyKind kind, String methodName, boolean returnsBoolean, List<Integer> keyCodes, List<ParamRole> roles) {
			this.kind = kind;
			this.methodName = methodName;
			this.returnsBoolean = returnsBoolean;
			this.keyCodes = keyCodes;
			this.roles = roles;
		}
	}

	/** APT WakeLockHandler: level, flags and tag used to build {@code PowerManager.newWakeLock}. */
	public static final class WakeLockSpec {
		public final String tag;
		public final String level;
		public final List<String> flags;

		public WakeLockSpec(String tag, String level, List<String> flags) {
			this.tag = tag;
			this.level = level;
			this.flags = flags;
		}
	}

	/** APT TraceHandler: tag and log level. Generated only when the {@code trace} option is true. */
	public static final class TraceSpec {
		public final String tag;
		public final String logMethod;
		public final String logConstant;
		public final String returnType;

		public TraceSpec(String tag, String logMethod, String logConstant, String returnType) {
			this.tag = tag;
			this.logMethod = logMethod;
			this.logConstant = logConstant;
			this.returnType = returnType;
		}
	}

	/** APT Suppose*Handler: a thread check prepended after every other method-body wrapper. */
	public static final class SupposeSpec {
		public final boolean uiThread;
		public final List<String> serial;

		public SupposeSpec(boolean uiThread, List<String> serial) {
			this.uiThread = uiThread;
			this.serial = serial;
		}
	}

	/**
	 * A method wrapped by {@code @WakeLock}, {@code @Trace} and/or {@code @Suppose*} that is not also
	 * {@code @UiThread} or {@code @Background}. Combined methods store the specs on {@link MethodInfo}.
	 */
	public static final class WrappedMethod {
		public final String name;
		public final List<Param> params;
		public final String visibility;
		public final String returnType;
		public WakeLockSpec wakeLock;
		public TraceSpec trace;
		public SupposeSpec suppose;
		/** Standalone {@code @Transactional}. Not combined with the other wrappers. */
		public boolean transactional;

		public WrappedMethod(String name, List<Param> params, String visibility, String returnType) {
			this.name = name;
			this.params = params;
			this.visibility = visibility;
			this.returnType = returnType;
		}
	}

	/** APT RootContextHandler / RootFragmentHandler: one field (or one-arg method) on an {@code @EBean}. */
	public static final class RootInjection {
		public final boolean fragment;
		public final boolean fieldForm;
		public final String name;
		public final boolean jvmField;
		public final String typeName;
		public final boolean plainContext;

		public RootInjection(boolean fragment, boolean fieldForm, String name, boolean jvmField, String typeName, boolean plainContext) {
			this.fragment = fragment;
			this.fieldForm = fieldForm;
			this.name = name;
			this.jvmField = jvmField;
			this.typeName = typeName;
			this.plainContext = plainContext;
		}
	}

	/** One {@code @BindingObject} target. The cast type may be qualified later when the binding class is generated this round. */
	public static final class BindingTarget {
		public final String name;
		public final String typeName;

		public BindingTarget(String name, String typeName) {
			this.name = name;
			this.typeName = typeName;
		}
	}

	/** APT BindingObjectHandler: field assignment or a method call whose parameters are cast from {@code viewDataBinding_}. */
	public static final class BindingObjectInjection {
		public enum Form {
			FIELD, METHOD
		}

		public final Form form;
		public final String name;
		public final boolean jvmField;
		public final List<BindingTarget> targets;

		public BindingObjectInjection(Form form, String name, boolean jvmField, List<BindingTarget> targets) {
			this.form = form;
			this.name = name;
			this.jvmField = jvmField;
			this.targets = targets;
		}
	}

	/** One {@code @HttpsClient} value (a field, or one parameter of a method injection). */
	public static final class HttpsTarget {
		public final String name;
		public final String typeName;
		public final int trustStore;
		public final String trustStoreResName;
		public final String trustStorePwd;
		public final int keyStore;
		public final String keyStoreResName;
		public final String keyStorePwd;
		public final boolean allowAllHostnames;

		public HttpsTarget(String name, String typeName, int trustStore, String trustStoreResName, String trustStorePwd,
				int keyStore, String keyStoreResName, String keyStorePwd, boolean allowAllHostnames) {
			this.name = name;
			this.typeName = typeName;
			this.trustStore = trustStore;
			this.trustStoreResName = trustStoreResName;
			this.trustStorePwd = trustStorePwd;
			this.keyStore = keyStore;
			this.keyStoreResName = keyStoreResName;
			this.keyStorePwd = keyStorePwd;
			this.allowAllHostnames = allowAllHostnames;
		}
	}

	/** APT HttpsClientHandler: init-body injection of a {@code DefaultHttpClient} subclass. */
	public static final class HttpsClientInjection {
		public enum Form {
			FIELD, METHOD
		}

		public final Form form;
		public final String name;
		public final boolean jvmField;
		public final List<HttpsTarget> targets;

		public HttpsClientInjection(Form form, String name, boolean jvmField, List<HttpsTarget> targets) {
			this.form = form;
			this.name = name;
			this.jvmField = jvmField;
			this.targets = targets;
		}
	}

	/** Standalone {@code @IgnoreWhen} (not also {@code @UiThread} / {@code @Background}). */
	public static final class IgnoreWhenMethod {
		public final String name;
		public final String state;
		public final List<Param> params;
		public final String visibility;

		public IgnoreWhenMethod(String name, String state, List<Param> params, String visibility) {
			this.name = name;
			this.state = state;
			this.params = params;
			this.visibility = visibility;
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
	public final List<ViewListener> touchListeners = new ArrayList<>();
	public final List<ViewListener> focusChangeListeners = new ArrayList<>();
	public final List<ViewListener> checkedChangeListeners = new ArrayList<>();
	public final List<ViewListener> editorActionListeners = new ArrayList<>();
	public final List<ViewListener> itemClickListeners = new ArrayList<>();
	public final List<ViewListener> itemLongClickListeners = new ArrayList<>();
	public final List<ViewListener> itemSelectListeners = new ArrayList<>();
	/** @TextChange / @BeforeTextChange / @AfterTextChange in source order. One TextWatcher is shared per view id. */
	public final List<ViewListener> textWatcherListeners = new ArrayList<>();
	/** @SeekBar* methods in source order. One OnSeekBarChangeListener is shared per view id. */
	public final List<ViewListener> seekListeners = new ArrayList<>();
	/** @Page* methods in source order. One OnPageChangeListener is shared per view id. */
	public final List<ViewListener> pageListeners = new ArrayList<>();
	public final List<KeyBinding> keyBindings = new ArrayList<>();
	public final List<ReceiverActionBinding> receiverActions = new ArrayList<>();
	public final List<ServiceActionBinding> serviceActions = new ArrayList<>();
	public final List<WrappedMethod> wrappedMethods = new ArrayList<>();
	public final List<RootInjection> roots = new ArrayList<>();
	/** Logged without skipping generation (APT warnings such as PARTIAL_WAKE_LOCK flags). */
	public final List<String> warnings = new ArrayList<>();
	/** FQCN of the ViewPager type used for @Page* listeners, androidx preferred over the support library. */
	public String viewPagerType;
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
	public final List<PrefInjection> prefs = new ArrayList<>();
	/** {@code @SharedPref} interfaces nested in this component, emitted as static nested helpers. */
	public final List<SharedPrefModel> nestedPrefs = new ArrayList<>();
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
	public final List<ViewsByIdInjection> viewsByIds = new ArrayList<>();
	public final List<FromHtmlField> fromHtml = new ArrayList<>();
	public final List<NonConfigField> nonConfigFields = new ArrayList<>();
	public final List<Integer> windowFeatures = new ArrayList<>();
	public boolean fullscreen;
	/** Activity extends AppCompat / ActionBarActivity, so window features use {@code supportRequestWindowFeature}. */
	public boolean appCompatWindow;
	/** Activity extends FragmentActivity, so retain/restore use the custom non-configuration methods. */
	public boolean customNonConfiguration;
	public final List<ViewCtor> viewConstructors = new ArrayList<>();
	public final List<ReceiverBinding> receivers = new ArrayList<>();
	public final List<ExtraConstant> receiverExtraConstants = new ArrayList<>();
	public final List<IgnoreWhenMethod> ignoreWhens = new ArrayList<>();
	/** Any {@code @IgnoreWhen(VIEW_DESTROYED)} needs the volatile {@code viewDestroyed_} flag. */
	public boolean needsViewDestroyed;
	/** {@code @DataBound}: inflate the layout with DataBindingUtil instead of setContentView/inflate. */
	public boolean dataBound;
	/** {@code androidx.databinding.DataBindingUtil} when that class is on the classpath, otherwise the support/framework name. */
	public String dataBindingUtil;
	/** Base type of the generated {@code viewDataBinding_} field. */
	public String viewDataBindingType;
	/** {@code @CustomTitle} layout id. {@link AnnotationNames#RES_DEFAULT} when absent. */
	public int customTitleLayoutId = AnnotationNames.RES_DEFAULT;
	public final List<BindingObjectInjection> bindingObjects = new ArrayList<>();
	public final List<HttpsClientInjection> httpsClients = new ArrayList<>();

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

	public boolean hasViewListeners() {
		return !clickListeners.isEmpty() || !longClickListeners.isEmpty() || !touchListeners.isEmpty()
				|| !focusChangeListeners.isEmpty() || !checkedChangeListeners.isEmpty() || !editorActionListeners.isEmpty()
				|| !itemClickListeners.isEmpty() || !itemLongClickListeners.isEmpty() || !itemSelectListeners.isEmpty()
				|| !textWatcherListeners.isEmpty() || !seekListeners.isEmpty() || !pageListeners.isEmpty();
	}

	public boolean usesWakeLock() {
		for (MethodInfo method : uiThreads) {
			if (method.wakeLock != null) {
				return true;
			}
		}
		for (MethodInfo method : backgrounds) {
			if (method.wakeLock != null) {
				return true;
			}
		}
		for (WrappedMethod method : wrappedMethods) {
			if (method.wakeLock != null) {
				return true;
			}
		}
		for (MvpModel nested : nestedModels) {
			if (nested.usesWakeLock()) {
				return true;
			}
		}
		return false;
	}

	/** True when the generated type must implement view-changed callbacks (views, listeners, fragment lookup, html). */
	public boolean hasViewSupport() {
		return !viewInjections.isEmpty() || !afterViews.isEmpty() || hasViewListeners()
				|| !viewsByIds.isEmpty() || !fromHtml.isEmpty()
				|| !fragmentByIds.isEmpty() || !fragmentByTags.isEmpty()
				|| !bindingObjects.isEmpty() || hasCustomTitle();
	}

	public boolean hasCustomTitle() {
		return customTitleLayoutId != AnnotationNames.RES_DEFAULT;
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
