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
package org.androidannotations.ksp.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.devtools.ksp.processing.Resolver;
import com.google.devtools.ksp.symbol.ClassKind;
import com.google.devtools.ksp.symbol.KSAnnotation;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.KSDeclaration;
import com.google.devtools.ksp.symbol.KSFile;
import com.google.devtools.ksp.symbol.KSFunctionDeclaration;
import com.google.devtools.ksp.symbol.KSNode;
import com.google.devtools.ksp.symbol.KSPropertyDeclaration;
import com.google.devtools.ksp.symbol.KSType;
import com.google.devtools.ksp.symbol.KSTypeReference;
import com.google.devtools.ksp.symbol.KSValueParameter;
import com.google.devtools.ksp.symbol.Modifier;

import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;

import org.androidannotations.ksp.AnnotationNames;
import org.androidannotations.ksp.KspAnnotationUtils;
import org.androidannotations.ksp.MvpModel;
import org.androidannotations.ksp.ResCompat;

/**
 * Builds the {@link MvpModel} for one annotated class. Mirrors APT ModelExtractor + ModelProcessor:
 * own declarations first, then members inherited from the superclass chain (farthest-first). The
 * walk stops at java.lang.Object and before any android.* class; interfaces are not followed.
 */
public final class MvpHandler {

	private static final int MAX_SUPERCLASS_DEPTH = 64;

/**
 * Builds the model for one annotated class: own declarations first, then members inherited from
 * the superclass chain (farthest-first), mirroring APT ModelExtractor + ModelProcessor. Ancestor
 * class-level annotations are intentionally not collected (APT: currently do not take into account).
 */
	public MvpModel build(Resolver resolver, KSClassDeclaration classDeclaration, String classSuffix) {
		String packageName = "";
		if (classDeclaration.getPackageName().asString() != null) {
			packageName = classDeclaration.getPackageName().asString();
		}
		String simpleName = classDeclaration.getSimpleName().asString();
		String qualifiedName = classDeclaration.getQualifiedName() != null
				? classDeclaration.getQualifiedName().asString()
				: simpleName;
		boolean nested = !qualifiedName.equals(packageName.isEmpty() ? simpleName : packageName + "." + simpleName);

		KSAnnotation eActivity = KspAnnotationUtils.findAnnotation(classDeclaration.getAnnotations(), AnnotationNames.E_ACTIVITY);
		KSAnnotation eBean = KspAnnotationUtils.findAnnotation(classDeclaration.getAnnotations(), AnnotationNames.E_BEAN);
		KSAnnotation eFragment = KspAnnotationUtils.findAnnotation(classDeclaration.getAnnotations(), AnnotationNames.E_FRAGMENT);

		MvpModel.Kind kind;
		int layoutId = AnnotationNames.RES_DEFAULT;
		String layoutResName = "";
		String scope = "Default";
		boolean forceLayoutInjection = false;
		if (eActivity != null) {
			kind = MvpModel.Kind.ACTIVITY;
			layoutId = KspAnnotationUtils.intArg(eActivity, "value", AnnotationNames.RES_DEFAULT);
			layoutResName = KspAnnotationUtils.stringArg(eActivity, "resName", "");
		} else if (eFragment != null) {
			kind = MvpModel.Kind.FRAGMENT;
			layoutId = KspAnnotationUtils.intArg(eFragment, "value", AnnotationNames.RES_DEFAULT);
			layoutResName = KspAnnotationUtils.stringArg(eFragment, "resName", "");
			forceLayoutInjection = KspAnnotationUtils.booleanArg(eFragment, "forceLayoutInjection", false);
		} else if (eBean != null) {
			kind = MvpModel.Kind.BEAN;
			scope = KspAnnotationUtils.enumArg(eBean, "scope", "Default");
		} else {
			kind = MvpModel.Kind.BEAN;
		}

		MvpModel model = new MvpModel(kind, packageName, simpleName, qualifiedName, classSuffix, layoutId, layoutResName, nested, scope);
		model.forceLayoutInjection = forceLayoutInjection;

		KSAnnotation optionsMenu = KspAnnotationUtils.findAnnotation(classDeclaration.getAnnotations(), AnnotationNames.OPTIONS_MENU);
		if (optionsMenu != null) {
			collectOptionsMenu(optionsMenu, model);
		}

		collectMembers(resolver, classDeclaration.getDeclarations(), model);
		for (KSClassDeclaration ancestor : superclasses(classDeclaration)) {
			collectMembers(resolver, ancestor.getDeclarations(), model);
		}
		return model;
	}

	private void collectMembers(Resolver resolver, Sequence<KSDeclaration> declarations, MvpModel model) {
		for (KSDeclaration declaration : SequencesKt.toList(declarations)) {
			if (declaration instanceof KSPropertyDeclaration) {
				collectProperty(resolver, (KSPropertyDeclaration) declaration, model);
			} else if (declaration instanceof KSFunctionDeclaration) {
				collectFunction(resolver, (KSFunctionDeclaration) declaration, model);
			}
		}
	}

	/** Mirrors APT ModelExtractor's superclass walk: class chain only (no interfaces), farthest-first. */
	private List<KSClassDeclaration> superclasses(KSClassDeclaration classDeclaration) {
		List<KSClassDeclaration> chain = new ArrayList<>();
		KSClassDeclaration current = superclassOf(classDeclaration);
		int depth = 0;
		while (current != null && depth < MAX_SUPERCLASS_DEPTH) {
			String qualifiedName = current.getQualifiedName() != null ? current.getQualifiedName().asString() : "";
			if ("java.lang.Object".equals(qualifiedName) || qualifiedName.startsWith("android.")) {
				break;
			}
			chain.add(current);
			current = superclassOf(current);
			depth++;
		}
		Collections.reverse(chain);
		return chain;
	}

	private void collectProperty(Resolver resolver, KSPropertyDeclaration property, MvpModel model) {
		String name = property.getSimpleName().asString();
		String typeName = KspAnnotationUtils.propertyTypeName(property);
		// Kotlin exposes a directly accessible backing field only for lateinit or @JvmField properties;
		// initialized non-lateinit vars get a private backing field, so generated code must call the setter.
		boolean jvmField = KspAnnotationUtils.hasJvmField(property) || property.getModifiers().contains(Modifier.LATEINIT);

		KSAnnotation viewById = KspAnnotationUtils.findAnnotation(property.getAnnotations(), AnnotationNames.VIEW_BY_ID);
		if (viewById != null) {
			model.viewByIds.add(new MvpModel.FieldInjection(name, typeName, jvmField,
					KspAnnotationUtils.intArg(viewById, "value", AnnotationNames.RES_DEFAULT),
					KspAnnotationUtils.stringArg(viewById, "resName", "")));
		}

		KSAnnotation stringRes = KspAnnotationUtils.findAnnotation(property.getAnnotations(), AnnotationNames.STRING_RES);
		if (stringRes != null) {
			model.stringRes.add(new MvpModel.FieldInjection(name, typeName, jvmField,
					KspAnnotationUtils.intArg(stringRes, "value", AnnotationNames.RES_DEFAULT),
					KspAnnotationUtils.stringArg(stringRes, "resName", "")));
		}

		for (ResCompat.Type type : ResCompat.ALL) {
			KSAnnotation res = KspAnnotationUtils.findAnnotation(property.getAnnotations(), type.annotationName);
			if (res == null) {
				continue;
			}
			// APT parity (AbstractResHandler.validate + InjectHelper): final fields first, then the allowed field type.
			if (!property.isMutable()) {
				model.errors.add(type.annotationName + " cannot be used on a final element");
				continue;
			}
			String javaType = KspAnnotationUtils.fullJavaTypeName(property.getType());
			if (!type.allowedTypes.contains(javaType)) {
				model.errors.add(type.annotationName + " can only be used on a field which is a " + type.allowedTypes + ", not " + javaType);
				continue;
			}
			if (property.getModifiers().contains(Modifier.PRIVATE)) {
				model.errors.add(type.annotationName + " cannot be used on a private element");
				continue;
			}
			model.resFields.add(new MvpModel.ResField(name, typeName, jvmField,
				KspAnnotationUtils.intArg(res, "value", AnnotationNames.RES_DEFAULT),
				KspAnnotationUtils.stringArg(res, "resName", ""),
				type.rInnerClass, type.method, type.invocation));
		}

		KSAnnotation extra = KspAnnotationUtils.findAnnotation(property.getAnnotations(), AnnotationNames.EXTRA);
		if (extra != null) {
			String fullType = KspAnnotationUtils.fullTypeNameWithArgs(property.getType());
			ExtraTemplates templates = extraTemplatesFor(resolver, fullType);
			model.extras.add(new MvpModel.FieldInjection(name, typeName, jvmField,
					AnnotationNames.RES_DEFAULT,
					KspAnnotationUtils.stringArg(extra, "value", ""),
					fullType,
					templates.write,
					templates.read,
					templates.unchecked));
		}

		KSAnnotation fragmentArg = KspAnnotationUtils.findAnnotation(property.getAnnotations(), AnnotationNames.FRAGMENT_ARG);
		if (fragmentArg != null) {
			String fullType = KspAnnotationUtils.fullTypeNameWithArgs(property.getType());
			ExtraTemplates templates = templatesFor(resolver, fullType, "args_");
			model.fragmentArgs.add(new MvpModel.FieldInjection(name, typeName, jvmField,
					AnnotationNames.RES_DEFAULT,
					KspAnnotationUtils.stringArg(fragmentArg, "value", ""),
					fullType,
					templates.write,
					templates.read,
					templates.unchecked));
		}

		KSAnnotation instanceState = KspAnnotationUtils.findAnnotation(property.getAnnotations(), AnnotationNames.INSTANCE_STATE);
		if (instanceState != null) {
			collectInstanceState(resolver, property, name, model);
		}

		KSAnnotation fragmentById = KspAnnotationUtils.findAnnotation(property.getAnnotations(), AnnotationNames.FRAGMENT_BY_ID);
		if (fragmentById != null) {
			model.fragmentByIds.add(new MvpModel.FragmentInjection(name, typeName,
					KspAnnotationUtils.intArg(fragmentById, "value", AnnotationNames.RES_DEFAULT),
					KspAnnotationUtils.stringArg(fragmentById, "resName", ""),
					KspAnnotationUtils.booleanArg(fragmentById, "childFragment", false)));
		}

		KSAnnotation fragmentByTag = KspAnnotationUtils.findAnnotation(property.getAnnotations(), AnnotationNames.FRAGMENT_BY_TAG);
		if (fragmentByTag != null) {
			model.fragmentByTags.add(new MvpModel.FragmentInjection(name, typeName,
					AnnotationNames.RES_DEFAULT,
					KspAnnotationUtils.stringArg(fragmentByTag, "value", ""),
					KspAnnotationUtils.booleanArg(fragmentByTag, "childFragment", false)));
		}

		KSAnnotation bean = KspAnnotationUtils.findAnnotation(property.getAnnotations(), AnnotationNames.BEAN);
		if (bean != null) {
			collectBeanField(resolver, property, name, jvmField, model);
		}

		KSAnnotation systemService = KspAnnotationUtils.findAnnotation(property.getAnnotations(), AnnotationNames.SYSTEM_SERVICE);
		if (systemService != null) {
			collectSystemServiceField(resolver, property, name, jvmField, model);
		}

		KSAnnotation injectMenu = KspAnnotationUtils.findAnnotation(property.getAnnotations(), AnnotationNames.INJECT_MENU);
		if (injectMenu != null) {
			collectMenuField(resolver, property, name, false, injectMenu, model);
		}

		KSAnnotation optionsMenuItem = KspAnnotationUtils.findAnnotation(property.getAnnotations(), AnnotationNames.OPTIONS_MENU_ITEM);
		if (optionsMenuItem != null) {
			collectMenuField(resolver, property, name, true, optionsMenuItem, model);
		}
	}

