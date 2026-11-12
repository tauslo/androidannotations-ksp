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
import org.androidannotations.ksp.env.RClassIndex;

public final class MvpJavaGenerator {

	private final String resourcePackage;
	private final String rClassName;
	private final RClassIndex rClassIndex;

	public MvpJavaGenerator(String resourcePackage, String rClassName, RClassIndex rClassIndex) {
		this.resourcePackage = resourcePackage;
		this.rClassName = rClassName;
		this.rClassIndex = rClassIndex;
	}

	public String generate(MvpModel model) {
		if (model.kind == MvpModel.Kind.ACTIVITY) {
			return generateActivity(model);
		}
		if (model.kind == MvpModel.Kind.FRAGMENT) {
			return generateFragment(model);
		}
		return generateBean(model);
	}

	private String generateActivity(MvpModel model) {
		StringBuilder out = new StringBuilder();
		appendHeader(out, model);
		out.append("public final class ").append(model.generatedSimpleName())
				.append(" extends ").append(model.simpleName)
				.append(" implements org.androidannotations.api.view.HasViews,\n")
				.append("\t\torg.androidannotations.api.view.OnViewChangedListener,\n")
				.append("\t\torg.androidannotations.api.bean.BeanHolder {\n\n");

		for (MvpModel.FieldInjection extra : model.extras) {
			String key = extra.resName != null && !extra.resName.isEmpty() ? extra.resName : extra.propertyName;
			out.append("\tpublic static final String ").append(KspAnnotationUtils.extraKeyConstant(extra.propertyName))
					.append(" = \"").append(key).append("\";\n");
		}
		if (!model.extras.isEmpty()) {
			out.append('\n');
		}

		out.append("\tprivate final org.androidannotations.api.view.OnViewChangedNotifier onViewChangedNotifier_ = new org.androidannotations.api.view.OnViewChangedNotifier();\n");
		out.append("\tprivate final java.util.HashMap<Class<?>, Object> beans_ = new java.util.HashMap<Class<?>, Object>();\n\n");

		out.append("\t@Override\n");
		out.append("\tpublic void onCreate(android.os.Bundle savedInstanceState) {\n");
		out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier previousNotifier = org.androidannotations.api.view.OnViewChangedNotifier.replaceNotifier(onViewChangedNotifier_);\n");
		out.append("\t\tinit_(savedInstanceState);\n");
		out.append("\t\tsuper.onCreate(savedInstanceState);\n");
		out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier.replaceNotifier(previousNotifier);\n");
		appendSetContentViewCall(out, model);
		out.append("\t}\n\n");

		out.append("\tprivate void init_(android.os.Bundle savedInstanceState) {\n");
		if (!model.stringRes.isEmpty()) {
			out.append("\t\tandroid.content.res.Resources resources_ = getResources();\n");
		}
		for (MvpModel.FieldInjection injection : model.stringRes) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "string", injection.propertyName, injection.resId, injection.resName, rClassIndex);
			appendAssign(out, injection, "resources_.getString(" + resExpr + ")");
		}
		if (!model.extras.isEmpty() || !model.afterExtras.isEmpty()) {
			out.append("\t\tinjectExtras_();\n");
		}
		appendBeanInjections(out, model);
		for (String method : model.afterInject) {
			out.append("\t\t").append(method).append("();\n");
		}
		out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier.registerOnViewChangedListener(this);\n");
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

		out.append("\t@Override\n");
		out.append("\t@SuppressWarnings(\"unchecked\")\n");
		out.append("\tpublic <T extends android.view.View> T internalFindViewById(int id) {\n");
		out.append("\t\treturn (T) findViewById(id);\n");
		out.append("\t}\n\n");

		out.append("\t@Override\n");
		out.append("\tpublic void onViewChanged(org.androidannotations.api.view.HasViews hasViews) {\n");
		for (MvpModel.FieldInjection view : model.viewByIds) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", view.propertyName, view.resId, view.resName, rClassIndex);
			appendAssign(out, view, "(" + view.typeName + ") hasViews.internalFindViewById(" + resExpr + ")");
		}
		appendViewListeners(out, model);
		for (String method : model.afterViews) {
			out.append("\t\t").append(method).append("();\n");
		}
		out.append("\t}\n\n");

		appendBeanHolderMethods(out);
		appendBackgroundMethods(out, model);
		appendUiThreadMethods(out, model);
		appendIntentBuilder(out, model);

		appendNestedClasses(out, model);
		out.append("}\n");
		return out.toString();
	}

	private String generateFragment(MvpModel model) {
		StringBuilder out = new StringBuilder();
		appendHeader(out, model);
		boolean hasViewSupport = !model.viewByIds.isEmpty() || !model.afterViews.isEmpty()
			|| !model.clickListeners.isEmpty() || !model.longClickListeners.isEmpty();
		out.append("public final class ").append(model.generatedSimpleName())
				.append(" extends ").append(model.simpleName)
				.append(" implements org.androidannotations.api.bean.BeanHolder, org.androidannotations.api.view.HasViews")
				.append(hasViewSupport ? ", org.androidannotations.api.view.OnViewChangedListener" : "")
				.append(" {\n\n");

		out.append("\tprivate final org.androidannotations.api.view.OnViewChangedNotifier onViewChangedNotifier_ = new org.androidannotations.api.view.OnViewChangedNotifier();\n");
		out.append("\tprivate android.view.View contentView_;\n");
		out.append("\tprivate final java.util.HashMap<Class<?>, Object> beans_ = new java.util.HashMap<Class<?>, Object>();\n");
		for (MvpModel.FieldInjection arg : model.fragmentArgs) {
			String key = arg.resName != null && !arg.resName.isEmpty() ? arg.resName : arg.propertyName;
			out.append("\tpublic final static String ").append(KspAnnotationUtils.fragmentArgKeyConstant(arg.propertyName))
					.append(" = \"").append(key).append("\";\n");
		}
		if (!model.fragmentArgs.isEmpty()) {
			out.append("\n");
		}

		out.append("\t@Override\n");
		out.append("\tpublic void onCreate(android.os.Bundle savedInstanceState) {\n");
		out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier previousNotifier = org.androidannotations.api.view.OnViewChangedNotifier.replaceNotifier(onViewChangedNotifier_);\n");
		out.append("\t\tinit_(savedInstanceState);\n");
		out.append("\t\tsuper.onCreate(savedInstanceState);\n");
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
		for (MvpModel.FieldInjection view : model.viewByIds) {
			out.append("\t\t").append(view.propertyName).append(" = null;\n");
		}
		out.append("\t}\n\n");

		out.append("\tprivate void init_(android.os.Bundle savedInstanceState) {\n");
		if (hasViewSupport) {
			out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier.registerOnViewChangedListener(this);\n");
		}
		out.append("\t\tinjectFragmentArguments_();\n");
		out.append("\t}\n\n");

		out.append("\t@Override\n");
		out.append("\tpublic void onViewCreated(android.view.View view, android.os.Bundle savedInstanceState) {\n");
		out.append("\t\tsuper.onViewCreated(view, savedInstanceState);\n");
		out.append("\t\tonViewChangedNotifier_.notifyViewChanged(this);\n");
		out.append("\t}\n\n");

		out.append("\tpublic static FragmentBuilder_ builder() {\n");
		out.append("\t\treturn new FragmentBuilder_();\n");
		out.append("\t}\n\n");

		appendFragmentBeanHolderMethods(out);

		if (hasViewSupport) {
			out.append("\t@Override\n");
			out.append("\tpublic void onViewChanged(org.androidannotations.api.view.HasViews hasViews) {\n");
			for (MvpModel.FieldInjection view : model.viewByIds) {
				String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", view.propertyName, view.resId, view.resName, rClassIndex);
				out.append("\t\tthis.").append(view.propertyName).append(" = hasViews.internalFindViewById(").append(resExpr).append(");\n");
			}
			appendViewListeners(out, model);
			for (MvpModel.FragmentInjection byTag : model.fragmentByTags) {
				String tag = byTag.resName != null && !byTag.resName.isEmpty() ? byTag.resName : byTag.propertyName;
				String manager = byTag.childFragment ? "getChildFragmentManager()" : "getFragmentManager()";
				out.append("\t\tthis.").append(byTag.propertyName).append(" = ((").append(byTag.typeName).append(") ").append(manager).append(".findFragmentByTag(\"").append(tag).append("\"));\n");
			}
			for (MvpModel.FragmentInjection byId : model.fragmentByIds) {
				String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", byId.propertyName, byId.resId, byId.resName, rClassIndex);
				String manager = byId.childFragment ? "getChildFragmentManager()" : "getFragmentManager()";
				out.append("\t\tthis.").append(byId.propertyName).append(" = ((").append(byId.typeName).append(") ").append(manager).append(".findFragmentById(").append(resExpr).append("));\n");
			}
			for (String method : model.afterViews) {
				out.append("\t\t").append(method).append("();\n");
			}
			out.append("\t}\n\n");
		}

		appendFragmentArgsMethod(out, model);
		appendBackgroundMethods(out, model);
		appendUiThreadMethods(out, model);
		appendFragmentBuilder(out, model);

		appendNestedClasses(out, model);
		out.append("}\n");
		return out.toString();
	}

	private void appendViewListeners(StringBuilder out, MvpModel model) {
		LinkedHashMap<String, String> refs = new LinkedHashMap<>();
		LinkedHashMap<String, String> locals = new LinkedHashMap<>();
		LinkedHashMap<String, List<String>> setters = new LinkedHashMap<>();
		for (MvpModel.FieldInjection view : model.viewByIds) {
			String rName = null;
			if (view.resId != AnnotationNames.RES_DEFAULT) {
				rName = rClassIndex != null ? rClassIndex.fieldNameForValue("id", view.resId) : null;
			}
			if (rName == null && rClassIndex != null) {
				rName = rClassIndex.resolveFieldName("id", view.propertyName);
			}
			if (rName != null) {
				refs.put(rName, view.propertyName);
			}
		}
		appendListenerIds(model, model.clickListeners, false, refs, locals, setters);
		appendListenerIds(model, model.longClickListeners, true, refs, locals, setters);
		for (Map.Entry<String, String> e : locals.entrySet()) {
			out.append("\t\tandroid.view.View ").append(e.getKey()).append(" = hasViews.internalFindViewById(")
				.append(e.getValue()).append(");\n");
		}
		for (Map.Entry<String, List<String>> e : setters.entrySet()) {
			out.append("\t\tif (").append(refs.get(e.getKey())).append(" != null) {\n");
			for (String s : e.getValue()) {
				out.append(s);
			}
			out.append("\t\t}\n");
		}
	}

	private void appendListenerIds(MvpModel model, List<MvpModel.ViewListener> listeners, boolean longClick,
		Map<String, String> refs, Map<String, String> locals, Map<String, List<String>> setters) {
		for (MvpModel.ViewListener listener : listeners) {
			for (Integer resId : listener.valueIds) {
				String rName = rClassIndex != null ? rClassIndex.fieldNameForValue("id", resId) : null;
				if (rName == null) {
					rName = Integer.toString(resId);
				}
				addListenerEntry(model, refs, locals, setters, rName,
					KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", rName, resId, null, rClassIndex),
					listener, longClick);
			}
			for (String resName : listener.nameIds) {
				String rName = rClassIndex != null ? rClassIndex.resolveFieldName("id", resName) : null;
				if (rName == null) {
					rName = resName;
				}
				addListenerEntry(model, refs, locals, setters, rName,
					KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", rName, AnnotationNames.RES_DEFAULT, resName, rClassIndex),
					listener, longClick);
			}
		}
	}

	private void addListenerEntry(MvpModel model, Map<String, String> refs, Map<String, String> locals,
		Map<String, List<String>> setters, String rName, String findExpr, MvpModel.ViewListener listener, boolean longClick) {
		if (!refs.containsKey(rName)) {
			String local = "view_" + rName;
			refs.put(rName, local);
			locals.put(local, findExpr);
		}
		String ref = refs.get(rName);
		StringBuilder call = new StringBuilder(model.generatedSimpleName()).append(".this.").append(listener.methodName).append("(");
		if (!listener.params.isEmpty()) {
			String type = listener.params.get(0).typeName;
			boolean plainView = "android.view.View".equals(type) || "View".equals(type);
			call.append(plainView ? "view" : "((" + type + ") view)");
		}
		call.append(")");
		StringBuilder s = new StringBuilder("\t\t\t").append(ref).append(longClick
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
		List<String> list = setters.get(rName);
		if (list == null) {
			list = new ArrayList<>();
			setters.put(rName, list);
		}
		list.add(s.toString());
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
				out.append("\t\treturn inflater.inflate(").append(resExpr).append(", container, false);\n");
			} else {
				out.append("\t\tcontentView_ = super.onCreateView(inflater, container, savedInstanceState);\n");
				out.append("\t\tif (contentView_ == null) {\n");
				out.append("\t\t\tcontentView_ = inflater.inflate(").append(resExpr).append(", container, false);\n");
				out.append("\t\t}\n");
				out.append("\t\treturn contentView_;\n");
			}
		} else if (model.forceLayoutInjection) {
			out.append("\t\treturn contentView_;\n");
		} else {
			out.append("\t\tcontentView_ = super.onCreateView(inflater, container, savedInstanceState);\n");
			out.append("\t\treturn contentView_;\n");
		}
		out.append("\t}\n\n");
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
		boolean hasViewSupport = !model.viewByIds.isEmpty() || !model.afterViews.isEmpty();
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
		out.append("\t\tthis.context_ = context;\n");
		if (defaultScope) {
			out.append("\t\tinit_();\n");
		}
		out.append("\t}\n\n");

		out.append("\tprivate ").append(generated).append("(android.content.Context context, Object rootFragment) {\n");
		out.append("\t\tthis.context_ = context;\n");
		out.append("\t\tthis.rootFragment_ = rootFragment;\n");
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

		if (defaultScope) {
			// APT parity: rebind() is only generated for the Default scope.
			out.append("\tpublic void rebind(android.content.Context context) {\n");
			out.append("\t\tthis.context_ = context;\n");
			out.append("\t\tinit_();\n");
			out.append("\t}\n\n");
		}

		out.append("\tprivate void init_() {\n");
		if (!model.stringRes.isEmpty()) {
			out.append("\t\tandroid.content.res.Resources resources_ = context_.getResources();\n");
		}
		for (MvpModel.FieldInjection injection : model.stringRes) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "string", injection.propertyName, injection.resId, injection.resName, rClassIndex);
			appendAssign(out, injection, "resources_.getString(" + resExpr + ")");
		}
		if (hasViewSupport) {
			out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier.registerOnViewChangedListener(this);\n");
		}
		out.append("\t}\n\n");

		if (hasViewSupport) {
			out.append("\t@Override\n");
			out.append("\tpublic void onViewChanged(org.androidannotations.api.view.HasViews hasViews) {\n");
			for (MvpModel.FieldInjection view : model.viewByIds) {
				String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", view.propertyName, view.resId, view.resName, rClassIndex);
				appendAssign(out, view, "(" + view.typeName + ") hasViews.internalFindViewById(" + resExpr + ")");
			}
			for (String method : model.afterViews) {
				out.append("\t\t").append(method).append("();\n");
			}
			out.append("\t}\n\n");
		}

		appendBackgroundMethods(out, model);
		appendUiThreadMethods(out, model);

		appendNestedClasses(out, model);
		out.append("}\n");
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

	private void appendAssign(StringBuilder out, MvpModel.FieldInjection injection, String expression) {
		appendAssign(out, injection, expression, "\t\t");
	}

	private void appendAssign(StringBuilder out, MvpModel.FieldInjection injection, String expression, String indent) {
		if (injection.jvmField) {
			out.append(indent).append(injection.propertyName).append(" = ").append(expression).append(";\n");
		} else {
			out.append(indent).append(KspAnnotationUtils.setterName(injection.propertyName)).append("(").append(expression).append(");\n");
		}
	}

	/** APT BeanHandler parity: field assigns / braced method blocks inside the init-body injection block. */
	private void appendBeanInjections(StringBuilder out, MvpModel model) {
		for (MvpModel.BeanInjection bean : model.beans) {
			if (bean.form == MvpModel.BeanInjection.Form.FIELD) {
				MvpModel.BeanTarget target = bean.targets.get(0);
				appendBeanAssign(out, bean.name, bean.jvmField, getInstanceExpr(target));
			} else {
				out.append("\t\t{\n");
				for (MvpModel.BeanTarget target : bean.targets) {
					out.append("\t\t\t").append(target.declaredType).append(' ').append(target.name)
							.append(" = ").append(getInstanceExpr(target)).append(";\n");
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

	private static String getInstanceExpr(MvpModel.BeanTarget target) {
		return target.beanGeneratedQualifiedName + ".getInstance_(this" + (target.withRootFragmentArg ? ", null" : "") + ")";
	}

	private void appendBeanAssign(StringBuilder out, String propertyName, boolean jvmField, String expression) {
		if (jvmField) {
			out.append("\t\t").append(propertyName).append(" = ").append(expression).append(";\n");
		} else {
			out.append("\t\t").append(KspAnnotationUtils.setterName(propertyName)).append('(').append(expression).append(");\n");
		}
	}

	private void appendIntentBuilder(StringBuilder out, MvpModel model) {
		String generated = model.generatedSimpleName();
		out.append("\tpublic static IntentBuilder_ intent(android.content.Context context) {\n");
		out.append("\t\treturn new IntentBuilder_(context);\n");
		out.append("\t}\n\n");

		out.append("\tpublic static class IntentBuilder_ extends org.androidannotations.api.builder.ActivityIntentBuilder<IntentBuilder_> {\n\n");
		out.append("\t\tpublic IntentBuilder_(android.content.Context context) {\n");
		out.append("\t\t\tsuper(context, ").append(generated).append(".class);\n");
		out.append("\t\t}\n\n");

		for (MvpModel.FieldInjection extra : model.extras) {
			String constant = KspAnnotationUtils.extraKeyConstant(extra.propertyName);
			String paramType = extra.extraFullType != null ? extra.extraFullType : extra.typeName;
			out.append("\t\tpublic IntentBuilder_ ").append(extra.propertyName).append('(').append(paramType).append(" ")
					.append(extra.propertyName).append(") {\n");
			out.append("\t\t\treturn super.extra(").append(constant).append(", ")
					.append(extra.extraWriteExpr.replace("{arg}", extra.propertyName)).append(");\n");
			out.append("\t\t}\n\n");
		}

		out.append("\t\t@Override\n");
		out.append("\t\tpublic org.androidannotations.api.builder.PostActivityStarter startForResult(int requestCode) {\n");
		out.append("\t\t\tif (context instanceof android.app.Activity) {\n");
		out.append("\t\t\t\t((android.app.Activity) context).startActivityForResult(intent, requestCode);\n");
		out.append("\t\t\t} else {\n");
		out.append("\t\t\t\tcontext.startActivity(intent);\n");
		out.append("\t\t\t}\n");
		out.append("\t\t\treturn new org.androidannotations.api.builder.PostActivityStarter(context);\n");
		out.append("\t\t}\n");
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
		out.append("\tpublic static class FragmentBuilder_ extends org.androidannotations.api.builder.FragmentBuilder<FragmentBuilder_, ").append(model.qualifiedName).append("> {\n\n");
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
			out.append("\t\tpublic FragmentBuilder_ ").append(arg.propertyName).append('(').append(paramType).append(" ").append(arg.propertyName)
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
		for (MvpModel nested : model.nestedModels) {
			out.append('\n');
			String source = generateNestedBean(nested);
			for (String line : source.split("\n")) {
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
		out.append("\t\t\torg.androidannotations.api.bean.BeanHolder beanHolder = (org.androidannotations.api.bean.BeanHolder) " + holderRef + ";\n");
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
		out.append("\t@SuppressWarnings(\"unchecked\")\n");
		out.append("\tpublic <T> T getBean(Class<T> key) {\n");
		out.append("\t\treturn (T) beans_.get(key);\n");
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
			appendParams(out, method.params, false);
			out.append(") {\n");
			out.append("\t\torg.androidannotations.api.BackgroundExecutor.execute(new org.androidannotations.api.BackgroundExecutor.Task(\"")
					.append(escape(method.id)).append("\", ").append(method.delay).append("L, \"")
					.append(escape(method.serial)).append("\") {\n");
			out.append("\t\t\t@Override\n");
			out.append("\t\t\tpublic void execute() {\n");
			out.append("\t\t\t\ttry {\n");
			out.append("\t\t\t\t\t").append(model.generatedSimpleName()).append(".super.").append(method.name).append("(");
			appendParamNames(out, method.params);
			out.append(");\n");
			out.append("\t\t\t\t} catch (Throwable e__) {\n");
			out.append("\t\t\t\t\tThread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e__);\n");
			out.append("\t\t\t\t}\n");
			out.append("\t\t\t}\n");
			out.append("\t\t});\n");
			out.append("\t}\n\n");
		}
	}

	private void appendUiThreadMethods(StringBuilder out, MvpModel model) {
		for (MvpModel.MethodInfo method : model.uiThreads) {
			out.append("\t@Override\n");
			out.append("\t").append(method.visibility).append(" void ").append(method.name).append("(");
			appendParams(out, method.params, true);
			out.append(") {\n");
			if (method.delay == 0L && method.reusePropagation) {
				out.append("\t\tif (Thread.currentThread() == android.os.Looper.getMainLooper().getThread()) {\n");
				out.append("\t\t\t").append(model.generatedSimpleName()).append(".super.").append(method.name).append("(");
				appendParamNames(out, method.params);
				out.append(");\n");
				out.append("\t\t\treturn;\n");
				out.append("\t\t}\n");
			}
			for (MvpModel.Param param : method.params) {
				out.append("\t\tfinal ").append(param.typeName).append(' ').append(param.name).append("_ = ")
						.append(param.name).append(";\n");
			}
			out.append("\t\torg.androidannotations.api.UiThreadExecutor.runTask(\"").append(escape(method.id))
					.append("\", new Runnable() {\n");
			out.append("\t\t\t@Override\n");
			out.append("\t\t\tpublic void run() {\n");
			out.append("\t\t\t\t").append(model.generatedSimpleName()).append(".super.").append(method.name).append("(");
			for (int i = 0; i < method.params.size(); i++) {
				if (i > 0) {
					out.append(", ");
				}
				out.append(method.params.get(i).name).append("_");
			}
			out.append(");\n");
			out.append("\t\t\t}\n");
			out.append("\t\t}, ").append(method.delay).append("L);\n");
			out.append("\t}\n\n");
		}
	}

	private void appendParams(StringBuilder out, java.util.List<MvpModel.Param> params, boolean makeFinal) {
		for (int i = 0; i < params.size(); i++) {
			if (i > 0) {
				out.append(", ");
			}
			MvpModel.Param param = params.get(i);
			if (makeFinal) {
				out.append("final ");
			}
			out.append(param.typeName).append(' ').append(param.name);
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
}
