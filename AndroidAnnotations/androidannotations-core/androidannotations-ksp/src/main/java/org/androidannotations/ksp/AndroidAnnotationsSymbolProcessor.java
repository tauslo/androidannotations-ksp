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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
		classes.addAll(toClassList(resolver.getSymbolsWithAnnotation(AnnotationNames.E_FRAGMENT, false)));
		classes.addAll(toClassList(resolver.getSymbolsWithAnnotation(AnnotationNames.E_APPLICATION, false)));
		classes.addAll(toClassList(resolver.getSymbolsWithAnnotation(AnnotationNames.E_SERVICE, false)));

		environment.prepare(resolver, containingFiles(classes));
		if (!environment.isValid()) {
			return Collections.emptyList();
		}

		String resourcePackage = environment.getResourcePackage();
		String classSuffix = environment.getClassSuffix();
		int minSdkVersion = environment.getAndroidManifest() != null ? environment.getAndroidManifest().getMinSdkVersion() : -1;
		Map<String, ResCompat.Mode> resCompatModes = new HashMap<String, ResCompat.Mode>();
		for (ResCompat.Type type : ResCompat.ALL) {
			if (type.contextCompatAware) {
				resCompatModes.put(type.method, ResCompat.modeFor(resolver, minSdkVersion, type));
			}
		}
		MvpJavaGenerator generator = new MvpJavaGenerator(resourcePackage, environment.getRClassName(), environment.getRClassIndex(), resCompatModes);

		MvpHandler handler = new MvpHandler();

		warnUnimplemented(resolver);

		int generated = 0;
		List<MvpModel> models = new ArrayList<>();
		List<KSClassDeclaration> origins = new ArrayList<>();
		for (KSClassDeclaration classDeclaration : classes) {
			if (classDeclaration.getClassKind() != ClassKind.CLASS) {
				continue;
			}
			if (classDeclaration.getModifiers().contains(Modifier.ABSTRACT)) {
				continue;
			}
			models.add(handler.build(resolver, classDeclaration, classSuffix));
			origins.add(classDeclaration);
		}

		// Attach nested annotated models to their enclosing generated model (APT parity: static nested class).
		for (int i = 0; i < models.size(); i++) {
			MvpModel model = models.get(i);
			if (!model.nested) {
				continue;
			}
			KSClassDeclaration classDeclaration = origins.get(i);
			Set<Modifier> modifiers = classDeclaration.getModifiers();
			// Kotlin nested classes are static unless declared inner; Java sources expose the static keyword as JAVA_STATIC.
			boolean isStatic = modifiers.contains(Modifier.JAVA_STATIC) || !modifiers.contains(Modifier.INNER);
			if (!isStatic || modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.FINAL)) {
				logger.error("AndroidAnnotations KSP: nested " + model.qualifiedName + " must be a static non-private, non-final class (APT parity)", classDeclaration);
				models.set(i, null);
				continue;
			}
			String outerQualifiedName = model.qualifiedName.substring(0, model.qualifiedName.lastIndexOf('.'));
			MvpModel outer = null;
			for (int j = 0; j < models.size(); j++) {
				if (j != i && models.get(j) != null && models.get(j).qualifiedName.equals(outerQualifiedName)) {
					outer = models.get(j);
					break;
				}
			}
			if (outer == null) {
				logger.error("AndroidAnnotations KSP: enclosing class " + outerQualifiedName + " of nested " + model.qualifiedName
						+ " must be a generated AndroidAnnotations class (annotated, non-abstract)", classDeclaration);
				models.set(i, null);
				continue;
			}
			List<String> nestedResourceErrors = validateResources(model, environment.getRClassIndex());
			if (!nestedResourceErrors.isEmpty()) {
				for (String error : nestedResourceErrors) {
					logger.error("AndroidAnnotations KSP: " + error + " in " + model.qualifiedName, classDeclaration);
				}
				models.set(i, null);
				continue;
			}
			if (!model.errors.isEmpty()) {
				for (String error : model.errors) {
					logger.error("AndroidAnnotations KSP: " + error + " in " + model.qualifiedName, classDeclaration);
				}
				models.set(i, null);
				continue;
			}
			outer.nestedModels.add(model);
		}

		for (int i = 0; i < models.size(); i++) {
			MvpModel model = models.get(i);
			if (model == null || model.nested) {
				continue;
			}
			KSClassDeclaration classDeclaration = origins.get(i);
			String fqcn = model.generatedQualifiedName();
			if (!written.add(fqcn)) {
				continue;
			}
			if (model.kind == MvpModel.Kind.ACTIVITY || model.kind == MvpModel.Kind.SERVICE) {
				ComponentValidator.componentRegistered(classDeclaration, environment);
			} else if (model.kind == MvpModel.Kind.APPLICATION) {
				ComponentValidator.applicationRegistered(classDeclaration, environment);
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
			if (!model.errors.isEmpty()) {
				for (String error : model.errors) {
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
				|| (model.layoutResName != null && !model.layoutResName.isEmpty()) || !model.fragmentByIds.isEmpty()
				|| !model.clickListeners.isEmpty() || !model.longClickListeners.isEmpty() || !model.resFields.isEmpty();
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
		validateViewListeners(canCheckValues, index, model.clickListeners, errors);
		validateViewListeners(canCheckValues, index, model.longClickListeners, errors);
		for (MvpModel.FieldInjection stringRes : model.stringRes) {
			errors.addAll(validateInjection(canCheckValues, index, "string", stringRes));
		}
		for (MvpModel.ResField resField : model.resFields) {
			errors.addAll(validateInjection(canCheckValues, index, resField.rInnerClass, resField.propertyName, resField.resId, resField.resName));
		}
		for (MvpModel.FragmentInjection byId : model.fragmentByIds) {
			errors.addAll(validateFragmentById(canCheckValues, index, byId));
		}
		for (MvpModel.OptionsMenuRef menu : model.optionsMenus) {
			errors.addAll(validateInjection(canCheckValues, index, "menu", "", menu.resId, menu.resName));
		}
		for (MvpModel.OptionsItem item : model.optionsItems) {
			for (Integer resId : item.valueIds) {
				if (canCheckValues && !index.containsIdValue("id", resId)) {
					errors.add("Resource id value not found in R.id: " + resId);
				}
			}
			for (String resName : item.nameIds) {
				if (!index.containsField("id", resName)) {
					errors.add("Resource name not found in R.id: " + resName);
				}
			}
		}
		for (MvpModel.MenuInjection injection : model.optionsMenuItemInjections) {
			if (injection.fieldForm) {
				errors.addAll(validateInjection(canCheckValues, index, "id", injection.name, injection.resId, injection.resName));
			} else {
				for (int i = 0; i < injection.params.size(); i++) {
					List<Integer> ids = injection.valueIds.get(i);
					if (!ids.isEmpty()) {
						if (canCheckValues && !index.containsIdValue("id", ids.get(0))) {
							errors.add("Resource id value not found in R.id: " + ids.get(0));
						}
					} else if (!injection.nameIds.get(i).isEmpty() && !index.containsField("id", injection.nameIds.get(i).get(0))) {
						errors.add("Resource name not found in R.id: " + injection.nameIds.get(i).get(0));
					}
				}
			}
		}
		return errors;
	}

	/** Mirrors APT {@code AbstractViewListenerHandler} id validation for {@code @Click}/{@code @LongClick}. */
	private void validateViewListeners(boolean canCheckValues, RClassIndex index, List<MvpModel.ViewListener> listeners, List<String> errors) {
		for (MvpModel.ViewListener listener : listeners) {
			for (Integer resId : listener.valueIds) {
				if (canCheckValues && !index.containsIdValue("id", resId)) {
					errors.add("Resource id value not found in R.id: " + resId);
				}
			}
			for (String resName : listener.nameIds) {
				if (!index.containsField("id", resName)) {
					errors.add("Resource name not found in R.id: " + resName);
				}
			}
		}
	}

	/** Mirrors APT {@code resIdsExist} with the USE_ELEMENT_NAME fallback for {@code @ViewById}/{@code @StringRes}. */
	private List<String> validateInjection(boolean canCheckValues, RClassIndex index, String resType, MvpModel.FieldInjection injection) {
		return validateInjection(canCheckValues, index, resType, injection.propertyName, injection.resId, injection.resName);
	}

	private List<String> validateInjection(boolean canCheckValues, RClassIndex index, String resType, String propertyName, int resId, String resName) {
		List<String> errors = new ArrayList<>();
		if (resId != AnnotationNames.RES_DEFAULT) {
			if (canCheckValues && !index.containsIdValue(resType, resId)) {
				errors.add("Resource id value not found in R." + resType + ": " + resId);
			}
		} else if (resName != null && !resName.isEmpty()) {
			if (!index.containsField(resType, resName)) {
				errors.add("Resource name not found in R." + resType + ": " + resName);
			}
		} else if (!index.containsField(resType, propertyName)) {
			errors.add("Resource name not found in R." + resType + ": " + propertyName);
		}
		return errors;
	}

	/** Mirrors APT {@code resIdsExist} for {@code @FragmentById}. */
	private List<String> validateFragmentById(boolean canCheckValues, RClassIndex index, MvpModel.FragmentInjection byId) {
		List<String> errors = new ArrayList<>();
		if (byId.resId != AnnotationNames.RES_DEFAULT) {
			if (canCheckValues && !index.containsIdValue("id", byId.resId)) {
				errors.add("Resource id value not found in R.id: " + byId.resId);
			}
		} else if (byId.resName != null && !byId.resName.isEmpty()) {
			if (!index.containsField("id", byId.resName)) {
				errors.add("Resource name not found in R.id: " + byId.resName);
			}
		} else if (!index.containsField("id", byId.propertyName)) {
			errors.add("Resource name not found in R.id: " + byId.propertyName);
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

	/** Warns on AA annotations that are recognized but not implemented yet (one warning per annotation type). */
	private void warnUnimplemented(Resolver resolver) {
		for (String annotationName : AnnotationNames.RECOGNIZED_NOT_IMPLEMENTED) {
			List<KSAnnotated> symbols = SequencesKt.toList(resolver.getSymbolsWithAnnotation(annotationName, false));
			if (!symbols.isEmpty()) {
				KSAnnotated first = symbols.get(0);
				logger.warn("AndroidAnnotations KSP: @" + annotationName.substring(annotationName.lastIndexOf('.') + 1)
						+ " is recognized but not implemented yet; ignoring " + symbols.size() + " annotated element(s)",
						first instanceof KSNode ? (KSNode) first : null);
			}
		}
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