	private void collectFunction(Resolver resolver, KSFunctionDeclaration function, MvpModel model) {
		String name = function.getSimpleName().asString();
		if ("<init>".equals(name)) {
			return;
		}

		KSAnnotation afterInject = KspAnnotationUtils.findAnnotation(function.getAnnotations(), AnnotationNames.AFTER_INJECT);
		if (afterInject != null) {
			if (model.kind != MvpModel.Kind.ACTIVITY && model.kind != MvpModel.Kind.FRAGMENT) {
				model.errors.add("@AfterInject is only supported in @EActivity or @EFragment components: method '" + name + "'");
			} else if (!function.getParameters().isEmpty()) {
				model.errors.add("@AfterInject method '" + name + "' must have no parameters");
			} else if (function.getModifiers().contains(Modifier.PRIVATE)) {
				model.errors.add("@AfterInject method '" + name + "' must not be private");
			} else if (!"void".equals(functionReturnType(function))) {
				model.errors.add("@AfterInject can only be used on a method with a void return type");
			} else {
				model.afterInject.add(name);
			}
		}

		if (KspAnnotationUtils.findAnnotation(function.getAnnotations(), AnnotationNames.AFTER_VIEWS) != null) {
			model.afterViews.add(name);
		}

		if (KspAnnotationUtils.findAnnotation(function.getAnnotations(), AnnotationNames.AFTER_EXTRAS) != null) {
			model.afterExtras.add(name);
		}

		KSAnnotation background = KspAnnotationUtils.findAnnotation(function.getAnnotations(), AnnotationNames.BACKGROUND);
		if (background != null) {
			model.backgrounds.add(new MvpModel.MethodInfo(name, KspAnnotationUtils.methodParams(function),
					KspAnnotationUtils.stringArg(background, "id", ""),
					KspAnnotationUtils.longArg(background, "delay", 0L),
					KspAnnotationUtils.stringArg(background, "serial", ""),
					false, methodVisibility(function)));
		}

		KSAnnotation uiThread = KspAnnotationUtils.findAnnotation(function.getAnnotations(), AnnotationNames.UI_THREAD);
		if (uiThread != null) {
			Object propagation = KspAnnotationUtils.argValue(uiThread, "propagation");
			boolean reuse = true;
			if (propagation != null) {
				reuse = propagation.toString().endsWith("REUSE");
			}
			model.uiThreads.add(new MvpModel.MethodInfo(name, KspAnnotationUtils.methodParams(function),
					KspAnnotationUtils.stringArg(uiThread, "id", ""),
					KspAnnotationUtils.longArg(uiThread, "delay", 0L),
					"",
					reuse, methodVisibility(function)));
		}

		KSAnnotation bean = KspAnnotationUtils.findAnnotation(function.getAnnotations(), AnnotationNames.BEAN);
		if (bean != null) {
			collectBeanMethod(resolver, function, name, model);
		} else if (hasBeanParam(function)) {
			collectBeanParams(resolver, function, name, model);
		}

		KSAnnotation systemService = KspAnnotationUtils.findAnnotation(function.getAnnotations(), AnnotationNames.SYSTEM_SERVICE);
		if (systemService != null) {
			collectSystemServiceMethod(resolver, function, name, model);
		} else if (hasSystemServiceParam(function)) {
			collectSystemServiceParams(resolver, function, name, model);
		}

		KSAnnotation click = KspAnnotationUtils.findAnnotation(function.getAnnotations(), AnnotationNames.CLICK);
		if (click != null) {
			collectViewListener(resolver, function, name, click, false, model);
		}

		KSAnnotation longClick = KspAnnotationUtils.findAnnotation(function.getAnnotations(), AnnotationNames.LONG_CLICK);
		if (longClick != null) {
			collectViewListener(resolver, function, name, longClick, true, model);
		}

		KSAnnotation optionsItem = KspAnnotationUtils.findAnnotation(function.getAnnotations(), AnnotationNames.OPTIONS_ITEM);
		if (optionsItem != null) {
			collectOptionsItem(resolver, function, name, optionsItem, model);
		}

		KSAnnotation injectMenu = KspAnnotationUtils.findAnnotation(function.getAnnotations(), AnnotationNames.INJECT_MENU);
		if (injectMenu != null) {
			collectMenuMethod(resolver, function, name, false, injectMenu, model);
		} else if (hasMenuParam(function, AnnotationNames.INJECT_MENU)) {
			collectMenuParams(resolver, function, name, false, model);
		}

		KSAnnotation optionsMenuItem = KspAnnotationUtils.findAnnotation(function.getAnnotations(), AnnotationNames.OPTIONS_MENU_ITEM);
		if (optionsMenuItem != null) {
			collectMenuMethod(resolver, function, name, true, optionsMenuItem, model);
		} else if (hasMenuParam(function, AnnotationNames.OPTIONS_MENU_ITEM)) {
			collectMenuParams(resolver, function, name, true, model);
		}

		KSAnnotation onActivityResult = KspAnnotationUtils.findAnnotation(function.getAnnotations(), AnnotationNames.ON_ACTIVITY_RESULT);
		if (onActivityResult != null) {
			collectActivityResult(resolver, function, name, onActivityResult, model);
		}
	}

	/** APT elementVisibilityModifierToJMod parity: generated @Background/@UiThread overrides keep the source method's visibility. */
	private static String methodVisibility(KSFunctionDeclaration function) {
		java.util.Set<Modifier> modifiers = function.getModifiers();
		if (modifiers.contains(Modifier.PUBLIC)) {
			return "public";
		}
		if (modifiers.contains(Modifier.PROTECTED)) {
			return "protected";
		}
		if (modifiers.contains(Modifier.PRIVATE)) {
			return "private";
		}
		// No explicit visibility: Kotlin defaults to public, Java to package-private; KSP omits an implicit public.
		KSNode parent = function.getParent();
		while (parent != null && !(parent instanceof KSFile)) {
			parent = parent.getParent();
		}
		if (parent instanceof KSFile) {
			String fileName = ((KSFile) parent).getFileName();
			if (fileName.endsWith(".kt")) {
				return "public";
			}
		}
		return "";
	}

	/** APT AbstractViewListenerHandler parity: @Click/@LongClick method validation and id collection. */
	private void collectViewListener(Resolver resolver, KSFunctionDeclaration function, String name,
		KSAnnotation annotation, boolean longClick, MvpModel model) {
		String label = longClick ? "@LongClick" : "@Click";
		if (model.kind != MvpModel.Kind.ACTIVITY && model.kind != MvpModel.Kind.FRAGMENT) {
			model.errors.add(label + " is only supported in @EActivity or @EFragment components: method \'" + name + "\'");
			return;
		}
		List<MvpModel.Param> params = KspAnnotationUtils.methodParams(function);
		if (params.size() > 1) {
			model.errors.add(label + " method \'" + name + "\' must have at most one parameter");
			return;
		}
		if (!params.isEmpty() && !isViewSubtype(resolver, params.get(0).typeName)) {
			model.errors.add(label + " method \'" + name + "\' parameter must be a View subtype");
			return;
		}
		String returnType = functionReturnType(function);
		boolean returnsBoolean = false;
		if (longClick) {
			if (!"void".equals(returnType) && !"boolean".equals(returnType)) {
				model.errors.add(label + " method \'" + name + "\' must return void or boolean");
				return;
			}
			returnsBoolean = "boolean".equals(returnType);
		} else if (!"void".equals(returnType)) {
			model.errors.add(label + " method \'" + name + "\' must return void");
			return;
		}
		List<Integer> valueIds = KspAnnotationUtils.intArrayArg(annotation, "value");
		if (valueIds.size() == 1 && valueIds.get(0) == AnnotationNames.RES_DEFAULT) {
			valueIds = new ArrayList<>();
		}
		List<String> nameIds;
		if (!valueIds.isEmpty()) {
			nameIds = new ArrayList<>();
		} else {
			nameIds = KspAnnotationUtils.stringArrayArg(annotation, "resName");
			if (nameIds.isEmpty() || nameIds.size() == 1 && "".equals(nameIds.get(0))) {
				// APT USE_ELEMENT_NAME parity: the default id is the raw method name (no suffix stripping).
				nameIds = Collections.singletonList(name);
			}
		}
		List<MvpModel.ViewListener> target = longClick ? model.longClickListeners : model.clickListeners;
		for (MvpModel.ViewListener other : target) {
			if ((!valueIds.isEmpty() && other.valueIds.equals(valueIds)) || (!nameIds.isEmpty() && other.nameIds.equals(nameIds))) {
				model.errors.add(label + " on method \'" + name + "\' has duplicate resource ids");
				return;
			}
		}
		target.add(new MvpModel.ViewListener(longClick, name, params, returnsBoolean, valueIds, nameIds));
	}

/** APT OptionsMenuHandler parity: class-level @OptionsMenu menu resource refs (NEED_RES_ID, no element-name fallback). */
	private void collectOptionsMenu(KSAnnotation annotation, MvpModel model) {
		if (model.kind != MvpModel.Kind.ACTIVITY && model.kind != MvpModel.Kind.FRAGMENT) {
			model.errors.add("@OptionsMenu is only supported in @EActivity or @EFragment components");
			return;
		}
		List<Integer> ids = KspAnnotationUtils.intArrayArg(annotation, "value");
		if (ids.size() == 1 && ids.get(0) == AnnotationNames.RES_DEFAULT) {
			String resName = KspAnnotationUtils.stringArg(annotation, "resName", "");
			if (resName.isEmpty()) {
				model.errors.add("@OptionsMenu requires a menu resource id");
				return;
			}
			model.optionsMenus.add(new MvpModel.OptionsMenuRef(AnnotationNames.RES_DEFAULT, resName));
			return;
		}
		for (Integer id : ids) {
			if (id != AnnotationNames.RES_DEFAULT) {
				model.optionsMenus.add(new MvpModel.OptionsMenuRef(id, ""));
			}
		}
	}

