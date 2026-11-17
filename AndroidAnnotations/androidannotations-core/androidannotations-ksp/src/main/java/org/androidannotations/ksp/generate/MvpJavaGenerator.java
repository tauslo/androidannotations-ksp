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
package org.androidannotations.ksp.generate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

import org.androidannotations.ksp.AnnotationNames;
import org.androidannotations.ksp.KspAnnotationUtils;
import org.androidannotations.ksp.MvpModel;
import org.androidannotations.ksp.SharedPrefModel;
import org.androidannotations.ksp.IntentBuilderCompat;
import org.androidannotations.ksp.ResCompat;
import org.androidannotations.ksp.env.RClassIndex;

public final class MvpJavaGenerator {

	private final String resourcePackage;
	private final String rClassName;
	private final RClassIndex rClassIndex;

	private final Map<String, ResCompat.Mode> resCompatModes;

	private final IntentBuilderCompat intentBuilderCompat;

	public MvpJavaGenerator(IntentBuilderCompat intentBuilderCompat, String resourcePackage, String rClassName, RClassIndex rClassIndex, Map<String, ResCompat.Mode> resCompatModes) {
		this.intentBuilderCompat = intentBuilderCompat;
		this.resourcePackage = resourcePackage;
		this.rClassName = rClassName;
		this.rClassIndex = rClassIndex;
		this.resCompatModes = resCompatModes;
	}

	public String generate(MvpModel model) {
		if (model.kind == MvpModel.Kind.ACTIVITY) {
			return generateActivity(model);
		}
		if (model.kind == MvpModel.Kind.FRAGMENT) {
			return generateFragment(model);
		}
		if (model.kind == MvpModel.Kind.APPLICATION) {
			return generateApplication(model);
		}
		if (model.kind == MvpModel.Kind.SERVICE) {
			return generateService(model);
		}
		if (model.kind == MvpModel.Kind.RECEIVER) {
			return generateReceiver(model);
		}
		if (model.kind == MvpModel.Kind.INTENT_SERVICE) {
			return generateIntentService(model);
		}
		if (model.kind == MvpModel.Kind.PROVIDER) {
			return generateProvider(model);
		}
		if (model.kind == MvpModel.Kind.VIEW || model.kind == MvpModel.Kind.VIEW_GROUP) {
			return generateView(model, false);
		}
		return generateBean(model);
	}

	private String generateActivity(MvpModel model) {
		StringBuilder out = new StringBuilder();
		appendHeader(out, model);
		boolean hasViewSupport = model.hasViewSupport();
		appendClassAnnotations(out, model);
		out.append("public final class ").append(model.generatedSimpleName())
				.append(" extends ").append(model.simpleName)
				.append(" implements org.androidannotations.api.bean.BeanHolder, org.androidannotations.api.view.HasViews")
				.append(hasViewSupport ? ", org.androidannotations.api.view.OnViewChangedListener" : "")
				.append(" {\n\n");

		for (MvpModel.FieldInjection extra : model.extras) {
			String key = extra.resName != null && !extra.resName.isEmpty() ? extra.resName : extra.propertyName;
			out.append("\tpublic static final String ").append(KspAnnotationUtils.extraKeyConstant(extra.propertyName))
					.append(" = \"").append(key).append("\";\n");
		}
		if (!model.extras.isEmpty()) {
			out.append('\n');
		}

		out.append("\tprivate final org.androidannotations.api.view.OnViewChangedNotifier onViewChangedNotifier_ = new org.androidannotations.api.view.OnViewChangedNotifier();\n");
		out.append("\tprivate final java.util.HashMap<Class<?>, Object> beans_ = new java.util.HashMap<Class<?>, Object>();\n");
		appendDataBindingField(out, model);
		appendActivityResultConstants(out, model);
		appendReceiverFields(out, model);
		appendPowerManagerField(out, model);
		out.append("\n");

		out.append("\t@Override\n");
		out.append("\tpublic void onCreate(android.os.Bundle savedInstanceState) {\n");
		out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier previousNotifier = org.androidannotations.api.view.OnViewChangedNotifier.replaceNotifier(onViewChangedNotifier_);\n");
		out.append("\t\tinit_(savedInstanceState);\n");
		out.append("\t\tsuper.onCreate(savedInstanceState);\n");
		out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier.replaceNotifier(previousNotifier);\n");
		appendSetContentViewCall(out, model);
		appendPreferenceScreenCall(out, model);
		out.append("\t}\n\n");

		out.append("\t@Override\n");
		out.append("\tpublic <T extends android.view.View> T internalFindViewById(int id) {\n");
		out.append("\t\treturn ((T) this.findViewById(id));\n");
		out.append("\t}\n\n");

		out.append("\tprivate void init_(android.os.Bundle savedInstanceState) {\n");
		appendPowerManagerInit(out, model);
		if (!model.stringRes.isEmpty() || !model.resFields.isEmpty()) {
			out.append("\t\tandroid.content.res.Resources resources_ = this.getResources();\n");
		}
		if (hasViewSupport) {
			out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier.registerOnViewChangedListener(this);\n");
		}
		for (MvpModel.FieldInjection injection : model.stringRes) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "string", injection.propertyName, injection.resId, injection.resName, rClassIndex);
			appendAssign(out, injection.propertyName, true, "resources_.getString(" + resExpr + ")");
		}
		for (MvpModel.ResField resField : model.resFields) {
			appendResAssign(out, model.kind, resField);
		}
		appendSystemServiceInjections(out, model, "this");
		appendAppInjections(out, model);
		appendPrefInjections(out, model);
		if (!model.extras.isEmpty() || !model.afterExtras.isEmpty()) {
			out.append("\t\tinjectExtras_();\n");
		}
		appendNonConfigurationRestore(out, model);
		appendBeanInjections(out, model);
		// APT parity: FullscreenHandler/WindowFeatureHandler emit after the bean handlers in the kapt output.
		appendWindowFeatures(out, model);
		appendHttpsClientInjections(out, model);
		if (!model.instanceStates.isEmpty()) {
			out.append("\t\trestoreSavedInstanceState_(savedInstanceState);\n");
		}
		for (String method : model.afterInject) {
			out.append("\t\t").append(method).append("();\n");
		}
		appendReceiverFilterInit(out, model, false);
		appendReceiverRegister(out, model, "OnCreateOnDestroy", "\t\t");
		out.append("\t}\n\n");

		if (!model.extras.isEmpty() || !model.afterExtras.isEmpty()) {
			boolean unchecked = false;
			for (MvpModel.FieldInjection extra : model.extras) {
				unchecked |= extra.extraUnchecked;
			}
			if (unchecked) {
				out.append("\t@SuppressWarnings(\"unchecked\")\n");
			}
			out.append("\tprivate void injectExtras_() {\n");
			out.append("\t\tandroid.os.Bundle extras_ = getIntent().getExtras();\n");
			if (!model.extras.isEmpty()) {
				out.append("\t\tif (extras_ != null) {\n");
				for (MvpModel.FieldInjection extra : model.extras) {
					String constant = KspAnnotationUtils.extraKeyConstant(extra.propertyName);
					out.append("\t\t\tif (extras_.containsKey(").append(constant).append(")) {\n");
					appendAssign(out, extra, extra.extraReadExpr.replace("{key}", constant), "\t\t\t\t");
					out.append("\t\t\t}\n");
				}
				out.append("\t\t}\n");
			}
			for (String method : model.afterExtras) {
				out.append("\t\t").append(method).append("();\n");
			}
			out.append("\t}\n\n");

			out.append("\t@Override\n");
			out.append("\tpublic void setIntent(android.content.Intent newIntent) {\n");
			out.append("\t\tsuper.setIntent(newIntent);\n");
			out.append("\t\tinjectExtras_();\n");
			out.append("\t}\n\n");
		}

		out.append("\t@Override\n");
		out.append("\tpublic void setContentView(int layoutResID) {\n");
		out.append("\t\tsuper.setContentView(layoutResID);\n");
		out.append("\t\tonViewChangedNotifier_.notifyViewChanged(this);\n");
		out.append("\t}\n\n");

		out.append("\t@Override\n");
		out.append("\tpublic void setContentView(android.view.View view, android.view.ViewGroup.LayoutParams params) {\n");
		out.append("\t\tsuper.setContentView(view, params);\n");
		out.append("\t\tonViewChangedNotifier_.notifyViewChanged(this);\n");
		out.append("\t}\n\n");

		out.append("\t@Override\n");
		out.append("\tpublic void setContentView(android.view.View view) {\n");
		out.append("\t\tsuper.setContentView(view);\n");
		out.append("\t\tonViewChangedNotifier_.notifyViewChanged(this);\n");
		out.append("\t}\n\n");

		appendIntentStaticMethod(out, model);
		appendFragmentIntentOverloads(out, model);
		appendBeanHolderMethods(out);
		boolean unbindEarly = model.kind == MvpModel.Kind.ACTIVITY && model.dataBound && hasLayout(model) && !hasReceiver(model, "OnCreateOnDestroy");
		if (unbindEarly) {
			out.append("\t@Override\n");
			out.append("\tpublic void onDestroy() {\n");
			out.append("\t\tviewDataBinding_.unbind();\n");
			out.append("\t\tsuper.onDestroy();\n");
			out.append("\t}\n\n");
		}
		if (hasViewSupport) {
			out.append("\t@Override\n");
			out.append("\tpublic void onViewChanged(org.androidannotations.api.view.HasViews hasViews) {\n");
			appendCustomTitle(out, model);
			ViewInjectionPlan plan = planViewInjections(model);
			out.append(plan.pass1);
			appendViewsById(out, model);
			appendViewListeners(out, model, plan);
			out.append(plan.pass3);
			appendBindingObjects(out, model);
			appendFragmentInjections(out, model);
			appendFromHtml(out, model);
			for (String method : model.afterViews) {
				out.append("\t\t").append(method).append("();\n");
			}
			out.append("\t}\n\n");
			appendFragmentHelpers(out, model);
		}
		appendNonConfigurationMethods(out, model);
		appendReceiverLifecycle(out, model);
		appendOptionsMenuMethods(out, model);
		appendInstanceStateMethods(out, model);
		appendActivityResultMethods(out, model);
		appendThreadMethods(out, model);
		appendKeyCallbacks(out, model);
		appendPreferenceHooks(out, model);
		appendIntentBuilder(out, model);

		appendNestedClasses(out, model);
		appendNonConfigurationHolder(out, model);
		out.append("}\n");
		return out.toString();
	}

	private String generateFragment(MvpModel model) {
		StringBuilder out = new StringBuilder();
		appendHeader(out, model);
		boolean hasViewSupport = model.hasViewSupport();
		appendClassAnnotations(out, model);
		out.append("public final class ").append(model.generatedSimpleName())
				.append(" extends ").append(model.qualifiedName)
				.append(" implements org.androidannotations.api.bean.BeanHolder, org.androidannotations.api.view.HasViews")
				.append(hasViewSupport ? ", org.androidannotations.api.view.OnViewChangedListener" : "")
				.append(" {\n\n");

		out.append("\tprivate final org.androidannotations.api.view.OnViewChangedNotifier onViewChangedNotifier_ = new org.androidannotations.api.view.OnViewChangedNotifier();\n");
		out.append("\tprivate android.view.View contentView_;\n");
		out.append("\tprivate final java.util.HashMap<Class<?>, Object> beans_ = new java.util.HashMap<Class<?>, Object>();\n");
		appendDataBindingField(out, model);
		for (MvpModel.FieldInjection arg : model.fragmentArgs) {
			String key = arg.resName != null && !arg.resName.isEmpty() ? arg.resName : arg.propertyName;
			out.append("\tpublic final static String ").append(KspAnnotationUtils.fragmentArgKeyConstant(arg.propertyName))
					.append(" = \"").append(key).append("\";\n");
		}
		appendActivityResultConstants(out, model);
		appendReceiverFields(out, model);
		if (model.needsViewDestroyed) {
			out.append("\tprivate volatile boolean viewDestroyed_ = true;\n");
		}
		appendPowerManagerField(out, model);
		if (!model.fragmentArgs.isEmpty() || !model.oarExtraConstants.isEmpty() || !model.receiverExtraConstants.isEmpty()) {
			out.append("\n");
		}

		out.append("\t@Override\n");
		out.append("\tpublic void onCreate(android.os.Bundle savedInstanceState) {\n");
		out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier previousNotifier = org.androidannotations.api.view.OnViewChangedNotifier.replaceNotifier(onViewChangedNotifier_);\n");
		out.append("\t\tinit_(savedInstanceState);\n");
		out.append("\t\tsuper.onCreate(savedInstanceState);\n");
		appendReceiverRegister(out, model, "OnCreateOnDestroy", "\t\t");
		appendPreferenceScreenCall(out, model);
		out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier.replaceNotifier(previousNotifier);\n");
		out.append("\t}\n\n");

		out.append("\t@Override\n");
		out.append("\tpublic <T extends android.view.View> T internalFindViewById(int id) {\n");
		out.append("\t\treturn ((T)((contentView_ == null)?null:contentView_.findViewById(id)));\n");
		out.append("\t}\n\n");

		appendFragmentOnCreateView(out, model);

		out.append("\t@Override\n");
		out.append("\tpublic void onDestroyView() {\n");
		out.append("\t\tsuper.onDestroyView();\n");
		out.append("\t\tcontentView_ = null;\n");
		if (model.dataBound && hasLayout(model)) {
			out.append("\t\tviewDataBinding_.unbind();\n");
			out.append("\t\tviewDataBinding_ = null;\n");
		}
		for (MvpModel.ViewInjection injection : model.viewInjections) {
			if (injection.form == MvpModel.ViewInjection.Form.FIELD) {
				out.append("\t\t").append(injection.field.propertyName).append(" = null;\n");
			}
		}
		for (MvpModel.BindingObjectInjection injection : model.bindingObjects) {
			if (injection.form == MvpModel.BindingObjectInjection.Form.FIELD) {
				out.append("\t\t").append(injection.name).append(" = null;\n");
			}
		}
		if (model.needsViewDestroyed) {
			out.append("\t\tviewDestroyed_ = true;\n");
		}
		out.append("\t}\n\n");

		out.append("\tprivate void init_(android.os.Bundle savedInstanceState) {\n");
		appendPowerManagerInit(out, model);
		boolean hasMenus = !model.optionsMenus.isEmpty() || !model.injectMenuInjections.isEmpty() || !model.optionsMenuItemInjections.isEmpty();
		// APT parity: the three virtual blocks (before/injection/after) are created on first access; a direct append
		// (setHasOptionsMenu) lands at the tail once any earlier handler has created them, otherwise it is first.
		boolean hasPreMenuContent = hasViewSupport || !model.resFields.isEmpty() || !model.fragmentArgs.isEmpty()
				|| !model.systemServices.isEmpty() || !model.beans.isEmpty() || !model.prefs.isEmpty();
		if (hasMenus && !hasPreMenuContent) {
			out.append("\t\tsetHasOptionsMenu(true);\n");
		}
		if (!model.resFields.isEmpty()) {
			out.append("\t\tandroid.content.res.Resources resources_ = getActivity().getResources();\n");
		}
		if (hasViewSupport) {
			out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier.registerOnViewChangedListener(this);\n");
		}
		for (MvpModel.ResField resField : model.resFields) {
			appendResAssign(out, model.kind, resField);
		}
		if (!model.fragmentArgs.isEmpty()) {
			out.append("\t\tinjectFragmentArguments_();\n");
		}
		appendSystemServiceInjections(out, model, "getActivity()");
		appendPrefInjections(out, model);
		appendBeanInjections(out, model);
		appendHttpsClientInjections(out, model);
		if (!model.instanceStates.isEmpty()) {
			out.append("\t\trestoreSavedInstanceState_(savedInstanceState);\n");
		}
		for (String method : model.afterInject) {
			out.append("\t\t").append(method).append("();\n");
		}
		if (hasMenus && hasPreMenuContent) {
			out.append("\t\tsetHasOptionsMenu(true);\n");
		}
		appendReceiverFilterInit(out, model, false);
		out.append("\t}\n\n");

		out.append("\t@Override\n");
		out.append("\tpublic void onViewCreated(android.view.View view, android.os.Bundle savedInstanceState) {\n");
		out.append("\t\tsuper.onViewCreated(view, savedInstanceState);\n");
		out.append("\t\tonViewChangedNotifier_.notifyViewChanged(this);\n");
		out.append("\t}\n\n");

		String generated = model.generatedSimpleName();
		out.append("\tpublic static ").append(generated).append(".FragmentBuilder_ builder() {\n");
		out.append("\t\treturn new ").append(generated).append(".FragmentBuilder_();\n");
		out.append("\t}\n\n");

		appendFragmentBeanHolderMethods(out);
		appendOptionsMenuMethods(out, model);

		if (hasViewSupport) {
			out.append("\t@Override\n");
			out.append("\tpublic void onViewChanged(org.androidannotations.api.view.HasViews hasViews) {\n");
			ViewInjectionPlan plan = planViewInjections(model);
			out.append(plan.pass1);
			appendViewsById(out, model);
			appendFromHtml(out, model);
			appendViewListeners(out, model, plan);
			out.append(plan.pass3);
			appendBindingObjects(out, model);
			appendFragmentInjections(out, model);
			for (String method : model.afterViews) {
				out.append("\t\t").append(method).append("();\n");
			}
			out.append("\t}\n\n");
			appendFragmentHelpers(out, model);
		}

		appendFragmentArgsMethod(out, model);
		appendInstanceStateMethods(out, model);
		appendActivityResultMethods(out, model);
		appendKeyCallbacks(out, model);
		appendReceiverLifecycle(out, model);
		appendIgnoreWhenMethods(out, model);
		appendThreadMethods(out, model);
		appendFragmentBuilder(out, model);
		appendPreferenceHooks(out, model);

		appendNestedClasses(out, model);
		out.append("}\n");
		return out.toString();
	}

	private void appendViewListeners(StringBuilder out, MvpModel model, ViewInjectionPlan plan) {
		LinkedHashMap<String, String> refs = new LinkedHashMap<>();
		LinkedHashMap<String, String> locals = new LinkedHashMap<>();
		LinkedHashMap<String, String> localTypes = new LinkedHashMap<>();
		LinkedHashMap<String, List<String>> setters = new LinkedHashMap<>();
		LinkedHashMap<String, String> foundTypes = new LinkedHashMap<>(plan.foundTypes);
		refs.putAll(plan.foundRefs);
		appendListenerIds(model, model.clickListeners, refs, locals, localTypes, foundTypes, setters);
		appendListenerIds(model, model.longClickListeners, refs, locals, localTypes, foundTypes, setters);
		appendListenerIds(model, model.touchListeners, refs, locals, localTypes, foundTypes, setters);
		appendListenerIds(model, model.focusChangeListeners, refs, locals, localTypes, foundTypes, setters);
		appendListenerIds(model, model.checkedChangeListeners, refs, locals, localTypes, foundTypes, setters);
		appendListenerIds(model, model.itemClickListeners, refs, locals, localTypes, foundTypes, setters);
		appendListenerIds(model, model.itemSelectListeners, refs, locals, localTypes, foundTypes, setters);
		appendListenerIds(model, model.itemLongClickListeners, refs, locals, localTypes, foundTypes, setters);
		appendListenerIds(model, model.editorActionListeners, refs, locals, localTypes, foundTypes, setters);
		for (Map.Entry<String, String> e : locals.entrySet()) {
			String type = localTypes.get(e.getKey());
			if (type == null) {
				type = "android.view.View";
			}
			if (type.indexOf('<') >= 0) {
				out.append("\t\t@SuppressWarnings(\"unchecked\")\n");
			}
			out.append("\t\t").append(type).append(" ").append(e.getKey()).append(" = hasViews.internalFindViewById(")
				.append(e.getValue()).append(");\n");
		}
		for (Map.Entry<String, List<String>> e : setters.entrySet()) {
			out.append("\t\tif (").append(refs.get(e.getKey())).append(" != null) {\n");
			for (String s : e.getValue()) {
				out.append(s);
			}
			out.append("\t\t}\n");
		}
		appendTextWatchers(out, model);
		appendSeekListeners(out, model);
		appendPageListeners(out, model);
	}

	/** APT ViewByIdHandler parity: planned pass1 (before-injection) / pass3 (injection) code plus the found-view cache. */
	private static final class ViewInjectionPlan {
		final StringBuilder pass1 = new StringBuilder();
		final StringBuilder pass3 = new StringBuilder();
		final LinkedHashMap<String, String> foundRefs = new LinkedHashMap<>();
		final LinkedHashMap<String, String> foundTypes = new LinkedHashMap<>();
	}

	/**
	 * APT parity: walks {@code model.viewInjections} in source order. The first occurrence of an id declares the view
	 * in pass1 (field assign or {@code view_<name>} local); repeats reassign from the cache in pass3; method-injection
	 * forms append a braced block with per-parameter decl/assign and the invocation.
	 */
	private ViewInjectionPlan planViewInjections(MvpModel model) {
		ViewInjectionPlan plan = new ViewInjectionPlan();
		// kapt stub semantics: Kotlin properties are emitted as fields before methods, so FIELD forms
		// are processed first; within each group the source declaration order is preserved.
		List<MvpModel.ViewInjection> orderedInjections = new ArrayList<>(model.viewInjections.size());
		for (MvpModel.ViewInjection injection : model.viewInjections) {
			if (injection.form == MvpModel.ViewInjection.Form.FIELD) {
				orderedInjections.add(injection);
			}
		}
		for (MvpModel.ViewInjection injection : model.viewInjections) {
			if (injection.form != MvpModel.ViewInjection.Form.FIELD) {
				orderedInjections.add(injection);
			}
		}
		for (MvpModel.ViewInjection injection : orderedInjections) {
			if (injection.form == MvpModel.ViewInjection.Form.FIELD) {
				MvpModel.FieldInjection field = injection.field;
				String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", field.propertyName, field.resId, field.resName, rClassIndex);
				String assign = field.jvmField ? "this." + field.propertyName : setterName(field.propertyName);
				String rName = null;
				if (field.resId != AnnotationNames.RES_DEFAULT) {
					rName = rClassIndex != null ? rClassIndex.fieldNameForValue("id", field.resId) : null;
				}
				if (rName == null && rClassIndex != null) {
					rName = rClassIndex.resolveFieldName("id", field.propertyName);
				}
				if (rName != null && plan.foundRefs.containsKey(rName)) {
					plan.pass3.append("\t\t").append(assign).append(" = ").append(castRef(plan.foundTypes.get(rName), field.typeName, plan.foundRefs.get(rName))).append(";\n");
				} else {
					plan.pass1.append("\t\t").append(assign).append(" = hasViews.internalFindViewById(").append(resExpr).append(");\n");
					if (rName != null) {
						plan.foundRefs.put(rName, "this." + field.propertyName);
						plan.foundTypes.put(rName, field.typeName);
					}
				}
			} else {
				for (MvpModel.ViewMethodParam param : injection.params) {
					String rName = viewIdFieldName(param.idKey, param.resId, param.resName);
					if (!plan.foundRefs.containsKey(rName)) {
						String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", param.idKey, param.resId, param.resName, rClassIndex);
						if (param.typeName.contains("<")) {
							plan.pass1.append("\t\t@SuppressWarnings(\"unchecked\")\n");
						}
						plan.pass1.append("\t\t").append(param.typeName).append(" view_").append(rName)
								.append(" = hasViews.internalFindViewById(").append(resExpr).append(");\n");
						plan.foundRefs.put(rName, "view_" + rName);
						plan.foundTypes.put(rName, param.typeName);
					}
				}
				plan.pass3.append("\t\t{\n");
				for (MvpModel.ViewMethodParam param : injection.params) {
					String rName = viewIdFieldName(param.idKey, param.resId, param.resName);
					plan.pass3.append("\t\t\t").append(param.typeName).append(' ').append(param.paramName).append(" = null;\n");
					plan.pass3.append("\t\t\t").append(param.paramName).append(" = ").append(castRef(plan.foundTypes.get(rName), param.typeName, plan.foundRefs.get(rName))).append(";\n");
				}
				plan.pass3.append("\t\t\t").append(injection.methodName).append('(');
				for (int i = 0; i < injection.params.size(); i++) {
					if (i > 0) {
						plan.pass3.append(", ");
					}
					plan.pass3.append(injection.params.get(i).paramName);
				}
				plan.pass3.append(");\n");
				plan.pass3.append("\t\t}\n");
			}
		}
		return plan;
	}

	/** APT USE_ELEMENT_NAME parity: value ref, then resName, then the element (id) key as R.id field name. */
	private String viewIdFieldName(String idKey, int resId, String resName) {
		if (resId != AnnotationNames.RES_DEFAULT && rClassIndex != null) {
			String name = rClassIndex.fieldNameForValue("id", resId);
			if (name != null) {
				return name;
			}
		}
		String name = resName != null && !resName.isEmpty() ? resName : idKey;
		if (rClassIndex != null) {
			String resolved = rClassIndex.resolveFieldName("id", name);
			if (resolved != null) {
				return resolved;
			}
		}
		return name;
	}

	/** APT FoundHolder.getOrCastRef parity: reuse the cached ref when types match or the request is the base View type. */
	private static String castRef(String cachedType, String reqType, String ref) {
		if ((cachedType != null && cachedType.equals(reqType)) || "android.view.View".equals(reqType)) {
			return ref;
		}
		return "((" + reqType + ") " + ref + ")";
	}

