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
		for (String method : model.afterViews) {
			out.append("\t\t").append(method).append("();\n");
		}
		out.append("\t}\n\n");

		appendBeanHolderMethods(out);
		appendBackgroundMethods(out, model);
		appendUiThreadMethods(out, model);
		appendIntentBuilder(out, model);

		out.append("}\n");
		return out.toString();
	}

	private String generateBean(MvpModel model) {
		StringBuilder out = new StringBuilder();
		appendHeader(out, model);
		out.append("public final class ").append(model.generatedSimpleName())
				.append(" extends ").append(model.simpleName)
				.append(" implements org.androidannotations.api.view.OnViewChangedListener {\n\n");
		out.append("\tprivate android.content.Context context_;\n");
		out.append("\tprivate Object rootFragment_;\n\n");

		out.append("\tprivate ").append(model.generatedSimpleName()).append("(android.content.Context context) {\n");
		out.append("\t\tthis.context_ = context;\n");
		out.append("\t\tinit_();\n");
		out.append("\t}\n\n");

		out.append("\tprivate ").append(model.generatedSimpleName()).append("(android.content.Context context, Object rootFragment) {\n");
		out.append("\t\tthis.context_ = context;\n");
		out.append("\t\tthis.rootFragment_ = rootFragment;\n");
		out.append("\t\tinit_();\n");
		out.append("\t}\n\n");

		out.append("\tpublic static ").append(model.generatedSimpleName()).append(" getInstance_(android.content.Context context) {\n");
		out.append("\t\treturn new ").append(model.generatedSimpleName()).append("(context);\n");
		out.append("\t}\n\n");

		out.append("\tpublic static ").append(model.generatedSimpleName()).append(" getInstance_(android.content.Context context, Object rootFragment) {\n");
		out.append("\t\treturn new ").append(model.generatedSimpleName()).append("(context, rootFragment);\n");
		out.append("\t}\n\n");

		out.append("\tprivate void init_() {\n");
		if (!model.stringRes.isEmpty()) {
			out.append("\t\tandroid.content.res.Resources resources_ = context_.getResources();\n");
		}
		for (MvpModel.FieldInjection injection : model.stringRes) {
			String resExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName, "string", injection.propertyName, injection.resId, injection.resName, rClassIndex);
			appendAssign(out, injection, "resources_.getString(" + resExpr + ")");
		}
		out.append("\t\torg.androidannotations.api.view.OnViewChangedNotifier.registerOnViewChangedListener(this);\n");
		out.append("\t}\n\n");

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

		appendBackgroundMethods(out, model);
		appendUiThreadMethods(out, model);

		out.append("}\n");
		return out.toString();
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
			out.append("\tpublic void ").append(method.name).append("(");
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
			out.append("\tpublic void ").append(method.name).append("(");
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