	/** APT InjectHelper.processField parity: @InjectMenu/@OptionsMenuItem on a field. */
	private void collectMenuField(Resolver resolver, KSPropertyDeclaration property, String name, boolean menuItem,
			KSAnnotation annotation, MvpModel model) {
		String label = menuItem ? "@OptionsMenuItem" : "@InjectMenu";
		if (model.kind != MvpModel.Kind.ACTIVITY && model.kind != MvpModel.Kind.FRAGMENT) {
			model.errors.add(label + " is only supported in @EActivity or @EFragment components: property '" + name + "'");
			return;
		}
		if (!property.isMutable()) {
			model.errors.add(label + " cannot be used on a final element: property '" + name + "'");
			return;
		}
		if (property.getModifiers().contains(Modifier.PRIVATE)) {
			model.errors.add(label + " property '" + name + "' must not be private");
			return;
		}
		String javaType = KspAnnotationUtils.fullJavaTypeName(property.getType());
		KSClassDeclaration typeDecl = resolveClassDeclaration(resolver, javaType);
		String targetName = menuItem ? "android.view.MenuItem" : "android.view.Menu";
		if (!targetName.equals(javaType) && (typeDecl == null || !implementsInterface(typeDecl, targetName))) {
			model.errors.add(label + " property '" + name + "' must be a " + (menuItem ? "MenuItem" : "Menu") + " subtype");
			return;
		}
		int resId = AnnotationNames.RES_DEFAULT;
		String resName = "";
		if (menuItem) {
			List<Integer> valueIds = KspAnnotationUtils.intArrayArg(annotation, "value");
			if (!(valueIds.size() == 1 && valueIds.get(0) == AnnotationNames.RES_DEFAULT)) {
				resId = valueIds.get(0); // APT extractOneAnnotationFieldRef: first id only
			} else {
				List<String> nameIds = KspAnnotationUtils.stringArrayArg(annotation, "resName");
				if (!nameIds.isEmpty() && !"".equals(nameIds.get(0))) {
					resName = nameIds.get(0);
				}
			}
			if (resId == AnnotationNames.RES_DEFAULT && resName.isEmpty()) {
				resName = name; // APT USE_ELEMENT_NAME: raw property name
			}
		}
		List<MvpModel.MenuInjection> target = menuItem ? model.optionsMenuItemInjections : model.injectMenuInjections;
		target.add(new MvpModel.MenuInjection(true, name, resId, resName, null, null, null, true));
	}

	/** APT OptionsItemHandler parity: @OptionsItem method validation and id collection. */
	private void collectOptionsItem(Resolver resolver, KSFunctionDeclaration function, String name,
			KSAnnotation annotation, MvpModel model) {
		if (model.kind != MvpModel.Kind.ACTIVITY && model.kind != MvpModel.Kind.FRAGMENT) {
			model.errors.add("@OptionsItem is only supported in @EActivity or @EFragment components: method '" + name + "'");
			return;
		}
		if (function.getModifiers().contains(Modifier.PRIVATE)) {
			model.errors.add("@OptionsItem method '" + name + "' must not be private");
			return;
		}
		List<MvpModel.Param> params = KspAnnotationUtils.methodParams(function);
		if (params.size() > 1) {
			model.errors.add("@OptionsItem method '" + name + "' must have at most one parameter");
			return;
		}
		if (!params.isEmpty() && !isMenuItemSubtype(resolver, params.get(0).typeName)) {
			model.errors.add("@OptionsItem method '" + name + "' parameter must be a MenuItem subtype");
			return;
		}
		String returnType = functionReturnType(function);
		boolean returnsBoolean = false;
		if (!"void".equals(returnType) && !"boolean".equals(returnType) && !"Boolean".equals(returnType)
				&& !"java.lang.Boolean".equals(returnType)) {
			model.errors.add("@OptionsItem method '" + name + "' must return void or boolean");
			return;
		}
		returnsBoolean = !"void".equals(returnType);
		List<Integer> valueIds = KspAnnotationUtils.intArrayArg(annotation, "value");
		if (valueIds.size() == 1 && valueIds.get(0) == AnnotationNames.RES_DEFAULT) {
			valueIds = new ArrayList<>();
		}
		List<String> nameIds;
		if (!valueIds.isEmpty()) {
			nameIds = new ArrayList<>();
		} else {
			nameIds = KspAnnotationUtils.stringArrayArg(annotation, "resName");
			if (nameIds.isEmpty() || nameIds.size() == 1 && "".equals(nameIds.get(0))) {
				// APT USE_ELEMENT_NAME parity: the default id is the raw method name.
				nameIds = Collections.singletonList(name);
			}
		}
		for (MvpModel.OptionsItem other : model.optionsItems) {
			if ((!valueIds.isEmpty() && other.valueIds.equals(valueIds)) || (!nameIds.isEmpty() && other.nameIds.equals(nameIds))) {
				model.errors.add("@OptionsItem on method '" + name + "' has duplicate resource ids");
				return;
			}
		}
		model.optionsItems.add(new MvpModel.OptionsItem(name, returnsBoolean, !params.isEmpty(), valueIds, nameIds));
	}

	private boolean isMenuItemSubtype(Resolver resolver, String typeName) {
		if ("android.view.MenuItem".equals(typeName) || "MenuItem".equals(typeName)) {
			return true;
		}
		KSClassDeclaration declaration = resolveClassDeclaration(resolver, typeName);
		return declaration != null && implementsInterface(declaration, "android.view.MenuItem");
	}

	/** APT InjectHelper.processMethod parity: @InjectMenu/@OptionsMenuItem on a method with exactly one parameter. */
	private void collectMenuMethod(Resolver resolver, KSFunctionDeclaration function, String name, boolean menuItem,
			KSAnnotation annotation, MvpModel model) {
		String label = menuItem ? "@OptionsMenuItem" : "@InjectMenu";
		if (model.kind != MvpModel.Kind.ACTIVITY && model.kind != MvpModel.Kind.FRAGMENT) {
			model.errors.add(label + " is only supported in @EActivity or @EFragment components: method '" + name + "'");
			return;
		}
		if (function.getModifiers().contains(Modifier.PRIVATE)) {
			model.errors.add(label + " method '" + name + "' must not be private");
			return;
		}
		List<KSValueParameter> params = function.getParameters();
		if (params.size() != 1) {
			model.errors.add(label + " method '" + name + "' must have exactly one parameter");
			return;
		}
		String javaType = KspAnnotationUtils.fullJavaTypeName(params.get(0).getType());
		KSClassDeclaration typeDecl = resolveClassDeclaration(resolver, javaType);
		String targetName = menuItem ? "android.view.MenuItem" : "android.view.Menu";
		if (!targetName.equals(javaType) && (typeDecl == null || !implementsInterface(typeDecl, targetName))) {
			model.errors.add(label + " method '" + name + "' parameter must be a " + (menuItem ? "MenuItem" : "Menu") + " subtype");
			return;
		}
		List<Integer> valueIds = new ArrayList<>();
		List<String> nameIds = new ArrayList<>();
		if (menuItem) {
			valueIds = KspAnnotationUtils.intArrayArg(annotation, "value");
			if (valueIds.size() == 1 && valueIds.get(0) == AnnotationNames.RES_DEFAULT) {
				valueIds = new ArrayList<>();
			} else {
				valueIds = Collections.singletonList(valueIds.get(0)); // APT extractOneAnnotationFieldRef: first id only
			}
			if (valueIds.isEmpty()) {
				nameIds = KspAnnotationUtils.stringArrayArg(annotation, "resName");
				if (nameIds.isEmpty() || nameIds.size() == 1 && "".equals(nameIds.get(0))) {
					nameIds = Collections.singletonList(name); // APT USE_ELEMENT_NAME: raw method name
				} else {
					nameIds = Collections.singletonList(nameIds.get(0));
				}
			}
		}
		List<MvpModel.MenuInjection> target = menuItem ? model.optionsMenuItemInjections : model.injectMenuInjections;
		target.add(new MvpModel.MenuInjection(false, name, AnnotationNames.RES_DEFAULT, "", KspAnnotationUtils.methodParams(function),
				Collections.singletonList(valueIds), Collections.singletonList(nameIds), true));
	}

