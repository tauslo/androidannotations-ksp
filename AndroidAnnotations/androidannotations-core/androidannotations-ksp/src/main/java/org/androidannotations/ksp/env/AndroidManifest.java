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

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable model of an AndroidManifest.xml, mirroring the APT
 * {@code org.androidannotations.helper.AndroidManifest} data holder.
 * <p>
 * Component names are fully qualified and resolved against the compile classpath by
 * {@link AndroidManifestFinder}.
 */
public final class AndroidManifest {

	private final String sourceFile;
	private final String applicationPackage;
	private final String applicationClassName;
	private final List<String> componentQualifiedNames;
	private final Map<String, MetaDataInfo> metaDataQualifiedNames;
	private final List<String> permissionQualifiedNames;
	private final int minSdkVersion;
	private final int maxSdkVersion;
	private final int targetSdkVersion;
	private final boolean debuggable;

	private AndroidManifest(String sourceFile, String applicationPackage, String applicationClassName, List<String> componentQualifiedNames,
			Map<String, MetaDataInfo> metaDataQualifiedNames, List<String> permissionQualifiedNames, int minSdkVersion, int maxSdkVersion, int targetSdkVersion, boolean debuggable) {
		this.sourceFile = sourceFile;
		this.applicationPackage = applicationPackage == null ? "" : applicationPackage;
		this.applicationClassName = applicationClassName;
		this.componentQualifiedNames = Collections.unmodifiableList(componentQualifiedNames);
		this.metaDataQualifiedNames = Collections.unmodifiableMap(metaDataQualifiedNames);
		this.permissionQualifiedNames = Collections.unmodifiableList(permissionQualifiedNames);
		this.minSdkVersion = minSdkVersion;
		this.maxSdkVersion = maxSdkVersion;
		this.targetSdkVersion = targetSdkVersion;
		this.debuggable = debuggable;
	}

	public static AndroidManifest create(String sourceFile, String applicationPackage, String applicationClassName, List<String> componentQualifiedNames,
			Map<String, MetaDataInfo> metaDataQualifiedNames, List<String> permissionQualifiedNames, int minSdkVersion, int maxSdkVersion, int targetSdkVersion, boolean debuggable) {
		return new AndroidManifest(sourceFile, applicationPackage, applicationClassName, componentQualifiedNames, metaDataQualifiedNames, permissionQualifiedNames, minSdkVersion,
				maxSdkVersion, targetSdkVersion, debuggable);
	}

	/** Manifest of a library project: only the package and SDK info are kept. */
	public static AndroidManifest createLibraryManifest(String sourceFile, String applicationPackage, int minSdkVersion, int maxSdkVersion, int targetSdkVersion) {
		return new AndroidManifest(sourceFile, applicationPackage, "", Collections.emptyList(), Collections.emptyMap(), Collections.emptyList(), minSdkVersion, maxSdkVersion,
				targetSdkVersion, false);
	}

	public String getSourceFile() {
		return sourceFile;
	}

	public String getApplicationPackage() {
		return applicationPackage;
	}

	public boolean hasApplicationPackage() {
		return !applicationPackage.isEmpty();
	}

	public String getApplicationClassName() {
		return applicationClassName;
	}

	public List<String> getComponentQualifiedNames() {
		return componentQualifiedNames;
	}

	public Map<String, MetaDataInfo> getMetaDataQualifiedNames() {
		return metaDataQualifiedNames;
	}

	public List<String> getPermissionQualifiedNames() {
		return permissionQualifiedNames;
	}

	public int getMinSdkVersion() {
		return minSdkVersion;
	}

	public int getMaxSdkVersion() {
		return maxSdkVersion;
	}

	public int getTargetSdkVersion() {
		return targetSdkVersion;
	}

	public boolean isDebuggable() {
		return debuggable;
	}

	@Override
	public String toString() {
		return "AndroidManifest [sourceFile=" + sourceFile + ", applicationPackage=" + applicationPackage + ", componentQualifiedNames=" + componentQualifiedNames
				+ ", permissionQualifiedNames=" + permissionQualifiedNames + ", minSdkVersion=" + minSdkVersion + ", debuggable=" + debuggable + "]";
	}

	public static final class MetaDataInfo {

		private final String name;
		private final String value;
		private final String resource;

		public MetaDataInfo(String name, String value, String resource) {
			this.name = name;
			this.value = value;
			this.resource = resource;
		}

		public String getName() {
			return name;
		}

		public String getValue() {
			return value;
		}

		public String getResource() {
			return resource;
		}
	}
}