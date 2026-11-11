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
import java.util.List;

import com.google.devtools.ksp.processing.Resolver;
import com.google.devtools.ksp.symbol.ClassKind;
import com.google.devtools.ksp.symbol.KSAnnotation;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.KSDeclaration;
import com.google.devtools.ksp.symbol.KSFunctionDeclaration;
import com.google.devtools.ksp.symbol.KSPropertyDeclaration;
import com.google.devtools.ksp.symbol.KSType;
import com.google.devtools.ksp.symbol.KSTypeReference;

import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;

import org.androidannotations.ksp.AnnotationNames;
import org.androidannotations.ksp.KspAnnotationUtils;
import org.androidannotations.ksp.MvpModel;

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

		MvpModel.Kind kind;
		int layoutId = AnnotationNames.RES_DEFAULT;
		String layoutResName = "";
		if (eActivity != null) {
			kind = MvpModel.Kind.ACTIVITY;
			layoutId = KspAnnotationUtils.intArg(eActivity, "value", AnnotationNames.RES_DEFAULT);
			layoutResName = KspAnnotationUtils.stringArg(eActivity, "resName", "");
		} else if (eBean != null) {
			kind = MvpModel.Kind.BEAN;
		} else {
			kind = MvpModel.Kind.BEAN;
		}

		MvpModel model = new MvpModel(kind, packageName, simpleName, qualifiedName, classSuffix, layoutId, layoutResName, nested);

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
				collectFunction((KSFunctionDeclaration) declaration, model);
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
		boolean jvmField = KspAnnotationUtils.hasJvmField(property);

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
	}

	private void collectFunction(KSFunctionDeclaration function, MvpModel model) {
		String name = function.getSimpleName().asString();
		if ("<init>".equals(name)) {
			return;
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
					false));
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
					reuse));
		}
	}

	/** Write/read expression templates for one @Extra, mirroring APT IntentBuilder.getSuperPutExtraInvocation + BundleHelper. */
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
			read = "extras_.get" + suffix + "({key})";
		} else if (fullType.endsWith("[]")) {
			String component = fullType.substring(0, fullType.length() - 2);
			KSClassDeclaration componentDecl = resolveClassDeclaration(resolver, component);
			if (componentDecl != null && implementsInterface(componentDecl, "android.os.Parcelable")) {
				read = "org.androidannotations.api.bundle.BundleHelper.getParcelableArray(extras_, {key}, " + component + "[].class)";
			} else {
				read = "(" + fullType + ") extras_.getSerializable({key})";
			}
		} else if (fullType.startsWith("java.util.ArrayList<")) {
			String arg = topLevelGenericArg(fullType);
			KSClassDeclaration argDecl = resolveClassDeclaration(resolver, arg);
			if (argDecl != null && implementsInterface(argDecl, "android.os.Parcelable")) {
				read = "extras_.getParcelableArrayList({key})";
			} else {
				read = "(" + fullType + ") extras_.getSerializable({key})";
				unchecked = true;
			}
		} else if (fullType.startsWith("android.util.SparseArray<")) {
			read = "extras_.getSparseParcelableArray({key})";
		} else if (parceler) {
			read = "org.parceler.Parcels.unwrap(extras_.getParcelable({key}))";
		} else if (parcelable) {
			read = "extras_.getParcelable({key})";
		} else if (serializable) {
			read = "(" + fullType + ") extras_.getSerializable({key})";
			unchecked = fullType.contains("<");
		} else {
			read = "(" + fullType + ") extras_.get({key})";
		}

		return new ExtraTemplates(write, read, unchecked);
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