	/** APT InjectHelper.processParam parity: one shared braced block per method; the call is emitted only when ALL parameters are annotated. */
	private void collectMenuParams(Resolver resolver, KSFunctionDeclaration function, String name, boolean menuItem, MvpModel model) {
		String label = menuItem ? "@OptionsMenuItem" : "@InjectMenu";
		if (model.kind != MvpModel.Kind.ACTIVITY && model.kind != MvpModel.Kind.FRAGMENT) {
			model.errors.add(label + " is only supported in @EActivity or @EFragment components: method '" + name + "'");
			return;
		}
		if (function.getModifiers().contains(Modifier.PRIVATE)) {
			model.errors.add(label + " method '" + name + "' must not be private");
			return;
		}
		String annotationName = menuItem ? AnnotationNames.OPTIONS_MENU_ITEM : AnnotationNames.INJECT_MENU;
		List<KSValueParameter> params = function.getParameters();
		List<MvpModel.Param> paramModels = KspAnnotationUtils.methodParams(function);
		List<List<Integer>> valueIdsList = new ArrayList<>();
		List<List<String>> nameIdsList = new ArrayList<>();
		boolean allAnnotated = true;
		boolean anyAnnotated = false;
		for (KSValueParameter param : params) {
			KSAnnotation menuAnn = findParamMenu(param, annotationName);
			if (menuAnn == null) {
				allAnnotated = false;
				valueIdsList.add(new ArrayList<Integer>());
				nameIdsList.add(new ArrayList<String>());
				continue;
			}
			anyAnnotated = true;
			String javaType = KspAnnotationUtils.fullJavaTypeName(param.getType());
			KSClassDeclaration typeDecl = resolveClassDeclaration(resolver, javaType);
			String targetName = menuItem ? "android.view.MenuItem" : "android.view.Menu";
			if (!targetName.equals(javaType) && (typeDecl == null || !implementsInterface(typeDecl, targetName))) {
				model.errors.add(label + " on parameter '" + param.getName().asString() + "' of method '" + name
						+ "' must be a " + (menuItem ? "MenuItem" : "Menu") + " subtype");
				valueIdsList.add(new ArrayList<Integer>());
				nameIdsList.add(new ArrayList<String>());
				continue;
			}
			List<Integer> valueIds = new ArrayList<>();
			List<String> nameIds = new ArrayList<>();
			if (menuItem) {
				valueIds = KspAnnotationUtils.intArrayArg(menuAnn, "value");
				if (valueIds.size() == 1 && valueIds.get(0) == AnnotationNames.RES_DEFAULT) {
					valueIds = new ArrayList<>();
				} else {
					valueIds = Collections.singletonList(valueIds.get(0)); // first id only
				}
				if (valueIds.isEmpty()) {
					nameIds = KspAnnotationUtils.stringArrayArg(menuAnn, "resName");
					if (nameIds.isEmpty() || nameIds.size() == 1 && "".equals(nameIds.get(0))) {
						nameIds = Collections.singletonList(param.getName().asString()); // APT USE_ELEMENT_NAME: param name
					} else {
						nameIds = Collections.singletonList(nameIds.get(0));
					}
				}
			}
			valueIdsList.add(valueIds);
			nameIdsList.add(nameIds);
		}
		if (anyAnnotated) {
			// partially annotated: APT leaves dead locals (decl+assign, no invocation) and no error
			List<MvpModel.MenuInjection> target = menuItem ? model.optionsMenuItemInjections : model.injectMenuInjections;
			target.add(new MvpModel.MenuInjection(false, name, AnnotationNames.RES_DEFAULT, "", paramModels,
					valueIdsList, nameIdsList, allAnnotated));
		}
	}

	private static KSAnnotation findParamMenu(KSValueParameter param, String qualifiedName) {
		for (KSAnnotation annotation : SequencesKt.toList(param.getAnnotations())) {
			if (qualifiedName.equals(KspAnnotationUtils.safeQualifiedName(annotation))) {
				return annotation;
			}
		}
		return null;
	}

	private static boolean hasMenuParam(KSFunctionDeclaration function, String qualifiedName) {
		for (KSValueParameter param : function.getParameters()) {
			if (findParamMenu(param, qualifiedName) != null) {
				return true;
			}
		}
		return false;
	}

	private String functionReturnType(KSFunctionDeclaration function) {
		KSTypeReference ref = function.getReturnType();
		if (ref == null) {
			return "void";
		}
		try {
			return KspAnnotationUtils.typeName(ref);
		} catch (Exception e) {
			return "void";
		}
	}

	private boolean isViewSubtype(Resolver resolver, String typeName) {
		if ("android.view.View".equals(typeName) || "View".equals(typeName)) {
			return true;
		}
		KSClassDeclaration declaration = resolveClassDeclaration(resolver, typeName);
		return declaration != null && implementsInterface(declaration, "android.view.View");
	}

	/** APT BeanHandler.processField parity: one assignment target in the init-body injection block. */
	private void collectBeanField(Resolver resolver, KSPropertyDeclaration property, String name, boolean jvmField, MvpModel model) {
		if (model.kind != MvpModel.Kind.ACTIVITY && model.kind != MvpModel.Kind.FRAGMENT) {
			model.errors.add("@Bean is only supported in @EActivity or @EFragment components: property '" + name + "'");
			return;
		}
		if (property.getModifiers().contains(Modifier.PRIVATE)) {
			model.errors.add("@Bean property '" + name + "' must not be private");
			return;
		}
		KSAnnotation bean = KspAnnotationUtils.findAnnotation(property.getAnnotations(), AnnotationNames.BEAN);
		MvpModel.BeanTarget target = resolveBeanTarget(resolver, property.getType(), bean, name, model);
		if (target != null) {
			model.beans.add(new MvpModel.BeanInjection(MvpModel.BeanInjection.Form.FIELD, name, jvmField,
					Collections.singletonList(target)));
		}
	}

	/** APT BeanHandler.processMethod parity: @Bean on a method with exactly one parameter. */
	private void collectBeanMethod(Resolver resolver, KSFunctionDeclaration function, String name, MvpModel model) {
		if (model.kind != MvpModel.Kind.ACTIVITY && model.kind != MvpModel.Kind.FRAGMENT) {
			model.errors.add("@Bean is only supported in @EActivity or @EFragment components: method '" + name + "'");
			return;
		}
		if (function.getModifiers().contains(Modifier.PRIVATE)) {
			model.errors.add("@Bean method '" + name + "' must not be private");
			return;
		}
		List<KSValueParameter> params = function.getParameters();
		if (params.size() != 1) {
			model.errors.add("@Bean method '" + name + "' must have exactly one parameter");
			return;
		}
		KSAnnotation bean = KspAnnotationUtils.findAnnotation(function.getAnnotations(), AnnotationNames.BEAN);
		MvpModel.BeanTarget target = resolveBeanTarget(resolver, params.get(0).getType(), bean, name, model);
		if (target != null) {
			String declaredType = samePackageRef(KspAnnotationUtils.fullTypeNameWithArgs(params.get(0).getType()), model);
			MvpModel.BeanTarget local = new MvpModel.BeanTarget(params.get(0).getName().asString(),
					declaredType, target.beanGeneratedQualifiedName, target.withRootFragmentArg);
			model.beans.add(new MvpModel.BeanInjection(MvpModel.BeanInjection.Form.METHOD, name, false,
					Collections.singletonList(local)));
		}
	}

	/** APT BeanHandler.processParam parity: one shared braced block per method; the call is emitted only when ALL parameters are annotated. */
	private void collectBeanParams(Resolver resolver, KSFunctionDeclaration function, String name, MvpModel model) {
		if (model.kind != MvpModel.Kind.ACTIVITY && model.kind != MvpModel.Kind.FRAGMENT) {
			model.errors.add("@Bean is only supported in @EActivity or @EFragment components: method '" + name + "'");
			return;
		}
		if (function.getModifiers().contains(Modifier.PRIVATE)) {
			model.errors.add("@Bean method '" + name + "' must not be private");
			return;
		}
		List<MvpModel.BeanTarget> targets = new ArrayList<>();
		for (KSValueParameter param : function.getParameters()) {
			KSAnnotation bean = findParamBean(param);
			if (bean == null) {
				return; // partially annotated: APT leaves dead locals, KSP skips the whole block without error
			}
			MvpModel.BeanTarget target = resolveBeanTarget(resolver, param.getType(), bean, name, model);
			if (target == null) {
				return;
			}
			String declaredType = samePackageRef(KspAnnotationUtils.fullTypeNameWithArgs(param.getType()), model);
			targets.add(new MvpModel.BeanTarget(param.getName().asString(), declaredType,
					target.beanGeneratedQualifiedName, target.withRootFragmentArg));
		}
		model.beans.add(new MvpModel.BeanInjection(MvpModel.BeanInjection.Form.METHOD, name, false, targets));
	}

	private static KSAnnotation findParamBean(KSValueParameter param) {
		for (KSAnnotation annotation : SequencesKt.toList(param.getAnnotations())) {
			if (AnnotationNames.BEAN.equals(KspAnnotationUtils.safeQualifiedName(annotation))) {
				return annotation;
			}
		}
		return null;
	}

	private static boolean hasBeanParam(KSFunctionDeclaration function) {
		for (KSValueParameter param : function.getParameters()) {
			if (findParamBean(param) != null) {
				return true;
			}
		}
		return false;
	}

	/** APT AndroidSystemServices parity: full Java type name -> android.content.Context.* constant, in alphabetical order. */
	private static final Map<String, String> SYSTEM_SERVICES = new HashMap<>();

