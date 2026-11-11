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
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.KSFile;
import com.google.devtools.ksp.symbol.KSNode;
import com.google.devtools.ksp.symbol.Modifier;

import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;

import org.androidannotations.ksp.env.KspEnvironment;
import org.androidannotations.ksp.env.RClassIndex;
import org.androidannotations.ksp.generate.MvpJavaGenerator;
import org.androidannotations.ksp.handler.MvpHandler;
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
		MvpJavaGenerator generator = new MvpJavaGenerator(resourcePackage, environment.getRClassName(), environment.getRClassIndex());

		MvpHandler handler = new MvpHandler();

		int generated = 0;
		for (KSClassDeclaration classDeclaration : classes) {
			if (classDeclaration.getClassKind() != ClassKind.CLASS) {
				continue;
			}
			if (classDeclaration.getModifiers().contains(Modifier.ABSTRACT)) {
				continue;
			}
			MvpModel model = handler.build(resolver, classDeclaration, classSuffix);
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
			List<String> resourceErrors = validateResources(model, environment.getRClassIndex());
			if (!resourceErrors.isEmpty()) {
				for (String error : resourceErrors) {
					logger.error("AndroidAnnotations KSP: " + error + " in " + model.qualifiedName, classDeclaration);
				}
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

	/**
	 * Validates the resource references of {@code model} against the resolved R class, mirroring APT
	 * {@code IdValidatorHelper.resIdsExist}. Value refs need the symbol list to be loaded; name and element-name
	 * refs only need the field names. Returns one message per problem; empty means the model may be generated.
	 */
	private List<String> validateResources(MvpModel model, RClassIndex index) {
		List<String> errors = new ArrayList<>();
		if (index == null || !index.isResolved()) {
			return errors;
		}
		boolean canCheckValues = index.hasValues();
		// @EActivity layout uses ALLOW_NO_RES_ID: nothing given is fine, otherwise the value or name must exist.
		if (model.layoutId != AnnotationNames.RES_DEFAULT) {
			if (canCheckValues && !index.containsIdValue("layout", model.layoutId)) {
				errors.add("Resource id value not found in R.layout: " + model.layoutId);
			}
		} else if (model.layoutResName != null && !model.layoutResName.isEmpty()) {
			if (!index.containsField("layout", model.layoutResName)) {
				errors.add("Resource name not found in R.layout: " + model.layoutResName);
			}
		}
		for (MvpModel.FieldInjection view : model.viewByIds) {
			errors.addAll(validateInjection(canCheckValues, index, "id", view));
		}
		for (MvpModel.FieldInjection stringRes : model.stringRes) {
			errors.addAll(validateInjection(canCheckValues, index, "string", stringRes));
		}
		return errors;
	}

	/** Mirrors APT {@code resIdsExist} with the USE_ELEMENT_NAME fallback for {@code @ViewById}/{@code @StringRes}. */
	private List<String> validateInjection(boolean canCheckValues, RClassIndex index, String resType, MvpModel.FieldInjection injection) {
		List<String> errors = new ArrayList<>();
		if (injection.resId != AnnotationNames.RES_DEFAULT) {
			if (canCheckValues && !index.containsIdValue(resType, injection.resId)) {
				errors.add("Resource id value not found in R." + resType + ": " + injection.resId);
			}
		} else if (injection.resName != null && !injection.resName.isEmpty()) {
			if (!index.containsField(resType, injection.resName)) {
				errors.add("Resource name not found in R." + resType + ": " + injection.resName);
			}
		} else if (!index.containsField(resType, injection.propertyName)) {
			errors.add("Resource name not found in R." + resType + ": " + injection.propertyName);
		}
		return errors;
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