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
package org.androidannotations.ksp;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.devtools.ksp.processing.CodeGenerator;
import com.google.devtools.ksp.processing.Dependencies;
import com.google.devtools.ksp.processing.KSPLogger;
import com.google.devtools.ksp.processing.Resolver;
import com.google.devtools.ksp.processing.SymbolProcessor;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import com.google.devtools.ksp.symbol.ClassKind;
import com.google.devtools.ksp.symbol.KSAnnotated;
import com.google.devtools.ksp.symbol.KSAnnotation;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.KSDeclaration;
import com.google.devtools.ksp.symbol.KSFile;
import com.google.devtools.ksp.symbol.KSFunctionDeclaration;
import com.google.devtools.ksp.symbol.KSNode;
import com.google.devtools.ksp.symbol.KSPropertyDeclaration;
import com.google.devtools.ksp.symbol.KSType;
import com.google.devtools.ksp.symbol.KSTypeReference;
import com.google.devtools.ksp.symbol.Modifier;

import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;

import org.androidannotations.ksp.env.KspEnvironment;
import org.androidannotations.ksp.validate.ComponentValidator;

public class AndroidAnnotationsSymbolProcessor implements SymbolProcessor {

	private final CodeGenerator codeGenerator;
	private final KSPLogger logger;
	private final KspEnvironment environment;
	private final Set<String> written = new LinkedHashSet<>();

	public AndroidAnnotationsSymbolProcessor(SymbolProcessorEnvironment environment) {
		this.codeGenerator = environment.getCodeGenerator();
		this.logger = environment.getLogger();
		this.environment = new KspEnvironment(environment);
	}

	@Override
	public List<KSAnnotated> process(Resolver resolver) {
		long start = System.currentTimeMillis();

		List<KSClassDeclaration> classes = new ArrayList<>();
		classes.addAll(toClassList(resolver.getSymbolsWithAnnotation(AnnotationNames.E_ACTIVITY, false)));
		classes.addAll(toClassList(resolver.getSymbolsWithAnnotation(AnnotationNames.E_BEAN, false)));

		environment.prepare(resolver, containingFiles(classes));
		if (!environment.isValid()) {
			return Collections.emptyList();
		}

		String resourcePackage = environment.getResourcePackage();
		String classSuffix = environment.getClassSuffix();
		MvpJavaGenerator generator = new MvpJavaGenerator(resourcePackage, environment.getRClassName());

		int generated = 0;
		for (KSClassDeclaration classDeclaration : classes) {
			if (classDeclaration.getClassKind() != ClassKind.CLASS) {
				continue;
			}
			if (classDeclaration.getModifiers().contains(Modifier.ABSTRACT)) {
				continue;
			}
			MvpModel model = buildModel(resolver, classDeclaration, classSuffix);
			String fqcn = model.generatedQualifiedName();
			if (!written.add(fqcn)) {
				continue;
			}
			if (model.kind == MvpModel.Kind.ACTIVITY) {
				ComponentValidator.componentRegistered(classDeclaration, environment);
			}
			if (resourcePackage == null && needsResourcePackage(model)) {
				logger.error("AndroidAnnotations KSP: could not determine the resource package name for " + model.qualifiedName
						+ ". Set the 'resourcePackageName' KSP argument or provide an AndroidManifest.xml with a package attribute.", classDeclaration);
				continue;
			}
			String source = generator.generate(model);
			writeJavaFile(model, source, classDeclaration);
			generated++;
		}

		String manifestPath = environment.getAndroidManifest() != null ? environment.getAndroidManifest().getSourceFile() : "none";
		logger.warn("AndroidAnnotations KSP generated " + generated + " class(es) in " + (System.currentTimeMillis() - start) + " ms (resourcePackage="
				+ resourcePackage + ", manifest=" + manifestPath + ")", null);
		return Collections.emptyList();
	}

	private boolean needsResourcePackage(MvpModel model) {
		return !model.stringRes.isEmpty() || !model.viewByIds.isEmpty() || model.layoutId != AnnotationNames.RES_DEFAULT
				|| (model.layoutResName != null && !model.layoutResName.isEmpty());
	}

	private List<KSFile> containingFiles(List<KSClassDeclaration> classes) {
		List<KSFile> files = new ArrayList<>();
		for (KSClassDeclaration classDeclaration : classes) {
			KSFile file = classDeclaration.getContainingFile();
			if (file != null && !files.contains(file)) {
				files.add(file);
			}
		}
		return files;
	}

	private List<KSClassDeclaration> toClassList(Sequence<KSAnnotated> sequence) {
		List<KSClassDeclaration> result = new ArrayList<>();
		for (KSAnnotated annotated : SequencesKt.toList(sequence)) {
			if (annotated instanceof KSClassDeclaration) {
				result.add((KSClassDeclaration) annotated);
			}
		}
		return result;
	}

	private MvpModel buildModel(Resolver resolver, KSClassDeclaration classDeclaration, String classSuffix) {
		String packageName = "";
		if (classDeclaration.getPackageName().asString() != null) {
			packageName = classDeclaration.getPackageName().asString();
		}
		String simpleName = classDeclaration.getSimpleName().asString();
		String qualifiedName = classDeclaration.getQualifiedName() != null
				? classDeclaration.getQualifiedName().asString()
				: simpleName;

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

		MvpModel model = new MvpModel(kind, packageName, simpleName, qualifiedName, classSuffix, layoutId, layoutResName);

		for (KSDeclaration declaration : SequencesKt.toList(classDeclaration.getDeclarations())) {
			if (declaration instanceof KSPropertyDeclaration) {
				collectProperty(resolver, (KSPropertyDeclaration) declaration, model);
			} else if (declaration instanceof KSFunctionDeclaration) {
				collectFunction((KSFunctionDeclaration) declaration, model);
			}
		}
		return model;
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

	private void writeJavaFile(MvpModel model, String source, KSClassDeclaration origin) {
		try {
			String packageName = model.packageName == null ? "" : model.packageName;
			KSFile containingFile = origin.getContainingFile();
			Dependencies dependencies = containingFile != null
					? new Dependencies(false, containingFile)
					: new Dependencies(false);
			OutputStream stream = codeGenerator.createNewFile(
					dependencies,
					packageName,
					model.generatedSimpleName(),
					"java");
			try (OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
				writer.write(source);
			}
		} catch (IOException e) {
			logger.error("Failed to write " + model.generatedQualifiedName() + ": " + e.getMessage(), (KSNode) origin);
		}
	}
}