	static {
		SYSTEM_SERVICES.put("android.view.accessibility.AccessibilityManager", "android.content.Context.ACCESSIBILITY_SERVICE");
		SYSTEM_SERVICES.put("android.accounts.AccountManager", "android.content.Context.ACCOUNT_SERVICE");
		SYSTEM_SERVICES.put("android.app.ActivityManager", "android.content.Context.ACTIVITY_SERVICE");
		SYSTEM_SERVICES.put("android.app.AlarmManager", "android.content.Context.ALARM_SERVICE");
		SYSTEM_SERVICES.put("android.app.AppOpsManager", "android.content.Context.APP_OPS_SERVICE");
		SYSTEM_SERVICES.put("android.appwidget.AppWidgetManager", "android.content.Context.APPWIDGET_SERVICE");
		SYSTEM_SERVICES.put("android.media.AudioManager", "android.content.Context.AUDIO_SERVICE");
		SYSTEM_SERVICES.put("android.app.backup.IBackupManager", "android.content.Context.BACKUP_SERVICE");
		SYSTEM_SERVICES.put("android.os.BatteryManager", "android.content.Context.BATTERY_SERVICE");
		SYSTEM_SERVICES.put("android.bluetooth.BluetoothManager", "android.content.Context.BLUETOOTH_SERVICE");
		SYSTEM_SERVICES.put("android.hardware.camera2.CameraManager", "android.content.Context.CAMERA_SERVICE");
		SYSTEM_SERVICES.put("android.view.accessibility.CaptioningManager", "android.content.Context.CAPTIONING_SERVICE");
		SYSTEM_SERVICES.put("android.telephony.CarrierConfigManager", "android.content.Context.CARRIER_CONFIG_SERVICE");
		SYSTEM_SERVICES.put("android.content.ClipboardManager", "android.content.Context.CLIPBOARD_SERVICE");
		SYSTEM_SERVICES.put("android.text.ClipboardManager", "android.content.Context.CLIPBOARD_SERVICE");
		SYSTEM_SERVICES.put("android.net.ConnectivityManager", "android.content.Context.CONNECTIVITY_SERVICE");
		SYSTEM_SERVICES.put("android.hardware.ConsumerIrManager", "android.content.Context.CONSUMER_IR_SERVICE");
		SYSTEM_SERVICES.put("android.location.CountryDetector", "android.content.Context.COUNTRY_DETECTOR");
		SYSTEM_SERVICES.put("android.app.admin.DevicePolicyManager", "android.content.Context.DEVICE_POLICY_SERVICE");
		SYSTEM_SERVICES.put("android.hardware.display.DisplayManager", "android.content.Context.DISPLAY_SERVICE");
		SYSTEM_SERVICES.put("android.app.DownloadManager", "android.content.Context.DOWNLOAD_SERVICE");
		SYSTEM_SERVICES.put("android.os.DropBoxManager", "android.content.Context.DROPBOX_SERVICE");
		SYSTEM_SERVICES.put("android.net.EthernetManager", "android.content.Context.ETHERNET_SERVICE");
		SYSTEM_SERVICES.put("android.hardware.fingerprint.FingerprintManager", "android.content.Context.FINGERPRINT_SERVICE");
		SYSTEM_SERVICES.put("android.os.HardwarePropertiesManager", "android.content.Context.HARDWARE_PROPERTIES_SERVICE");
		SYSTEM_SERVICES.put("android.hardware.hdmi.HdmiControlManager", "android.content.Context.HDMI_CONTROL_SERVICE");
		SYSTEM_SERVICES.put("android.hardware.input.InputManager", "android.content.Context.INPUT_SERVICE");
		SYSTEM_SERVICES.put("android.view.inputmethod.InputMethodManager", "android.content.Context.INPUT_METHOD_SERVICE");
		SYSTEM_SERVICES.put("android.app.job.JobScheduler", "android.content.Context.JOB_SCHEDULER_SERVICE");
		SYSTEM_SERVICES.put("android.app.KeyguardManager", "android.content.Context.KEYGUARD_SERVICE");
		SYSTEM_SERVICES.put("android.content.pm.LauncherApps", "android.content.Context.LAUNCHER_APPS_SERVICE");
		SYSTEM_SERVICES.put("android.view.LayoutInflater", "android.content.Context.LAYOUT_INFLATER_SERVICE");
		SYSTEM_SERVICES.put("android.location.LocationManager", "android.content.Context.LOCATION_SERVICE");
		SYSTEM_SERVICES.put("android.media.projection.MediaProjectionManager", "android.content.Context.MEDIA_PROJECTION_SERVICE");
		SYSTEM_SERVICES.put("android.media.MediaRouter", "android.content.Context.MEDIA_ROUTER_SERVICE");
		SYSTEM_SERVICES.put("android.media.session.MediaSessionManager", "android.content.Context.MEDIA_SESSION_SERVICE");
		SYSTEM_SERVICES.put("android.media.midi.MidiManager", "android.content.Context.MIDI_SERVICE");
		SYSTEM_SERVICES.put("android.net.NetworkScoreManager", "android.content.Context.NETWORK_SCORE_SERVICE");
		SYSTEM_SERVICES.put("android.net.NetworkManagementService", "android.content.Context.NETWORKMANAGEMENT_SERVICE");
		SYSTEM_SERVICES.put("android.net.NetworkPolicyManager", "android.content.Context.NETWORK_POLICY_SERVICE");
		SYSTEM_SERVICES.put("android.app.usage.NetworkStatsManager", "android.content.Context.NETWORK_STATS_SERVICE");
		SYSTEM_SERVICES.put("android.nfc.NfcManager", "android.content.Context.NFC_SERVICE");
		SYSTEM_SERVICES.put("android.app.NotificationManager", "android.content.Context.NOTIFICATION_SERVICE");
		SYSTEM_SERVICES.put("android.net.nsd.NsdManager", "android.content.Context.NSD_SERVICE");
		SYSTEM_SERVICES.put("android.service.persistentdata.PersistentDataBlockManager", "android.content.Context.PERSISTENT_DATA_BLOCK_SERVICE");
		SYSTEM_SERVICES.put("android.print.PrintManager", "android.content.Context.PRINT_SERVICE");
		SYSTEM_SERVICES.put("android.os.PowerManager", "android.content.Context.POWER_SERVICE");
		SYSTEM_SERVICES.put("android.content.RestrictionsManager", "android.content.Context.RESTRICTIONS_SERVICE");
		SYSTEM_SERVICES.put("android.app.SearchManager", "android.content.Context.SEARCH_SERVICE");
		SYSTEM_SERVICES.put("android.hardware.SerialManager", "android.content.Context.SERIAL_SERVICE");
		SYSTEM_SERVICES.put("android.hardware.SensorManager", "android.content.Context.SENSOR_SERVICE");
		SYSTEM_SERVICES.put("android.net.sip.SipManager", "android.content.Context.SIP_SERVICE");
		SYSTEM_SERVICES.put("android.content.pm.ShortcutManager", "android.content.Context.SHORTCUT_SERVICE");
		SYSTEM_SERVICES.put("android.app.StatusBarManager", "android.content.Context.STATUS_BAR_SERVICE");
		SYSTEM_SERVICES.put("android.os.storage.StorageManager", "android.content.Context.STORAGE_SERVICE");
		SYSTEM_SERVICES.put("android.telephony.SubscriptionManager", "android.content.Context.TELEPHONY_SUBSCRIPTION_SERVICE");
		SYSTEM_SERVICES.put("android.os.health.SystemHealthManager", "android.content.Context.SYSTEM_HEALTH_SERVICE");
		SYSTEM_SERVICES.put("android.telecom.TelecomManager", "android.content.Context.TELECOM_SERVICE");
		SYSTEM_SERVICES.put("android.telephony.TelephonyManager", "android.content.Context.TELEPHONY_SERVICE");
		SYSTEM_SERVICES.put("android.view.textservice.TextServicesManager", "android.content.Context.TEXT_SERVICES_MANAGER_SERVICE");
		SYSTEM_SERVICES.put("android.net.ThrottleManager", "android.content.Context.THROTTLE_SERVICE");
		SYSTEM_SERVICES.put("android.app.trust.TrustManager", "android.content.Context.TRUST_SERVICE");
		SYSTEM_SERVICES.put("android.media.tv.TvInputManager", "android.content.Context.TV_INPUT_SERVICE");
		SYSTEM_SERVICES.put("android.app.UiModeManager", "android.content.Context.UI_MODE_SERVICE");
		SYSTEM_SERVICES.put("android.os.IUpdateLock", "android.content.Context.UPDATE_LOCK_SERVICE");
		SYSTEM_SERVICES.put("android.app.usage.UsageStatsManager", "android.content.Context.USAGE_STATS_SERVICE");
		SYSTEM_SERVICES.put("android.hardware.usb.UsbManager", "android.content.Context.USB_SERVICE");
		SYSTEM_SERVICES.put("android.os.UserManager", "android.content.Context.USER_SERVICE");
		SYSTEM_SERVICES.put("android.os.Vibrator", "android.content.Context.VIBRATOR_SERVICE");
		SYSTEM_SERVICES.put("android.app.WallpaperManager", "android.content.Context.WALLPAPER_SERVICE");
		SYSTEM_SERVICES.put("android.net.wifi.WifiManager", "android.content.Context.WIFI_SERVICE");
		SYSTEM_SERVICES.put("android.net.wifi.passpoint.WifiPasspointManager", "android.content.Context.WIFI_PASSPOINT_SERVICE");
		SYSTEM_SERVICES.put("android.net.wifi.p2p.WifiP2pManager", "android.content.Context.WIFI_P2P_SERVICE");
		SYSTEM_SERVICES.put("android.net.wifi.RttManager", "android.content.Context.WIFI_RTT_SERVICE");
		SYSTEM_SERVICES.put("android.view.WindowManager", "android.content.Context.WINDOW_SERVICE");
	}

	/**
	 * APT SystemServiceHandler + InjectHelper parity: @SystemService on a field. Validation order: enclosing component,
	 * final, known service type, private. AppWidgetManager's minSdk&lt;21 special path (APT createSpecialInjection) is not
	 * replicated: the KSP processor has no manifest minSdk, so the normal getSystemService path is always emitted.
	 */
	private void collectSystemServiceField(Resolver resolver, KSPropertyDeclaration property, String name, boolean jvmField, MvpModel model) {
		String fqn = AnnotationNames.SYSTEM_SERVICE;
		if (model.kind != MvpModel.Kind.ACTIVITY && model.kind != MvpModel.Kind.FRAGMENT) {
			model.errors.add(fqn + " can only be used in a field annotated with @org.androidannotations.annotations.EActivity, @org.androidannotations.annotations.EFragment");
			return;
		}
		if (!property.isMutable()) {
			model.errors.add(fqn + " cannot be used on a final element");
			return;
		}
		MvpModel.SystemServiceTarget target = resolveSystemServiceTarget(name, KspAnnotationUtils.fullJavaTypeName(property.getType()), model);
		if (target == null) {
			return;
		}
		if (property.getModifiers().contains(Modifier.PRIVATE)) {
			model.errors.add(fqn + " cannot be used on a private element");
			return;
		}
		model.systemServices.add(new MvpModel.SystemServiceInjection(MvpModel.SystemServiceInjection.Form.FIELD, name, jvmField,
				Collections.singletonList(target)));
	}