/** APT EActivityHolder.setOnCreateOptionsMenu / setOnOptionsItemSelected and InjectHelper parity. */
	private void appendOptionsMenuMethods(StringBuilder out, MvpModel model) {
		boolean menuCreate = !model.optionsMenus.isEmpty() || !model.injectMenuInjections.isEmpty()
				|| !model.optionsMenuItemInjections.isEmpty();
		if (menuCreate) {
			out.append("\t@Override\n");
			if (model.kind == MvpModel.Kind.ACTIVITY) {
				out.append("\tpublic boolean onCreateOptionsMenu(android.view.Menu menu) {\n");
				out.append("\t\tandroid.view.MenuInflater menuInflater = getMenuInflater();\n");
				for (MvpModel.OptionsMenuRef menu : model.optionsMenus) {
					String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "menu", "", menu.resId, menu.resName, rClassIndex);
					out.append("\t\tmenuInflater.inflate(").append(resExpr).append(", menu);\n");
				}
				appendMenuInjections(out, model);
				out.append("\t\treturn super.onCreateOptionsMenu(menu);\n");
			} else {
				out.append("\tpublic void onCreateOptionsMenu(android.view.Menu menu, android.view.MenuInflater inflater) {\n");
				for (MvpModel.OptionsMenuRef menu : model.optionsMenus) {
					String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "menu", "", menu.resId, menu.resName, rClassIndex);
					out.append("\t\tinflater.inflate(").append(resExpr).append(", menu);\n");
				}
				appendMenuInjections(out, model);
				out.append("\t\tsuper.onCreateOptionsMenu(menu, inflater);\n");
			}
			out.append("\t}\n\n");
		}

		if (!model.optionsItems.isEmpty()) {
			out.append("\t@Override\n");
			out.append("\tpublic boolean onOptionsItemSelected(android.view.MenuItem item) {\n");
			out.append("\t\tint itemId_ = item.getItemId();\n");
			for (MvpModel.OptionsItem item : model.optionsItems) {
				List<String> idCmps = new ArrayList<>();
				for (Integer resId : item.valueIds) {
					String rName = rClassIndex != null ? rClassIndex.fieldNameForValue("id", resId) : null;
					if (rName == null) {
						rName = Integer.toString(resId);
					}
					idCmps.add("itemId_ == " + KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", rName, resId, null, rClassIndex));
				}
				for (String resName : item.nameIds) {
					String rName = rClassIndex != null ? rClassIndex.resolveFieldName("id", resName) : null;
					if (rName == null) {
						rName = resName;
					}
					idCmps.add("itemId_ == " + KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", rName, AnnotationNames.RES_DEFAULT, resName, rClassIndex));
				}
				String ifExpr;
				if (idCmps.size() == 1) {
					ifExpr = idCmps.get(0);
				} else {
					StringBuilder sb = new StringBuilder();
					for (int i = 0; i < idCmps.size(); i++) {
						if (i > 0) {
							sb.append("||");
						}
						sb.append("(").append(idCmps.get(i)).append(")");
					}
					ifExpr = sb.toString();
				}
				out.append("\t\tif (").append(ifExpr).append(") {\n");
				String call = item.methodName + (item.hasMenuItemParam ? "(item)" : "()");
				if (item.returnsBoolean) {
					out.append("\t\t\treturn ").append(call).append(";\n");
				} else {
					out.append("\t\t\t").append(call).append(";\n");
					out.append("\t\t\treturn true;\n");
				}
				out.append("\t\t}\n");
			}
			out.append("\t\treturn super.onOptionsItemSelected(item);\n");
			out.append("\t}\n\n");
		}
	}

	private void appendMenuInjections(StringBuilder out, MvpModel model) {
		for (MvpModel.MenuInjection injection : model.injectMenuInjections) {
			if (injection.fieldForm) {
				out.append("\t\tthis.").append(injection.name).append(" = menu;\n");
			} else {
				appendMenuMethodBlock(out, model, injection, false);
			}
		}
		for (MvpModel.MenuInjection injection : model.optionsMenuItemInjections) {
			if (injection.fieldForm) {
				String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", injection.name,
						injection.resId, injection.resName, rClassIndex);
				out.append("\t\tthis.").append(injection.name).append(" = menu.findItem(").append(resExpr).append(");\n");
			} else {
				appendMenuMethodBlock(out, model, injection, true);
			}
		}
	}

	/** APT InjectHelper.processMethod/processParam parity: braced block with one statement per line (JCodeModel rendering). */
	private void appendMenuMethodBlock(StringBuilder out, MvpModel model, MvpModel.MenuInjection injection, boolean findItem) {
		out.append("\t\t{\n");
		for (int i = 0; i < injection.params.size(); i++) {
			MvpModel.Param param = injection.params.get(i);
			out.append("\t\t\t").append(param.typeName).append(" ").append(param.name).append(" = null;\n");
			if (findItem) {
				List<Integer> valueIds = injection.valueIds.get(i);
				List<String> nameIds = injection.nameIds.get(i);
				String resExpr;
				if (!valueIds.isEmpty()) {
					Integer resId = valueIds.get(0);
					String rName = rClassIndex != null ? rClassIndex.fieldNameForValue("id", resId) : null;
					if (rName == null) {
						rName = Integer.toString(resId);
					}
					resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", rName, resId, null, rClassIndex);
				} else {
					String resName = nameIds.isEmpty() ? param.name : nameIds.get(0);
					String rName = rClassIndex != null ? rClassIndex.resolveFieldName("id", resName) : null;
					if (rName == null) {
						rName = resName;
					}
					resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", rName, AnnotationNames.RES_DEFAULT, resName, rClassIndex);
				}
				out.append("\t\t\t").append(param.name).append(" = menu.findItem(").append(resExpr).append(");\n");
			} else {
				out.append("\t\t\t").append(param.name).append(" = menu;\n");
			}
		}
		if (injection.allParamsAnnotated) {
			StringBuilder call = new StringBuilder(injection.name).append("(");
			for (int i = 0; i < injection.params.size(); i++) {
				if (i > 0) {
					call.append(", ");
				}
				call.append(injection.params.get(i).name);
			}
			out.append("\t\t\t").append(call).append(");\n");
		}
		out.append("\t\t}\n");
	}

	private void appendListenerIds(MvpModel model, List<MvpModel.ViewListener> listeners, Map<String, String> refs,
			Map<String, String> locals, Map<String, String> localTypes, Map<String, String> foundTypes, Map<String, List<String>> setters) {
		for (MvpModel.ViewListener listener : listeners) {
			for (Integer resId : listener.valueIds) {
				String rName = rClassIndex != null ? rClassIndex.fieldNameForValue("id", resId) : null;
				if (rName == null) {
					rName = Integer.toString(resId);
				}
				addListenerEntry(model, refs, locals, localTypes, foundTypes, setters, rName,
					KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", rName, resId, null, rClassIndex),
					listener);
			}
			for (String resName : listener.nameIds) {
				String rName = rClassIndex != null ? rClassIndex.resolveFieldName("id", resName) : null;
				if (rName == null) {
					rName = resName;
				}
				addListenerEntry(model, refs, locals, localTypes, foundTypes, setters, rName,
					KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", rName, AnnotationNames.RES_DEFAULT, resName, rClassIndex),
					listener);
			}
		}
	}

	private void addListenerEntry(MvpModel model, Map<String, String> refs, Map<String, String> locals, Map<String, String> localTypes,
			Map<String, String> foundTypes, Map<String, List<String>> setters, String rName, String findExpr, MvpModel.ViewListener listener) {
		String declType = listenerDeclarationType(listener.kind);
		if (!refs.containsKey(rName)) {
			String local = "view_" + rName;
			refs.put(rName, local);
			locals.put(local, findExpr);
			localTypes.put(local, declType);
			foundTypes.put(rName, declType);
		}
		String ref = refs.get(rName);
		StringBuilder s = new StringBuilder();
		if (listener.kind == MvpModel.ListenerKind.CLICK || listener.kind == MvpModel.ListenerKind.LONG_CLICK) {
			boolean longClick = listener.kind == MvpModel.ListenerKind.LONG_CLICK;
			StringBuilder call = new StringBuilder(model.generatedSimpleName()).append(".this.").append(listener.methodName).append("(");
			if (!listener.params.isEmpty()) {
				String type = listener.params.get(0).typeName;
				boolean plainView = "android.view.View".equals(type) || "View".equals(type);
				call.append(plainView ? "view" : "((" + type + ") view)");
			}
			call.append(")");
			s.append("\t\t\t").append(ref).append(longClick
				? ".setOnLongClickListener(new android.view.View.OnLongClickListener() {\n"
				: ".setOnClickListener(new android.view.View.OnClickListener() {\n");
			s.append("\t\t\t@Override\n");
			if (longClick) {
				s.append("\t\t\tpublic boolean onLongClick(android.view.View view) {\n");
				if (listener.returnsBoolean) {
					s.append("\t\t\t\treturn ").append(call).append(";\n");
				} else {
					s.append("\t\t\t\t").append(call).append(";\n");
					s.append("\t\t\t\treturn true;\n");
				}
			} else {
				s.append("\t\t\tpublic void onClick(android.view.View view) {\n");
				s.append("\t\t\t\t").append(call).append(";\n");
			}
			s.append("\t\t\t}\n\t\t\t});\n");
		} else {
			String receiver = castRef(foundTypes.get(rName), declType, ref);
			s.append(extendedListenerSetter(model, listener, receiver));
		}
		List<String> list = setters.get(rName);
		if (list == null) {
			list = new ArrayList<>();
			setters.put(rName, list);
		}
		list.add(s.toString());
	}

	/** Type of a newly declared found-view local. Matches APT {@code getListenerTargetClass}. */
	private static String listenerDeclarationType(MvpModel.ListenerKind kind) {
		switch (kind) {
		case CHECKED_CHANGE:
			return "android.widget.CompoundButton";
		case EDITOR_ACTION:
			return "android.widget.TextView";
		case ITEM_CLICK:
		case ITEM_LONG_CLICK:
		case ITEM_SELECT:
			return "android.widget.AdapterView<?>";
		default:
			return "android.view.View";
		}
	}

	private String extendedListenerSetter(MvpModel model, MvpModel.ViewListener listener, String receiver) {
		switch (listener.kind) {
		case TOUCH:
			return anonymousListener(receiver, "setOnTouchListener", "android.view.View.OnTouchListener",
				"public boolean onTouch(android.view.View view, android.view.MotionEvent event)",
				voidOrBooleanBody(listener, qualifiedCall(model, listener, listenerArgs(listener, "view", "android.view.View"))), false);
		case FOCUS_CHANGE:
			return anonymousListener(receiver, "setOnFocusChangeListener", "android.view.View.OnFocusChangeListener",
				"public void onFocusChange(android.view.View view, boolean hasFocus)",
				"\t\t\t\t" + qualifiedCall(model, listener, listenerArgs(listener, "view", "android.view.View")) + ";\n", false);
		case CHECKED_CHANGE:
			return anonymousListener(receiver, "setOnCheckedChangeListener", "android.widget.CompoundButton.OnCheckedChangeListener",
				"public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked)",
				"\t\t\t\t" + qualifiedCall(model, listener, listenerArgs(listener, "buttonView", "android.widget.CompoundButton")) + ";\n", false);
		case EDITOR_ACTION:
			return anonymousListener(receiver, "setOnEditorActionListener", "android.widget.TextView.OnEditorActionListener",
				"public boolean onEditorAction(android.widget.TextView textView, int actionId, android.view.KeyEvent event)",
				voidOrBooleanBody(listener, qualifiedCall(model, listener, listenerArgs(listener, "textView", "android.widget.TextView"))), false);
		case ITEM_CLICK:
			return itemClickSetter(model, listener, receiver, false);
		case ITEM_LONG_CLICK:
			return itemClickSetter(model, listener, receiver, true);
		case ITEM_SELECT:
			return itemSelectSetter(model, listener, receiver);
		default:
			return "";
		}
	}

	private String itemClickSetter(MvpModel model, MvpModel.ViewListener listener, String receiver, boolean longClick) {
		List<String> args = new ArrayList<>();
		boolean suppress = false;
		if (listener.params.size() == 1) {
			if (listener.roles.get(0) == MvpModel.ParamRole.INT) {
				args.add("position");
			} else {
				String type = listener.params.get(0).typeName;
				args.add("((" + type + ") parent.getAdapter().getItem(position))");
				suppress = type.indexOf('<') >= 0;
			}
		}
		String call = qualifiedCall(model, listener, args);
		String body;
		String signature;
		String setter;
		String listenerType;
		if (longClick) {
			setter = "setOnItemLongClickListener";
			listenerType = "android.widget.AdapterView.OnItemLongClickListener";
			signature = "public boolean onItemLongClick(android.widget.AdapterView<?> parent, android.view.View view, int position, long id)";
			body = listener.returnsBoolean ? "\t\t\t\treturn " + call + ";\n" : "\t\t\t\t" + call + ";\n\t\t\t\treturn true;\n";
		} else {
			setter = "setOnItemClickListener";
			listenerType = "android.widget.AdapterView.OnItemClickListener";
			signature = "public void onItemClick(android.widget.AdapterView<?> parent, android.view.View view, int position, long id)";
			body = "\t\t\t\t" + call + ";\n";
		}
		return anonymousListener(receiver, setter, listenerType, signature, body, suppress);
	}

	private String itemSelectSetter(MvpModel model, MvpModel.ViewListener listener, String receiver) {
		List<String> selected = new ArrayList<>();
		selected.add("true");
		List<String> nothing = new ArrayList<>();
		nothing.add("false");
		boolean suppress = false;
		if (listener.params.size() == 2) {
			if (listener.roles.get(1) == MvpModel.ParamRole.INT) {
				selected.add("position");
				nothing.add("-1");
			} else {
				String type = listener.params.get(1).typeName;
				selected.add("((" + type + ") parent.getAdapter().getItem(position))");
				nothing.add("null");
				suppress = type.indexOf('<') >= 0;
			}
		}
		StringBuilder s = new StringBuilder("\t\t\t").append(receiver)
			.append(".setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {\n");
		s.append("\t\t\t@Override\n");
		s.append("\t\t\tpublic void onNothingSelected(android.widget.AdapterView<?> parent) {\n");
		s.append("\t\t\t\t").append(qualifiedCall(model, listener, nothing)).append(";\n");
		s.append("\t\t\t}\n");
		s.append("\t\t\t@Override\n");
		if (suppress) {
			s.append("\t\t\t@SuppressWarnings(\"unchecked\")\n");
		}
		s.append("\t\t\tpublic void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {\n");
		s.append("\t\t\t\t").append(qualifiedCall(model, listener, selected)).append(";\n");
		s.append("\t\t\t}\n\t\t\t});\n");
		return s.toString();
	}

	private List<String> listenerArgs(MvpModel.ViewListener listener, String viewExpr, String viewBaseType) {
		List<String> args = new ArrayList<>();
		for (int i = 0; i < listener.params.size(); i++) {
			MvpModel.ParamRole role = listener.roles.get(i);
			if (role == MvpModel.ParamRole.MOTION_EVENT) {
				args.add("event");
			} else if (role == MvpModel.ParamRole.VIEW || role == MvpModel.ParamRole.TEXT_VIEW) {
				args.add(typedCast(listener.params.get(i).typeName, viewBaseType, viewExpr));
			} else if (role == MvpModel.ParamRole.BOOLEAN) {
				args.add(listener.kind == MvpModel.ListenerKind.FOCUS_CHANGE ? "hasFocus" : "isChecked");
			} else if (role == MvpModel.ParamRole.INT) {
				args.add("actionId");
			} else if (role == MvpModel.ParamRole.KEY_EVENT) {
				args.add("event");
			}
		}
		return args;
	}

	private static String typedCast(String typeName, String baseType, String expr) {
		if (baseType.equals(typeName) || ("android.view.View".equals(baseType) && "View".equals(typeName))) {
			return expr;
		}
		return "((" + typeName + ") " + expr + ")";
	}

	private static String voidOrBooleanBody(MvpModel.ViewListener listener, String call) {
		if (listener.returnsBoolean) {
			return "\t\t\t\treturn " + call + ";\n";
		}
		return "\t\t\t\t" + call + ";\n\t\t\t\treturn true;\n";
	}

	private static String qualifiedCall(MvpModel model, MvpModel.ViewListener listener, List<String> args) {
		StringBuilder call = new StringBuilder(model.generatedSimpleName());
		call.append(".this.").append(listener.methodName).append('(');
		for (int i = 0; i < args.size(); i++) {
			if (i > 0) {
				call.append(", ");
			}
			call.append(args.get(i));
		}
		call.append(')');
		return call.toString();
	}

	private String anonymousListener(String receiver, String setter, String listenerType, String signature, String body, boolean suppressUnchecked) {
		StringBuilder s = new StringBuilder("\t\t\t").append(receiver).append(".").append(setter)
			.append("(new ").append(listenerType).append("() {\n");
		s.append("\t\t\t@Override\n");
		if (suppressUnchecked) {
			s.append("\t\t\t@SuppressWarnings(\"unchecked\")\n");
		}
		s.append("\t\t\t").append(signature).append(" {\n");
		s.append(body);
		s.append("\t\t\t}\n\t\t\t});\n");
		return s.toString();
	}

	/**
	 * APT TextWatcherHolder parity: every id gets one anonymous TextWatcher. Methods of @BeforeTextChange, @TextChange
	 * and @AfterTextChange on that id are appended into the matching callback, in source order.
	 */
	private void appendTextWatchers(StringBuilder out, MvpModel model) {
		if (model.textWatcherListeners.isEmpty()) {
			return;
		}
		LinkedHashMap<String, String> exprs = new LinkedHashMap<>();
		LinkedHashMap<String, List<MvpModel.ViewListener>> groups = new LinkedHashMap<>();
		for (MvpModel.ViewListener listener : model.textWatcherListeners) {
			for (Integer resId : listener.valueIds) {
				String rName = rClassIndex != null ? rClassIndex.fieldNameForValue("id", resId) : null;
				if (rName == null) {
					rName = Integer.toString(resId);
				}
				addTextGroup(groups, exprs, rName,
					KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", rName, resId, null, rClassIndex), listener);
			}
			for (String resName : listener.nameIds) {
				String rName = rClassIndex != null ? rClassIndex.resolveFieldName("id", resName) : null;
				if (rName == null) {
					rName = resName;
				}
				addTextGroup(groups, exprs, rName,
					KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", rName, AnnotationNames.RES_DEFAULT, resName, rClassIndex), listener);
			}
		}
		for (Map.Entry<String, List<MvpModel.ViewListener>> group : groups.entrySet()) {
			String viewType = "android.widget.TextView";
			out.append("\t\t{\n");
			out.append("\t\t\tfinal ").append(viewType).append(" view = ((").append(viewType)
				.append(") hasViews.internalFindViewById(").append(exprs.get(group.getKey())).append("));\n");
			out.append("\t\t\tif (view != null) {\n");
			out.append("\t\t\t\tview.addTextChangedListener(new android.text.TextWatcher() {\n");
			appendTextCallback(out, model, group.getValue(), MvpModel.ListenerKind.BEFORE_TEXT_CHANGE,
				"public void beforeTextChanged(java.lang.CharSequence s, int start, int count, int after)");
			appendTextCallback(out, model, group.getValue(), MvpModel.ListenerKind.TEXT_CHANGE,
				"public void onTextChanged(java.lang.CharSequence s, int start, int before, int count)");
			appendTextCallback(out, model, group.getValue(), MvpModel.ListenerKind.AFTER_TEXT_CHANGE,
				"public void afterTextChanged(android.text.Editable s)");
			out.append("\t\t\t\t});\n");
			out.append("\t\t\t}\n");
			out.append("\t\t}\n");
		}
	}

	private static void addTextGroup(Map<String, List<MvpModel.ViewListener>> groups, Map<String, String> exprs, String rName,
			String expr, MvpModel.ViewListener listener) {
		exprs.put(rName, expr);
		List<MvpModel.ViewListener> list = groups.get(rName);
		if (list == null) {
			list = new ArrayList<>();
			groups.put(rName, list);
		}
		list.add(listener);
	}


	private void appendTextCallback(StringBuilder out, MvpModel model, List<MvpModel.ViewListener> listeners,
			MvpModel.ListenerKind kind, String signature) {
		out.append("\t\t\t\t\t@Override\n");
		out.append("\t\t\t\t\t").append(signature).append(" {\n");
		for (MvpModel.ViewListener listener : listeners) {
			if (listener.kind != kind) {
				continue;
			}
			out.append("\t\t\t\t\t\t").append(qualifiedCall(model, listener, textWatcherArgs(listener))).append(";\n");
		}
		out.append("\t\t\t\t\t}\n");
	}

	private static List<String> textWatcherArgs(MvpModel.ViewListener listener) {
		List<String> args = new ArrayList<>();
		for (MvpModel.ParamRole role : listener.roles) {
			switch (role) {
			case CHAR_SEQUENCE:
			case EDITABLE:
				args.add("s");
				break;
			case START:
				args.add("start");
				break;
			case COUNT:
				args.add("count");
				break;
			case BEFORE:
				args.add("before");
				break;
			case AFTER:
				args.add("after");
				break;
			case TEXT_VIEW:
				args.add("view");
				break;
			default:
				break;
			}
		}
		return args;
	}


	private void appendFragmentOnCreateView(StringBuilder out, MvpModel model) {
		boolean hasLayoutId = model.layoutId != AnnotationNames.RES_DEFAULT;
		boolean hasLayoutName = model.layoutResName != null && !model.layoutResName.isEmpty();
		boolean hasLayout = hasLayoutId || hasLayoutName;
		out.append("\t@Override\n");
		out.append("\tpublic android.view.View onCreateView(android.view.LayoutInflater inflater, android.view.ViewGroup container, android.os.Bundle savedInstanceState) {\n");
		if (hasLayout) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "layout", "", model.layoutId, hasLayoutName ? model.layoutResName : "", rClassIndex);
			if (model.dataBound) {
				if (model.forceLayoutInjection) {
					appendDataBindingInflate(out, model, resExpr, "getActivity()", "container", false, "\t\t");
					out.append("\t\tcontentView_ = viewDataBinding_.getRoot();\n");
				} else {
					out.append("\t\tcontentView_ = super.onCreateView(inflater, container, savedInstanceState);\n");
					out.append("\t\tif (contentView_ == null) {\n");
					appendDataBindingInflate(out, model, resExpr, "getActivity()", "container", false, "\t\t\t");
					out.append("\t\t\tcontentView_ = viewDataBinding_.getRoot();\n");
					out.append("\t\t}\n");
				}
				appendViewCreated(out, model);
				out.append("\t\treturn contentView_;\n");
			} else if (model.forceLayoutInjection) {
				appendViewCreated(out, model);
				out.append("\t\treturn inflater.inflate(").append(resExpr).append(", container, false);\n");
			} else {
				out.append("\t\tcontentView_ = super.onCreateView(inflater, container, savedInstanceState);\n");
				out.append("\t\tif (contentView_ == null) {\n");
				out.append("\t\t\tcontentView_ = inflater.inflate(").append(resExpr).append(", container, false);\n");
				out.append("\t\t}\n");
				appendViewCreated(out, model);
				out.append("\t\treturn contentView_;\n");
			}
		} else if (model.forceLayoutInjection) {
			appendViewCreated(out, model);
			out.append("\t\treturn contentView_;\n");
		} else {
			out.append("\t\tcontentView_ = super.onCreateView(inflater, container, savedInstanceState);\n");
			appendViewCreated(out, model);
			out.append("\t\treturn contentView_;\n");
		}
		out.append("\t}\n\n");
	}

	/** APT EApplicationHolder parity: singleton INSTANCE_ field, getInstance/setForTesting, onCreate assigns the instance. */
	private String generateApplication(MvpModel model) {
		StringBuilder out = new StringBuilder();
		appendHeader(out, model);
		appendClassAnnotations(out, model);
		out.append("public final class ").append(model.generatedSimpleName())
			.append(" extends ").append(model.simpleName)
			.append(" {\n\n");

		out.append("\tprivate static ").append(model.simpleName).append(" INSTANCE_;\n");
		appendPowerManagerField(out, model);
		out.append("\n");

		out.append("\tpublic static ").append(model.simpleName).append(" getInstance() {\n");
		out.append("\t\treturn INSTANCE_;\n");
		out.append("\t}\n\n");

		out.append("\t/**\n");
		out.append("\t * Visible for testing purposes\n");
		out.append("\t */\n");
		out.append("\tpublic static void setForTesting(").append(model.simpleName).append(" application) {\n");
		out.append("\t\tINSTANCE_ = application;\n");
		out.append("\t}\n\n");

		out.append("\t@Override\n");
		out.append("\tpublic void onCreate() {\n");
		out.append("\t\tINSTANCE_ = this;\n");
		out.append("\t\tinit_();\n");
		out.append("\t\tsuper.onCreate();\n");
		out.append("\t}\n\n");

		out.append("\tprivate void init_() {\n");
		appendPowerManagerInit(out, model);
		if (!model.stringRes.isEmpty() || !model.resFields.isEmpty()) {
			out.append("\t\tandroid.content.res.Resources resources_ = this.getResources();\n");
		}
		for (MvpModel.FieldInjection injection : model.stringRes) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "string", injection.propertyName, injection.resId, injection.resName, rClassIndex);
			appendAssign(out, injection.propertyName, true, "resources_.getString(" + resExpr + ")");
		}
		for (MvpModel.ResField resField : model.resFields) {
			appendResAssign(out, MvpModel.Kind.APPLICATION, resField);
		}
		appendSystemServiceInjections(out, model, "this");
		appendAppInjections(out, model);
		appendPrefInjections(out, model);
		appendBeanInjections(out, model);
		appendHttpsClientInjections(out, model);
		for (String method : model.afterInject) {
			out.append("\t\t").append(method).append("();\n");
		}
		out.append("\t}\n\n");

		appendThreadMethods(out, model);

		appendNestedClasses(out, model);
		out.append("}\n");
		return out.toString();
	}

	/** APT EServiceHolder parity: intent() static factory before init_, onCreate calls init_ then super; nested ServiceIntentBuilder. */
	private String generateService(MvpModel model) {
		StringBuilder out = new StringBuilder();
		appendHeader(out, model);
		appendClassAnnotations(out, model);
		out.append("public final class ").append(model.generatedSimpleName())
			.append(" extends ").append(model.simpleName)
			.append(" {\n\n");

		appendReceiverFields(out, model);
		appendPowerManagerField(out, model);
		appendIntentStaticMethod(out, model);

		out.append("\tprivate void init_() {\n");
		appendPowerManagerInit(out, model);
		if (!model.stringRes.isEmpty() || !model.resFields.isEmpty()) {
			out.append("\t\tandroid.content.res.Resources resources_ = this.getResources();\n");
		}
		for (MvpModel.FieldInjection injection : model.stringRes) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "string", injection.propertyName, injection.resId, injection.resName, rClassIndex);
			appendAssign(out, injection.propertyName, true, "resources_.getString(" + resExpr + ")");
		}
		for (MvpModel.ResField resField : model.resFields) {
			appendResAssign(out, MvpModel.Kind.SERVICE, resField);
		}
		appendSystemServiceInjections(out, model, "this");
		appendAppInjections(out, model);
		appendPrefInjections(out, model);
		appendBeanInjections(out, model);
		appendHttpsClientInjections(out, model);
		for (String method : model.afterInject) {
			out.append("\t\t").append(method).append("();\n");
		}
		appendReceiverFilterInit(out, model, false);
		out.append("\t}\n\n");

		out.append("\t@Override\n");
		out.append("\tpublic void onCreate() {\n");
		out.append("\t\tinit_();\n");
		out.append("\t\tsuper.onCreate();\n");
		appendReceiverRegister(out, model, "OnCreateOnDestroy", "\t\t");
		out.append("\t}\n\n");

		appendReceiverLifecycle(out, model);
		appendThreadMethods(out, model);

		String generated = model.generatedSimpleName();
		out.append("\tpublic static class IntentBuilder_ extends org.androidannotations.api.builder.ServiceIntentBuilder<").append(generated).append(".IntentBuilder_> {\n\n");
		out.append("\t\tpublic IntentBuilder_(android.content.Context context) {\n");
		out.append("\t\t\tsuper(context, ").append(generated).append(".class);\n");
		out.append("\t\t}\n\n");
		out.append("\t}\n\n");

		appendNestedClasses(out, model);
		out.append("}\n");
		return out.toString();
	}

	private String generateBean(MvpModel model) {
		StringBuilder out = new StringBuilder();
		appendHeader(out, model);
		appendBeanClass(out, model, false);
		return out.toString();
	}

	/** Generates a bean as a static nested class (no package line), mirroring APT BaseGeneratedClassHolder. */
	public String generateNestedBean(MvpModel model) {
		StringBuilder out = new StringBuilder();
		appendBeanClass(out, model, true);
		return out.toString();
	}

	private void appendBeanClass(StringBuilder out, MvpModel model, boolean nested) {
		// APT parity (EComponentWithViewSupportHolder.setOnViewChanged): the OnViewChangedListener interface and notifier
		// registration are added only when view support is used (@ViewById/@AfterViews). A bean without view support
		// implements no extra interface.
		boolean hasViewSupport = model.hasViewSupport();
		String implementsClause = hasViewSupport ? " implements org.androidannotations.api.view.OnViewChangedListener" : "";
		if (nested) {
			appendClassAnnotations(out, model);
			out.append("public static final class ").append(model.generatedSimpleName())
					.append(" extends ").append(model.simpleName)
					.append(implementsClause).append(" {\n\n");
		} else {
			appendClassAnnotations(out, model);
			out.append("public final class ").append(model.generatedSimpleName())
					.append(" extends ").append(model.simpleName)
					.append(implementsClause).append(" {\n\n");
		}
		String generated = model.generatedSimpleName();
		String scope = model.scope;
		if (!"Activity".equals(scope) && !"Fragment".equals(scope) && !"Singleton".equals(scope)) {
			scope = "Default";
		}
		boolean defaultScope = "Default".equals(scope);

		out.append("\tprivate android.content.Context context_;\n");
		out.append("\tprivate Object rootFragment_;\n");
		appendPowerManagerField(out, model);
		out.append("\n");
		if ("Singleton".equals(scope)) {
			out.append("\tprivate static ").append(generated).append(" instance_;\n\n");
		}

		// APT parity (EBeanHandler.process): constructors call init_() only for the Default scope.
		out.append("\tprivate ").append(generated).append("(android.content.Context context) {\n");
		out.append("\t\tcontext_ = context;\n");
		if (defaultScope) {
			out.append("\t\tinit_();\n");
		}
		out.append("\t}\n\n");

		out.append("\tprivate ").append(generated).append("(android.content.Context context, Object rootFragment) {\n");
		out.append("\t\tcontext_ = context;\n");
		out.append("\t\trootFragment_ = rootFragment;\n");
		if (defaultScope) {
			out.append("\t\tinit_();\n");
		}
		out.append("\t}\n\n");

		// APT parity (EBeanHolder.createFactoryMethod): factory methods per @EBean scope.
		switch (scope) {
		case "Activity":
			out.append("\tpublic static ").append(generated).append(" getInstance_(android.content.Context context) {\n");
			appendBeanHolderLookup(out, generated, "context", "context");
			out.append("\t\t").append(generated).append(" bean = new ").append(generated).append("(context);\n");
			out.append("\t\tbean.init_();\n");
			out.append("\t\treturn bean;\n");
			out.append("\t}\n\n");
			break;
		case "Fragment":
			out.append("\tpublic static ").append(generated).append(" getInstance_(android.content.Context context) {\n");
			out.append("\t\t").append(generated).append(" bean = new ").append(generated).append("(context);\n");
			out.append("\t\tbean.init_();\n");
			out.append("\t\treturn bean;\n");
			out.append("\t}\n\n");
			out.append("\tpublic static ").append(generated).append(" getInstance_(android.content.Context context, Object rootFragment) {\n");
			appendBeanHolderLookup(out, generated, "rootFragment", "context, rootFragment");
			out.append("\t\treturn new ").append(generated).append("(context, rootFragment);\n");
			out.append("\t}\n\n");
			break;
		case "Singleton":
			out.append("\tpublic static ").append(generated).append(" getInstance_(android.content.Context context) {\n");
			out.append("\t\tif (instance_ == null) {\n");
			out.append("\t\t\torg.androidannotations.api.view.OnViewChangedNotifier previousNotifier = org.androidannotations.api.view.OnViewChangedNotifier.replaceNotifier(null);\n");
			out.append("\t\t\tinstance_ = new ").append(generated).append("(context.getApplicationContext());\n");
			out.append("\t\t\tinstance_.init_();\n");
			out.append("\t\t\torg.androidannotations.api.view.OnViewChangedNotifier.replaceNotifier(previousNotifier);\n");
			out.append("\t\t}\n");
			out.append("\t\treturn instance_;\n");
			out.append("\t}\n\n");
			break;
		default:
			out.append("\tpublic static ").append(generated).append(" getInstance_(android.content.Context context) {\n");
			out.append("\t\treturn new ").append(generated).append("(context);\n");
			out.append("\t}\n\n");
			out.append("\tpublic static ").append(generated).append(" getInstance_(android.content.Context context, Object rootFragment) {\n");
			out.append("\t\treturn new ").append(generated).append("(context, rootFragment);\n");
			out.append("\t}\n\n");
			break;
		}

		out.append("\tprivate void init_() {\n");
		appendPowerManagerInit(out, model);
		appendRootInjections(out, model);
		if (!model.stringRes.isEmpty() || !model.resFields.isEmpty()) {
			out.append("\t\tandroid.content.res.Resources resources_ = context_.getResources();\n");
		}
		for (MvpModel.FieldInjection injection : model.stringRes) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "string", injection.propertyName, injection.resId, injection.resName, rClassIndex);
			appendAssign(out, injection.propertyName, true, "resources_.getString(" + resExpr + ")");
		}
		for (MvpModel.ResField resField : model.resFields) {
			appendResAssign(out, model.kind, resField);
		}
		appendPrefInjections(out, model);
		appendHttpsClientInjections(out, model);
		if (hasViewSupport) {
			out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier.registerOnViewChangedListener(this);\n");
		}
		out.append("\t}\n\n");

		if (defaultScope) {
			// APT parity: rebind() is only generated for the Default scope.
			out.append("\tpublic void rebind(android.content.Context context) {\n");
			out.append("\t\tcontext_ = context;\n");
			out.append("\t\tinit_();\n");
			out.append("\t}\n\n");
		}

		if (hasViewSupport) {
			out.append("\t@Override\n");
			out.append("\tpublic void onViewChanged(org.androidannotations.api.view.HasViews hasViews) {\n");
			ViewInjectionPlan plan = planViewInjections(model);
			out.append(plan.pass1);
			appendViewsById(out, model);
			appendFromHtml(out, model);
			out.append(plan.pass3);
			appendBindingObjects(out, model);
			for (String method : model.afterViews) {
				out.append("\t\t").append(method).append("();\n");
			}
			out.append("\t}\n\n");
		}

		appendThreadMethods(out, model);

		appendNestedClasses(out, model);
		out.append("}\n");
	}

	private String generateView(MvpModel model, boolean nested) {
		StringBuilder out = new StringBuilder();
		if (!nested) {
			appendHeader(out, model);
		}
		boolean hasViewSupport = model.hasViewSupport();
		String extendsName = model.simpleName;
		if (nested && model.qualifiedName != null && model.packageName != null
				&& model.qualifiedName.startsWith(model.packageName + ".")) {
			extendsName = model.qualifiedName.substring(model.packageName.length() + 1);
		}
		out.append("/**\n");
		out.append(" * We use @SuppressWarning here because our java code\n");
		out.append(" * generator doesn't know that there is no need\n");
		out.append(" * to import OnXXXListeners from View as we already\n");
		out.append(" * are in a View.\n");
		out.append(" */\n");
		out.append("@SuppressWarnings(\"unused\")\n");
		appendClassAnnotations(out, model);
		out.append(nested ? "public static final class " : "public final class ").append(model.generatedSimpleName())
				.append(" extends ").append(extendsName)
				.append(" implements org.androidannotations.api.view.HasViews")
				.append(hasViewSupport ? ", org.androidannotations.api.view.OnViewChangedListener" : "")
				.append(" {\n\n");
		out.append("\tprivate boolean alreadyInflated_ = false;\n");
		out.append("\tprivate final org.androidannotations.api.view.OnViewChangedNotifier onViewChangedNotifier_ = new org.androidannotations.api.view.OnViewChangedNotifier();\n");
		appendDataBindingField(out, model);
		appendReceiverFields(out, model);
		appendPowerManagerField(out, model);
		String generated = model.generatedSimpleName();
		for (MvpModel.ViewCtor ctor : model.viewConstructors) {
			out.append("\tpublic ").append(generated).append("(");
			appendParams(out, ctor.params, false, false, false);
			out.append(") {\n\t\tsuper(");
			appendParamNames(out, ctor.params);
			out.append(");\n\t\tinit_();\n\t}\n\n");
			out.append("\tpublic static ").append(extendsName).append(" build(");
			appendParams(out, ctor.params, false, false, false);
			out.append(") {\n\t\t").append(generated).append(" instance = new ").append(generated).append("(");
			appendParamNames(out, ctor.params);
			out.append(");\n\t\tinstance.onFinishInflate();\n\t\treturn instance;\n\t}\n\n");
		}
		out.append("/**\n");
		out.append(" * The alreadyInflated_ hack is needed because of an Android bug\n");
		out.append(" * which leads to infinite calls of onFinishInflate()\n");
		out.append(" * when inflating a layout with a parent and using\n");
		out.append(" * the <code>&lt;merge /&gt;</code> tag.\n");
		out.append(" */\n");
		out.append("\t@Override\n");
		out.append("\tpublic void onFinishInflate() {\n");
		out.append("\t\tif (!alreadyInflated_) {\n");
		out.append("\t\t\talreadyInflated_ = true;\n");
		if (model.kind == MvpModel.Kind.VIEW_GROUP && (model.layoutId != AnnotationNames.RES_DEFAULT
				|| (model.layoutResName != null && !model.layoutResName.isEmpty()))) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "layout", "", model.layoutId, model.layoutResName, rClassIndex);
			if (model.dataBound) {
				appendDataBindingInflate(out, model, resExpr, "getContext()", "this", true, "\t\t\t");
			} else {
				out.append("\t\t\tinflate(getContext(), ").append(resExpr).append(", this);\n");
			}
		}
		out.append("\t\t\tonViewChangedNotifier_.notifyViewChanged(this);\n");
		out.append("\t\t}\n");
		out.append("\t\tsuper.onFinishInflate();\n");
		out.append("\t}\n\n");
		out.append("\tprivate void init_() {\n");
		appendPowerManagerInit(out, model);
		out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier previousNotifier = org.androidannotations.api.view.OnViewChangedNotifier.replaceNotifier(onViewChangedNotifier_);\n");
		if (!model.stringRes.isEmpty() || !model.resFields.isEmpty()) {
			out.append("\t\tandroid.content.res.Resources resources_ = getContext().getResources();\n");
		}
		for (MvpModel.FieldInjection injection : model.stringRes) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "string", injection.propertyName, injection.resId, injection.resName, rClassIndex);
			appendAssign(out, injection.propertyName, true, "resources_.getString(" + resExpr + ")");
		}
		for (MvpModel.ResField resField : model.resFields) {
			appendResAssign(out, model.kind, resField);
		}
		appendPrefInjections(out, model);
		appendHttpsClientInjections(out, model);
		if (hasViewSupport) {
			out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier.registerOnViewChangedListener(this);\n");
		}
		appendBeanInjections(out, model);
		for (String method : model.afterInject) {
			out.append("\t\t").append(method).append("();\n");
		}
		appendReceiverFilterInit(out, model, false);
		out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier.replaceNotifier(previousNotifier);\n");
		out.append("\t}\n\n");
		out.append("\t@Override\n");
		out.append("\tpublic <T extends android.view.View> T internalFindViewById(int id) {\n");
		out.append("\t\treturn ((T) this.findViewById(id));\n");
		out.append("\t}\n\n");

		if (hasViewSupport) {
			out.append("\t@Override\n");
			out.append("\tpublic void onViewChanged(org.androidannotations.api.view.HasViews hasViews) {\n");
			ViewInjectionPlan plan = planViewInjections(model);
			out.append(plan.pass1);
			appendViewsById(out, model);
			appendFromHtml(out, model);
			appendViewListeners(out, model, plan);
			out.append(plan.pass3);
			appendBindingObjects(out, model);
			appendFragmentInjections(out, model);
			for (String method : model.afterViews) {
				out.append("\t\t").append(method).append("();\n");
			}
			out.append("\t}\n\n");
			appendFragmentHelpers(out, model);
		}
		if (!model.receivers.isEmpty()) {
			out.append("\t@Override\n");
			out.append("\tpublic void onAttachedToWindow() {\n");
			out.append("\t\tsuper.onAttachedToWindow();\n");
			appendReceiverRegister(out, model, "OnAttachOnDetach", "\t\t");
			out.append("\t}\n\n");
			out.append("\t@Override\n");
			out.append("\tpublic void onDetachedFromWindow() {\n");
			appendReceiverUnregister(out, model, "OnAttachOnDetach", "\t\t");
			out.append("\t\tsuper.onDetachedFromWindow();\n");
			out.append("\t}\n\n");
		}
		appendThreadMethods(out, model);
		appendKeyCallbacks(out, model);
		appendNestedClasses(out, model);
		out.append("}\n");
		return out.toString();
	}

	private void appendWindowFeatures(StringBuilder out, MvpModel model) {
		if (model.fullscreen) {
			out.append("\t\tgetWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN, android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);\n");
		}
		String method = model.appCompatWindow ? "supportRequestWindowFeature" : "requestWindowFeature";
		for (Integer feature : model.windowFeatures) {
			out.append("\t\t").append(method).append("(").append(feature.intValue()).append(");\n");
		}
		if (model.hasCustomTitle()) {
			// APT CustomTitleHandler always calls requestWindowFeature, including on AppCompat activities.
			out.append("\t\trequestWindowFeature(android.view.Window.FEATURE_CUSTOM_TITLE);\n");
		}
	}

	private void appendDataBindingField(StringBuilder out, MvpModel model) {
		if (!model.dataBound) {
			return;
		}
		String type = model.viewDataBindingType != null ? model.viewDataBindingType : "androidx.databinding.ViewDataBinding";
		out.append("\tprivate ").append(type).append(" viewDataBinding_;\n");
	}

	private void appendDataBindingInflate(StringBuilder out, MvpModel model, String resExpr, String contextExpr, String containerExpr, boolean attach, String indent) {
		String util = model.dataBindingUtil != null ? model.dataBindingUtil : "androidx.databinding.DataBindingUtil";
		out.append(indent).append("viewDataBinding_ = ").append(util).append(".inflate(android.view.LayoutInflater.from(")
				.append(contextExpr).append("), ").append(resExpr).append(", ").append(containerExpr).append(", ").append(attach).append(");\n");
	}

	private void appendCustomTitle(StringBuilder out, MvpModel model) {
		if (!model.hasCustomTitle()) {
			return;
		}
		String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "layout", "", model.customTitleLayoutId, "", rClassIndex);
		out.append("\t\tthis.getWindow().setFeatureInt(android.view.Window.FEATURE_CUSTOM_TITLE, ").append(resExpr).append(");\n");
	}

	private void appendBindingObjects(StringBuilder out, MvpModel model) {
		for (MvpModel.BindingObjectInjection injection : model.bindingObjects) {
			if (injection.form == MvpModel.BindingObjectInjection.Form.FIELD) {
				MvpModel.BindingTarget target = injection.targets.get(0);
				appendAssign(out, injection.name, injection.jvmField, "((" + bindingCastType(target.typeName) + ") viewDataBinding_)");
			} else {
				out.append("\t\t{\n");
				for (MvpModel.BindingTarget target : injection.targets) {
					String cast = bindingCastType(target.typeName);
					out.append("\t\t\t").append(cast).append(" ").append(target.name).append(" = null;\n");
					out.append("\t\t\t").append(target.name).append(" = ((").append(cast).append(") viewDataBinding_);\n");
				}
				out.append("\t\t\t").append(injection.name).append("(");
				for (int i = 0; i < injection.targets.size(); i++) {
					if (i > 0) {
						out.append(", ");
					}
					out.append(injection.targets.get(i).name);
				}
				out.append(");\n");
				out.append("\t\t}\n");
			}
		}
	}

	/** APT BindingObjectHandler: a binding class generated in this round has only a simple name. */
	private String bindingCastType(String typeName) {
		if (typeName == null || typeName.isEmpty()) {
			return "androidx.databinding.ViewDataBinding";
		}
		int generic = typeName.indexOf('<');
		String head = generic < 0 ? typeName : typeName.substring(0, generic);
		if (head.indexOf('.') < 0 && resourcePackage != null && !resourcePackage.isEmpty()) {
			return resourcePackage + ".databinding." + typeName;
		}
		return typeName;
	}

	private void appendHttpsClientInjections(StringBuilder out, MvpModel model) {
		for (MvpModel.HttpsClientInjection injection : model.httpsClients) {
			if (injection.form == MvpModel.HttpsClientInjection.Form.FIELD) {
				MvpModel.HttpsTarget target = injection.targets.get(0);
				appendAssign(out, injection.name, injection.jvmField, httpsClientExpression(model, target));
			} else {
				out.append("\t\t{\n");
				for (MvpModel.HttpsTarget target : injection.targets) {
					out.append("\t\t\t").append(target.typeName).append(" ").append(target.name).append(" = ")
							.append(httpsClientExpression(model, target)).append(";\n");
				}
				out.append("\t\t\t").append(injection.name).append("(");
				for (int i = 0; i < injection.targets.size(); i++) {
					if (i > 0) {
						out.append(", ");
					}
					out.append(injection.targets.get(i).name);
				}
				out.append(");\n");
				out.append("\t\t}\n");
			}
		}
	}

	/**
	 * Anonymous {@code DefaultHttpClient} from HttpsClientHandler. Keystore loading uses the outer component
	 * ({@code Foo_.this} / {@code context_}) because an unqualified {@code this.getResources()} would bind to the client.
	 */
	private String httpsClientExpression(MvpModel model, MvpModel.HttpsTarget target) {
		boolean useTrust = hasRes(target.trustStore, target.trustStoreResName);
		boolean useKey = hasRes(target.keyStore, target.keyStoreResName);
		String trustExpr = useTrust ? KspAnnotationUtils.resExpression(resourcePackage, rClassName, "raw", "", target.trustStore, target.trustStoreResName, rClassIndex) : "";
		String keyExpr = useKey ? KspAnnotationUtils.resExpression(resourcePackage, rClassName, "raw", "", target.keyStore, target.keyStoreResName, rClassIndex) : "";
		StringBuilder body = new StringBuilder();
		body.append("new org.apache.http.impl.client.DefaultHttpClient() {\n");
		body.append("\t\t\t@Override\n");
		body.append("\t\t\tprotected org.apache.http.conn.ClientConnectionManager createClientConnectionManager() {\n");
		body.append("\t\t\t\ttry {\n");
		if (!useTrust && !useKey) {
			body.append("\t\t\t\t\torg.apache.http.conn.ClientConnectionManager ccm = super.createClientConnectionManager();\n");
			if (target.allowAllHostnames) {
				body.append("\t\t\t\t\t((org.apache.http.conn.ssl.SSLSocketFactory) ccm.getSchemeRegistry().getScheme(\"https\").getSocketFactory()).setHostnameVerifier(org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);\n");
			}
			body.append("\t\t\t\t\treturn ccm;\n");
		} else {
			if (useKey) {
				body.append("\t\t\t\t\tjava.security.KeyStore keystore = java.security.KeyStore.getInstance(\"BKS\");\n");
			}
			body.append("\t\t\t\t\tjava.security.KeyStore trusted = java.security.KeyStore.getInstance(\"BKS\");\n");
			body.append("\t\t\t\t\tandroid.content.res.Resources res = getResources();\n");
			if (useKey) {
				body.append("\t\t\t\t\tjava.io.InputStream inKeystore = res.openRawResource(").append(keyExpr).append(");\n");
			}
			if (useTrust) {
				body.append("\t\t\t\t\tjava.io.InputStream inTrustStore = res.openRawResource(").append(trustExpr).append(");\n");
			} else {
				body.append("\t\t\t\t\tjava.io.InputStream inTrustStore = new java.io.FileInputStream(\"/system/etc/security/cacerts.bks\");\n");
			}
			body.append("\t\t\t\t\ttry {\n");
			if (useKey) {
				body.append("\t\t\t\t\t\tkeystore.load(inKeystore, \"").append(escape(target.keyStorePwd)).append("\".toCharArray());\n");
			}
			body.append("\t\t\t\t\t\ttrusted.load(inTrustStore, \"").append(escape(target.trustStorePwd)).append("\".toCharArray());\n");
			body.append("\t\t\t\t\t} finally {\n");
			if (useKey) {
				body.append("\t\t\t\t\t\tinKeystore.close();\n");
			}
			body.append("\t\t\t\t\t\tinTrustStore.close();\n");
			body.append("\t\t\t\t\t}\n");
			body.append("\t\t\t\t\torg.apache.http.conn.ssl.SSLSocketFactory newSslSocketFactory = new org.apache.http.conn.ssl.SSLSocketFactory(")
					.append(useKey ? "keystore" : "null").append(", \"").append(escape(target.keyStorePwd)).append("\", trusted);\n");
			if (target.allowAllHostnames) {
				body.append("\t\t\t\t\tnewSslSocketFactory.setHostnameVerifier(org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);\n");
			}
			body.append("\t\t\t\t\torg.apache.http.conn.scheme.SchemeRegistry registry = new org.apache.http.conn.scheme.SchemeRegistry();\n");
			body.append("\t\t\t\t\tregistry.register(new org.apache.http.conn.scheme.Scheme(\"https\", newSslSocketFactory, 443));\n");
			body.append("\t\t\t\t\tregistry.register(new org.apache.http.conn.scheme.Scheme(\"http\", org.apache.http.conn.scheme.PlainSocketFactory.getSocketFactory(), 80));\n");
			body.append("\t\t\t\t\torg.apache.http.conn.ClientConnectionManager ccm = new org.apache.http.impl.conn.SingleClientConnManager(getParams(), registry);\n");
			body.append("\t\t\t\t\treturn ccm;\n");
		}
		body.append("\t\t\t\t} catch (final java.lang.Exception e) {\n");
		body.append("\t\t\t\t\te.printStackTrace();\n");
		body.append("\t\t\t\t\treturn super.createClientConnectionManager();\n");
		body.append("\t\t\t\t}\n");
		body.append("\t\t\t}\n");
		body.append("\t\t}");
		return body.toString();
	}

	private static boolean hasLayout(MvpModel model) {
		return model.layoutId != AnnotationNames.RES_DEFAULT || (model.layoutResName != null && !model.layoutResName.isEmpty());
	}

	private static boolean hasRes(int resId, String resName) {
		return resId != AnnotationNames.RES_DEFAULT || (resName != null && !resName.isEmpty());
	}

	private void appendTransactionalBody(StringBuilder out, MvpModel model, String name, List<MvpModel.Param> params, String returnType, String indent) {
		String db = params.get(0).name;
		String tag = model.generatedSimpleName();
		if (tag.length() > 23) {
			tag = tag.substring(0, 23);
		}
		boolean voidReturn = returnType == null || "void".equals(returnType);
		out.append(indent).append(db).append(".beginTransaction();\n");
		out.append(indent).append("try {\n");
		if (voidReturn) {
			out.append(indent).append("\t").append(superCall(model, name, params)).append(";\n");
			out.append(indent).append("\t").append(db).append(".setTransactionSuccessful();\n");
			out.append(indent).append("\treturn;\n");
		} else {
			out.append(indent).append("\t").append(returnType).append(" result_ = ").append(superCall(model, name, params)).append(";\n");
			out.append(indent).append("\t").append(db).append(".setTransactionSuccessful();\n");
			out.append(indent).append("\treturn result_;\n");
		}
		out.append(indent).append("} catch (final java.lang.RuntimeException e) {\n");
		out.append(indent).append("\tandroid.util.Log.e(\"").append(escape(tag)).append("\", \"Error in transaction\", e);\n");
		out.append(indent).append("\tthrow e;\n");
		out.append(indent).append("} finally {\n");
		out.append(indent).append("\t").append(db).append(".endTransaction();\n");
		out.append(indent).append("}\n");
	}

	private void appendNonConfigurationRestore(StringBuilder out, MvpModel model) {
		if (model.nonConfigFields.isEmpty()) {
			return;
		}
		String getter = model.customNonConfiguration ? "getLastCustomNonConfigurationInstance" : "getLastNonConfigurationInstance";
		String holderRef = nonConfigurationHolderRef(model);
		out.append("\t\t").append(holderRef).append(" nonConfigurationInstance = ((").append(holderRef).append(") super.").append(getter).append("());\n");
		out.append("\t\tif (nonConfigurationInstance != null) {\n");
		for (MvpModel.NonConfigField field : model.nonConfigFields) {
			out.append("\t\t\t").append(field.propertyName).append(" = nonConfigurationInstance.").append(field.propertyName).append(";\n");
			if (field.rebindGeneratedName != null) {
				out.append("\t\t\t((").append(field.rebindGeneratedName).append(") ").append(field.propertyName).append(").rebind(this);\n");
			}
		}
		out.append("\t\t}\n");
	}

	private String nonConfigurationHolderRef(MvpModel model) {
		return model.generatedSimpleName() + ".NonConfigurationInstancesHolder";
	}

	private void appendNonConfigurationMethods(StringBuilder out, MvpModel model) {
		if (model.nonConfigFields.isEmpty()) {
			return;
		}
		String retain = model.customNonConfiguration ? "onRetainCustomNonConfigurationInstance" : "onRetainNonConfigurationInstance";
		String last = model.customNonConfiguration ? "getLastCustomNonConfigurationInstance" : "getLastNonConfigurationInstance";
		String holderRef = nonConfigurationHolderRef(model);
		out.append("\t@Override\n");
		out.append("\tpublic Object ").append(last).append("() {\n");
		out.append("\t\t").append(holderRef).append(" nonConfigurationInstance = ((").append(holderRef).append(") super.").append(last).append("());\n");
		out.append("\t\tif (nonConfigurationInstance == null) {\n");
		out.append("\t\t\treturn null;\n");
		out.append("\t\t}\n");
		out.append("\t\treturn nonConfigurationInstance.superNonConfigurationInstance;\n");
		out.append("\t}\n\n");
		out.append("\t@Override\n");
		out.append("\tpublic ").append(holderRef).append(" ").append(retain).append("() {\n");
		out.append("\t\t").append(holderRef).append(" nonConfigurationInstanceState_ = new ").append(holderRef).append("();\n");
		out.append("\t\tnonConfigurationInstanceState_.superNonConfigurationInstance = super.").append(retain).append("();\n");
		for (MvpModel.NonConfigField field : model.nonConfigFields) {
			out.append("\t\tnonConfigurationInstanceState_.").append(field.propertyName).append(" = ").append(field.propertyName).append(";\n");
		}
		out.append("\t\treturn nonConfigurationInstanceState_;\n");
		out.append("\t}\n\n");
	}

	private void appendNonConfigurationHolder(StringBuilder out, MvpModel model) {
		if (model.nonConfigFields.isEmpty()) {
			return;
		}
		out.append("\tprivate static class NonConfigurationInstancesHolder {\n");
		for (MvpModel.NonConfigField field : model.nonConfigFields) {
			out.append("\t\tpublic ").append(toSimpleName(field.typeName)).append(" ").append(field.propertyName).append(";\n");
		}
		out.append("\t\tpublic Object superNonConfigurationInstance;\n");
		out.append("\t}\n");
	}

	private void appendViewsById(StringBuilder out, MvpModel model) {
		if (model.viewsByIds.isEmpty()) {
			return;
		}
		// APT ViewsByIdHandler parity: one lookup local per unique id, shared by every field listing that id.
		Map<String, String> resExprs = new LinkedHashMap<>();
		List<List<String>> fieldIdNames = new ArrayList<>();
		for (MvpModel.ViewsByIdInjection injection : model.viewsByIds) {
			List<String> names = new ArrayList<>();
			int index = 0;
			for (Integer id : injection.valueIds) {
				String rName = rClassIndex.fieldNameForValue("id", id);
				if (rName == null) {
					rName = injection.name + "_" + index;
				}
				names.add(rName);
				resExprs.putIfAbsent(rName, KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", injection.name, id, "", rClassIndex));
				index++;
			}
			for (String resName : injection.nameIds) {
				String rName = rClassIndex.resolveFieldName("id", resName);
				if (rName == null) {
					rName = injection.name + "_" + index;
				}
				names.add(rName);
				resExprs.putIfAbsent(rName, KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", resName, AnnotationNames.RES_DEFAULT, resName, rClassIndex));
				index++;
			}
			fieldIdNames.add(names);
		}
		Set<String> lookedUp = new LinkedHashSet<>();
		for (int g = 0; g < model.viewsByIds.size(); g++) {
			MvpModel.ViewsByIdInjection injection = model.viewsByIds.get(g);
			String listName = "list_" + injection.name;
			out.append("\t\tjava.util.ArrayList<").append(injection.viewType).append("> ").append(listName)
					.append(" = new java.util.ArrayList<").append(injection.viewType).append(">();\n");
			for (String rName : fieldIdNames.get(g)) {
				if (!lookedUp.contains(rName)) {
					lookedUp.add(rName);
					out.append("\t\t").append(injection.viewType).append(" view_").append(rName).append(" = hasViews.internalFindViewById(").append(resExprs.get(rName)).append(");\n");
				}
			}
		}
		out.append("\n");
		for (String rName : resExprs.keySet()) {
			out.append("\t\tif (view_").append(rName).append(" != null) {\n");
			for (int g = 0; g < model.viewsByIds.size(); g++) {
				if (fieldIdNames.get(g).contains(rName)) {
					out.append("\t\t\tlist_").append(model.viewsByIds.get(g).name).append(".add(view_").append(rName).append(");\n");
				}
			}
			out.append("\t\t}\n");
		}
		for (int g = 0; g < model.viewsByIds.size(); g++) {
			MvpModel.ViewsByIdInjection injection = model.viewsByIds.get(g);
			String listName = "list_" + injection.name;
			String value = injection.array ? listName + ".toArray(new " + injection.viewType + "[" + listName + ".size()])" : listName;
			if (injection.form == MvpModel.ViewsByIdInjection.Form.FIELD) {
				if (injection.jvmField) {
					out.append("\t\tthis.").append(injection.name).append(" = ").append(value).append(";\n");
				} else {
					out.append("\t\t").append(setterName(injection.name)).append("(").append(value).append(");\n");
				}
			} else {
				out.append("\t\t").append(injection.name).append("(").append(value).append(");\n");
			}
		}
	}

	private void appendFromHtml(StringBuilder out, MvpModel model) {
		for (MvpModel.FromHtmlField field : model.fromHtml) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "string", field.propertyName, field.resId, field.resName, rClassIndex);
			// APT FromHtmlHandler parity: the view reference is the bare field name.
			out.append("\t\tif (").append(field.propertyName).append(" != null) {\n");
			out.append("\t\t\t").append(field.propertyName).append(".setText(android.text.Html.fromHtml(").append(contextRef(model.kind)).append(".getString(").append(resExpr).append(")));\n");
			out.append("\t\t}\n");
		}
	}

	private void appendFragmentInjections(StringBuilder out, MvpModel model) {
		boolean useHelpers = model.kind != MvpModel.Kind.FRAGMENT;
		// APT parity (CorePlugin): FragmentByIdHandler runs before FragmentByTagHandler.
		for (MvpModel.FragmentInjection byId : model.fragmentByIds) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", byId.propertyName, byId.resId, byId.resName, rClassIndex);
			String call = useHelpers
					? fragmentHelperName(byId.nativeFragment, true) + "(" + resExpr + ")"
					: fragmentManagerExpr(model, byId.childFragment, byId.nativeFragment) + ".findFragmentById(" + resExpr + ")";
			out.append("\t\tthis.").append(byId.propertyName).append(" = ((").append(byId.typeName).append(") ").append(call).append(");\n");
		}
		for (MvpModel.FragmentInjection byTag : model.fragmentByTags) {
			String tag = byTag.resName != null && !byTag.resName.isEmpty() ? byTag.resName : byTag.propertyName;
			String call = useHelpers
					? fragmentHelperName(byTag.nativeFragment, false) + "(\"" + tag + "\")"
					: fragmentManagerExpr(model, byTag.childFragment, byTag.nativeFragment) + ".findFragmentByTag(\"" + tag + "\")";
			out.append("\t\tthis.").append(byTag.propertyName).append(" = ((").append(byTag.typeName).append(") ").append(call).append(");\n");
		}
	}

	private static String fragmentHelperName(boolean nativeFragment, boolean byId) {
		return (nativeFragment ? "findNativeFragment" : "findSupportFragment") + (byId ? "ById" : "ByTag");
	}

	/** APT parity: helpers are created lazily on first use while handlers run, so order is first-use across byId fields then byTag fields. */
	private static List<String> fragmentHelperPlan(MvpModel model) {
		List<String> plan = new ArrayList<>();
		for (MvpModel.FragmentInjection byId : model.fragmentByIds) {
			String key = fragmentHelperName(byId.nativeFragment, true);
			if (!plan.contains(key)) {
				plan.add(key);
			}
		}
		for (MvpModel.FragmentInjection byTag : model.fragmentByTags) {
			String key = fragmentHelperName(byTag.nativeFragment, false);
			if (!plan.contains(key)) {
				plan.add(key);
			}
		}
		return plan;
	}

	private void appendFragmentHelpers(StringBuilder out, MvpModel model) {
		if (model.kind == MvpModel.Kind.FRAGMENT || (model.fragmentByIds.isEmpty() && model.fragmentByTags.isEmpty())) {
			return;
		}
		boolean viewHost = model.kind == MvpModel.Kind.VIEW || model.kind == MvpModel.Kind.VIEW_GROUP;
		for (String key : fragmentHelperPlan(model)) {
			boolean byId = key.endsWith("ById");
			boolean nativeFragment = key.startsWith("findNativeFragment");
			String retType = nativeFragment ? "android.app.Fragment" : "androidx.fragment.app.Fragment";
			out.append("\tprivate ").append(retType).append(" ").append(key).append("(").append(byId ? "int id" : "String tag").append(") {\n");
			if (viewHost) {
				String activityType = nativeFragment ? "android.app.Activity" : "androidx.fragment.app.FragmentActivity";
				out.append("\t\tif (!(getContext() instanceof ").append(activityType).append(")) {\n");
				out.append("\t\t\treturn null;\n");
				out.append("\t\t}\n");
				out.append("\t\t").append(activityType).append(" activity_ = ((").append(activityType).append(") getContext());\n");
				out.append("\t\treturn activity_.").append(nativeFragment ? "getFragmentManager()" : "getSupportFragmentManager()").append(".findFragment").append(byId ? "ById(id" : "ByTag(tag").append(");\n");
			} else {
				out.append("\t\treturn ").append(nativeFragment ? "getFragmentManager()" : "getSupportFragmentManager()").append(".findFragment").append(byId ? "ById(id" : "ByTag(tag").append(");\n");
			}
			out.append("\t}\n\n");
		}
	}

	private static String fragmentManagerExpr(MvpModel model, boolean child, boolean nativeFragment) {
		if (model.kind == MvpModel.Kind.FRAGMENT) {
			return child ? "getChildFragmentManager()" : "getFragmentManager()";
		}
		if (model.kind == MvpModel.Kind.VIEW || model.kind == MvpModel.Kind.VIEW_GROUP) {
			if (nativeFragment) {
				return "((android.app.Activity) getContext()).getFragmentManager()";
			}
			return "((androidx.fragment.app.FragmentActivity) getContext()).getSupportFragmentManager()";
		}
		return nativeFragment ? "getFragmentManager()" : "getSupportFragmentManager()";
	}

	private void appendViewCreated(StringBuilder out, MvpModel model) {
		if (model.needsViewDestroyed) {
			out.append("\t\tviewDestroyed_ = false;\n");
		}
	}

	private void appendReceiverExtraConstants(StringBuilder out, MvpModel model) {
		for (MvpModel.ExtraConstant constant : model.receiverExtraConstants) {
			out.append("\tpublic final static String ").append(constant.constantName).append(" = \"").append(escape(constant.key)).append("\";\n");
		}
	}

	private void appendReceiverFields(StringBuilder out, MvpModel model) {
		if (model.receivers.isEmpty()) {
			return;
		}
		ReceiverPlan plan = planReceivers(model);
		java.util.LinkedHashSet<String> emittedFilters = new java.util.LinkedHashSet<>();
		for (ReceiverBound bound : plan.bounds) {
			if (emittedFilters.add(bound.filterField)) {
				out.append("\tprivate final android.content.IntentFilter ").append(bound.filterField).append(" = new android.content.IntentFilter();\n");
			}
			out.append("\tprivate final android.content.BroadcastReceiver ").append(bound.receiverField).append(" = new android.content.BroadcastReceiver() {\n");
			if (bound.binding.unchecked) {
				out.append("\t\t@SuppressWarnings(\"unchecked\")\n");
			}
			java.util.LinkedHashSet<String> emittedConstants = new java.util.LinkedHashSet<>();
			for (MvpModel.ReceiverArg arg : bound.binding.args) {
				if (arg.role == MvpModel.ReceiverArg.Role.EXTRA && emittedConstants.add(arg.extraConstant)) {
					out.append("\t\tpublic final static String ").append(arg.extraConstant).append(" = \"").append(escape(arg.extraKey)).append("\";\n");
				}
			}
			out.append("\t\tpublic void onReceive(android.content.Context context, android.content.Intent intent) {\n");
			boolean hasExtra = false;
			for (MvpModel.ReceiverArg arg : bound.binding.args) {
				if (arg.role == MvpModel.ReceiverArg.Role.EXTRA) {
					hasExtra = true;
					break;
				}
			}
			if (hasExtra) {
				out.append("\t\t\tandroid.os.Bundle extras_ = ((intent.getExtras() != null) ? intent.getExtras() : new android.os.Bundle());\n");
			}
			List<String> callArgs = new ArrayList<>();
			for (MvpModel.ReceiverArg arg : bound.binding.args) {
				if (arg.role == MvpModel.ReceiverArg.Role.CONTEXT) {
					callArgs.add("context");
				} else if (arg.role == MvpModel.ReceiverArg.Role.INTENT) {
					callArgs.add("intent");
				} else {
					out.append("\t\t\t").append(toSimpleName(arg.typeName)).append(" ").append(arg.name).append(" = ").append(arg.readExpr).append(";\n");
					callArgs.add(arg.name);
				}
			}
			out.append("\t\t\t").append(model.generatedSimpleName()).append(".this.").append(bound.binding.methodName).append("(");
			for (int i = 0; i < callArgs.size(); i++) {
				if (i > 0) {
					out.append(", ");
				}
				out.append(callArgs.get(i));
			}
			out.append(");\n");
			out.append("\t\t}\n");
			out.append("\t};\n");
		}
	}

	private void appendReceiverFilterInit(StringBuilder out, MvpModel model, boolean attachBlock) {
		if (model.receivers.isEmpty()) {
			return;
		}
		boolean fragment = model.kind == MvpModel.Kind.FRAGMENT;
		for (ReceiverFilter filter : planReceivers(model).filters) {
			boolean onAttach = fragment && "OnAttachOnDetach".equals(filter.registerAt);
			if (onAttach != attachBlock) {
				continue;
			}
			for (String action : filter.actions) {
				out.append("\t\t").append(filter.fieldName).append(".addAction(\"").append(escape(action)).append("\");\n");
			}
			for (String scheme : filter.schemes) {
				out.append("\t\t").append(filter.fieldName).append(".addDataScheme(\"").append(escape(scheme)).append("\");\n");
			}
		}
	}

	private void appendReceiverRegister(StringBuilder out, MvpModel model, String registerAt, String indent) {
		appendReceiverBind(out, model, registerAt, indent, true);
	}

	private void appendReceiverUnregister(StringBuilder out, MvpModel model, String registerAt, String indent) {
		appendReceiverBind(out, model, registerAt, indent, false);
	}

	private void appendReceiverBind(StringBuilder out, MvpModel model, String registerAt, String indent, boolean register) {
		if (model.receivers.isEmpty()) {
			return;
		}
		String verb = register ? "registerReceiver" : "unregisterReceiver";
		for (ReceiverBound bound : planReceivers(model).bounds) {
			if (!registerAt.equals(bound.binding.registerAt)) {
				continue;
			}
			String target = contextRef(model.kind);
			if (bound.binding.local) {
				target = bound.binding.localManager + ".getInstance(" + target + ")";
			}
			out.append(indent).append(target).append(".").append(verb).append("(").append(bound.receiverField);
			if (register) {
				out.append(", ").append(bound.filterField);
			}
			out.append(");\n");
		}
	}

	private void appendReceiverLifecycle(StringBuilder out, MvpModel model) {
		boolean unbind = model.kind == MvpModel.Kind.ACTIVITY && model.dataBound && hasLayout(model);
		if ((model.receivers.isEmpty() || model.kind == MvpModel.Kind.VIEW || model.kind == MvpModel.Kind.VIEW_GROUP) && !unbind) {
			return;
		}
		if (hasReceiver(model, "OnCreateOnDestroy")) {
			out.append("\t@Override\n");
			out.append("\tpublic void onDestroy() {\n");
			appendReceiverUnregister(out, model, "OnCreateOnDestroy", "\t\t");
			if (unbind) {
				out.append("\t\tviewDataBinding_.unbind();\n");
			}
			out.append("\t\tsuper.onDestroy();\n");
			out.append("\t}\n\n");
		}
		if (model.receivers.isEmpty() || model.kind == MvpModel.Kind.VIEW || model.kind == MvpModel.Kind.VIEW_GROUP) {
			return;
		}
		if (model.kind == MvpModel.Kind.SERVICE) {
			return;
		}
		if (hasReceiver(model, "OnStartOnStop")) {
			out.append("\t@Override\n");
			out.append("\tpublic void onStart() {\n");
			out.append("\t\tsuper.onStart();\n");
			appendReceiverRegister(out, model, "OnStartOnStop", "\t\t");
			out.append("\t}\n\n");
			out.append("\t@Override\n");
			out.append("\tpublic void onStop() {\n");
			appendReceiverUnregister(out, model, "OnStartOnStop", "\t\t");
			out.append("\t\tsuper.onStop();\n");
			out.append("\t}\n\n");
		}
		if (hasReceiver(model, "OnResumeOnPause")) {
			out.append("\t@Override\n");
			out.append("\tpublic void onResume() {\n");
			out.append("\t\tsuper.onResume();\n");
			appendReceiverRegister(out, model, "OnResumeOnPause", "\t\t");
			out.append("\t}\n\n");
			out.append("\t@Override\n");
			out.append("\tpublic void onPause() {\n");
			appendReceiverUnregister(out, model, "OnResumeOnPause", "\t\t");
			out.append("\t\tsuper.onPause();\n");
			out.append("\t}\n\n");
		}
		if (model.kind == MvpModel.Kind.FRAGMENT && hasReceiver(model, "OnAttachOnDetach")) {
			out.append("\t@Override\n");
			out.append("\tpublic void onAttach(android.app.Activity activity) {\n");
			out.append("\t\tsuper.onAttach(activity);\n");
			appendReceiverFilterInit(out, model, true);
			appendReceiverRegister(out, model, "OnAttachOnDetach", "\t\t");
			out.append("\t}\n\n");
			out.append("\t@Override\n");
			out.append("\tpublic void onDetach() {\n");
			appendReceiverUnregister(out, model, "OnAttachOnDetach", "\t\t");
			out.append("\t\tsuper.onDetach();\n");
			out.append("\t}\n\n");
		}
	}

	private static boolean hasReceiver(MvpModel model, String registerAt) {
		for (MvpModel.ReceiverBinding binding : model.receivers) {
			if (registerAt.equals(binding.registerAt)) {
				return true;
			}
		}
		return false;
	}

	private void appendIgnoreWhenMethods(StringBuilder out, MvpModel model) {
		for (MvpModel.IgnoreWhenMethod method : model.ignoreWhens) {
			out.append("\t@Override\n");
			out.append("\t").append(method.visibility).append(" void ").append(method.name).append("(");
			appendParams(out, method.params, true);
			out.append(") {\n");
			out.append("\t\tif (").append(ignoreWhenCondition(model, method.state)).append(") {\n");
			out.append("\t\t\t").append(model.generatedSimpleName()).append(".super.").append(method.name).append("(");
			appendParamNames(out, method.params);
			out.append(");\n");
			out.append("\t\t}\n");
			out.append("\t}\n\n");
		}
	}

	private void appendGuardedSuperCall(StringBuilder out, MvpModel model, MvpModel.MethodInfo method, String indent) {
		StringBuilder call = new StringBuilder();
		call.append(model.generatedSimpleName()).append(".super.").append(method.name).append("(");
		for (int i = 0; i < method.params.size(); i++) {
			if (i > 0) {
				call.append(", ");
			}
			call.append(method.params.get(i).name);
		}
		call.append(");");
		if (method.ignoreWhenState == null) {
			out.append(indent).append(call).append("\n");
			return;
		}
		out.append(indent).append("if (").append(ignoreWhenCondition(model, method.ignoreWhenState)).append(") {\n");
		out.append(indent).append("\t").append(call).append("\n");
		out.append(indent).append("}\n");
	}

	private static String ignoreWhenCondition(MvpModel model, String state) {
		if ("VIEW_DESTROYED".equals(state)) {
			return "!viewDestroyed_";
		}
		return model.generatedSimpleName() + ".this.getActivity() != null";
	}

	private ReceiverPlan planReceivers(MvpModel model) {
		ReceiverPlan plan = new ReceiverPlan();
		java.util.LinkedHashMap<String, ReceiverFilter> filters = new java.util.LinkedHashMap<>();
		int index = 1;
		for (MvpModel.ReceiverBinding binding : model.receivers) {
			String key = binding.registerAt + "\0" + String.join("\0", binding.actions) + "\0" + String.join("\0", binding.dataSchemes);
			ReceiverFilter filter = filters.get(key);
			if (filter == null) {
				filter = new ReceiverFilter();
				filter.fieldName = "intentFilter" + index + "_";
				index++;
				filter.actions = binding.actions;
				filter.schemes = binding.dataSchemes;
				filter.registerAt = binding.registerAt;
				filters.put(key, filter);
				plan.filters.add(filter);
			}
			ReceiverBound bound = new ReceiverBound();
			bound.binding = binding;
			bound.filterField = filter.fieldName;
			bound.receiverField = binding.methodName + "Receiver_";
			plan.bounds.add(bound);
		}
		return plan;
	}

	private static final class ReceiverPlan {
		final List<ReceiverFilter> filters = new ArrayList<>();
		final List<ReceiverBound> bounds = new ArrayList<>();
	}

	private static final class ReceiverFilter {
		String fieldName;
		String registerAt;
		List<String> actions;
		List<String> schemes;
	}

	private static final class ReceiverBound {
		MvpModel.ReceiverBinding binding;
		String filterField;
		String receiverField;
	}

	private void appendHeader(StringBuilder out, MvpModel model) {
		if (model.packageName != null && !model.packageName.isEmpty()) {
			out.append("package ").append(model.packageName).append(";\n\n");
		}
		out.append("/**\n");
		out.append(" * Generated by AndroidAnnotations KSP MVP processor.\n");
		out.append(" */\n");
	}

	/** APT APTCodeModelHelper.copyNonAAAnnotations parity: non-AndroidAnnotations class annotations in source order. */
	private void appendClassAnnotations(StringBuilder out, MvpModel model) {
		for (String annotation : model.classAnnotations) {
			out.append(annotation).append('\n');
		}
	}

	/** APT PreferenceScreenHandler: {@code addPreferencesFromResource} at the end of onCreate (activity) or after super (fragment). */
	private void appendPreferenceScreenCall(StringBuilder out, MvpModel model) {
		if (!model.hasPreferenceScreen) {
			return;
		}
		String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "xml", "", model.preferenceScreenResId, model.preferenceScreenResName, rClassIndex);
		out.append("\t\taddPreferencesFromResource(").append(resExpr).append(");\n");
	}

	/**
	 * APT PreferencesDelegate + Preference* handlers. Headers override {@code onBuildHeaders}.
	 * ByKey / Change / Click / AfterPreferences share one {@code addPreferencesFromResource} override.
	 */
	private void appendPreferenceHooks(StringBuilder out, MvpModel model) {
		if (model.hasPreferenceHeaders) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "xml", "", model.preferenceHeadersResId, model.preferenceHeadersResName, rClassIndex);
			out.append("\t@Override\n");
			out.append("\tpublic void onBuildHeaders(java.util.List<android.preference.PreferenceActivity.Header> target) {\n");
			out.append("\t\tloadHeadersFromResource(").append(resExpr).append(", target);\n");
			out.append("\t\tsuper.onBuildHeaders(target);\n");
			out.append("\t}\n\n");
		}
		if (!model.hasPreferenceInjection()) {
			return;
		}
		out.append("\t@Override\n");
		out.append("\tpublic void addPreferencesFromResource(int preferencesResId) {\n");
		out.append("\t\tsuper.addPreferencesFromResource(preferencesResId);\n");
		appendPreferenceInjection(out, model);
		out.append("\t}\n\n");
	}

	private void appendPreferenceInjection(StringBuilder out, MvpModel model) {
		String base = model.preferenceBaseClass();
		LinkedHashMap<String, FoundPref> found = new LinkedHashMap<>();
		for (MvpModel.PreferenceField field : model.preferenceFields) {
			String key = preferenceKey(field.resId, field.resName, field.elementKey);
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "string", field.elementKey, field.resId, field.resName, rClassIndex);
			String find = "this.findPreference(this.getString(" + resExpr + "))";
			appendAssign(out, field.propertyName, field.jvmField, castPreference(find, base, field.typeName, base));
			String ref = field.jvmField ? "this." + field.propertyName : getterName(field.propertyName) + "()";
			found.put(key, new FoundPref(ref, field.typeName));
		}
		for (MvpModel.PreferenceMethod method : model.preferenceMethods) {
			// APT InjectHelper parity: emit the inject block first, then append fresh local declarations after it.
			List<String> deferredDecls = new ArrayList<>();
			out.append("\t\t{\n");
			for (MvpModel.PreferenceParam param : method.params) {
				String key = preferenceKey(param.resId, param.resName, param.fallbackName);
				FoundPref holder = found.get(key);
				if (holder == null) {
					String local = "preference_" + key;
					found.put(key, new FoundPref(local, param.typeName));
					holder = found.get(key);
					String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "string", param.fallbackName, param.resId, param.resName, rClassIndex);
					String find = "this.findPreference(this.getString(" + resExpr + "))";
					deferredDecls.add("\t\t" + param.typeName + ' ' + local + " = "
							+ castPreference(find, base, param.typeName, base) + ";\n");
				}
				String value = castPreference(holder.ref, holder.type, param.typeName, base);
				out.append("\t\t\t").append(param.typeName).append(' ').append(param.name).append(" = null;\n");
				out.append("\t\t\t").append(param.name).append(" = ").append(value).append(";\n");
			}
			out.append("\t\t\t").append(method.methodName).append('(');
			for (int i = 0; i < method.params.size(); i++) {
				if (i > 0) {
					out.append(", ");
				}
				out.append(method.params.get(i).name);
			}
			out.append(");\n");
			out.append("\t\t}\n");
			for (String decl : deferredDecls) {
				out.append(decl);
			}
		}
		LinkedHashMap<String, StringBuilder> bodies = new LinkedHashMap<>();
		List<String> order = new ArrayList<>();
		appendPreferenceListeners(out, model, model.preferenceChanges, true, found, bodies, order);
		appendPreferenceListeners(out, model, model.preferenceClicks, false, found, bodies, order);
		for (String key : order) {
			FoundPref holder = found.get(key);
			out.append("\t\tif (").append(holder.ref).append(" != null) {\n");
			out.append(bodies.get(key));
			out.append("\t\t}\n");
		}
		for (String method : model.afterPreferences) {
			out.append("\t\t").append(method).append("();\n");
		}
	}

	private void appendPreferenceListeners(StringBuilder out, MvpModel model, List<MvpModel.PreferenceListener> listeners, boolean change,
			Map<String, FoundPref> found, Map<String, StringBuilder> bodies, List<String> order) {
		for (MvpModel.PreferenceListener listener : listeners) {
			List<String> keys = new ArrayList<>();
			List<String> exprs = new ArrayList<>();
			for (Integer resId : listener.valueIds) {
				String key = preferenceKey(resId, "", "");
				keys.add(key);
				exprs.add(KspAnnotationUtils.resExpression(resourcePackage, rClassName, "string", key, resId, null, rClassIndex));
			}
			for (String resName : listener.nameIds) {
				String key = preferenceKey(AnnotationNames.RES_DEFAULT, resName, resName);
				keys.add(key);
				exprs.add(KspAnnotationUtils.resExpression(resourcePackage, rClassName, "string", resName, AnnotationNames.RES_DEFAULT, resName, rClassIndex));
			}
			for (int i = 0; i < keys.size(); i++) {
				ensurePreferenceLocal(out, model, found, keys.get(i), exprs.get(i));
				StringBuilder body = bodies.get(keys.get(i));
				if (body == null) {
					body = new StringBuilder();
					bodies.put(keys.get(i), body);
					order.add(keys.get(i));
				}
				body.append(preferenceSetter(model, listener, change, found.get(keys.get(i))));
			}
		}
	}

	private void ensurePreferenceLocal(StringBuilder out, MvpModel model, Map<String, FoundPref> found, String key, String resExpr) {
		if (found.containsKey(key)) {
			return;
		}
		String base = model.preferenceBaseClass();
		String local = "preference_" + key;
		out.append("\t\t").append(base).append(' ').append(local)
				.append(" = this.findPreference(this.getString(").append(resExpr).append("));\n");
		found.put(key, new FoundPref(local, base));
	}

	private String preferenceSetter(MvpModel model, MvpModel.PreferenceListener listener, boolean change, FoundPref holder) {
		String base = model.preferenceBaseClass();
		String listenerType = change ? model.preferenceChangeListener() : model.preferenceClickListener();
		String callback = change ? "onPreferenceChange" : "onPreferenceClick";
		String setter = change ? "setOnPreferenceChangeListener" : "setOnPreferenceClickListener";
		StringBuilder call = new StringBuilder();
		call.append(model.generatedSimpleName()).append(".this.").append(listener.methodName).append('(');
		for (int i = 0; i < listener.argKinds.size(); i++) {
			if (i > 0) {
				call.append(", ");
			}
			call.append(preferenceArg(listener.argKinds.get(i), listener.argTypes.get(i), base));
		}
		call.append(')');
		StringBuilder source = new StringBuilder();
		source.append("\t\t\t").append(holder.ref).append('.').append(setter).append("(new ").append(listenerType).append("() {\n");
		source.append("\t\t\t\t@Override\n");
		if (listener.unchecked) {
			source.append("\t\t\t\t@SuppressWarnings(\"unchecked\")\n");
		}
		source.append("\t\t\t\tpublic boolean ").append(callback).append('(').append(base).append(" preference");
		if (change) {
			source.append(", java.lang.Object newValue");
		}
		source.append(") {\n");
		if (listener.returnsBoolean) {
			source.append("\t\t\t\t\treturn ").append(call).append(";\n");
		} else {
			source.append("\t\t\t\t\t").append(call).append(";\n");
			source.append("\t\t\t\t\treturn true;\n");
		}
		source.append("\t\t\t\t}\n");
		source.append("\t\t\t});\n");
		return source.toString();
	}

	private static String preferenceArg(String kind, String type, String base) {
		if ("PREFERENCE".equals(kind)) {
			return base.equals(type) ? "preference" : "((" + type + ") preference)";
		}
		if ("OBJECT".equals(kind)) {
			return "newValue";
		}
		if ("INT".equals(kind)) {
			return "java.lang.Integer.valueOf(((java.lang.String) newValue))";
		}
		if ("LONG".equals(kind)) {
			return "java.lang.Long.valueOf(((java.lang.String) newValue))";
		}
		if ("FLOAT".equals(kind)) {
			return "java.lang.Float.valueOf(((java.lang.String) newValue))";
		}
		return "((" + type + ") newValue)";
	}

	private static String castPreference(String expr, String fromType, String toType, String base) {
		if (fromType.equals(toType) || base.equals(toType)) {
			return expr;
		}
		return "((" + toType + ") " + expr + ")";
	}

	private String preferenceKey(int resId, String resName, String fallback) {
		if (resId != AnnotationNames.RES_DEFAULT) {
			String name = rClassIndex != null ? rClassIndex.fieldNameForValue("string", resId) : null;
			return name != null ? name : ("v" + resId);
		}
		String name = (resName != null && !resName.isEmpty()) ? resName : fallback;
		if (name == null) {
			name = "";
		}
		if (rClassIndex != null) {
			String resolved = rClassIndex.resolveFieldName("string", name);
			if (resolved != null) {
				return resolved;
			}
		}
		return name;
	}

	private static final class FoundPref {
		final String ref;
		final String type;

		FoundPref(String ref, String type) {
			this.ref = ref;
			this.type = type;
		}
	}

	private void appendSetContentViewCall(StringBuilder out, MvpModel model) {
		boolean hasLayoutId = model.layoutId != AnnotationNames.RES_DEFAULT;
		boolean hasLayoutName = model.layoutResName != null && !model.layoutResName.isEmpty();
		if (hasLayoutId || hasLayoutName) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "layout", "", model.layoutId, hasLayoutName ? model.layoutResName : "", rClassIndex);
			if (model.dataBound) {
				out.append("\t\tandroid.view.ViewGroup contentView = internalFindViewById(android.R.id.content);\n");
				appendDataBindingInflate(out, model, resExpr, "this", "contentView", false, "\t\t");
				out.append("\t\tsetContentView(viewDataBinding_.getRoot(), viewDataBinding_.getRoot().getLayoutParams());\n");
			} else {
				out.append("\t\tsetContentView(").append(resExpr).append(");\n");
			}
		}
	}

	/** Mirrors APT ContextCompatAwareResHandler branch selection; the backing field is always assigned directly (APT parity). */
	private void appendResAssign(StringBuilder out, MvpModel.Kind kind, MvpModel.ResField resField) {
		String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, resField.rInnerClass, resField.propertyName, resField.resId, resField.resName, rClassIndex);
		ResCompat.Mode mode = resCompatModes.get(resField.method);
		String contextRef = contextRef(kind);
		String contextCall = contextRef + "." + resField.method;
		if ("this".equals(contextRef)) {
			contextCall = resField.method;
		}
		if ("ANIMATION_UTILS".equals(resField.invocation)) {
			// APT AnimationResHandler parity: AnimationUtils.loadAnimation(context, R.anim.x).
			appendAssign(out, resField.propertyName, true, "android.view.animation.AnimationUtils.loadAnimation(" + contextRef + ", " + resExpr + ")");
			return;
		}
		if ("HTML_FROM_HTML".equals(resField.invocation)) {
			// APT HtmlResHandler parity: Html.fromHtml(resources_.getString(R.string.x)).
			appendAssign(out, resField.propertyName, true, "android.text.Html.fromHtml(resources_." + resField.method + "(" + resExpr + "))");
			return;
		}
		if (mode == null || "RESOURCES".equals(mode.kind)) {
			appendAssign(out, resField.propertyName, true, "resources_." + resField.method + "(" + resExpr + ")");
			return;
		}
		if ("ANDROIDX_COMPAT".equals(mode.kind) || "SUPPORT_COMPAT".equals(mode.kind)) {
			String compatClass = "ANDROIDX_COMPAT".equals(mode.kind) ? "androidx.core.content.ContextCompat" : "android.support.v4.content.ContextCompat";
			appendAssign(out, resField.propertyName, true, compatClass + "." + resField.method + "(" + contextRef + ", " + resExpr + ")");
			return;
		}
		if ("CONTEXT_METHOD".equals(mode.kind)) {
			appendAssign(out, resField.propertyName, true, contextCall + "(" + resExpr + ")");
			return;
		}
		// IF_GUARD: mirrors APT ContextCompatAwareResHandler.createCallWithIfGuard.
		out.append("\t\tif (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.").append(mode.platformName).append(") {\n");
		appendAssign(out, resField.propertyName, true, contextCall + "(" + resExpr + ")", "\t\t\t");
		out.append("\t\t} else {\n");
		appendAssign(out, resField.propertyName, true, "resources_." + resField.method + "(" + resExpr + ")", "\t\t\t");
		out.append("\t\t}\n");
	}

	private void appendAssign(StringBuilder out, String propertyName, boolean jvmField, String expression) {
		appendAssign(out, propertyName, jvmField, expression, "\t\t");
	}

	private void appendAssign(StringBuilder out, String propertyName, boolean jvmField, String expression, String indent) {
		if (jvmField) {
			out.append(indent).append("this.").append(propertyName).append(" = ").append(expression).append(";\n");
		} else {
			out.append(indent).append(setterName(propertyName)).append("(").append(expression).append(");\n");
		}
	}

	private void appendAssign(StringBuilder out, MvpModel.FieldInjection injection, String expression) {
		appendAssign(out, injection, expression, "\t\t");
	}

	private void appendAssign(StringBuilder out, MvpModel.FieldInjection injection, String expression, String indent) {
		if (injection.jvmField) {
			out.append(indent).append("this.").append(injection.propertyName).append(" = ").append(expression).append(";\n");
		} else {
			out.append(indent).append(setterName(injection.propertyName)).append("(").append(expression).append(");\n");
		}
	}

	/** APT PrefHandler parity: {@code field = new Pref_(context)} inside the init-body injection block. */
	private void appendPrefInjections(StringBuilder out, MvpModel model) {
		String contextRef = contextRef(model.kind);
		for (MvpModel.PrefInjection pref : model.prefs) {
			if (pref.generatedQualifiedName == null) {
				continue;
			}
			appendAssign(out, pref.propertyName, pref.jvmField, "new " + prefReference(pref.generatedQualifiedName, model.packageName) + "(" + contextRef + ")");
		}
	}

	/** Same-package generated helpers are referenced by simple name (no import in the generated source). */
	private static String prefReference(String generatedQualifiedName, String packageName) {
		if (packageName == null || packageName.isEmpty()) {
			return generatedQualifiedName;
		}
		String prefix = packageName + ".";
		if (!generatedQualifiedName.startsWith(prefix)) {
			return generatedQualifiedName;
		}
		String rest = generatedQualifiedName.substring(prefix.length());
		return rest.contains(".") ? generatedQualifiedName : rest;
	}

	/** APT BeanHandler parity: field assigns / braced method blocks inside the init-body injection block. */
	private void appendBeanInjections(StringBuilder out, MvpModel model) {
		for (MvpModel.BeanInjection bean : model.beans) {
			if (bean.form == MvpModel.BeanInjection.Form.FIELD) {
				int times = isNonConfigField(model, bean.name) ? 2 : 1;
				for (int i = 0; i < times; i++) {
					MvpModel.BeanTarget target = bean.targets.get(0);
					String expr = getInstanceExpr(target, model.kind);
					if (bean.assignOnlyIfNull) {
						String read = bean.jvmField ? "this." + bean.name : getterName(bean.name) + "()";
						out.append("\t\tif (").append(read).append(" == null) {\n");
						appendBeanAssign(out, bean.name, bean.jvmField, expr, "\t\t\t");
						out.append("\t\t}\n");
					} else {
						appendBeanAssign(out, bean.name, bean.jvmField, expr);
					}
				}
			} else {
				out.append("\t\t{\n");
				for (MvpModel.BeanTarget target : bean.targets) {
					out.append("\t\t\t").append(target.declaredType).append(' ').append(target.name).append(" = null;\n");
					out.append("\t\t\t").append(target.name).append(" = ").append(getInstanceExpr(target, model.kind)).append(";\n");
				}
				out.append("\t\t\t").append(bean.name).append('(');
				for (int i = 0; i < bean.targets.size(); i++) {
					if (i > 0) {
						out.append(", ");
					}
					out.append(bean.targets.get(i).name);
				}
				out.append(");\n");
				out.append("\t\t}\n");
			}
		}
	}

	private static boolean isNonConfigField(MvpModel model, String name) {
		for (MvpModel.NonConfigField f : model.nonConfigFields) {
			if (f.propertyName.equals(name)) {
				return true;
			}
		}
		return false;
	}

	private static String getInstanceExpr(MvpModel.BeanTarget target, MvpModel.Kind kind) {
		String contextRef = contextRef(kind);
		String rootFragmentRef = kind == MvpModel.Kind.FRAGMENT ? "this" : "null";
		return target.beanGeneratedQualifiedName + ".getInstance_(" + contextRef + (target.withRootFragmentArg ? ", " + rootFragmentRef : "") + ")";
	}

	private static String contextRef(MvpModel.Kind kind) {
		if (kind == MvpModel.Kind.FRAGMENT) {
			return "getActivity()";
		}
		if (kind == MvpModel.Kind.BEAN) {
			return "context_";
		}
		if (kind == MvpModel.Kind.VIEW || kind == MvpModel.Kind.VIEW_GROUP || kind == MvpModel.Kind.PROVIDER) {
			return "getContext()";
		}
		if (kind == MvpModel.Kind.RECEIVER) {
			return "context";
		}
		return "this";
	}

	private void appendBeanAssign(StringBuilder out, String propertyName, boolean jvmField, String expression) {
		appendBeanAssign(out, propertyName, jvmField, expression, "\t\t");
	}

	private void appendBeanAssign(StringBuilder out, String propertyName, boolean jvmField, String expression, String indent) {
		if (jvmField) {
			out.append(indent).append("this.").append(propertyName).append(" = ").append(expression).append(";\n");
		} else {
			out.append(indent).append(setterName(propertyName)).append("(").append(expression).append(");\n");
		}
	}

	private static String getterName(String propertyName) {
		if (propertyName.length() == 1) {
			return "get" + propertyName.toUpperCase(java.util.Locale.US);
		}
		return "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
	}

	/** APT AppHandler parity: field assigns / braced method blocks inside the init-body injection block (no-arg static getInstance()). */
	private void appendAppInjections(StringBuilder out, MvpModel model) {
		for (MvpModel.AppInjection app : model.apps) {
			if (app.form == MvpModel.AppInjection.Form.FIELD) {
				MvpModel.AppTarget target = app.targets.get(0);
				appendBeanAssign(out, app.name, app.jvmField, target.appGeneratedQualifiedName + ".getInstance()");
			} else {
				out.append("\t\t{\n");
				for (MvpModel.AppTarget target : app.targets) {
					out.append("\t\t\t").append(target.declaredType).append(' ').append(target.name).append(" = null;\n");
					out.append("\t\t\t").append(target.name).append(" = ").append(target.appGeneratedQualifiedName).append(".getInstance();\n");
				}
				out.append("\t\t\t").append(app.name).append('(');
				for (int i = 0; i < app.targets.size(); i++) {
					if (i > 0) {
						out.append(", ");
					}
					out.append(app.targets.get(i).name);
				}
				out.append(");\n");
				out.append("\t\t}\n");
			}
		}
	}

	/** APT SystemServiceHandler parity: field assigns / braced method blocks inside the init-body injection block. */
	private void appendSystemServiceInjections(StringBuilder out, MvpModel model, String contextRef) {
		for (MvpModel.SystemServiceInjection injection : model.systemServices) {
			if (injection.form == MvpModel.SystemServiceInjection.Form.FIELD) {
				MvpModel.SystemServiceTarget target = injection.targets.get(0);
				appendAssign(out, injection.name, injection.jvmField, getSystemServiceExpr(target, contextRef));
			} else {
				out.append("\t\t{\n");
				for (MvpModel.SystemServiceTarget target : injection.targets) {
					out.append("\t\t\t").append(target.declaredType).append(' ').append(target.name).append(" = null;\n");
					out.append("\t\t\t").append(target.name).append(" = ").append(getSystemServiceExpr(target, contextRef)).append(";\n");
				}
				out.append("\t\t\t").append(injection.name).append('(');
				for (int i = 0; i < injection.targets.size(); i++) {
					if (i > 0) {
						out.append(", ");
					}
					out.append(injection.targets.get(i).name);
				}
				out.append(");\n");
				out.append("\t\t}\n");
			}
		}
	}

	private static String getSystemServiceExpr(MvpModel.SystemServiceTarget target, String contextRef) {
		String ctx = target.appContext ? contextRef + ".getApplicationContext()" : contextRef;
		return "((" + target.declaredType + ") " + ctx + ".getSystemService(" + target.serviceConst + "))";
	}

	private static String setterName(String propertyName) {
		return "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
	}

	private void appendIntentStaticMethod(StringBuilder out, MvpModel model) {
		String generated = model.generatedSimpleName();
		out.append("\tpublic static ").append(generated).append(".IntentBuilder_ intent(android.content.Context context) {\n");
		out.append("\t\treturn new ").append(generated).append(".IntentBuilder_(context);\n");
		out.append("\t}\n\n");
	}

	/** APT ActivityIntentBuilder.createAdditionalIntentMethods: static intent overloads for the fragment classpath. */
	private void appendFragmentIntentOverloads(StringBuilder out, MvpModel model) {
		String generated = model.generatedSimpleName();
		if (intentBuilderCompat.fragment) {
			out.append("\tpublic static ").append(generated).append(".IntentBuilder_ intent(android.app.Fragment fragment) {\n");
			out.append("\t\treturn new ").append(generated).append(".IntentBuilder_(fragment);\n");
			out.append("\t}\n\n");
		}
		if (intentBuilderCompat.androidxFragment) {
			appendStaticFragmentIntent(out, generated, "androidx.fragment.app.Fragment");
		} else if (intentBuilderCompat.supportFragment) {
			appendStaticFragmentIntent(out, generated, "android.support.v4.app.Fragment");
		}
	}

	private void appendStaticFragmentIntent(StringBuilder out, String generated, String fragmentType) {
		out.append("\tpublic static ").append(generated).append(".IntentBuilder_ intent(").append(fragmentType).append(" supportFragment) {\n");
		out.append("\t\treturn new ").append(generated).append(".IntentBuilder_(supportFragment);\n");
		out.append("\t}\n\n");
	}

	/** APT ActivityIntentBuilder.createAdditionalConstructor: private field plus fragment constructor. */
	private void appendFragmentField(StringBuilder out, String fragmentType, String fieldName) {
		out.append("\t\tprivate ").append(fragmentType).append(" ").append(fieldName).append(";\n\n");
	}

	private void appendFragmentConstructor(StringBuilder out, String generated, String fragmentType, String fieldName) {
		out.append("\t\tpublic IntentBuilder_(").append(fragmentType).append(" fragment) {\n");
		out.append("\t\t\tsuper(fragment.getActivity(), ").append(generated).append(".class);\n");
		out.append("\t\t\t").append(fieldName).append(" = fragment;\n");
		out.append("\t\t}\n\n");
	}

	private String supportFragmentType() {
		return intentBuilderCompat.androidxFragment ? "androidx.fragment.app.Fragment" : "android.support.v4.app.Fragment";
	}

	/** APT ActivityIntentBuilder.overrideStartForResultMethod: nested else{if} fragment branches, then the compat/activity/context starter. */
	private void appendStartForResult(StringBuilder out) {
		boolean hasSupport = intentBuilderCompat.androidxFragment || intentBuilderCompat.supportFragment;
		boolean hasFragment = intentBuilderCompat.fragment;
		boolean options = intentBuilderCompat.activityOptionsInFragment;
		boolean guard = intentBuilderCompat.guardActivityOptions;

		out.append("\t\t@Override\n");
		out.append("\t\tpublic org.androidannotations.api.builder.PostActivityStarter startForResult(int requestCode) {\n");
		String s1 = "\t\t\t";
		int depth = 0;

		if (hasSupport) {
			out.append(s1 + padTabs(depth)).append("if (fragmentSupport_!= null) {\n");
			out.append(s1 + padTabs(depth + 1)).append("fragmentSupport_.startActivityForResult(intent, requestCode);\n");
			out.append(s1 + padTabs(depth)).append("} else {\n");
			depth++;
		}
		if (hasFragment) {
			out.append(s1 + padTabs(depth)).append("if (fragment_!= null) {\n");
			String fBody = s1 + padTabs(depth + 1);
			if (options && guard) {
				out.append(fBody).append("if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {\n");
				out.append(fBody + "\t").append("fragment_.startActivityForResult(intent, requestCode, lastOptions);\n");
				out.append(fBody).append("} else {\n");
				out.append(fBody + "\t").append("fragment_.startActivityForResult(intent, requestCode);\n");
				out.append(fBody).append("}\n");
			} else if (options) {
				out.append(fBody).append("fragment_.startActivityForResult(intent, requestCode, lastOptions);\n");
			} else {
				out.append(fBody).append("fragment_.startActivityForResult(intent, requestCode);\n");
			}
			out.append(s1 + padTabs(depth)).append("} else {\n");
			depth++;
		}

		String aLevel = s1 + padTabs(depth);
		out.append(aLevel).append("if (context instanceof android.app.Activity) {\n");
		String aBody = aLevel + "\t";
		out.append(aBody).append("android.app.Activity activity = ((android.app.Activity) context);\n");
		if (intentBuilderCompat.activityCompat != null) {
			out.append(aBody).append(intentBuilderCompat.activityCompat).append(".startActivityForResult(activity, intent, requestCode, lastOptions);\n");
		} else if (options && guard) {
			out.append(aBody).append("if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {\n");
			out.append(aBody + "\t").append("activity.startActivityForResult(intent, requestCode, lastOptions);\n");
			out.append(aBody).append("} else {\n");
			out.append(aBody + "\t").append("activity.startActivityForResult(intent, requestCode);\n");
			out.append(aBody).append("}\n");
		} else if (options) {
			out.append(aBody).append("activity.startActivityForResult(intent, requestCode, lastOptions);\n");
		} else {
			out.append(aBody).append("activity.startActivityForResult(intent, requestCode);\n");
		}
		out.append(aLevel).append("} else {\n");
		if (options && guard) {
			out.append(aBody).append("if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {\n");
			out.append(aBody + "\t").append("context.startActivity(intent, lastOptions);\n");
			out.append(aBody).append("} else {\n");
			out.append(aBody + "\t").append("context.startActivity(intent);\n");
			out.append(aBody).append("}\n");
		} else if (options) {
			out.append(aBody).append("context.startActivity(intent, lastOptions);\n");
		} else {
			out.append(aBody).append("context.startActivity(intent);\n");
		}
		out.append(aLevel).append("}\n");
		while (depth > 0) {
			depth--;
			out.append(s1 + padTabs(depth)).append("}\n");
		}
		out.append("\t\t\treturn new org.androidannotations.api.builder.PostActivityStarter(context);\n");
		out.append("\t\t}\n");
	}

	private static String padTabs(int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++) {
			sb.append('\t');
		}
		return sb.toString();
	}

	private void appendIntentBuilder(StringBuilder out, MvpModel model) {
		String generated = model.generatedSimpleName();
		out.append("\tpublic static class IntentBuilder_ extends org.androidannotations.api.builder.ActivityIntentBuilder<").append(generated).append(".IntentBuilder_> {\n\n");

		if (intentBuilderCompat.fragment) {
			appendFragmentField(out, "android.app.Fragment", "fragment_");
		}
		if (intentBuilderCompat.androidxFragment || intentBuilderCompat.supportFragment) {
			appendFragmentField(out, supportFragmentType(), "fragmentSupport_");
		}

		out.append("\t\tpublic IntentBuilder_(android.content.Context context) {\n");
		out.append("\t\t\tsuper(context, ").append(generated).append(".class);\n");
		out.append("\t\t}\n\n");

		if (intentBuilderCompat.fragment) {
			appendFragmentConstructor(out, generated, "android.app.Fragment", "fragment_");
		}
		if (intentBuilderCompat.androidxFragment || intentBuilderCompat.supportFragment) {
			appendFragmentConstructor(out, generated, supportFragmentType(), "fragmentSupport_");
		}

		appendStartForResult(out);

		for (MvpModel.FieldInjection extra : model.extras) {
			String constant = KspAnnotationUtils.extraKeyConstant(extra.propertyName);
			String paramType = toSimpleName(extra.extraFullType != null ? extra.extraFullType : extra.typeName);
			out.append("\t\tpublic ").append(generated).append(".IntentBuilder_ ").append(extra.propertyName).append('(').append(paramType).append(" ")
					.append(extra.propertyName).append(") {\n");
			out.append("\t\t\treturn super.extra(").append(constant).append(", ")
					.append(extra.extraWriteExpr.replace("{arg}", extra.propertyName)).append(");\n");
			out.append("\t\t}\n\n");
		}

		out.append("\t}\n");
	}

	private void appendFragmentBeanHolderMethods(StringBuilder out) {
		out.append("\t@Override\n");
		out.append("\tpublic <T> T getBean(Class<T> key) {\n");
		out.append("\t\treturn ((T) beans_.get(key));\n");
		out.append("\t}\n\n");
		out.append("\t@Override\n");
		out.append("\tpublic <T> void putBean(Class<T> key, T value) {\n");
		out.append("\t\tbeans_.put(key, value);\n");
		out.append("\t}\n\n");
	}

	private void appendFragmentArgsMethod(StringBuilder out, MvpModel model) {
		if (model.fragmentArgs.isEmpty()) {
			return;
		}
		boolean unchecked = false;
		for (MvpModel.FieldInjection arg : model.fragmentArgs) {
			unchecked |= arg.extraUnchecked;
		}
		if (unchecked) {
			out.append("\t@SuppressWarnings(\"unchecked\")\n");
		}
		out.append("\tprivate void injectFragmentArguments_() {\n");
		out.append("\t\tandroid.os.Bundle args_ = getArguments();\n");
		if (!model.fragmentArgs.isEmpty()) {
			out.append("\t\tif (args_!= null) {\n");
			for (MvpModel.FieldInjection arg : model.fragmentArgs) {
				String constant = KspAnnotationUtils.fragmentArgKeyConstant(arg.propertyName);
				out.append("\t\t\tif (args_.containsKey(").append(constant).append(")) {\n");
				out.append("\t\t\t\tthis.").append(arg.propertyName).append(" = ").append(arg.extraReadExpr.replace("{key}", constant)).append(";\n");
				out.append("\t\t\t}\n");
			}
			out.append("\t\t}\n");
		}
		out.append("\t}\n\n");
	}

	private void appendFragmentBuilder(StringBuilder out, MvpModel model) {
		String generated = model.generatedSimpleName();
		out.append("\tpublic static class FragmentBuilder_ extends org.androidannotations.api.builder.FragmentBuilder<").append(generated).append(".FragmentBuilder_, ").append(model.qualifiedName).append("> {\n\n");
		out.append("\t\t@Override\n");
		out.append("\t\tpublic ").append(model.qualifiedName).append(" build() { ").append(generated).append(" fragment_ = new ").append(generated).append("(); fragment_.setArguments(args); return fragment_; }\n");
		for (int i = 0; i < model.fragmentArgs.size(); i++) {
			MvpModel.FieldInjection arg = model.fragmentArgs.get(i);
			String constant = KspAnnotationUtils.fragmentArgKeyConstant(arg.propertyName);
			String paramType = arg.extraFullType != null ? arg.extraFullType : arg.typeName;
			out.append("\n");
			out.append("\t\t/**\n");
			out.append("\t\t * @param ").append(arg.propertyName).append("\n");
			out.append("\t\t *     value for this Fragment argument\n");
			out.append("\t\t * @return\n");
			out.append("\t\t *     the FragmentBuilder to chain calls\n");
			out.append("\t\t */\n");
			out.append("\t\tpublic ").append(generated).append(".FragmentBuilder_ ").append(arg.propertyName).append('(').append(toSimpleName(paramType)).append(" ").append(arg.propertyName)
					.append(") { args.").append(fragmentArgPutMethod(arg)).append('(').append(constant).append(", ")
					.append(arg.extraWriteExpr.replace("{arg}", arg.propertyName)).append("); return this; }\n");
			if (i < model.fragmentArgs.size() - 1) {
				out.append("\n");
			}
		}
		out.append("\t}\n");
	}

	private String fragmentArgPutMethod(MvpModel.FieldInjection arg) {
		String fullType = arg.extraFullType != null ? arg.extraFullType : arg.typeName;
		String suffix = KspAnnotationUtils.BUNDLE_GET_SUFFIX.get(fullType);
		if (suffix != null) {
			return "put" + suffix;
		}
		String write = arg.extraWriteExpr;
		if (write.startsWith("(android.os.Parcelable)") || write.startsWith("org.parceler.Parcels.wrap")) {
			return "putParcelable";
		}
		return "putSerializable";
	}

	/** Inlines directly nested annotated models as static nested classes (APT parity), re-indented one level. */
	private void appendNestedClasses(StringBuilder out, MvpModel model) {
		appendNestedPrefs(out, model);
		for (MvpModel nested : model.nestedModels) {
			out.append('\n');
			String source = (nested.kind == MvpModel.Kind.VIEW || nested.kind == MvpModel.Kind.VIEW_GROUP)
					? generateView(nested, true) : generateNestedBean(nested);
			for (String line : source.split("\n")) {
				if (!line.isEmpty()) {
					out.append('\t').append(line).append('\n');
				} else {
					out.append('\n');
				}
			}
		}
	}

	/** Inlines {@code @SharedPref} helpers nested in this component (APT: static nested class of the enclosing generated class). */
	private void appendNestedPrefs(StringBuilder out, MvpModel model) {
		if (model.nestedPrefs.isEmpty()) {
			return;
		}
		SharedPrefGenerator prefGenerator = new SharedPrefGenerator(resourcePackage, rClassName, rClassIndex);
		for (SharedPrefModel pref : model.nestedPrefs) {
			out.append('\n');
			String source = prefGenerator.generateNested(pref);
			for (String line : source.split("\n", -1)) {
				if (!line.isEmpty()) {
					out.append('\t').append(line).append('\n');
				} else {
					out.append('\n');
				}
			}
		}
	}

	/** APT requestInstanceFromBeanHolder: BeanHolder lookup with create/put/init, body indented two tabs. */
	private void appendBeanHolderLookup(StringBuilder out, String generated, String holderRef, String newArgs) {
		out.append("\t\tif (" + holderRef + " instanceof org.androidannotations.api.bean.BeanHolder) {\n");
		out.append("\t\t\torg.androidannotations.api.bean.BeanHolder beanHolder = ((org.androidannotations.api.bean.BeanHolder) " + holderRef + ");\n");
		out.append("\t\t\t" + generated + " bean = beanHolder.getBean(" + generated + ".class);\n");
		out.append("\t\t\tif (bean == null) {\n");
		out.append("\t\t\t\tbean = new " + generated + "(" + newArgs + ");\n");
		out.append("\t\t\t\tbeanHolder.putBean(" + generated + ".class, bean);\n");
		out.append("\t\t\t\tbean.init_();\n");
		out.append("\t\t\t}\n");
		out.append("\t\t\treturn bean;\n");
		out.append("\t\t}\n");
	}

	private void appendBeanHolderMethods(StringBuilder out) {
		out.append("\t@Override\n");
		out.append("\tpublic <T> T getBean(Class<T> key) {\n");
		out.append("\t\treturn ((T) beans_.get(key));\n");
		out.append("\t}\n\n");
		out.append("\t@Override\n");
		out.append("\tpublic <T> void putBean(Class<T> key, T value) {\n");
		out.append("\t\tbeans_.put(key, value);\n");
		out.append("\t}\n\n");
	}

	/** One thread-wrapped override awaiting emission, sorted by APT handler claim and source order. */
	private static final class ThreadMethodEntry {
		final int claimIndex;
		final int sourceOrder;
		final MvpModel.MethodInfo threaded;
		final boolean background;
		final MvpModel.WrappedMethod wrapped;

		ThreadMethodEntry(int claimIndex, int sourceOrder, MvpModel.MethodInfo threaded, boolean background, MvpModel.WrappedMethod wrapped) {
			this.claimIndex = claimIndex;
			this.sourceOrder = sourceOrder;
			this.threaded = threaded;
			this.background = background;
			this.wrapped = wrapped;
		}
	}

	/**
	 * APT CorePlugin parity: thread wrappers are emitted in handler order (Transactional, Trace, WakeLock,
	 * UiThread, Background, SupposeUiThread, SupposeBackground), each group in source order. A method is
	 * claimed by the earliest handler among its annotations; within a claim, visit order decides.
	 */
	private void appendThreadMethods(StringBuilder out, MvpModel model) {
		if (model.uiThreads.isEmpty() && model.backgrounds.isEmpty() && model.wrappedMethods.isEmpty()) {
			return;
		}
		List<ThreadMethodEntry> entries = new ArrayList<>();
		for (MvpModel.MethodInfo method : model.uiThreads) {
			entries.add(new ThreadMethodEntry(claimIndex(method, 2), method.sourceOrder, method, false, null));
		}
		for (MvpModel.MethodInfo method : model.backgrounds) {
			entries.add(new ThreadMethodEntry(claimIndex(method, 3), method.sourceOrder, method, true, null));
		}
		for (MvpModel.WrappedMethod method : model.wrappedMethods) {
			int claim = method.transactional ? -1 : claimIndex(method, 6);
			entries.add(new ThreadMethodEntry(claim, method.sourceOrder, null, false, method));
		}
		entries.sort((a, b) -> a.claimIndex != b.claimIndex ? Integer.compare(a.claimIndex, b.claimIndex)
				: Integer.compare(a.sourceOrder, b.sourceOrder));
		for (ThreadMethodEntry entry : entries) {
			if (entry.wrapped != null) {
				appendWrappedMethod(out, model, entry.wrapped);
			} else if (entry.background) {
				appendBackgroundMethod(out, model, entry.threaded);
			} else {
				appendUiThreadMethod(out, model, entry.threaded);
			}
		}
	}

	/** Earliest APT thread handler claiming the method: trace=0, wakeLock=1, uiThread=2, background=3, supposeUiThread=4, supposeBackground=5. */
	private static int claimIndex(MvpModel.MethodInfo method, int base) {
		int claim = base;
		if (method.trace != null) {
			claim = Math.min(claim, 0);
		}
		if (method.wakeLock != null) {
			claim = Math.min(claim, 1);
		}
		if (method.suppose != null) {
			claim = Math.min(claim, method.suppose.uiThread ? 4 : 5);
		}
		return claim;
	}
	/** Earliest APT thread handler claiming a wrapped method: trace=0, wakeLock=1, supposeUiThread=4, supposeBackground=5. */
	private static int claimIndex(MvpModel.WrappedMethod method, int base) {
		int claim = base;
		if (method.trace != null) {
			claim = Math.min(claim, 0);
		}
		if (method.wakeLock != null) {
			claim = Math.min(claim, 1);
		}
		if (method.suppose != null) {
			claim = Math.min(claim, method.suppose.uiThread ? 4 : 5);
		}
		return claim;
	}


	private void appendBackgroundMethod(StringBuilder out, MvpModel model, MvpModel.MethodInfo method) {
		out.append("\t@Override\n");
		out.append("\t").append(method.visibility).append(" void ").append(method.name).append("(");
		appendParams(out, method.params, true);
		out.append(") {\n");
		appendSupposePrefix(out, method.suppose, "\t\t");
		out.append("\t\torg.androidannotations.api.BackgroundExecutor.execute(new org.androidannotations.api.BackgroundExecutor.Task(\"")
				.append(escape(method.id)).append("\", ").append(method.delay).append("L, \"")
				.append(escape(method.serial)).append("\") {\n");
		out.append("\t\t\t@Override\n");
		out.append("\t\t\tpublic void execute() {\n");
		out.append("\t\t\t\ttry {\n");
		appendThreadedCore(out, model, method.params, method.name, method.ignoreWhenState, method.wakeLock, method.trace, "void", "\t\t\t\t\t");
		out.append("\t\t\t\t} catch (final Throwable e) {\n");
		out.append("\t\t\t\t\tThread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);\n");
		out.append("\t\t\t\t}\n");
		out.append("\t\t\t}\n");
		out.append("\t\t});\n");
		out.append("\t}\n\n");
	}

	private void appendUiThreadMethod(StringBuilder out, MvpModel model, MvpModel.MethodInfo method) {
		out.append("\t@Override\n");
		out.append("\t").append(method.visibility).append(" void ").append(method.name).append("(");
		appendParams(out, method.params, true);
		out.append(") {\n");
		appendSupposePrefix(out, method.suppose, "\t\t");
		if (method.delay == 0L && method.reusePropagation) {
			out.append("\t\tif (Thread.currentThread() == android.os.Looper.getMainLooper().getThread()) {\n");
			appendThreadedCoreBraced(out, model, method.params, method.name, method.ignoreWhenState, method.wakeLock, method.trace, "void", "\t\t\t");
			out.append("\t\t\treturn;\n");
			out.append("\t\t}\n");
		}

		out.append("\t\torg.androidannotations.api.UiThreadExecutor.runTask(\"").append(escape(method.id))
				.append("\", new Runnable() {\n");
		out.append("\t\t\t@Override\n");
		out.append("\t\t\tpublic void run() {\n");
		appendThreadedCoreBraced(out, model, method.params, method.name, method.ignoreWhenState, method.wakeLock, method.trace, "void", "\t\t\t\t");
		out.append("\t\t\t}\n");
		out.append("\t\t}, ").append(method.delay).append("L);\n");
		out.append("\t}\n\n");
	}

	/**
	 * jcodemodel parity: inserting a body whose top level declares local variables (the wakeLock block) into an
	 * existing block forces a wrapping {@code { ... }}; plain trace/super bodies stay inline.
	 */
	private void appendThreadedCoreBraced(StringBuilder out, MvpModel model, List<MvpModel.Param> params, String name, String ignoreWhen,
			MvpModel.WakeLockSpec wakeLock, MvpModel.TraceSpec trace, String returnType, String indent) {
		if (wakeLock != null) {
			out.append(indent).append("{\n");
			appendThreadedCore(out, model, params, name, ignoreWhen, wakeLock, trace, returnType, indent + "\t");
			out.append(indent).append("}\n");
		} else {
			appendThreadedCore(out, model, params, name, ignoreWhen, wakeLock, trace, returnType, indent);
		}
	}


	private void appendWrappedMethod(StringBuilder out, MvpModel model, MvpModel.WrappedMethod method) {
		String ret = method.returnType == null || method.returnType.isEmpty() ? "void" : method.returnType;
		out.append("\t@Override\n");
		out.append("\t").append(method.visibility == null ? "" : method.visibility).append(method.visibility == null || method.visibility.isEmpty() ? "" : " ")
				.append(ret).append(" ").append(method.name).append("(");
		// @Transactional parameters keep canonical names. Other wrappers stay on the existing simple-name form.
		appendParams(out, method.params, true, !method.transactional);
		out.append(") {\n");
		if (method.transactional) {
			appendTransactionalBody(out, model, method.name, method.params, ret, "\t\t");
		} else {
			appendSupposePrefix(out, method.suppose, "\t\t");
			appendThreadedCore(out, model, method.params, method.name, null, method.wakeLock, method.trace, ret, "\t\t");
		}
		out.append("\t}\n\n");
	}

	/** APT InstanceStateHandler parity: onSaveInstanceState override + restoreSavedInstanceState_ helper. */
