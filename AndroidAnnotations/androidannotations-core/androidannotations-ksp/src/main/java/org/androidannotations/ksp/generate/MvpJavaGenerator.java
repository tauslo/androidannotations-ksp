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
		if (model.kind == MvpModel.Kind.VIEW || model.kind == MvpModel.Kind.VIEW_GROUP) {
			return generateView(model, false);
		}
		return generateBean(model);
	}

	private String generateActivity(MvpModel model) {
		StringBuilder out = new StringBuilder();
		appendHeader(out, model);
		boolean hasViewSupport = model.hasViewSupport();
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
		appendActivityResultConstants(out, model);
		appendReceiverExtraConstants(out, model);
		appendReceiverFields(out, model);
		out.append("\n");

		out.append("\t@Override\n");
		out.append("\tpublic void onCreate(android.os.Bundle savedInstanceState) {\n");
		out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier previousNotifier = org.androidannotations.api.view.OnViewChangedNotifier.replaceNotifier(onViewChangedNotifier_);\n");
		out.append("\t\tinit_(savedInstanceState);\n");
		out.append("\t\tsuper.onCreate(savedInstanceState);\n");
		out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier.replaceNotifier(previousNotifier);\n");
		appendSetContentViewCall(out, model);
		out.append("\t}\n\n");

		out.append("\t@Override\n");
		out.append("\tpublic <T extends android.view.View> T internalFindViewById(int id) {\n");
		out.append("\t\treturn ((T) this.findViewById(id));\n");
		out.append("\t}\n\n");

		out.append("\tprivate void init_(android.os.Bundle savedInstanceState) {\n");
		appendWindowFeatures(out, model);
		if (!model.stringRes.isEmpty() || !model.resFields.isEmpty()) {
			out.append("\t\tandroid.content.res.Resources resources_ = this.getResources();\n");
		}
		if (hasViewSupport) {
			out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier.registerOnViewChangedListener(this);\n");
		}
		for (MvpModel.FieldInjection injection : model.stringRes) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "string", injection.propertyName, injection.resId, injection.resName, rClassIndex);
			appendAssign(out, injection, "resources_.getString(" + resExpr + ")");
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
		if (hasViewSupport) {
			out.append("\t@Override\n");
			out.append("\tpublic void onViewChanged(org.androidannotations.api.view.HasViews hasViews) {\n");
			ViewInjectionPlan plan = planViewInjections(model);
			out.append(plan.pass1);
			appendViewsById(out, model);
			appendFromHtml(out, model);
			appendViewListeners(out, model, plan);
			out.append(plan.pass3);
			appendFragmentInjections(out, model);
			for (String method : model.afterViews) {
				out.append("\t\t").append(method).append("();\n");
			}
			out.append("\t}\n\n");
		}
		appendReceiverLifecycle(out, model);
		appendNonConfigurationMethods(out, model);
		appendOptionsMenuMethods(out, model);
		appendInstanceStateMethods(out, model);
		appendActivityResultMethods(out, model);
		appendUiThreadMethods(out, model);
		appendBackgroundMethods(out, model);
		appendIntentBuilder(out, model);

		appendNestedClasses(out, model);
		out.append("}\n");
		return out.toString();
	}

	private String generateFragment(MvpModel model) {
		StringBuilder out = new StringBuilder();
		appendHeader(out, model);
		boolean hasViewSupport = model.hasViewSupport();
		out.append("public final class ").append(model.generatedSimpleName())
				.append(" extends ").append(model.qualifiedName)
				.append(" implements org.androidannotations.api.bean.BeanHolder, org.androidannotations.api.view.HasViews")
				.append(hasViewSupport ? ", org.androidannotations.api.view.OnViewChangedListener" : "")
				.append(" {\n\n");

		out.append("\tprivate final org.androidannotations.api.view.OnViewChangedNotifier onViewChangedNotifier_ = new org.androidannotations.api.view.OnViewChangedNotifier();\n");
		out.append("\tprivate android.view.View contentView_;\n");
		if (model.needsViewDestroyed) {
			out.append("\tprivate volatile boolean viewDestroyed_ = true;\n");
		}
		out.append("\tprivate final java.util.HashMap<Class<?>, Object> beans_ = new java.util.HashMap<Class<?>, Object>();\n");
		for (MvpModel.FieldInjection arg : model.fragmentArgs) {
			String key = arg.resName != null && !arg.resName.isEmpty() ? arg.resName : arg.propertyName;
			out.append("\tpublic final static String ").append(KspAnnotationUtils.fragmentArgKeyConstant(arg.propertyName))
					.append(" = \"").append(key).append("\";\n");
		}
		appendActivityResultConstants(out, model);
		appendReceiverExtraConstants(out, model);
		appendReceiverFields(out, model);
		if (!model.fragmentArgs.isEmpty() || !model.oarExtraConstants.isEmpty() || !model.receiverExtraConstants.isEmpty()) {
			out.append("\n");
		}

		out.append("\t@Override\n");
		out.append("\tpublic void onCreate(android.os.Bundle savedInstanceState) {\n");
		out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier previousNotifier = org.androidannotations.api.view.OnViewChangedNotifier.replaceNotifier(onViewChangedNotifier_);\n");
		out.append("\t\tinit_(savedInstanceState);\n");
		out.append("\t\tsuper.onCreate(savedInstanceState);\n");
		appendReceiverRegister(out, model, "OnCreateOnDestroy", "\t\t");
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
		for (MvpModel.ViewInjection injection : model.viewInjections) {
			if (injection.form == MvpModel.ViewInjection.Form.FIELD) {
				out.append("\t\t").append(injection.field.propertyName).append(" = null;\n");
			}
		}
		if (model.needsViewDestroyed) {
			out.append("\t\tviewDestroyed_ = true;\n");
		}
		out.append("\t}\n\n");

		out.append("\tprivate void init_(android.os.Bundle savedInstanceState) {\n");
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
			appendFragmentInjections(out, model);
			for (String method : model.afterViews) {
				out.append("\t\t").append(method).append("();\n");
			}
			out.append("\t}\n\n");
		}

		appendFragmentArgsMethod(out, model);
		appendInstanceStateMethods(out, model);
		appendActivityResultMethods(out, model);
		appendUiThreadMethods(out, model);
		appendBackgroundMethods(out, model);
		appendIgnoreWhenMethods(out, model);
		appendReceiverLifecycle(out, model);
		appendFragmentBuilder(out, model);

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
		appendListenerIds(model, model.editorActionListeners, refs, locals, localTypes, foundTypes, setters);
		appendListenerIds(model, model.itemClickListeners, refs, locals, localTypes, foundTypes, setters);
		appendListenerIds(model, model.itemLongClickListeners, refs, locals, localTypes, foundTypes, setters);
		appendListenerIds(model, model.itemSelectListeners, refs, locals, localTypes, foundTypes, setters);
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
			for (MvpModel.ViewListener listener : group.getValue()) {
				String typed = textViewParamType(listener);
				if (typed != null) {
					viewType = typed;
					break;
				}
			}
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

	private static String textViewParamType(MvpModel.ViewListener listener) {
		for (int i = 0; i < listener.roles.size(); i++) {
			if (listener.roles.get(i) == MvpModel.ParamRole.TEXT_VIEW) {
				return listener.params.get(i).typeName;
			}
		}
		return null;
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
			if (model.forceLayoutInjection) {
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
		out.append("public final class ").append(model.generatedSimpleName())
			.append(" extends ").append(model.simpleName)
			.append(" {\n\n");

		out.append("\tprivate static ").append(model.simpleName).append(" INSTANCE_;\n\n");

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
		if (!model.stringRes.isEmpty() || !model.resFields.isEmpty()) {
			out.append("\t\tandroid.content.res.Resources resources_ = this.getResources();\n");
		}
		for (MvpModel.FieldInjection injection : model.stringRes) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "string", injection.propertyName, injection.resId, injection.resName, rClassIndex);
			appendAssign(out, injection, "resources_.getString(" + resExpr + ")");
		}
		for (MvpModel.ResField resField : model.resFields) {
			appendResAssign(out, MvpModel.Kind.APPLICATION, resField);
		}
		appendSystemServiceInjections(out, model, "this");
		appendAppInjections(out, model);
		appendPrefInjections(out, model);
		appendBeanInjections(out, model);
		for (String method : model.afterInject) {
			out.append("\t\t").append(method).append("();\n");
		}
		out.append("\t}\n\n");

		appendUiThreadMethods(out, model);
		appendBackgroundMethods(out, model);

		appendNestedClasses(out, model);
		out.append("}\n");
		return out.toString();
	}

	/** APT EServiceHolder parity: intent() static factory before init_, onCreate calls init_ then super; nested ServiceIntentBuilder. */
	private String generateService(MvpModel model) {
		StringBuilder out = new StringBuilder();
		appendHeader(out, model);
		out.append("public final class ").append(model.generatedSimpleName())
			.append(" extends ").append(model.simpleName)
			.append(" {\n\n");

		appendIntentStaticMethod(out, model);
		appendReceiverExtraConstants(out, model);
		appendReceiverFields(out, model);

		out.append("\tprivate void init_() {\n");
		if (!model.stringRes.isEmpty() || !model.resFields.isEmpty()) {
			out.append("\t\tandroid.content.res.Resources resources_ = this.getResources();\n");
		}
		for (MvpModel.FieldInjection injection : model.stringRes) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "string", injection.propertyName, injection.resId, injection.resName, rClassIndex);
			appendAssign(out, injection, "resources_.getString(" + resExpr + ")");
		}
		for (MvpModel.ResField resField : model.resFields) {
			appendResAssign(out, MvpModel.Kind.SERVICE, resField);
		}
		appendSystemServiceInjections(out, model, "this");
		appendAppInjections(out, model);
		appendPrefInjections(out, model);
		appendBeanInjections(out, model);
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
		appendUiThreadMethods(out, model);
		appendBackgroundMethods(out, model);

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
			out.append("public static final class ").append(model.generatedSimpleName())
					.append(" extends ").append(model.simpleName)
					.append(implementsClause).append(" {\n\n");
		} else {
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
		out.append("\tprivate Object rootFragment_;\n\n");
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
			out.append("\t\t\torg.androidannotations.api.view.OnViewChangedNotifier previousNotifier = OnViewChangedNotifier.replaceNotifier(null);\n");
			out.append("\t\t\tinstance_ = new ").append(generated).append("(context.getApplicationContext());\n");
			out.append("\t\t\tinstance_.init_();\n");
			out.append("\t\t\tOnViewChangedNotifier.replaceNotifier(previousNotifier);\n");
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
		if (!model.stringRes.isEmpty() || !model.resFields.isEmpty()) {
			out.append("\t\tandroid.content.res.Resources resources_ = context_.getResources();\n");
		}
		for (MvpModel.FieldInjection injection : model.stringRes) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "string", injection.propertyName, injection.resId, injection.resName, rClassIndex);
			appendAssign(out, injection, "resources_.getString(" + resExpr + ")");
		}
		for (MvpModel.ResField resField : model.resFields) {
			appendResAssign(out, model.kind, resField);
		}
		appendPrefInjections(out, model);
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
			for (String method : model.afterViews) {
				out.append("\t\t").append(method).append("();\n");
			}
			out.append("\t}\n\n");
		}

		appendUiThreadMethods(out, model);
		appendBackgroundMethods(out, model);

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
		out.append(nested ? "public static final class " : "public final class ").append(model.generatedSimpleName())
				.append(" extends ").append(extendsName)
				.append(" implements org.androidannotations.api.view.HasViews")
				.append(hasViewSupport ? ", org.androidannotations.api.view.OnViewChangedListener" : "")
				.append(" {\n\n");
		out.append("\tprivate final org.androidannotations.api.view.OnViewChangedNotifier onViewChangedNotifier_ = new org.androidannotations.api.view.OnViewChangedNotifier();\n");
		out.append("\tprivate boolean alreadyInflated_ = false;\n");
		appendReceiverExtraConstants(out, model);
		appendReceiverFields(out, model);
		String generated = model.generatedSimpleName();
		for (MvpModel.ViewCtor ctor : model.viewConstructors) {
			out.append("\tpublic ").append(generated).append("(");
			appendParams(out, ctor.params, false, false);
			out.append(") {\n\t\tsuper(");
			appendParamNames(out, ctor.params);
			out.append(");\n\t\tinit_();\n\t}\n\n");
			out.append("\tpublic static ").append(extendsName).append(" build(");
			appendParams(out, ctor.params, false, false);
			out.append(") {\n\t\t").append(generated).append(" instance = new ").append(generated).append("(");
			appendParamNames(out, ctor.params);
			out.append(");\n\t\tinstance.onFinishInflate();\n\t\treturn instance;\n\t}\n\n");
		}
		out.append("\t@Override\n");
		out.append("\tpublic <T extends android.view.View> T internalFindViewById(int id) {\n");
		out.append("\t\treturn ((T) this.findViewById(id));\n");
		out.append("\t}\n\n");
		out.append("\tprivate void init_() {\n");
		out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier previousNotifier = org.androidannotations.api.view.OnViewChangedNotifier.replaceNotifier(onViewChangedNotifier_);\n");
		if (!model.stringRes.isEmpty() || !model.resFields.isEmpty()) {
			out.append("\t\tandroid.content.res.Resources resources_ = getContext().getResources();\n");
		}
		for (MvpModel.FieldInjection injection : model.stringRes) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "string", injection.propertyName, injection.resId, injection.resName, rClassIndex);
			appendAssign(out, injection, "resources_.getString(" + resExpr + ")");
		}
		for (MvpModel.ResField resField : model.resFields) {
			appendResAssign(out, model.kind, resField);
		}
		appendPrefInjections(out, model);
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
		out.append("\tpublic void onFinishInflate() {\n");
		out.append("\t\tif (!alreadyInflated_) {\n");
		out.append("\t\t\talreadyInflated_ = true;\n");
		if (model.kind == MvpModel.Kind.VIEW_GROUP && (model.layoutId != AnnotationNames.RES_DEFAULT
				|| (model.layoutResName != null && !model.layoutResName.isEmpty()))) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "layout", "", model.layoutId, model.layoutResName, rClassIndex);
			out.append("\t\t\tinflate(getContext(), ").append(resExpr).append(", this);\n");
		}
		out.append("\t\t\tonViewChangedNotifier_.notifyViewChanged(this);\n");
		out.append("\t\t}\n");
		out.append("\t\tsuper.onFinishInflate();\n");
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
			appendFragmentInjections(out, model);
			for (String method : model.afterViews) {
				out.append("\t\t").append(method).append("();\n");
			}
			out.append("\t}\n\n");
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
		appendUiThreadMethods(out, model);
		appendBackgroundMethods(out, model);
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
	}

	private void appendNonConfigurationRestore(StringBuilder out, MvpModel model) {
		if (model.nonConfigFields.isEmpty()) {
			return;
		}
		String getter = model.customNonConfiguration ? "getLastCustomNonConfigurationInstance" : "getLastNonConfigurationInstance";
		out.append("\t\tNonConfigurationInstancesHolder nonConfigurationInstance = ((NonConfigurationInstancesHolder) super.").append(getter).append("());\n");
		out.append("\t\tif (nonConfigurationInstance != null) {\n");
		for (MvpModel.NonConfigField field : model.nonConfigFields) {
			String expr = "nonConfigurationInstance." + field.propertyName;
			if (field.jvmField) {
				out.append("\t\t\tthis.").append(field.propertyName).append(" = ").append(expr).append(";\n");
			} else {
				out.append("\t\t\t").append(setterName(field.propertyName)).append("(").append(expr).append(");\n");
			}
			if (field.rebindGeneratedName != null) {
				String read = field.jvmField ? "this." + field.propertyName : getterName(field.propertyName) + "()";
				out.append("\t\t\t((").append(field.rebindGeneratedName).append(") ").append(read).append(").rebind(this);\n");
			}
		}
		out.append("\t\t}\n");
	}

	private void appendNonConfigurationMethods(StringBuilder out, MvpModel model) {
		if (model.nonConfigFields.isEmpty()) {
			return;
		}
		String retain = model.customNonConfiguration ? "onRetainCustomNonConfigurationInstance" : "onRetainNonConfigurationInstance";
		String last = model.customNonConfiguration ? "getLastCustomNonConfigurationInstance" : "getLastNonConfigurationInstance";
		out.append("\tprivate static class NonConfigurationInstancesHolder {\n");
		out.append("\t\tpublic Object superNonConfigurationInstance;\n");
		for (MvpModel.NonConfigField field : model.nonConfigFields) {
			out.append("\t\tpublic ").append(toSimpleName(field.typeName)).append(" ").append(field.propertyName).append(";\n");
		}
		out.append("\t}\n\n");
		out.append("\t@Override\n");
		out.append("\tpublic Object ").append(last).append("() {\n");
		out.append("\t\tNonConfigurationInstancesHolder nonConfigurationInstance = ((NonConfigurationInstancesHolder) super.").append(last).append("());\n");
		out.append("\t\tif (nonConfigurationInstance == null) {\n");
		out.append("\t\t\treturn null;\n");
		out.append("\t\t}\n");
		out.append("\t\treturn nonConfigurationInstance.superNonConfigurationInstance;\n");
		out.append("\t}\n\n");
		out.append("\t@Override\n");
		out.append("\tpublic NonConfigurationInstancesHolder ").append(retain).append("() {\n");
		out.append("\t\tNonConfigurationInstancesHolder nonConfigurationInstanceState_ = new NonConfigurationInstancesHolder();\n");
		out.append("\t\tnonConfigurationInstanceState_.superNonConfigurationInstance = super.").append(retain).append("();\n");
		for (MvpModel.NonConfigField field : model.nonConfigFields) {
			String read = field.jvmField ? "this." + field.propertyName : getterName(field.propertyName) + "()";
			out.append("\t\tnonConfigurationInstanceState_.").append(field.propertyName).append(" = ").append(read).append(";\n");
		}
		out.append("\t\treturn nonConfigurationInstanceState_;\n");
		out.append("\t}\n\n");
	}

	private void appendViewsById(StringBuilder out, MvpModel model) {
		int group = 0;
		for (MvpModel.ViewsByIdInjection injection : model.viewsByIds) {
			group++;
			String listName = "list_" + injection.name;
			String viewType = injection.viewType;
			out.append("\t\tjava.util.ArrayList<").append(viewType).append("> ").append(listName)
					.append(" = new java.util.ArrayList<").append(viewType).append(">();\n");
			List<String> ids = new ArrayList<>();
			for (Integer id : injection.valueIds) {
				ids.add(KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", injection.name, id, "", rClassIndex));
			}
			for (String resName : injection.nameIds) {
				ids.add(KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", resName, AnnotationNames.RES_DEFAULT, resName, rClassIndex));
			}
			int index = 0;
			for (String idExpr : ids) {
				String local = "found_" + group + "_" + index;
				out.append("\t\t").append(viewType).append(" ").append(local).append(" = hasViews.internalFindViewById(").append(idExpr).append(");\n");
				out.append("\t\tif (").append(local).append(" != null) {\n");
				out.append("\t\t\t").append(listName).append(".add(").append(local).append(");\n");
				out.append("\t\t}\n");
				index++;
			}
			String value = injection.array ? listName + ".toArray(new " + viewType + "[" + listName + ".size()])" : listName;
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
			String ref = "this." + field.propertyName;
			for (MvpModel.ViewInjection injection : model.viewInjections) {
				if (injection.form == MvpModel.ViewInjection.Form.FIELD && injection.field.propertyName.equals(field.propertyName) && !injection.field.jvmField) {
					ref = getterName(field.propertyName) + "()";
				}
			}
			out.append("\t\tif (").append(ref).append(" != null) {\n");
			out.append("\t\t\t").append(ref).append(".setText(android.text.Html.fromHtml(").append(contextRef(model.kind)).append(".getString(").append(resExpr).append(")));\n");
			out.append("\t\t}\n");
		}
	}

	private void appendFragmentInjections(StringBuilder out, MvpModel model) {
		for (MvpModel.FragmentInjection byTag : model.fragmentByTags) {
			String tag = byTag.resName != null && !byTag.resName.isEmpty() ? byTag.resName : byTag.propertyName;
			String manager = fragmentManagerExpr(model, byTag.childFragment, byTag.nativeFragment);
			out.append("\t\tthis.").append(byTag.propertyName).append(" = ((").append(byTag.typeName).append(") ").append(manager).append(".findFragmentByTag(\"").append(tag).append("\"));\n");
		}
		for (MvpModel.FragmentInjection byId : model.fragmentByIds) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", byId.propertyName, byId.resId, byId.resName, rClassIndex);
			String manager = fragmentManagerExpr(model, byId.childFragment, byId.nativeFragment);
			out.append("\t\tthis.").append(byId.propertyName).append(" = ((").append(byId.typeName).append(") ").append(manager).append(".findFragmentById(").append(resExpr).append("));\n");
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
		for (ReceiverFilter filter : plan.filters) {
			out.append("\tprivate final android.content.IntentFilter ").append(filter.fieldName).append(" = new android.content.IntentFilter();\n");
		}
		for (ReceiverBound bound : plan.bounds) {
			out.append("\tprivate final android.content.BroadcastReceiver ").append(bound.receiverField).append(" = new android.content.BroadcastReceiver() {\n");
			if (bound.binding.unchecked) {
				out.append("\t\t@SuppressWarnings(\"unchecked\")\n");
			}
			out.append("\t\t@Override\n");
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
		if (model.receivers.isEmpty() || model.kind == MvpModel.Kind.VIEW || model.kind == MvpModel.Kind.VIEW_GROUP) {
			return;
		}
		if (hasReceiver(model, "OnCreateOnDestroy")) {
			out.append("\t@Override\n");
			out.append("\tpublic void onDestroy() {\n");
			appendReceiverUnregister(out, model, "OnCreateOnDestroy", "\t\t");
			out.append("\t\tsuper.onDestroy();\n");
			out.append("\t}\n\n");
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

	private void appendSetContentViewCall(StringBuilder out, MvpModel model) {
		boolean hasLayoutId = model.layoutId != AnnotationNames.RES_DEFAULT;
		boolean hasLayoutName = model.layoutResName != null && !model.layoutResName.isEmpty();
		if (hasLayoutId || hasLayoutName) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "layout", "", model.layoutId, hasLayoutName ? model.layoutResName : "", rClassIndex);
			out.append("\t\tsetContentView(").append(resExpr).append(");\n");
		}
	}

	/** Mirrors APT ContextCompatAwareResHandler branch selection for one resource field. */
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
			appendAssign(out, resField.propertyName, resField.jvmField, "android.view.animation.AnimationUtils.loadAnimation(" + contextRef + ", " + resExpr + ")");
			return;
		}
		if ("HTML_FROM_HTML".equals(resField.invocation)) {
			// APT HtmlResHandler parity: Html.fromHtml(resources_.getString(R.string.x)).
			appendAssign(out, resField.propertyName, resField.jvmField, "android.text.Html.fromHtml(resources_." + resField.method + "(" + resExpr + "))");
			return;
		}
		if (mode == null || "RESOURCES".equals(mode.kind)) {
			appendAssign(out, resField.propertyName, resField.jvmField, "resources_." + resField.method + "(" + resExpr + ")");
			return;
		}
		if ("ANDROIDX_COMPAT".equals(mode.kind) || "SUPPORT_COMPAT".equals(mode.kind)) {
			String compatClass = "ANDROIDX_COMPAT".equals(mode.kind) ? "androidx.core.content.ContextCompat" : "android.support.v4.content.ContextCompat";
			appendAssign(out, resField.propertyName, resField.jvmField, compatClass + "." + resField.method + "(" + contextRef + ", " + resExpr + ")");
			return;
		}
		if ("CONTEXT_METHOD".equals(mode.kind)) {
			appendAssign(out, resField.propertyName, resField.jvmField, contextCall + "(" + resExpr + ")");
			return;
		}
		// IF_GUARD: mirrors APT ContextCompatAwareResHandler.createCallWithIfGuard.
		out.append("\t\tif (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.").append(mode.platformName).append(") {\n");
		appendAssign(out, resField.propertyName, resField.jvmField, contextCall + "(" + resExpr + ")", "\t\t\t");
		out.append("\t\t} else {\n");
		appendAssign(out, resField.propertyName, resField.jvmField, "resources_." + resField.method + "(" + resExpr + ")", "\t\t\t");
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
			appendAssign(out, pref.propertyName, pref.jvmField, "new " + pref.generatedQualifiedName + "(" + contextRef + ")");
		}
	}

	/** APT BeanHandler parity: field assigns / braced method blocks inside the init-body injection block. */
	private void appendBeanInjections(StringBuilder out, MvpModel model) {
		for (MvpModel.BeanInjection bean : model.beans) {
			if (bean.form == MvpModel.BeanInjection.Form.FIELD) {
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
		if (kind == MvpModel.Kind.VIEW || kind == MvpModel.Kind.VIEW_GROUP) {
			return "getContext()";
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

	/** APT ActivityIntentBuilder.overrideStartForResultMethod: fragment branches, then the compat/activity/context starter. */
	private void appendStartForResult(StringBuilder out) {
		boolean hasSupport = intentBuilderCompat.androidxFragment || intentBuilderCompat.supportFragment;
		boolean hasFragment = intentBuilderCompat.fragment;
		boolean options = intentBuilderCompat.activityOptionsInFragment;
		boolean guard = intentBuilderCompat.guardActivityOptions;

		out.append("\t\t@Override\n");
		out.append("\t\tpublic org.androidannotations.api.builder.PostActivityStarter startForResult(int requestCode) {\n");
		String s1 = "\t\t\t";
		boolean inElse = false;
		if (hasSupport) {
			out.append(s1).append("if (fragmentSupport_!= null) {\n");
			out.append(s1 + "\t").append("fragmentSupport_.startActivityForResult(intent, requestCode);\n");
			inElse = true;
		}
		if (hasFragment) {
			if (inElse) {
				out.append(s1).append("} else if (fragment_!= null) {\n");
			} else {
				out.append(s1).append("if (fragment_!= null) {\n");
			}
			if (options && guard) {
				out.append(s1 + "\t").append("if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {\n");
				out.append(s1 + "\t\t").append("fragment_.startActivityForResult(intent, requestCode, lastOptions);\n");
				out.append(s1 + "\t").append("} else {\n");
				out.append(s1 + "\t\t").append("fragment_.startActivityForResult(intent, requestCode);\n");
				out.append(s1 + "\t").append("}\n");
			} else if (options) {
				out.append(s1 + "\t").append("fragment_.startActivityForResult(intent, requestCode, lastOptions);\n");
			} else {
				out.append(s1 + "\t").append("fragment_.startActivityForResult(intent, requestCode);\n");
			}
			inElse = true;
		}
		String s2 = inElse ? s1 + "\t" : s1;
		if (inElse) {
			out.append(s1).append("} else {\n");
		}

		out.append(s2).append("if (context instanceof android.app.Activity) {\n");
		String aLevel = s2 + "\t";
		out.append(aLevel).append("android.app.Activity activity = ((android.app.Activity) context);\n");
		if (intentBuilderCompat.activityCompat != null) {
			out.append(aLevel).append(intentBuilderCompat.activityCompat).append(".startActivityForResult(activity, intent, requestCode, lastOptions);\n");
		} else if (options && guard) {
			out.append(aLevel).append("if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {\n");
			out.append(aLevel + "\t").append("activity.startActivityForResult(intent, requestCode, lastOptions);\n");
			out.append(aLevel).append("} else {\n");
			out.append(aLevel + "\t").append("activity.startActivityForResult(intent, requestCode);\n");
			out.append(aLevel).append("}\n");
		} else if (options) {
			out.append(aLevel).append("activity.startActivityForResult(intent, requestCode, lastOptions);\n");
		} else {
			out.append(aLevel).append("activity.startActivityForResult(intent, requestCode);\n");
		}
		out.append(s2).append("} else {\n");
		String cLevel = s2 + "\t";
		if (options && guard) {
			out.append(cLevel).append("if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {\n");
			out.append(cLevel + "\t").append("context.startActivity(intent, lastOptions);\n");
			out.append(cLevel).append("} else {\n");
			out.append(cLevel + "\t").append("context.startActivity(intent);\n");
			out.append(cLevel).append("}\n");
		} else if (options) {
			out.append(cLevel).append("context.startActivity(intent, lastOptions);\n");
		} else {
			out.append(cLevel).append("context.startActivity(intent);\n");
		}
		out.append(s2).append("}\n");
		if (inElse) {
			out.append(s1).append("}\n");
		}
		out.append("\t\t\treturn new org.androidannotations.api.builder.PostActivityStarter(context);\n");
		out.append("\t\t}\n");
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

	private void appendBackgroundMethods(StringBuilder out, MvpModel model) {
		for (MvpModel.MethodInfo method : model.backgrounds) {
			out.append("\t@Override\n");
			out.append("\t").append(method.visibility).append(" void ").append(method.name).append("(");
			appendParams(out, method.params, true);
			out.append(") {\n");
			out.append("\t\torg.androidannotations.api.BackgroundExecutor.execute(new org.androidannotations.api.BackgroundExecutor.Task(\"")
					.append(escape(method.id)).append("\", ").append(method.delay).append("L, \"")
					.append(escape(method.serial)).append("\") {\n");
			out.append("\t\t\t@Override\n");
			out.append("\t\t\tpublic void execute() {\n");
			out.append("\t\t\t\ttry {\n");
			appendGuardedSuperCall(out, model, method, "\t\t\t\t\t");
			out.append("\t\t\t\t} catch (final Throwable e) {\n");
			out.append("\t\t\t\t\tThread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);\n");
			out.append("\t\t\t\t}\n");
			out.append("\t\t\t}\n");
			out.append("\t\t});\n");
			out.append("\t}\n\n");
		}
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

	private void appendUiThreadMethods(StringBuilder out, MvpModel model) {
		for (MvpModel.MethodInfo method : model.uiThreads) {
			out.append("\t@Override\n");
			out.append("\t").append(method.visibility).append(" void ").append(method.name).append("(");
			appendParams(out, method.params, true);
			out.append(") {\n");
			if (method.delay == 0L && method.reusePropagation) {
				out.append("\t\tif (Thread.currentThread() == android.os.Looper.getMainLooper().getThread()) {\n");
				appendGuardedSuperCall(out, model, method, "\t\t\t");
				out.append("\t\t\treturn;\n");
				out.append("\t\t}\n");
			}

			out.append("\t\torg.androidannotations.api.UiThreadExecutor.runTask(\"").append(escape(method.id))
					.append("\", new Runnable() {\n");
			out.append("\t\t\t@Override\n");
			out.append("\t\t\tpublic void run() {\n");
			appendGuardedSuperCall(out, model, method, "\t\t\t\t");
			out.append("\t\t\t}\n");
			out.append("\t\t}, ").append(method.delay).append("L);\n");
			out.append("\t}\n\n");
		}
	}

	private void appendParams(StringBuilder out, java.util.List<MvpModel.Param> params, boolean makeFinal) {
		appendParams(out, params, makeFinal, true);
	}

	private void appendParams(StringBuilder out, java.util.List<MvpModel.Param> params, boolean makeFinal, boolean simpleNames) {
		for (int i = 0; i < params.size(); i++) {
			if (i > 0) {
				out.append(", ");
			}
			MvpModel.Param param = params.get(i);
			if (param.nullAnnotation != null) {
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

	private static String simpleHead(String type) {
		int dot = type.lastIndexOf('.');
		return dot < 0 ? type : type.substring(dot + 1);
	}
}