	/** APT SystemServiceHandler parity: @SystemService on a method with exactly one parameter (local var named after the PARAM). */
	private void collectSystemServiceMethod(Resolver resolver, KSFunctionDeclaration function, String name, MvpModel model) {
		String fqn = AnnotationNames.SYSTEM_SERVICE;
		if (model.kind != MvpModel.Kind.ACTIVITY && model.kind != MvpModel.Kind.FRAGMENT) {
			model.errors.add(fqn + " can only be used in a field annotated with @org.androidannotations.annotations.EActivity, @org.androidannotations.annotations.EFragment");
			return;
		}
		if (!"void".equals(functionReturnType(function))) {
			model.errors.add(fqn + " can only be used on a method with a void return type");
			return;
		}
		List<KSValueParameter> params = function.getParameters();
		if (params.size() != 1) {
			model.errors.add("The method can only have 1 parameter");
			return;
		}
		KSValueParameter param = params.get(0);
		MvpModel.SystemServiceTarget target = resolveSystemServiceTarget(param.getName().asString(),
				KspAnnotationUtils.fullJavaTypeName(param.getType()), model);
		if (target == null) {
			return;
		}
		if (function.getModifiers().contains(Modifier.PRIVATE)) {
			model.errors.add(fqn + " cannot be used on a private element");
			return;
		}
		model.systemServices.add(new MvpModel.SystemServiceInjection(MvpModel.SystemServiceInjection.Form.METHOD, name, false,
				Collections.singletonList(target)));
	}

	/** APT SystemServiceHandler parity: @SystemService on method parameters; the block is emitted only when ALL parameters are annotated (partial: skip without error, @Bean precedent). */
	private void collectSystemServiceParams(Resolver resolver, KSFunctionDeclaration function, String name, MvpModel model) {
		String fqn = AnnotationNames.SYSTEM_SERVICE;
		if (model.kind != MvpModel.Kind.ACTIVITY && model.kind != MvpModel.Kind.FRAGMENT) {
			model.errors.add(fqn + " can only be used in a field annotated with @org.androidannotations.annotations.EActivity, @org.androidannotations.annotations.EFragment");
			return;
		}
		if (!"void".equals(functionReturnType(function))) {
			model.errors.add(fqn + " can only be used on a method with a void return type");
			return;
		}
		if (function.getModifiers().contains(Modifier.PRIVATE)) {
			model.errors.add(fqn + " cannot be used on a private element");
			return;
		}
		List<MvpModel.SystemServiceTarget> targets = new ArrayList<>();
		for (KSValueParameter param : function.getParameters()) {
			if (findParamSystemService(param) == null) {
				return; // partially annotated: KSP skips the whole block without error (@Bean precedent)
			}
			MvpModel.SystemServiceTarget target = resolveSystemServiceTarget(param.getName().asString(),
					KspAnnotationUtils.fullJavaTypeName(param.getType()), model);
			if (target == null) {
				return;
			}
			targets.add(target);
		}
		model.systemServices.add(new MvpModel.SystemServiceInjection(MvpModel.SystemServiceInjection.Form.METHOD, name, false, targets));
	}

	private static KSAnnotation findParamSystemService(KSValueParameter param) {
		for (KSAnnotation annotation : SequencesKt.toList(param.getAnnotations())) {
			if (AnnotationNames.SYSTEM_SERVICE.equals(KspAnnotationUtils.safeQualifiedName(annotation))) {
				return annotation;
			}
		}
		return null;
	}

	private static boolean hasSystemServiceParam(KSFunctionDeclaration function) {
		for (KSValueParameter param : function.getParameters()) {
			if (findParamSystemService(param) != null) {
				return true;
			}
		}
		return false;
	}

	/** APT AndroidSystemServices + CanonicalNameConstants parity: look up the Context.* constant for a full Java type name. */
	private static MvpModel.SystemServiceTarget resolveSystemServiceTarget(String name, String typeString, MvpModel model) {
		String serviceConst = SYSTEM_SERVICES.get(typeString);
		if (serviceConst == null) {
			model.errors.add("Unknown service type: " + typeString);
			return null;
		}
		boolean appContext = "android.net.wifi.WifiManager".equals(typeString) || "android.media.AudioManager".equals(typeString);
		return new MvpModel.SystemServiceTarget(name, typeString, serviceConst, appContext);
	}

	/**
	 * APT BeanHandler.assignValue parity: the injected bean class is the @Bean {@code value} argument when present,
	 * otherwise the erasure of the element type; it must carry @EBean. The two-argument getInstance_ form (context +
	 * literal null root fragment) is used unless the bean scope is Singleton or Activity.
	 */
	private MvpModel.BeanTarget resolveBeanTarget(Resolver resolver, KSTypeReference elementType, KSAnnotation bean, String name, MvpModel model) {
		KSClassDeclaration beanDecl = null;
		Object value = bean != null ? KspAnnotationUtils.argValue(bean, "value") : null;
		if (value instanceof KSTypeReference) {
			try {
				KSType type = ((KSTypeReference) value).resolve();
				if (type.getDeclaration() instanceof KSClassDeclaration) {
					beanDecl = (KSClassDeclaration) type.getDeclaration();
				}
			} catch (Exception ignored) {
				// fall back to the element type below
			}
		}
		if (beanDecl == null || beanDecl.getQualifiedName() == null) {
			beanDecl = resolveClassDeclaration(resolver, KspAnnotationUtils.typeName(elementType));
		}
		if (beanDecl == null || beanDecl.getQualifiedName() == null) {
			model.errors.add("@Bean on '" + name + "': could not resolve the injected bean type");
			return null;
		}
		String beanQualifiedName = beanDecl.getQualifiedName().asString();
		KSAnnotation eBean = KspAnnotationUtils.findAnnotation(beanDecl.getAnnotations(), AnnotationNames.E_BEAN);
		if (eBean == null) {
			model.errors.add("@Bean on '" + name + "': " + beanQualifiedName + " must be annotated with @EBean");
			return null;
		}
		String scope = KspAnnotationUtils.enumArg(eBean, "scope", "Default");
		boolean withRootFragmentArg = !"Singleton".equals(scope) && !"Activity".equals(scope);
		return new MvpModel.BeanTarget(name, null, samePackageRef(generatedBeanQualifiedName(beanQualifiedName), model), withRootFragmentArg);
	}

	/** Mirrors APT AnnotationHelper.generatedClassQualifiedNameFromQualifiedName: the "_" suffix lands on every class segment; the package is untouched. */
	private static String generatedBeanQualifiedName(String beanQualifiedName) {
		int lastDot = beanQualifiedName.lastIndexOf('.');
		if (lastDot < 0) {
			return beanQualifiedName + "_";
		}
		StringBuilder sb = new StringBuilder(beanQualifiedName.substring(0, lastDot));
		for (String segment : beanQualifiedName.substring(lastDot + 1).split("\\.")) {
			sb.append('.').append(segment).append('_');
		}
		return sb.toString();
	}

	/** APT/jcodemodel parity: classes in the component's own package are referenced unqualified; KSP emits no imports, other packages stay FQN. */
	private static String samePackageRef(String fullTypeName, MvpModel model) {
		String pkg = model.packageName == null ? "" : model.packageName;
		if (pkg.isEmpty()) {
			return fullTypeName;
		}
		int pkgSegments = pkg.split("\\.").length;
		StringBuilder sb = new StringBuilder();
		java.util.regex.Matcher m = java.util.regex.Pattern.compile(
			"(?<![A-Za-z0-9_$.])" + java.util.regex.Pattern.quote(pkg) + "(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*").matcher(fullTypeName);
		int last = 0;
		while (m.find()) {
			String qn = m.group();
			if (qn.split("\\.").length == pkgSegments + 1) {
				sb.append(fullTypeName, last, m.start());
				sb.append(qn, pkg.length() + 1, qn.length());
				last = m.end();
			}
		}
		return last == 0 ? fullTypeName : sb.append(fullTypeName.substring(last)).toString();
	}

	/** Write/read expression templates for one @Extra or @FragmentArg, mirroring APT IntentBuilder.getSuperPutExtraInvocation + BundleHelper. */
	private static final class ExtraTemplates {
		final String write; // template with {arg} placeholder
		final String read; // template with {key} placeholder
		final boolean unchecked;

		ExtraTemplates(String write, String read, boolean unchecked) {
			this.write = write;
			this.read = read;
			this.unchecked = unchecked;
		}
	}

	private ExtraTemplates extraTemplatesFor(Resolver resolver, String fullType) {
		return templatesFor(resolver, fullType, "extras_");
	}

	private ExtraTemplates templatesFor(Resolver resolver, String fullType, String bundleVar) {
		String base = fullType.contains("<") ? fullType.substring(0, fullType.indexOf('<')) : fullType;
		KSClassDeclaration typeDeclaration = resolveClassDeclaration(resolver, base);
		boolean parcelable = typeDeclaration != null && implementsInterface(typeDeclaration, "android.os.Parcelable");
		boolean serializable = typeDeclaration != null && implementsInterface(typeDeclaration, "java.io.Serializable");
		boolean parceler = isParcelerType(resolver, fullType);

		String write;
		if (isPrimitiveOrPrimitiveArray(fullType)) {
			write = "{arg}";
		} else if ("java.lang.String".equals(fullType) || "java.lang.CharSequence".equals(fullType)) {
			// KSP deviation: APT would cast CharSequence to Serializable (runtime CCE risk); keep the typed overload.
			write = "{arg}";
		} else if (parcelable) {
			write = "(android.os.Parcelable) {arg}";
		} else if (!KspAnnotationUtils.BUNDLE_GET_SUFFIX.containsKey(fullType) && parceler) {
			write = "org.parceler.Parcels.wrap({arg})";
		} else if (serializable) {
			write = "(java.io.Serializable) {arg}";
		} else {
			// KSP deviation: APT would emit a broken Serializable cast; leave uncast so the builder method fails to compile early.
			write = "{arg}";
		}

		String read;
		boolean unchecked = false;
		String suffix = KspAnnotationUtils.BUNDLE_GET_SUFFIX.get(fullType);
		if (suffix != null) {
			read = bundleVar + ".get" + suffix + "({key})";
		} else if (fullType.endsWith("[]")) {
			String component = fullType.substring(0, fullType.length() - 2);
			KSClassDeclaration componentDecl = resolveClassDeclaration(resolver, component);
			if (componentDecl != null && implementsInterface(componentDecl, "android.os.Parcelable")) {
				read = "org.androidannotations.api.bundle.BundleHelper.getParcelableArray(" + bundleVar + ", {key}, " + component + "[].class)";
			} else {
				read = "(" + fullType + ") " + bundleVar + ".getSerializable({key})";
			}
		} else if (fullType.startsWith("java.util.ArrayList<")) {
			String arg = topLevelGenericArg(fullType);
			KSClassDeclaration argDecl = resolveClassDeclaration(resolver, arg);
			if (argDecl != null && implementsInterface(argDecl, "android.os.Parcelable")) {
				read = bundleVar + ".getParcelableArrayList({key})";
			} else {
				read = "(" + fullType + ") " + bundleVar + ".getSerializable({key})";
				unchecked = true;
			}
		} else if (fullType.startsWith("android.util.SparseArray<")) {
			read = bundleVar + ".getSparseParcelableArray({key})";
		} else if (parceler) {
			read = "org.parceler.Parcels.unwrap(" + bundleVar + ".getParcelable({key}))";
		} else if (parcelable) {
			read = bundleVar + ".getParcelable({key})";
		} else if (serializable) {
			read = "(" + fullType + ") " + bundleVar + ".getSerializable({key})";
			unchecked = fullType.contains("<");
		} else {
			read = "(" + fullType + ") " + bundleVar + ".get({key})";
		}

		return new ExtraTemplates(write, read, unchecked);
	}

