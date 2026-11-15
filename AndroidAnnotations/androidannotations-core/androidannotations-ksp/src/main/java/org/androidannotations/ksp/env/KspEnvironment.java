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
package org.androidannotations.ksp.env;

import java.util.List;

import com.google.devtools.ksp.processing.KSPLogger;
import com.google.devtools.ksp.processing.Resolver;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import com.google.devtools.ksp.symbol.KSFile;

/**
 * Single access point for processor options and the resolved AndroidManifest, mirroring the APT
 * {@code AndroidAnnotationsEnvironment}. Handlers, holders and writers must read configuration through
 * this class instead of the raw KSP options map.
 */
public final class KspEnvironment {

	private final KSPLogger logger;
	private final KspOptions options;
	private final boolean valid;

	private AndroidManifest androidManifest;
	private RClassIndex rClassIndex;
	private boolean prepared;

	public KspEnvironment(SymbolProcessorEnvironment environment) {
		this.logger = environment.getLogger();
		this.options = KspOptions.from(environment.getOptions());
		this.valid = this.options.isValid();
		if (!valid) {
			logger.error("AndroidAnnotations KSP: " + options.getInitError(), null);
		}
	}

	/**
	 * Loads and resolves the AndroidManifest on the first call; later calls (further KSP rounds) are no-ops.
	 *
	 * @param sourceFiles files of the annotated classes, used to anchor manifest discovery to the module directory
	 */
	public void prepare(Resolver resolver, List<KSFile> sourceFiles) {
		if (prepared) {
			return;
		}
		prepared = true;
		if (!valid) {
			return;
		}
		this.androidManifest = AndroidManifestFinder.find(resolver, logger, sourceFiles, options.getAndroidManifestFile(), options.isLibrary(), options.getClassSuffix());
		if (androidManifest == null) {
			logger.warn("AndroidAnnotations KSP: no AndroidManifest.xml found; component registration validation is disabled. Set the 'androidManifestFile' KSP argument to enable it.", null);
		} else {
			logger.info("AndroidAnnotations KSP: using AndroidManifest " + androidManifest.getSourceFile(), null);
		}
		String resourcePackage = getResourcePackage();
		if (resourcePackage != null) {
			this.rClassIndex = RClassIndex.resolve(resolver, logger, sourceFiles, resourcePackage, getRClassName());
		}
	}

	/** The package holding the R class: explicit option, else the manifest application package. */
	public String getResourcePackage() {
		String resourcePackageName = options.getResourcePackageName();
		if (resourcePackageName != null) {
			return resourcePackageName;
		}
		if (androidManifest != null && androidManifest.hasApplicationPackage()) {
			return androidManifest.getApplicationPackage();
		}
		return null;
	}

	public String getClassSuffix() {
		return options.getClassSuffix();
	}

	public boolean isLibrary() {
		return options.isLibrary();
	}

	public boolean isTrace() {
		return options.isTrace();
	}

	public boolean isThreadControl() {
		return options.isThreadControl();
	}

	public boolean isUnimplementedIsError() {
		return options.isUnimplementedIsError();
	}

	public boolean isLogDebug() {
		return options.isLogDebug();
	}

	/** Simple name of the resource class to reference, mirroring APT {@code ProjectRClassFinder}. */
	public String getRClassName() {
		return options.isUseR2() ? "R2" : "R";
	}

	/** Index of the resolved R/R2 class (field names + int values), or null when no resource package is known. */
	public RClassIndex getRClassIndex() {
		return rClassIndex;
	}

	public AndroidManifest getAndroidManifest() {
		return androidManifest;
	}

	public KSPLogger getLogger() {
		return logger;
	}

	public boolean isValid() {
		return valid;
	}
}