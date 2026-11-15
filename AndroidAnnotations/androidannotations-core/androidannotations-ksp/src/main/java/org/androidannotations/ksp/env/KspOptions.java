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

import java.util.Map;

import javax.lang.model.SourceVersion;

/**
 * Typed access to the KSP options of the AndroidAnnotations processor.
 * <p>
 * Option names are aligned with the APT {@code Option} constants so that kapt and KSP
 * configurations stay interchangeable:
 * <ul>
 * <li>{@code classSuffix} (default {@code _}) - suffix of generated classes</li>
 * <li>{@code androidManifestFile} (default none) - explicit path to AndroidManifest.xml</li>
 * <li>{@code library} (default {@code false}) - relax component registration validation</li>
 * <li>{@code useR2} (default {@code false}) - reference {@code R2} instead of {@code R}</li>
 * <li>{@code resourcePackageName} (default: manifest application package) - package holding the R class</li>
 * <li>{@code trace} (default {@code false}) - generate {@code @Trace} timing logs</li>
 * <li>{@code threadControl} (default {@code true}) - generate {@code @SupposeUiThread} / {@code @SupposeBackground} checks</li>
 * <li>{@code unimplementedIsError} (default {@code false}) - report recognized-but-unimplemented AA annotations as errors</li>
 * <li>{@code logDebug} (default {@code false}) - emit processor timing / generated-count diagnostics at warn; otherwise info</li>
 * </ul>
 */
public final class KspOptions {

	public static final String CLASS_SUFFIX = "classSuffix";
	public static final String ANDROID_MANIFEST_FILE = "androidManifestFile";
	public static final String LIBRARY = "library";
	public static final String USE_R2 = "useR2";
	public static final String RESOURCE_PACKAGE_NAME = "resourcePackageName";
	/** APT {@code TraceHandler.OPTION_TRACE}. Default false: {@code @Trace} is ignored until enabled. */
	public static final String TRACE = "trace";
	/** APT {@code SupposeThreadHandler.OPTION_THREAD_CONTROL}. Default true. */
	public static final String THREAD_CONTROL = "threadControl";
	/** When true, {@code RECOGNIZED_NOT_IMPLEMENTED} annotations fail the build instead of warning. Default false. */
	public static final String UNIMPLEMENTED_IS_ERROR = "unimplementedIsError";
	/** When true, processor timing / generated-count lines use warn; default info. */
	public static final String LOG_DEBUG = "logDebug";

	private final String classSuffix;
	private final boolean library;
	private final boolean useR2;
	private final boolean trace;
	private final boolean threadControl;
	private final boolean unimplementedIsError;
	private final boolean logDebug;
	private final String resourcePackageName;
	private final String androidManifestFile;
	private final String initError;

	private KspOptions(String classSuffix, boolean library, boolean useR2, boolean trace, boolean threadControl, boolean unimplementedIsError, boolean logDebug, String resourcePackageName, String androidManifestFile, String initError) {
		this.classSuffix = classSuffix;
		this.library = library;
		this.useR2 = useR2;
		this.trace = trace;
		this.threadControl = threadControl;
		this.unimplementedIsError = unimplementedIsError;
		this.logDebug = logDebug;
		this.resourcePackageName = resourcePackageName;
		this.androidManifestFile = androidManifestFile;
		this.initError = initError;
	}

	public static KspOptions from(Map<String, String> options) {
		String suffix = options.get(CLASS_SUFFIX);
		if (suffix == null || suffix.trim().isEmpty()) {
			suffix = "_";
		} else {
			suffix = suffix.trim();
		}

		String error = validateClassSuffix(suffix);
		return new KspOptions(
				error == null ? suffix : "_",
				parseBoolean(options.get(LIBRARY), false),
				parseBoolean(options.get(USE_R2), false),
				parseBoolean(options.get(TRACE), false),
				parseBoolean(options.get(THREAD_CONTROL), true),
				parseBoolean(options.get(UNIMPLEMENTED_IS_ERROR), false),
				parseBoolean(options.get(LOG_DEBUG), false),
				emptyToNull(options.get(RESOURCE_PACKAGE_NAME)),
				emptyToNull(options.get(ANDROID_MANIFEST_FILE)),
				error);
	}

	private static String validateClassSuffix(String suffix) {
		if (suffix.isEmpty()) {
			return "'" + CLASS_SUFFIX + "' may not be an empty string.";
		}
		if (!SourceVersion.isName("ValidName" + suffix) || suffix.contains(".")) {
			return "'" + suffix + "' is not a valid Java identifier and cannot be used as " + CLASS_SUFFIX;
		}
		return null;
	}

	private static boolean parseBoolean(String value, boolean defaultValue) {
		if (value == null || value.trim().isEmpty()) {
			return defaultValue;
		}
		return Boolean.parseBoolean(value.trim());
	}

	private static String emptyToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	public boolean isValid() {
		return initError == null;
	}

	public String getInitError() {
		return initError;
	}

	public String getClassSuffix() {
		return classSuffix;
	}

	public boolean isLibrary() {
		return library;
	}

	public boolean isUseR2() {
		return useR2;
	}

	public String getResourcePackageName() {
		return resourcePackageName;
	}

	public String getAndroidManifestFile() {
		return androidManifestFile;
	}

	public boolean isTrace() {
		return trace;
	}

	/** True unless the KSP argument {@code threadControl} is explicitly {@code false}. */
	public boolean isThreadControl() {
		return threadControl;
	}

	/** True when unrecognized-as-implemented AA annotations should fail compilation. */
	public boolean isUnimplementedIsError() {
		return unimplementedIsError;
	}

	/** True when processor generated-count / timing diagnostics should use warn instead of info. */
	public boolean isLogDebug() {
		return logDebug;
	}
}