	/** APT InstanceStateHandler parity: validate one @InstanceState property and build its save/restore statements. */
	private void collectInstanceState(Resolver resolver, KSPropertyDeclaration property, String name, MvpModel model) {
		String fqn = AnnotationNames.INSTANCE_STATE;
		// APT validation order (ValidatorHelper): enclosing type, private, bundle-able type, final; all accumulate.
		boolean badEnclosing = model.kind == MvpModel.Kind.BEAN;
		if (badEnclosing) {
			model.errors.add(fqn + " can only be used in a field annotated with @org.androidannotations.annotations.EActivity, @org.androidannotations.annotations.EFragment, @org.androidannotations.annotations.EView, @org.androidannotations.annotations.EViewGroup.");
		}
		boolean privateElement = property.getModifiers().contains(Modifier.PRIVATE);
		if (privateElement) {
			model.errors.add(fqn + " cannot be used on a private element");
		}
		String typeString = KspAnnotationUtils.instanceStateStubTypeName(property);
		String typeError = instanceStateTypeError(resolver, typeString);
		boolean finalElement = !property.isMutable();
		if (finalElement) {
			model.errors.add(fqn + " cannot be used on a final element");
		}
		if (badEnclosing || privateElement || typeError != null || finalElement) {
			return;
		}

		String saveStmt;
		String restoreStmt;
		boolean unchecked = false;
		String suffix = KspAnnotationUtils.BUNDLE_GET_SUFFIX.get(typeString);
		if (suffix != null) {
			saveStmt = "bundle_.put" + suffix + "(\"" + name + "\", " + name + ");";
			restoreStmt = name + " = savedInstanceState.get" + suffix + "(\"" + name + "\");";
		} else if (typeString.endsWith("[]")) {
			String component = typeString.substring(0, typeString.length() - 2);
			KSClassDeclaration componentDecl = resolveClassDeclaration(resolver, component);
			if (componentDecl != null && implementsInterface(componentDecl, "android.os.Parcelable")) {
				saveStmt = "bundle_.putParcelableArray(\"" + name + "\", " + name + ");";
				restoreStmt = name + " = org.androidannotations.api.bundle.BundleHelper.getParcelableArray(savedInstanceState, \"" + name + "\", "
						+ displayType(model, component) + "[].class);";
				unchecked = component.contains("<");
			} else {
				saveStmt = "bundle_.putSerializable(\"" + name + "\", " + name + ");";
				restoreStmt = name + " = ((" + displayType(model, typeString) + ") savedInstanceState.getSerializable(\"" + name + "\"));";
			}
		} else if (typeString.startsWith("java.util.ArrayList<")) {
			String arg = topLevelGenericArg(typeString);
			KSClassDeclaration argDecl = resolveClassDeclaration(resolver, arg);
			if (argDecl != null && implementsInterface(argDecl, "android.os.Parcelable")) {
				saveStmt = "bundle_.putParcelableArrayList(\"" + name + "\", " + name + ");";
				restoreStmt = name + " = savedInstanceState.getParcelableArrayList(\"" + name + "\");";
				unchecked = arg.contains("<");
			} else {
				saveStmt = "bundle_.putSerializable(\"" + name + "\", " + name + ");";
				restoreStmt = name + " = ((" + displayType(model, typeString) + ") savedInstanceState.getSerializable(\"" + name + "\"));";
				unchecked = true;
			}
		} else if (typeString.startsWith("android.util.SparseArray<")) {
			saveStmt = "bundle_.putSparseParcelableArray(\"" + name + "\", " + name + ");";
			restoreStmt = name + " = savedInstanceState.getSparseParcelableArray(\"" + name + "\");";
		} else {
			String base = typeString.contains("<") ? typeString.substring(0, typeString.indexOf('<')) : typeString;
			KSClassDeclaration declaration = resolveClassDeclaration(resolver, base);
			boolean parcelable = declaration != null && implementsInterface(declaration, "android.os.Parcelable");
			if (parcelable) {
				saveStmt = "bundle_.putParcelable(\"" + name + "\", " + name + ");";
				restoreStmt = name + " = savedInstanceState.getParcelable(\"" + name + "\");";
			} else if (isParcelerType(resolver, typeString)) {
				saveStmt = "bundle_.putParcelable(\"" + name + "\", org.parceler.Parcels.wrap(" + name + "));";
				restoreStmt = name + " = org.parceler.Parcels.unwrap(savedInstanceState.getParcelable(\"" + name + "\"));";
			} else {
				saveStmt = "bundle_.putSerializable(\"" + name + "\", " + name + ");";
				restoreStmt = name + " = ((" + displayType(model, typeString) + ") savedInstanceState.getSerializable(\"" + name + "\"));";
				unchecked = typeString.contains("<");
			}
		}
		model.instanceStates.add(new MvpModel.InstanceState(name, saveStmt, restoreStmt, unchecked));
	}

	/** Mirrors APT BundleHelper.canBePutInABundle; returns null when the (stub) type is bundle-compatible. */
	private String instanceStateTypeError(Resolver resolver, String typeString) {
		if (KspAnnotationUtils.BUNDLE_GET_SUFFIX.containsKey(typeString)) {
			return null;
		}
		String base = typeString;
		while (base.endsWith("[]")) {
			base = base.substring(0, base.length() - 2);
		}
		if (base.startsWith("android.util.SparseArray<")) {
			String arg = topLevelGenericArg(base);
			KSClassDeclaration declaration = resolveClassDeclaration(resolver, arg);
			return declaration != null && implementsInterface(declaration, "android.os.Parcelable") ? null
					: "Unrecognized type. The type argument of SparseArray should implement Parcelable.";
		}
		KSClassDeclaration declaration = resolveClassDeclaration(resolver, base);
		boolean parcelable = declaration != null && implementsInterface(declaration, "android.os.Parcelable");
		boolean serializable = declaration != null && implementsInterface(declaration, "java.io.Serializable");
		if (!parcelable && !serializable && !isParcelerType(resolver, typeString)) {
			return "Unrecognized type. Please let your attribute be primitive or implement Serializable or Parcelable or an annotated Parceler bean.";
		}
		return null;
	}

	/** APT OnActivityResultHandler parity: validate one @OnActivityResult method and record its case group, extra reads and constants. */
	private void collectActivityResult(Resolver resolver, KSFunctionDeclaration function, String name, KSAnnotation annotation, MvpModel model) {
		String fqn = AnnotationNames.ON_ACTIVITY_RESULT;
		int errorsBefore = model.errors.size();
		if (model.kind != MvpModel.Kind.ACTIVITY && model.kind != MvpModel.Kind.FRAGMENT) {
			model.errors.add(fqn + " can only be used in a method annotated with @org.androidannotations.annotations.EActivity or @org.androidannotations.annotations.EFragment.");
		}
		boolean privateElement = function.getModifiers().contains(Modifier.PRIVATE);
		if (privateElement) {
			model.errors.add(fqn + " cannot be used on a private element");
		}
		int requestCode = KspAnnotationUtils.intArg(annotation, "value", 0);
		if (requestCode < 0 || requestCode > 0xFFFF) {
			model.errors.add("Due to a restriction in the fragment API, the requestCode has to be a positive integer inferior or equal to 0xFFFF");
		}
		if (!"void".equals(functionReturnType(function))) {
			model.errors.add(fqn + " can only be used on a method with a void return type");
		}
		String paramError = fqn + " can only have the following parameters: [ [ android.content.Intent (optional) ], [ int or java.lang.Integer (optional) ], [ annotated with Extra (multiple) (optional) ] ] in any order";
		int intentCount = 0;
		int resultCodeCount = 0;
		for (KSValueParameter parameter : function.getParameters()) {
			if (KspAnnotationUtils.findAnnotation(parameter.getAnnotations(), AnnotationNames.ON_ACTIVITY_RESULT_EXTRA) == null) {
				String type = KspAnnotationUtils.typeName(parameter.getType());
				if ("android.content.Intent".equals(type)) {
					intentCount++;
				} else if ("int".equals(type) || "java.lang.Integer".equals(type)) {
					resultCodeCount++;
				} else {
					model.errors.add(paramError);
				}
			}
		}
		if (intentCount > 1 || resultCodeCount > 1) {
			model.errors.add(paramError);
		}
		if (model.errors.size() > errorsBefore) {
			return;
		}

		MvpModel.ActivityResultMethod method = new MvpModel.ActivityResultMethod(name);
		for (KSValueParameter parameter : function.getParameters()) {
			KSAnnotation extra = KspAnnotationUtils.findAnnotation(parameter.getAnnotations(), AnnotationNames.ON_ACTIVITY_RESULT_EXTRA);
			String paramName = parameter.getName().asString();
			if (extra != null) {
				String typeString = KspAnnotationUtils.fullJavaTypeName(parameter.getType());
				String typeError = instanceStateTypeError(resolver, typeString);
				if (typeError != null) {
					model.errors.add(typeError);
					return;
				}
				String key = KspAnnotationUtils.stringArg(extra, "value", "");
				if (key.isEmpty()) {
					key = paramName;
				}
				String constantName = KspAnnotationUtils.extraKeyConstant(key);
				addOarExtraConstant(model, constantName, key);
				method.extraDecls.add(displayType(model, typeString) + " " + paramName + " = " + bundleReadExpr(resolver, model, typeString, constantName) + ";");
				method.argExprs.add(paramName);
			} else if ("android.content.Intent".equals(KspAnnotationUtils.typeName(parameter.getType()))) {
				method.argExprs.add("data");
			} else {
				method.argExprs.add("resultCode");
			}
		}
		findOrCreateActivityResultGroup(model, requestCode).methods.add(method);
	}

