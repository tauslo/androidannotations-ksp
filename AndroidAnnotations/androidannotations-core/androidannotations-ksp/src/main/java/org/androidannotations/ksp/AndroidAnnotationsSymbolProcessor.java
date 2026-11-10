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
			model.extras.add(new MvpModel.FieldInjection(name, typeName, jvmField,
					AnnotationNames.RES_DEFAULT,
					KspAnnotationUtils.stringArg(extra, "value", ""),
					extraCastFor(resolver, property)));
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

	/** Mirrors APT IntentBuilder.getSuperPutExtraInvocation: cast extra arguments to Parcelable/Serializable when needed. */
	private String extraCastFor(Resolver resolver, KSPropertyDeclaration property) {
		String typeName = KspAnnotationUtils.propertyTypeName(property);
		if (isStandardBundleType(typeName)) {
			return null;
		}
		KSClassDeclaration typeDeclaration = resolveClassDeclaration(resolver, typeName);
		if (typeDeclaration == null) {
			return null;
		}
		if (implementsInterface(typeDeclaration, "android.os.Parcelable")) {
			return "(android.os.Parcelable)";
		}
		if (implementsInterface(typeDeclaration, "java.io.Serializable")) {
			return "(java.io.Serializable)";
		}
		return null;
	}

	private boolean isStandardBundleType(String typeName) {
		String base = typeName;
		while (base.endsWith("[]")) {
			base = base.substring(0, base.length() - 2);
		}
		return "boolean".equals(base) || "byte".equals(base) || "char".equals(base) || "short".equals(base)
				|| "int".equals(base) || "long".equals(base) || "float".equals(base) || "double".equals(base)
				|| "java.lang.Boolean".equals(base) || "java.lang.Byte".equals(base) || "java.lang.Character".equals(base)
				|| "java.lang.Short".equals(base) || "java.lang.Integer".equals(base) || "java.lang.Long".equals(base)
				|| "java.lang.Float".equals(base) || "java.lang.Double".equals(base)
				|| "java.lang.String".equals(base) || "java.lang.CharSequence".equals(base)
				|| "android.os.Bundle".equals(base);
	}

	private KSClassDeclaration resolveClassDeclaration(Resolver resolver, String typeName) {
		if (typeName == null || typeName.isEmpty() || typeName.contains("[")) {
			return null;
		}
		try {
			return resolver.getClassDeclarationByName(resolver.getKSNameFromString(typeName));
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