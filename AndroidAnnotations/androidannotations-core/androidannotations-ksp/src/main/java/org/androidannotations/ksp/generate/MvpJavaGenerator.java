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
import org.androidannotations.ksp.ResCompat;
import org.androidannotations.ksp.env.RClassIndex;

public final class MvpJavaGenerator {

	private final String resourcePackage;
	private final String rClassName;
	private final RClassIndex rClassIndex;

	private final Map<String, ResCompat.Mode> resCompatModes;

	public MvpJavaGenerator(String resourcePackage, String rClassName, RClassIndex rClassIndex, Map<String, ResCompat.Mode> resCompatModes) {
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
		return generateBean(model);
	}

	private String generateActivity(MvpModel model) {
		StringBuilder out = new StringBuilder();
		appendHeader(out, model);
		boolean hasViewSupport = !model.viewByIds.isEmpty() || !model.afterViews.isEmpty()
				|| !model.clickListeners.isEmpty() || !model.longClickListeners.isEmpty();
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
		out.append("\tprivate final java.util.HashMap<Class<?>, Object> beans_ = new java.util.HashMap<Class<?>, Object>();\n\n");

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
		if (!model.extras.isEmpty() || !model.afterExtras.isEmpty()) {
			out.append("\t\tinjectExtras_();\n");
		}
		appendBeanInjections(out, model);
		for (String method : model.afterInject) {
			out.append("\t\t").append(method).append("();\n");
		}
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
		appendBeanHolderMethods(out);
		if (hasViewSupport) {
			out.append("\t@Override\n");
			out.append("\tpublic void onViewChanged(org.androidannotations.api.view.HasViews hasViews) {\n");
			for (MvpModel.FieldInjection view : model.viewByIds) {
				String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", view.propertyName, view.resId, view.resName, rClassIndex);
				if (view.jvmField) {
					out.append("\t\tthis.").append(view.propertyName).append(" = hasViews.internalFindViewById(").append(resExpr).append(");\n");
				} else {
					out.append("\t\t").append(setterName(view.propertyName)).append("(hasViews.internalFindViewById(").append(resExpr).append("));\n");
				}
			}
			appendViewListeners(out, model);
			for (String method : model.afterViews) {
				out.append("\t\t").append(method).append("();\n");
			}
			out.append("\t}\n\n");
		}
		appendOptionsMenuMethods(out, model);
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
		boolean hasViewSupport = !model.viewByIds.isEmpty() || !model.afterViews.isEmpty()
			|| !model.clickListeners.isEmpty() || !model.longClickListeners.isEmpty();
		out.append("public final class ").append(model.generatedSimpleName())
				.append(" extends ").append(model.qualifiedName)
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
		if (!model.optionsMenus.isEmpty() || !model.injectMenuInjections.isEmpty() || !model.optionsMenuItemInjections.isEmpty()) {
			out.append("\t\tsetHasOptionsMenu(true);\n");
		}
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
			for (MvpModel.FieldInjection view : model.viewByIds) {
				String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", view.propertyName, view.resId, view.resName, rClassIndex);
				if (view.jvmField) {
					out.append("\t\tthis.").append(view.propertyName).append(" = hasViews.internalFindViewById(").append(resExpr).append(");\n");
				} else {
					out.append("\t\t").append(setterName(view.propertyName)).append("(hasViews.internalFindViewById(").append(resExpr).append("));\n");
				}
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
			for (MvpModel.FieldInjection view : model.viewByIds) {
				String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "id", view.propertyName, view.resId, view.resName, rClassIndex);
				if (view.jvmField) {
					out.append("\t\tthis.").append(view.propertyName).append(" = hasViews.internalFindViewById(").append(resExpr).append(");\n");
				} else {
					out.append("\t\t").append(setterName(view.propertyName)).append("(hasViews.internalFindViewById(").append(resExpr).append("));\n");
				}
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

	/** Mirrors APT ContextCompatAwareResHandler branch selection for one resource field. */
	private void appendResAssign(StringBuilder out, MvpModel.Kind kind, MvpModel.ResField resField) {
		String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, resField.rInnerClass, resField.propertyName, resField.resId, resField.resName, rClassIndex);
		ResCompat.Mode mode = resCompatModes.get(resField.method);
		String contextRef = kind == MvpModel.Kind.BEAN ? "context_" : (kind == MvpModel.Kind.FRAGMENT ? "getActivity()" : "this");
		String contextCall = kind == MvpModel.Kind.BEAN ? "context_." + resField.method : (kind == MvpModel.Kind.FRAGMENT ? "getActivity()." + resField.method : resField.method);
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
			out.append("\t\tthis.").append(propertyName).append(" = ").append(expression).append(";\n");
		} else {
			out.append("\t\t").append(setterName(propertyName)).append("(").append(expression).append(");\n");
		}
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

	private void appendIntentBuilder(StringBuilder out, MvpModel model) {
		String generated = model.generatedSimpleName();
		out.append("\tpublic static class IntentBuilder_ extends org.androidannotations.api.builder.ActivityIntentBuilder<").append(generated).append(".IntentBuilder_> {\n\n");
		out.append("\t\tpublic IntentBuilder_(android.content.Context context) {\n");
		out.append("\t\t\tsuper(context, ").append(generated).append(".class);\n");
		out.append("\t\t}\n\n");

		for (MvpModel.FieldInjection extra : model.extras) {
			String constant = KspAnnotationUtils.extraKeyConstant(extra.propertyName);
			String paramType = toSimpleName(extra.extraFullType != null ? extra.extraFullType : extra.typeName);
			out.append("\t\tpublic ").append(generated).append(".IntentBuilder_ ").append(extra.propertyName).append('(').append(paramType).append(" ")
					.append(extra.propertyName).append(") {\n");
			out.append("\t\t\treturn super.extra(").append(constant).append(", ")
					.append(extra.extraWriteExpr.replace("{arg}", extra.propertyName)).append(");\n");
			out.append("\t\t}\n\n");
		}

		out.append("\t\t@Override\n");
		out.append("\t\tpublic org.androidannotations.api.builder.PostActivityStarter startForResult(int requestCode) {\n");
		out.append("\t\t\tif (context instanceof android.app.Activity) {\n");
		out.append("\t\t\t\tandroid.app.Activity activity = ((android.app.Activity) context);\n");
		out.append("\t\t\t\tactivity.startActivityForResult(intent, requestCode);\n");
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
			out.append("\t\t\t\t\t").append(model.generatedSimpleName()).append(".super.").append(method.name).append("(");
			appendParamNames(out, method.params);
			out.append(");\n");
			out.append("\t\t\t\t} catch (final Throwable e) {\n");
			out.append("\t\t\t\t\tThread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);\n");
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

			out.append("\t\torg.androidannotations.api.UiThreadExecutor.runTask(\"").append(escape(method.id))
					.append("\", new Runnable() {\n");
			out.append("\t\t\t@Override\n");
			out.append("\t\t\tpublic void run() {\n");
			out.append("\t\t\t\t").append(model.generatedSimpleName()).append(".super.").append(method.name).append("(");
			for (int i = 0; i < method.params.size(); i++) {
				if (i > 0) {
					out.append(", ");
				}
				out.append(method.params.get(i).name);
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
			if (param.nullAnnotation != null) {
				out.append("@org.jetbrains.annotations.").append(param.nullAnnotation).append(' ');
			}
			if (makeFinal) {
				out.append("final ");
			}
			out.append(toSimpleName(param.typeName)).append(' ').append(param.name);
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