	private MvpModel.ActivityResultGroup findOrCreateActivityResultGroup(MvpModel model, int requestCode) {
		for (MvpModel.ActivityResultGroup group : model.activityResults) {
			if (group.requestCode == requestCode) {
				return group;
			}
		}
		MvpModel.ActivityResultGroup group = new MvpModel.ActivityResultGroup(requestCode);
		model.activityResults.add(group);
		return group;
	}

	/** APT ExtraParameterHandler.getStaticExtraField parity: first declaration wins, also against field-level @Extra constants. */
	private void addOarExtraConstant(MvpModel model, String constantName, String key) {
		for (MvpModel.ExtraConstant existing : model.oarExtraConstants) {
			if (existing.constantName.equals(constantName)) {
				return;
			}
		}
		for (MvpModel.FieldInjection extra : model.extras) {
			if (constantName.equals(KspAnnotationUtils.extraKeyConstant(extra.propertyName))) {
				return;
			}
		}
		model.oarExtraConstants.add(new MvpModel.ExtraConstant(constantName, key));
	}

	/** Mirrors APT BundleHelper.getExpressionToRestoreFromBundle for @OnActivityResult @Extra params (jcodemodel double-paren cast form). */
	private String bundleReadExpr(Resolver resolver, MvpModel model, String typeString, String constant) {
		String suffix = KspAnnotationUtils.BUNDLE_GET_SUFFIX.get(typeString);
		if (suffix != null) {
			return "extras_.get" + suffix + "(" + constant + ")";
		}
		if (typeString.endsWith("[]")) {
			String component = typeString.substring(0, typeString.length() - 2);
			KSClassDeclaration componentDecl = resolveClassDeclaration(resolver, component);
			if (componentDecl != null && implementsInterface(componentDecl, "android.os.Parcelable")) {
				model.activityResultUnchecked |= component.contains("<");
				return "org.androidannotations.api.bundle.BundleHelper.getParcelableArray(extras_, " + constant + ", " + displayType(model, component) + "[].class)";
			}
			return "((" + displayType(model, typeString) + ") extras_.getSerializable(" + constant + "))";
		}
		if (typeString.startsWith("java.util.ArrayList")) {
			String arg = null;
			int lt = typeString.indexOf('<');
			if (lt >= 0 && typeString.endsWith(">")) {
				List<String> args = splitTopLevelArgs(typeString.substring(lt + 1, typeString.length() - 1));
				if (args.size() == 1) {
					arg = args.get(0).trim();
				}
			}
			KSClassDeclaration argDecl = arg != null ? resolveClassDeclaration(resolver, arg) : null;
			if (argDecl != null && implementsInterface(argDecl, "android.os.Parcelable")) {
				model.activityResultUnchecked |= arg.contains("<");
				return "extras_.getParcelableArrayList(" + constant + ")";
			}
			model.activityResultUnchecked = true;
			return "((" + displayType(model, typeString) + ") extras_.getSerializable(" + constant + "))";
		}
		if (typeString.startsWith("android.util.SparseArray")) {
			return "extras_.getSparseParcelableArray(" + constant + ")";
		}
		String base = typeString.contains("<") ? typeString.substring(0, typeString.indexOf('<')) : typeString;
		KSClassDeclaration declaration = resolveClassDeclaration(resolver, base);
		boolean parcelable = declaration != null && implementsInterface(declaration, "android.os.Parcelable");
		if (parcelable) {
			return "extras_.getParcelable(" + constant + ")";
		}
		if (isParcelerType(resolver, typeString)) {
			return "org.parceler.Parcels.unwrap(extras_.getParcelable(" + constant + "))";
		}
		model.activityResultUnchecked |= typeString.contains("<");
		return "((" + displayType(model, typeString) + ") extras_.getSerializable(" + constant + "))";
	}

	/** Display form for generated text: java.lang.* and same-package types bare, other packages fully qualified (gate maps FQNs via imports). */
	private String displayType(MvpModel model, String type) {
		if (type == null || type.isEmpty()) {
			return type;
		}
		String base = type;
		StringBuilder suffix = new StringBuilder();
		while (base.endsWith("[]")) {
			suffix.insert(0, "[]");
			base = base.substring(0, base.length() - 2);
		}
		int lt = base.indexOf('<');
		int gt = base.lastIndexOf('>');
		if (lt < 0 || gt <= lt) {
			return displayHead(model, base) + suffix;
		}
		StringBuilder args = new StringBuilder();
		for (String arg : splitTopLevelArgs(base.substring(lt + 1, gt))) {
			if (args.length() > 0) {
				args.append(", ");
			}
			args.append(displayType(model, arg.trim()));
		}
		return displayHead(model, base.substring(0, lt)) + "<" + args + ">" + suffix;
	}

	private String displayHead(MvpModel model, String qualifiedName) {
		String javaLang = "java.lang.";
		if (qualifiedName.startsWith(javaLang) && !qualifiedName.substring(javaLang.length()).contains(".")) {
			return qualifiedName.substring(javaLang.length());
		}
		String packagePrefix = model.packageName + ".";
		if (qualifiedName.startsWith(packagePrefix) && !qualifiedName.substring(packagePrefix.length()).contains(".")) {
			return qualifiedName.substring(packagePrefix.length());
		}
		return qualifiedName;
	}

	private static List<String> splitTopLevelArgs(String args) {
		List<String> result = new ArrayList<>();
		int depth = 0;
		int start = 0;
		for (int i = 0; i < args.length(); i++) {
			char c = args.charAt(i);
			if (c == '<') {
				depth++;
			} else if (c == '>') {
				depth--;
			} else if (c == ',' && depth == 0) {
				result.add(args.substring(start, i));
				start = i + 1;
			}
		}
		result.add(args.substring(start));
		return result;
	}

	private boolean isPrimitiveOrPrimitiveArray(String fullType) {
		String base = fullType;
		while (base.endsWith("[]")) {
			base = base.substring(0, base.length() - 2);
		}
		return "boolean".equals(base) || "byte".equals(base) || "char".equals(base) || "short".equals(base)
				|| "int".equals(base) || "long".equals(base) || "float".equals(base) || "double".equals(base);
	}

	/** Mirrors APT ParcelerHelper.isParcelType: @org.parceler.Parcel-annotated class or supported collection type. */
	private boolean isParcelerType(Resolver resolver, String fullType) {
		String base = fullType.contains("<") ? fullType.substring(0, fullType.indexOf('<')) : fullType;
		KSClassDeclaration declaration = resolveClassDeclaration(resolver, base);
		if (declaration == null) {
			return false;
		}
		if (KspAnnotationUtils.hasParcelerAnnotation(declaration)) {
			return true;
		}
		String arg = fullType.contains("<") ? topLevelGenericArg(fullType) : null;
		switch (base) {
		case "java.util.LinkedList":
		case "java.util.SortedSet":
		case "java.util.LinkedHashSet":
			return arg != null && isParcelerArgument(resolver, arg);
		case "java.util.LinkedHashMap":
		case "java.util.SortedMap":
			if (arg == null) {
				return false;
			}
			int split = topLevelComma(arg);
			if (split < 0) {
				return false;
			}
			return isParcelerArgument(resolver, arg.substring(0, split).trim())
					&& isParcelerArgument(resolver, arg.substring(split + 1).trim());
		default:
			return false;
		}
	}

	private boolean isParcelerArgument(Resolver resolver, String argType) {
		if ("android.os.Bundle".equals(argType) || "android.os.IBinder".equals(argType)) {
			return true;
		}
		String base = argType.contains("<") ? argType.substring(0, argType.indexOf('<')) : argType;
		KSClassDeclaration declaration = resolveClassDeclaration(resolver, base);
		if (declaration == null) {
			return false;
		}
		if (KspAnnotationUtils.hasParcelerAnnotation(declaration)) {
			return true;
		}
		return implementsInterface(declaration, "android.os.Parcelable");
	}

	private static String topLevelGenericArg(String fullType) {
		int start = fullType.indexOf('<');
		int end = fullType.lastIndexOf('>');
		if (start < 0 || end <= start) {
			return "";
		}
		return fullType.substring(start + 1, end);
	}

	private static int topLevelComma(String generics) {
		int depth = 0;
		for (int i = 0; i < generics.length(); i++) {
			char c = generics.charAt(i);
			if (c == '<') {
				depth++;
			} else if (c == '>') {
				depth--;
			} else if (c == ',' && depth == 0) {
				return i;
			}
		}
		return -1;
	}

	private KSClassDeclaration resolveClassDeclaration(Resolver resolver, String typeName) {
		if (typeName == null || typeName.isEmpty() || typeName.contains("[")) {
			return null;
		}
		String base = typeName.contains("<") ? typeName.substring(0, typeName.indexOf('<')) : typeName;
		try {
			return resolver.getClassDeclarationByName(resolver.getKSNameFromString(base));
		} catch (Exception e) {
			return null;
		}
	}

	private boolean implementsInterface(KSClassDeclaration declaration, String interfaceName) {
		int depth = 0;
		for (KSClassDeclaration current = declaration; current != null && depth < 32; current = superclassOf(current), depth++) {
			try {
				for (KSTypeReference superTypeRef : SequencesKt.toList(current.getSuperTypes())) {
					KSType superType = superTypeRef.resolve();
					if (superType.getDeclaration() instanceof KSClassDeclaration) {
						KSDeclaration typeDeclaration = superType.getDeclaration();
						String qualifiedName = typeDeclaration.getQualifiedName() != null ? typeDeclaration.getQualifiedName().asString() : null;
						if (interfaceName.equals(qualifiedName)) {
							return true;
						}
					}
				}
			} catch (Exception e) {
				return false;
			}
		}
		return false;
	}

	private KSClassDeclaration superclassOf(KSClassDeclaration declaration) {
		for (KSTypeReference superTypeRef : SequencesKt.toList(declaration.getSuperTypes())) {
			KSType superType = superTypeRef.resolve();
			if (superType.getDeclaration() instanceof KSClassDeclaration
					&& ((KSClassDeclaration) superType.getDeclaration()).getClassKind() == ClassKind.CLASS) {
				return (KSClassDeclaration) superType.getDeclaration();
			}
		}
		return null;
	}

}