/** APT ExtraParameterHandler parity: @OnActivityResult extra key constants (first declaration wins). */
	private void appendActivityResultConstants(StringBuilder out, MvpModel model) {
		for (MvpModel.ExtraConstant constant : model.oarExtraConstants) {
			out.append("\tpublic final static String ").append(constant.constantName).append(" = \"").append(constant.key).append("\";\n");
		}
	}

	/** APT OnActivityResultDelegate parity: generated onActivityResult override, one case per requestCode. */
	private void appendActivityResultMethods(StringBuilder out, MvpModel model) {
		if (model.activityResults.isEmpty()) {
			return;
		}
		out.append("\t@Override\n");
		if (model.activityResultUnchecked) {
			out.append("\t@SuppressWarnings(\"unchecked\")\n");
		}
		out.append("\tpublic void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {\n");
		out.append("\t\tsuper.onActivityResult(requestCode, resultCode, data);\n");
		out.append("\t\tswitch (requestCode) {\n");
		for (MvpModel.ActivityResultGroup group : model.activityResults) {
			out.append("\t\t\tcase  ").append(group.requestCode).append(" :\n");
			out.append("\t\t\t{\n");
			for (MvpModel.ActivityResultMethod method : group.methods) {
				String invocation = model.generatedSimpleName() + ".this." + method.methodName + "(" + String.join(", ", method.argExprs) + ");";
				if (method.extraDecls.isEmpty()) {
					out.append("\t\t\t\t").append(invocation).append("\n");
				} else {
					out.append("\t\t\t\t{\n");
					out.append("\t\t\t\t\tandroid.os.Bundle extras_ = (((data!= null)&&(data.getExtras()!= null))?data.getExtras():new android.os.Bundle());\n");
					for (String decl : method.extraDecls) {
						out.append("\t\t\t\t\t").append(decl).append("\n");
					}
					out.append("\t\t\t\t\t").append(invocation).append("\n");
					out.append("\t\t\t\t}\n");
				}
			}
			out.append("\t\t\t\tbreak;\n");
			out.append("\t\t\t}\n");
		}
		out.append("\t\t}\n");
		out.append("\t}\n\n");
	}

	private void appendInstanceStateMethods(StringBuilder out, MvpModel model) {
		if (model.instanceStates.isEmpty()) {
			return;
		}
		out.append("\t@Override\n");
		out.append("\tpublic void onSaveInstanceState(android.os.Bundle bundle_) {\n");
		out.append("\t\tsuper.onSaveInstanceState(bundle_);\n");
		for (MvpModel.InstanceState instanceState : model.instanceStates) {
			out.append("\t\t").append(instanceState.saveStmt).append("\n");
		}
		out.append("\t}\n\n");

		boolean unchecked = false;
		for (MvpModel.InstanceState instanceState : model.instanceStates) {
			unchecked |= instanceState.unchecked;
		}
		if (unchecked) {
			out.append("\t@SuppressWarnings(\"unchecked\")\n");
		}
		out.append("\tprivate void restoreSavedInstanceState_(android.os.Bundle savedInstanceState) {\n");
		out.append("\t\tif (savedInstanceState == null) {\n");
		out.append("\t\t\treturn;\n");
		out.append("\t\t}\n");
		for (MvpModel.InstanceState instanceState : model.instanceStates) {
			out.append("\t\t").append(instanceState.restoreStmt).append("\n");
		}
		out.append("\t}\n\n");
	}



	private void appendParams(StringBuilder out, java.util.List<MvpModel.Param> params, boolean makeFinal) {
		appendParams(out, params, makeFinal, true);
	}

	private void appendParams(StringBuilder out, java.util.List<MvpModel.Param> params, boolean makeFinal, boolean simpleNames) {
		appendParams(out, params, makeFinal, simpleNames, true);
	}

	private void appendParams(StringBuilder out, java.util.List<MvpModel.Param> params, boolean makeFinal, boolean simpleNames, boolean annotateNullability) {
		for (int i = 0; i < params.size(); i++) {
			if (i > 0) {
				out.append(", ");
			}
			MvpModel.Param param = params.get(i);
			if (annotateNullability && param.nullAnnotation != null) {
				out.append("@org.jetbrains.annotations.").append(param.nullAnnotation).append(' ');
			}
			if (makeFinal) {
				out.append("final ");
			}
			String type = simpleNames ? toSimpleName(param.typeName) : param.typeName;
			out.append(type).append(' ').append(param.name);
		}
	}

	private void appendParamNames(StringBuilder out, java.util.List<MvpModel.Param> params) {
		for (int i = 0; i < params.size(); i++) {
			if (i > 0) {
				out.append(", ");
			}
			out.append(params.get(i).name);
		}
	}

	private String escape(String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
	}


	/** APT parity: generated builder/param types use simple names (kapt imports resolve them); generics and arrays preserved. */
	private static String toSimpleName(String type) {
		if (type == null || type.isEmpty()) { return type; }
		String base = type;
		StringBuilder suffix = new StringBuilder();
		while (base.endsWith("[]")) { suffix.insert(0, "[]"); base = base.substring(0, base.length() - 2); }
		int lt = topLevelAngle(base);
		String head;
		if (lt < 0) {
			head = simpleHead(base);
		} else {
			StringBuilder args = new StringBuilder();
			for (String arg : splitTopLevelArgs(base.substring(lt + 1, base.length() - 1))) {
				if (args.length() > 0) { args.append(", "); }
				args.append(toSimpleName(arg.trim()));
			}
			head = simpleHead(base.substring(0, lt)) + "<" + args + ">";
		}
		return head + suffix;
	}

	private static int topLevelAngle(String type) {
		int depth = 0;
		for (int i = 0; i < type.length(); i++) {
			char c = type.charAt(i);
			if (c == '<') { if (depth == 0) { return i; } depth++; } else if (c == '>') { depth--; }
		}
		return -1;
	}

	private static List<String> splitTopLevelArgs(String args) {
		List<String> result = new ArrayList<>();
		int depth = 0; int start = 0;
		for (int i = 0; i < args.length(); i++) {
			char c = args.charAt(i);
			if (c == '<') { depth++; } else if (c == '>') { depth--; }
			else if (c == ',' && depth == 0) { result.add(args.substring(start, i)); start = i + 1; }
		}
		result.add(args.substring(start));
		return result;
	}

	private void appendPowerManagerField(StringBuilder out, MvpModel model) {
		if (model.usesWakeLock()) {
			out.append("\tprivate android.os.PowerManager powerManager_;\n");
		}
	}

	private void appendPowerManagerInit(StringBuilder out, MvpModel model) {
		if (!model.usesWakeLock()) {
			return;
		}
		out.append("\t\tpowerManager_ = ((android.os.PowerManager) ").append(contextRef(model.kind))
				.append(".getSystemService(android.content.Context.POWER_SERVICE));\n");
	}

	/**
	 * CorePlugin order, from the outside in: Suppose is prepended by the caller because those handlers run after
	 * UiThread/Background. This method then applies WakeLock around Trace around IgnoreWhen around {@code super}.
	 */
	private void appendThreadedCore(StringBuilder out, MvpModel model, List<MvpModel.Param> params, String name, String ignoreWhen,
			MvpModel.WakeLockSpec wakeLock, MvpModel.TraceSpec trace, String returnType, String indent) {
		if (wakeLock != null) {
			List<String> levelOps = new ArrayList<>();
			levelOps.add("android.os.PowerManager." + wakeLock.level);
			for (String flag : wakeLock.flags) {
				levelOps.add("android.os.PowerManager." + flag);
			}
			// jcodemodel parity: an OR chain of several operands is rendered parenthesized.
			String level = levelOps.size() > 1 ? "(" + String.join("|", levelOps) + ")" : levelOps.get(0);
			out.append(indent).append("android.os.PowerManager.WakeLock wakeLock = null;\n");
			out.append(indent).append("try {\n");
			out.append(indent).append("\twakeLock = powerManager_.newWakeLock(").append(level).append(", \"").append(escape(wakeLock.tag)).append("\");\n");
			out.append(indent).append("\twakeLock.acquire();\n");
			appendTraceOrSuper(out, model, params, name, ignoreWhen, trace, returnType, indent + "\t");
			out.append(indent).append("} finally {\n");
			out.append(indent).append("\tif (wakeLock != null) {\n");
			out.append(indent).append("\t\twakeLock.release();\n");
			out.append(indent).append("\t}\n");
			out.append(indent).append("}\n");
			return;
		}
		appendTraceOrSuper(out, model, params, name, ignoreWhen, trace, returnType, indent);
	}

	private void appendTraceOrSuper(StringBuilder out, MvpModel model, List<MvpModel.Param> params, String name, String ignoreWhen,
			MvpModel.TraceSpec trace, String returnType, String indent) {
		if (trace == null) {
			appendSuperStatement(out, model, params, name, ignoreWhen, returnType, indent, true);
			return;
		}
		String ret = returnType == null ? "void" : returnType;
		boolean voidReturn = "void".equals(ret);
		out.append(indent).append("if (android.util.Log.isLoggable(\"").append(escape(trace.tag)).append("\", android.util.Log.").append(trace.logConstant).append(")) {\n");
		String then = indent + "\t";
		out.append(then).append("android.util.Log.").append(trace.logMethod).append("(\"").append(escape(trace.tag)).append("\", ");
		appendTraceEnter(out, trace, name, params);
		out.append(");\n");
		out.append(then).append("long traceStart_ = java.lang.System.currentTimeMillis();\n");
		if (voidReturn) {
			out.append(then).append("try {\n");
			appendSuperStatement(out, model, params, name, ignoreWhen, ret, then + "\t", true);
			out.append(then).append("} finally {\n");
			out.append(then).append("\tlong traceDuration_ = (java.lang.System.currentTimeMillis() - traceStart_);\n");
			out.append(then).append("\tandroid.util.Log.").append(trace.logMethod).append("(\"").append(escape(trace.tag)).append("\", java.lang.String.format(\"Exiting [")
					.append(escape(traceSignature(trace, name, params))).append("], duration in ms: %d\", traceDuration_));\n");
			out.append(then).append("}\n");
		} else {
			out.append(then).append("java.lang.Object traceResult_ = null;\n");
			out.append(then).append("try {\n");
			out.append(then).append("\ttraceResult_ = ").append(superCall(model, name, params)).append(";\n");
			out.append(then).append("\treturn (").append(boxType(ret)).append(") traceResult_;\n");
			out.append(then).append("} finally {\n");
			out.append(then).append("\tlong traceDuration_ = (java.lang.System.currentTimeMillis() - traceStart_);\n");
			out.append(then).append("\tandroid.util.Log.").append(trace.logMethod).append("(\"").append(escape(trace.tag)).append("\", java.lang.String.format(\"Exiting [")
					.append(escape(traceSignature(trace, name, params))).append(" returning: %s], duration in ms: %d\", traceResult_, traceDuration_));\n");
			out.append(then).append("}\n");
		}
		out.append(indent).append("} else {\n");
		appendSuperStatement(out, model, params, name, ignoreWhen, ret, indent + "\t", true);
		out.append(indent).append("}\n");
	}

	private void appendTraceEnter(StringBuilder out, MvpModel.TraceSpec trace, String name, List<MvpModel.Param> params) {
		String shown = trace.returnType + " " + name;
		if (params == null || params.isEmpty()) {
			out.append("\"Entering [").append(shown).append("()]\"");
			return;
		}
		StringBuilder pattern = new StringBuilder("Entering [").append(shown).append("(");
		for (int i = 0; i < params.size(); i++) {
			if (i > 0) {
				pattern.append(", ");
			}
			pattern.append(params.get(i).name).append(" = %s");
		}
		pattern.append(")]");
		out.append("java.lang.String.format(\"").append(pattern).append("\"");
		for (MvpModel.Param param : params) {
			out.append(", ");
			if (param.typeName != null && param.typeName.endsWith("[]")) {
				out.append("java.util.Arrays.toString(").append(param.name).append(")");
			} else {
				out.append(param.name);
			}
		}
		out.append(")");
	}

	private static String traceSignature(MvpModel.TraceSpec trace, String name, List<MvpModel.Param> params) {
		StringBuilder signature = new StringBuilder();
		signature.append(trace.returnType).append(" ").append(name).append("(");
		if (params != null) {
			for (int i = 0; i < params.size(); i++) {
				if (i > 0) {
					signature.append(", ");
				}
				signature.append(toSimpleName(params.get(i).typeName));
			}
		}
		signature.append(")");
		return signature.toString();
	}

	private void appendSuperStatement(StringBuilder out, MvpModel model, List<MvpModel.Param> params, String name, String ignoreWhen,
			String returnType, String indent, boolean asStatement) {
		String call = superCall(model, name, params);
		boolean voidReturn = returnType == null || "void".equals(returnType);
		if (ignoreWhen != null) {
			out.append(indent).append("if (").append(ignoreWhenCondition(model, ignoreWhen)).append(") {\n");
			out.append(indent).append("\t");
			if (!voidReturn) {
				out.append("return ");
			}
			out.append(call).append(";\n");
			out.append(indent).append("}\n");
			return;
		}
		out.append(indent);
		if (!voidReturn && asStatement) {
			out.append("return ");
		}
		out.append(call).append(";\n");
	}

	private static String superCall(MvpModel model, String name, List<MvpModel.Param> params) {
		StringBuilder call = new StringBuilder();
		call.append(model.generatedSimpleName()).append(".super.").append(name).append("(");
		if (params != null) {
			for (int i = 0; i < params.size(); i++) {
				if (i > 0) {
					call.append(", ");
				}
				call.append(params.get(i).name);
			}
		}
		call.append(")");
		return call.toString();
	}

	private static String boxType(String type) {
		if ("boolean".equals(type)) {
			return "java.lang.Boolean";
		}
		if ("int".equals(type)) {
			return "java.lang.Integer";
		}
		if ("long".equals(type)) {
			return "java.lang.Long";
		}
		if ("float".equals(type)) {
			return "java.lang.Float";
		}
		if ("double".equals(type)) {
			return "java.lang.Double";
		}
		if ("short".equals(type)) {
			return "java.lang.Short";
		}
		if ("byte".equals(type)) {
			return "java.lang.Byte";
		}
		if ("char".equals(type)) {
			return "java.lang.Character";
		}
		return type;
	}

	private void appendSupposePrefix(StringBuilder out, MvpModel.SupposeSpec suppose, String indent) {
		if (suppose == null) {
			return;
		}
		if (suppose.uiThread) {
			out.append(indent).append("org.androidannotations.api.BackgroundExecutor.checkUiThread();\n");
			return;
		}
		out.append(indent).append("org.androidannotations.api.BackgroundExecutor.checkBgThread(");
		if (suppose.serial != null) {
			for (int i = 0; i < suppose.serial.size(); i++) {
				if (i > 0) {
					out.append(", ");
				}
				out.append("\"").append(escape(suppose.serial.get(i))).append("\"");
			}
		}
		out.append(");\n");
	}



	private void appendSeekListeners(StringBuilder out, MvpModel model) {
		appendGroupedCallback(out, model, model.seekListeners, "android.widget.SeekBar", "setOnSeekBarChangeListener",
				"android.widget.SeekBar.OnSeekBarChangeListener", true);
	}

	private void appendPageListeners(StringBuilder out, MvpModel model) {
		if (model.pageListeners.isEmpty()) {
			return;
		}
		String type = model.viewPagerType != null ? model.viewPagerType : "androidx.viewpager.widget.ViewPager";
		appendGroupedCallback(out, model, model.pageListeners, type, "addOnPageChangeListener", type + ".OnPageChangeListener", false);
	}

	private void appendGroupedCallback(StringBuilder out, MvpModel model, List<MvpModel.ViewListener> listeners, String viewType,
			String setter, String listenerType, boolean seekBar) {
		if (listeners.isEmpty()) {
			return;
		}
		LinkedHashMap<String, String> exprs = new LinkedHashMap<String, String>();
		LinkedHashMap<String, List<MvpModel.ViewListener>> groups = new LinkedHashMap<String, List<MvpModel.ViewListener>>();
		for (MvpModel.ViewListener listener : listeners) {
			for (Integer resId : listener.valueIds) {
				String rName = rClassIndex != null ? rClassIndex.fieldNameForValue("id", resId) : null;
				if (rName == null) {
					rName = Integer.toString(resId);
				}
				addTextGroup(groups, exprs, rName, KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", rName, resId, null, rClassIndex), listener);
			}
			for (String resName : listener.nameIds) {
				String rName = rClassIndex != null ? rClassIndex.resolveFieldName("id", resName) : null;
				if (rName == null) {
					rName = resName;
				}
				addTextGroup(groups, exprs, rName, KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", rName, AnnotationNames.RES_DEFAULT, resName, rClassIndex), listener);
			}
		}
		for (Map.Entry<String, List<MvpModel.ViewListener>> group : groups.entrySet()) {
			String castType = viewType;
			if (!seekBar) {
				for (MvpModel.ViewListener listener : group.getValue()) {
					for (int i = 0; i < listener.roles.size(); i++) {
						if (listener.roles.get(i) == MvpModel.ParamRole.VIEW_PAGER) {
							castType = listener.params.get(i).typeName;
						}
					}
				}
			}
			if (seekBar) {
				String viewName = "view_" + group.getKey();
				out.append("\t\t").append(castType).append(" ").append(viewName).append(" = hasViews.internalFindViewById(")
						.append(exprs.get(group.getKey())).append(");\n");
				out.append("\t\tif (").append(viewName).append(" != null) {\n");
				out.append("\t\t\t").append(viewName).append(".").append(setter).append("(new ").append(listenerType).append("() {\n");
			} else {
				out.append("\t\t{\n");
				out.append("\t\t\tfinal ").append(castType).append(" view = ((").append(castType).append(") hasViews.internalFindViewById(")
						.append(exprs.get(group.getKey())).append("));\n");
				out.append("\t\t\tif (view != null) {\n");
				out.append("\t\t\t\tview.").append(setter).append("(new ").append(listenerType).append("() {\n");
			}
			if (seekBar) {
				appendSeekCallback(out, model, group.getValue(), MvpModel.ListenerKind.SEEK_PROGRESS,
						"public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser)", 4);
				appendSeekCallback(out, model, group.getValue(), MvpModel.ListenerKind.SEEK_START,
						"public void onStartTrackingTouch(android.widget.SeekBar seekBar)", 4);
				appendSeekCallback(out, model, group.getValue(), MvpModel.ListenerKind.SEEK_STOP,
						"public void onStopTrackingTouch(android.widget.SeekBar seekBar)", 4);
			} else {
				appendSeekCallback(out, model, group.getValue(), MvpModel.ListenerKind.PAGE_SCROLL_STATE,
						"public void onPageScrollStateChanged(int state)", 5);
				appendSeekCallback(out, model, group.getValue(), MvpModel.ListenerKind.PAGE_SCROLLED,
						"public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels)", 5);
				appendSeekCallback(out, model, group.getValue(), MvpModel.ListenerKind.PAGE_SELECTED,
						"public void onPageSelected(int position)", 5);
			}
			if (seekBar) {
				out.append("\t\t\t});\n");
				out.append("\t\t}\n");
			} else {
				out.append("\t\t\t\t});\n");
				out.append("\t\t\t}\n");
				out.append("\t\t}\n");
			}
		}
	}

	private void appendSeekCallback(StringBuilder out, MvpModel model, List<MvpModel.ViewListener> listeners, MvpModel.ListenerKind kind, String signature, int baseIndent) {
		StringBuilder indent = new StringBuilder();
		for (int i = 0; i < baseIndent; i++) {
			indent.append('\t');
			}
		out.append(indent).append("@Override\n");
		out.append(indent).append(signature).append(" {\n");
		for (MvpModel.ViewListener listener : listeners) {
			if (listener.kind != kind) {
				continue;
				}
			out.append(indent).append('\t').append(qualifiedCall(model, listener, groupedArgs(listener))).append(";\n");
		}
		out.append(indent).append("}\n");
	}

	private static List<String> groupedArgs(MvpModel.ViewListener listener) {
		List<String> args = new ArrayList<String>();
		for (MvpModel.ParamRole role : listener.roles) {
			switch (role) {
			case SEEK_BAR:
				args.add("seekBar");
				break;
			case PROGRESS:
			case POSITION:
				args.add(role == MvpModel.ParamRole.PROGRESS ? "progress" : "position");
				break;
			case FROM_USER:
				args.add("fromUser");
				break;
			case VIEW_PAGER:
				args.add("view");
				break;
			case POSITION_OFFSET:
				args.add("positionOffset");
				break;
			case OFFSET_PIXELS:
				args.add("positionOffsetPixels");
				break;
			case SCROLL_STATE:
				args.add("state");
				break;
			default:
				break;
			}
		}
		return args;
	}

	private void appendKeyCallbacks(StringBuilder out, MvpModel model) {
		appendKeyMethod(out, model, MvpModel.KeyKind.DOWN, "onKeyDown", false);
		appendKeyMethod(out, model, MvpModel.KeyKind.LONG_PRESS, "onKeyLongPress", false);
		appendKeyMethod(out, model, MvpModel.KeyKind.MULTIPLE, "onKeyMultiple", true);
		appendKeyMethod(out, model, MvpModel.KeyKind.UP, "onKeyUp", false);
	}

	private void appendKeyMethod(StringBuilder out, MvpModel model, MvpModel.KeyKind kind, String callback, boolean multiple) {
		List<MvpModel.KeyBinding> bindings = new ArrayList<MvpModel.KeyBinding>();
		for (MvpModel.KeyBinding binding : model.keyBindings) {
			if (binding.kind == kind) {
				bindings.add(binding);
			}
		}
		if (bindings.isEmpty()) {
			return;
		}
		out.append("\t@Override\n");
		out.append("\tpublic boolean ").append(callback).append("(int keyCode, ");
		if (multiple) {
			out.append("int count, ");
		}
		out.append("android.view.KeyEvent keyEvent) {\n");
		out.append("\t\tswitch (keyCode) {\n");
		for (MvpModel.KeyBinding binding : bindings) {
			for (Integer code : binding.keyCodes) {
				String constant = org.androidannotations.ksp.KeyCodes.nameForCode(code.intValue());
				out.append("\t\tcase android.view.KeyEvent.").append(constant).append(":\n");
				out.append("\t\t{\n");
				StringBuilder call = new StringBuilder(binding.methodName).append("(");
				for (int i = 0; i < binding.roles.size(); i++) {
					if (i > 0) {
						call.append(", ");
					}
					call.append(binding.roles.get(i) == MvpModel.ParamRole.INT ? "count" : "keyEvent");
				}
				call.append(")");
				if (binding.returnsBoolean) {
					out.append("\t\t\treturn ").append(call).append(";\n");
				} else {
					out.append("\t\t\t").append(call).append(";\n");
					out.append("\t\t\treturn true;\n");
				}
				out.append("\t\t}\n");
			}
		}
		out.append("\t\t}\n");
		out.append("\t\treturn super.").append(callback).append("(keyCode, ");
		if (multiple) {
			out.append("count, ");
		}
		out.append("keyEvent);\n");
		out.append("\t}\n\n");
	}

	private void appendRootInjections(StringBuilder out, MvpModel model) {
		if (model.roots.isEmpty()) {
			return;
		}
		String tag = model.generatedSimpleName();
		if (tag.length() > 23) {
			tag = tag.substring(0, 23);
		}
		for (MvpModel.RootInjection root : model.roots) {
			String ref = root.fragment ? "rootFragment_" : "context_";
			String simple = toSimpleName(root.typeName);
			if (root.plainContext) {
				if (root.fieldForm) {
					appendAssign(out, root.name, root.jvmField, ref);
				} else {
					out.append("\t\t").append(root.name).append("(").append(ref).append(");\n");
				}
				continue;
			}
			if (root.fieldForm) {
				out.append("\t\tif (").append(ref).append(" instanceof ").append(root.typeName).append(") {\n");
				appendAssign(out, root.name, root.jvmField, "((" + root.typeName + ") " + ref + ")", "\t\t\t");
				if (root.fragment) {
					out.append("\t\t} else {\n");
					out.append("\t\t\tif (").append(ref).append("!= null) {\n");
					out.append("\t\t\t\tandroid.util.Log.w(\"").append(escape(tag)).append("\", ((\"Due to class \" + ").append(ref)
							.append(".getClass().getSimpleName())+\", the @RootFragment ").append(simple).append(" won't be populated\"));\n");
					out.append("\t\t\t} else {\n");
					out.append("\t\t\t\tandroid.util.Log.w(\"").append(escape(tag)).append("\", \"Due to not having a rootFragment reference the @RootFragment ")
							.append(simple).append(" won't be populated\");\n");
					out.append("\t\t\t}\n");
					out.append("\t\t}\n");
				} else {
					out.append("\t\t} else {\n");
					out.append("\t\t\tandroid.util.Log.w(\"").append(escape(tag)).append("\", ((\"Due to Context class \" + ").append(ref)
							.append(".getClass().getSimpleName())+\", the @RootContext ").append(simple).append(" won't be populated\"));\n");
					out.append("\t\t}\n");
				}
			} else {
				String local = root.fragment ? "rootFragmentArg_" : "rootContextArg_";
				out.append("\t\t").append(root.typeName).append(" ").append(local).append(" = null;\n");
				out.append("\t\tif (").append(ref).append(" instanceof ").append(root.typeName).append(") {\n");
				out.append("\t\t\t").append(local).append(" = (").append(root.typeName).append(") ").append(ref).append(";\n");
				out.append("\t\t} else {\n");
				out.append("\t\t\tandroid.util.Log.w(\"").append(escape(tag)).append("\", \"@").append(root.fragment ? "RootFragment" : "RootContext")
						.append(" ").append(simple).append(" won't be populated\");\n");
				out.append("\t\t}\n");
				out.append("\t\t").append(root.name).append("(").append(local).append(");\n");
			}
		}
	}

	private String generateReceiver(MvpModel model) {
		StringBuilder out = new StringBuilder();
		appendHeader(out, model);
		appendClassAnnotations(out, model);
		out.append("public final class ").append(model.generatedSimpleName()).append(" extends ").append(model.simpleName).append(" {\n\n");
		java.util.LinkedHashSet<String> emittedActionConstants = new java.util.LinkedHashSet<>();
		for (MvpModel.ReceiverActionBinding action : model.receiverActions) {
			appendLiteralField(out, action.actionsField, action.actions, action.actionsList);
			if (action.schemesField != null) {
				appendLiteralField(out, action.schemesField, action.dataSchemes, action.schemesList);
			}
			for (MvpModel.ReceiverArg arg : action.args) {
				if (arg.role == MvpModel.ReceiverArg.Role.EXTRA && emittedActionConstants.add(arg.extraConstant)) {
					out.append("\tpublic final static String ").append(arg.extraConstant).append(" = \"").append(escape(arg.extraKey)).append("\";\n");
				}
			}
		}
		appendPowerManagerField(out, model);
		out.append("\n");
		boolean unchecked = false;
		for (MvpModel.ReceiverActionBinding action : model.receiverActions) {
			unchecked |= action.unchecked;
		}
		out.append("\t@Override\n");
		if (unchecked) {
			out.append("\t@SuppressWarnings(\"unchecked\")\n");
		}
		out.append("\tpublic void onReceive(android.content.Context context, android.content.Intent intent) {\n");
		out.append("\t\tinit_(context);\n");
		out.append("\t\tsuper.onReceive(context, intent);\n");
		if (!model.receiverActions.isEmpty()) {
			out.append("\t\tString action = intent.getAction();\n");
			boolean schemes = false;
			for (MvpModel.ReceiverActionBinding action : model.receiverActions) {
				schemes |= action.schemesField != null;
			}
			if (schemes) {
				out.append("\t\tString dataScheme = intent.getScheme();\n");
			}
			for (MvpModel.ReceiverActionBinding action : model.receiverActions) {
				String test = action.actionsField + "." + (action.actionsList ? "contains" : "equals") + "(action)";
				if (action.schemesField != null) {
					test = test + " && " + action.schemesField + "." + (action.schemesList ? "contains" : "equals") + "(dataScheme)";
				}
				out.append("\t\tif (").append(test).append(") {\n");
				boolean hasExtra = false;
				for (MvpModel.ReceiverArg arg : action.args) {
					hasExtra |= arg.role == MvpModel.ReceiverArg.Role.EXTRA;
				}
				if (hasExtra) {
					out.append("\t\t\tandroid.os.Bundle extras_ = ((intent.getExtras() != null) ? intent.getExtras() : new android.os.Bundle());\n");
					for (MvpModel.ReceiverArg arg : action.args) {
						if (arg.role == MvpModel.ReceiverArg.Role.EXTRA) {
							out.append("\t\t\t").append(toSimpleName(arg.typeName)).append(" ").append(arg.name).append(" = ").append(arg.readExpr).append(";\n");
						}
					}
				}
				out.append("\t\t\t").append(model.generatedSimpleName()).append(".this.").append(action.methodName).append("(");
				for (int i = 0; i < action.args.size(); i++) {
					if (i > 0) {
						out.append(", ");
					}
					MvpModel.ReceiverArg arg = action.args.get(i);
					if (arg.role == MvpModel.ReceiverArg.Role.CONTEXT) {
						out.append("context");
					} else if (arg.role == MvpModel.ReceiverArg.Role.INTENT) {
						out.append("intent");
					} else {
						out.append(arg.name);
					}
				}
				out.append(");\n");
				out.append("\t\t\treturn;\n");
				out.append("\t\t}\n");
			}
		}
		out.append("\t}\n\n");
		appendThreadMethods(out, model);
		appendNestedClasses(out, model);
		out.append("\tprivate void init_(android.content.Context context) {\n");
		appendPowerManagerInit(out, model);
		appendContextInjections(out, model);
		out.append("\t}\n\n");
		out.append("}\n");
		return out.toString();
	}

	private String generateIntentService(MvpModel model) {
		StringBuilder out = new StringBuilder();
		appendHeader(out, model);
		appendClassAnnotations(out, model);
		out.append("public final class ").append(model.generatedSimpleName()).append(" extends ").append(model.simpleName).append(" {\n\n");
		java.util.LinkedHashSet<String> emitted = new java.util.LinkedHashSet<String>();
		for (MvpModel.ServiceActionBinding action : model.serviceActions) {
			out.append("\tpublic static final String ").append(action.actionField).append(" = \"").append(escape(action.actionValue)).append("\";\n");
			for (MvpModel.ServiceActionParam param : action.params) {
				if (emitted.add(param.constantName)) {
					out.append("\tpublic static final String ").append(param.constantName).append(" = \"").append(escape(param.name)).append("\";\n");
				}
			}
		}
		appendIntentStaticMethod(out, model);
		appendPowerManagerField(out, model);
		out.append("\n");
		StringBuilder initBody = new StringBuilder();
		appendPowerManagerInit(initBody, model);
		appendContextInjections(initBody, model);
		if (initBody.length() > 0) {
			out.append("\tprivate void init_() {\n");
			out.append(initBody.toString());
			out.append("\t}\n\n");
			out.append("\t@Override\n");
			out.append("\tpublic void onCreate() {\n");
			out.append("\t\tinit_();\n");
			out.append("\t\tsuper.onCreate();\n");
			out.append("\t}\n\n");
		}
		if (!model.serviceActions.isEmpty()) {
			boolean unchecked = false;
			for (MvpModel.ServiceActionBinding action : model.serviceActions) {
				unchecked |= action.unchecked;
			}
			out.append("\t@Override\n");
			if (unchecked) {
				out.append("\t@SuppressWarnings(\"unchecked\")\n");
			}
			out.append("\tpublic void onHandleIntent(android.content.Intent intent) {\n");
			out.append("\t\t").append(model.generatedSimpleName()).append(".super.onHandleIntent(intent);\n");
			out.append("\t\tif (intent == null) {\n");
			out.append("\t\t\treturn;\n");
			out.append("\t\t}\n");
			out.append("\t\tjava.lang.String action = intent.getAction();\n");
			for (MvpModel.ServiceActionBinding action : model.serviceActions) {
				out.append("\t\tif (").append(action.actionField).append(".equals(action)) {\n");
				if (!action.params.isEmpty()) {
					out.append("\t\t\tandroid.os.Bundle extras = intent.getExtras();\n");
					out.append("\t\t\tif (extras != null) {\n");
					for (MvpModel.ServiceActionParam param : action.params) {
						out.append("\t\t\t\t").append(param.typeName).append(" ").append(param.name).append("Extra = ").append(param.readExpr).append(";\n");
					}
					out.append("\t\t\t\tsuper.").append(action.methodName).append("(");
					for (int i = 0; i < action.params.size(); i++) {
						if (i > 0) {
							out.append(", ");
						}
						out.append(action.params.get(i).name).append("Extra");
					}
					out.append(");\n");
					out.append("\t\t\t\treturn;\n");
					out.append("\t\t\t}\n");
				} else {
					out.append("\t\t\tsuper.").append(action.methodName).append("();\n");
					out.append("\t\t\treturn;\n");
				}
				out.append("\t\t}\n");
			}
			out.append("\t}\n\n");
		}
		appendThreadMethods(out, model);
		String generated = model.generatedSimpleName();
		out.append("\tpublic static class IntentBuilder_ extends org.androidannotations.api.builder.ServiceIntentBuilder<").append(generated).append(".IntentBuilder_> {\n\n");
		out.append("\t\tpublic IntentBuilder_(android.content.Context context) {\n");
		out.append("\t\t\tsuper(context, ").append(generated).append(".class);\n");
		out.append("\t\t}\n\n");
		for (MvpModel.ServiceActionBinding action : model.serviceActions) {
			out.append("\t\t/**\n");
			out.append("\t\t * @return\n");
			out.append("\t\t *     the IntentBuilder to chain calls\n");
			out.append("\t\t */\n");
			out.append("\t\tpublic ").append(generated).append(".IntentBuilder_ ").append(action.methodName).append("(");
			for (int i = 0; i < action.params.size(); i++) {
				if (i > 0) {
					out.append(", ");
				}
				MvpModel.ServiceActionParam param = action.params.get(i);
				out.append(toSimpleName(param.typeName)).append(" ").append(param.name);
			}
			out.append(") {\n");
			out.append("\t\t\taction(").append(action.actionField).append(");\n");
			for (MvpModel.ServiceActionParam param : action.params) {
				out.append("\t\t\tsuper.extra(").append(param.constantName).append(", ").append(param.writeExpr.replace("{arg}", param.name)).append(");\n");
			}
			out.append("\t\t\treturn this;\n");
			out.append("\t\t}\n\n");
		}
		out.append("\t}\n\n");
		appendNestedClasses(out, model);
		out.append("}\n");
		return out.toString();
	}

	private String generateProvider(MvpModel model) {
		StringBuilder out = new StringBuilder();
		appendHeader(out, model);
		appendClassAnnotations(out, model);
		out.append("public final class ").append(model.generatedSimpleName()).append(" extends ").append(model.simpleName).append(" {\n\n");
		appendPowerManagerField(out, model);
		out.append("\tprivate void init_() {\n");
		appendPowerManagerInit(out, model);
		appendContextInjections(out, model);
		out.append("\t}\n\n");
		out.append("\t@Override\n");
		out.append("\tpublic boolean onCreate() {\n");
		out.append("\t\tinit_();\n");
		out.append("\t\treturn super.onCreate();\n");
		out.append("\t}\n\n");
		appendThreadMethods(out, model);
		appendNestedClasses(out, model);
		out.append("}\n");
		return out.toString();
	}

	/** Shared init-body injections for components whose context is {@link #contextRef}. */
	private void appendContextInjections(StringBuilder out, MvpModel model) {
		String context = contextRef(model.kind);
		if (!model.stringRes.isEmpty() || !model.resFields.isEmpty()) {
			out.append("\t\tandroid.content.res.Resources resources_ = ").append(context).append(".getResources();\n");
		}
		for (MvpModel.FieldInjection injection : model.stringRes) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "string", injection.propertyName, injection.resId, injection.resName, rClassIndex);
			appendAssign(out, injection.propertyName, true, "resources_.getString(" + resExpr + ")");
		}
		for (MvpModel.ResField resField : model.resFields) {
			appendResAssign(out, model.kind, resField);
		}
		appendSystemServiceInjections(out, model, context);
		appendAppInjections(out, model);
		appendPrefInjections(out, model);
		appendBeanInjections(out, model);
		appendHttpsClientInjections(out, model);
		for (String method : model.afterInject) {
			out.append("\t\t").append(method).append("();\n");
		}
	}

	private void appendLiteralField(StringBuilder out, String name, List<String> values, boolean list) {
		if (list) {
			out.append("\tpublic final static java.util.List<String> ").append(name).append(" = java.util.Arrays.asList(");
			for (int i = 0; i < values.size(); i++) {
				if (i > 0) {
					out.append(", ");
				}
				out.append("\"").append(escape(values.get(i))).append("\"");
			}
			out.append(");\n");
		} else if (!values.isEmpty()) {
			out.append("\tpublic final static String ").append(name).append(" = \"").append(escape(values.get(0))).append("\";\n");
		}
	}

	private static String simpleHead(String type) {
		int dot = type.lastIndexOf('.');
		return dot < 0 ? type : type.substring(dot + 1);
	}
}

