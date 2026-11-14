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
package org.androidannotations.ksp.validate;

import java.util.List;

import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.Modifier;

import org.androidannotations.ksp.env.AndroidManifest;
import org.androidannotations.ksp.env.KspEnvironment;

/**
 * Validates that {@code @E*} components are registered in the AndroidManifest.xml, mirroring APT
 * {@code ValidatorHelper.componentRegistered}:
 * <ul>
 * <li>abstract classes are skipped</li>
 * <li>library projects skip validation entirely</li>
 * <li>registering the original (non-generated) class is an error</li>
 * <li>a missing registration of the generated class is a warning</li>
 * </ul>
 */
public final class ComponentValidator {

	private ComponentValidator() {
	}

	public static void componentRegistered(KSClassDeclaration element, KspEnvironment environment) {
		if (element.getModifiers().contains(Modifier.ABSTRACT)) {
			return;
		}
		if (environment.isLibrary()) {
			return;
		}
		AndroidManifest manifest = environment.getAndroidManifest();
		if (manifest == null) {
			return;
		}

		String qualifiedName = element.getQualifiedName() != null ? element.getQualifiedName().asString() : null;
		if (qualifiedName == null) {
			return;
		}
		String classSuffix = environment.getClassSuffix();
		String generatedQualifiedName = qualifiedName + classSuffix;

		List<String> componentQualifiedNames = manifest.getComponentQualifiedNames();
		if (!componentQualifiedNames.contains(generatedQualifiedName)) {
			String simpleName = element.getSimpleName().asString();
			String generatedSimpleName = simpleName + classSuffix;
			if (componentQualifiedNames.contains(qualifiedName)) {
				environment.getLogger().error("The AndroidManifest.xml file contains the original component, and not the AndroidAnnotations generated component. Please register "
						+ generatedSimpleName + " instead of " + simpleName, element);
			} else {
				environment.getLogger().warn("The component " + generatedSimpleName + " is not registered in the AndroidManifest.xml file.", element);
			}
		}
	}


	/**
	 * Mirrors APT {@code CoreValidatorHelper.applicationRegistered}: for an {@code @EApplication} component the manifest's
	 * application class must be the generated one. Unlike {@link #componentRegistered} the messages use fully qualified
	 * names, and a missing application class is an error rather than a warning.
	 */
	public static void applicationRegistered(KSClassDeclaration element, KspEnvironment environment) {
		if (environment.isLibrary()) {
			return;
		}
		AndroidManifest manifest = environment.getAndroidManifest();
		if (manifest == null) {
			return;
		}

		String qualifiedName = element.getQualifiedName() != null ? element.getQualifiedName().asString() : null;
		if (qualifiedName == null) {
			return;
		}
		String generatedQualifiedName = qualifiedName + environment.getClassSuffix();

		String applicationClassName = manifest.getApplicationClassName();
		if (applicationClassName != null) {
			if (!element.getModifiers().contains(Modifier.ABSTRACT) && !applicationClassName.equals(generatedQualifiedName)) {
				if (applicationClassName.equals(qualifiedName)) {
					environment.getLogger().error("The AndroidManifest.xml file contains the original component, and not the AndroidAnnotations generated component." + " Please register "
						+ generatedQualifiedName + " instead of " + qualifiedName, element);
				} else {
					environment.getLogger().warn("The component " + generatedQualifiedName + " is not registered in the AndroidManifest.xml file.", element);
				}
			}
		} else {
			environment.getLogger().error("No application class registered in the AndroidManifest.xml", element);
		}
	}
}