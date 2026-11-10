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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.androidannotations.ksp.env.AndroidManifest.MetaDataInfo;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.devtools.ksp.processing.KSPLogger;
import com.google.devtools.ksp.processing.Resolver;
import com.google.devtools.ksp.symbol.KSFile;

/**
 * Locates and parses the AndroidManifest.xml for the project, mirroring the APT
 * {@code AndroidManifestFinder} behavior with KSP-appropriate discovery:
 * <ul>
 * <li>an explicit {@code androidManifestFile} option wins (relative paths resolve against the working directory)</li>
 * <li>otherwise the finder anchors on the directories of the source files being processed and probes upward from each,
 * preferring merged manifests ({@code build/intermediates/merged_manifests/<variant>/[process<Variant>Manifest/]AndroidManifest.xml})
 * over source manifests ({@code src/main/AndroidManifest.xml}), and preferring files that carry a {@code package} attribute.
 * Anchoring on source files is necessary because KSP runs in the Kotlin daemon, whose working directory is not the project</li>
 * <li>as a last resort the finder probes upward from the process working directory</li>
 * </ul>
 */
public final class AndroidManifestFinder {

	private static final String MERGED_MANIFESTS_DIR = "build" + File.separator + "intermediates" + File.separator + "merged_manifests";
	private static final String SOURCE_MANIFEST_PATH = "src" + File.separator + "main" + File.separator + "AndroidManifest.xml";

	private AndroidManifestFinder() {
	}

	public static AndroidManifest find(Resolver resolver, KSPLogger logger, List<KSFile> sourceFiles, String explicitPath, boolean library, String classSuffix) {
		if (explicitPath != null) {
			File file = new File(explicitPath);
			if (!file.isAbsolute()) {
				file = new File(System.getProperty("user.dir", "."), explicitPath);
			}
			if (!file.isFile()) {
				logger.error("Could not find the AndroidManifest.xml file in specified path : " + explicitPath, null);
				return null;
			}
			AndroidManifest manifest = parse(resolver, logger, file, library, classSuffix);
			if (manifest == null) {
				logger.error("Could not parse the AndroidManifest.xml file at path " + file, null);
			}
			return manifest;
		}

		Set<String> seenCandidates = new HashSet<>();
		List<File> mergedCandidates = new ArrayList<>();
		List<File> sourceCandidates = new ArrayList<>();
		for (KSFile sourceFile : sourceFiles) {
			File anchor = moduleAnchor(sourceFile);
			if (anchor == null) {
				continue;
			}
			collectUpward(anchor, seenCandidates, mergedCandidates, sourceCandidates);
		}

		// Merged manifests win over src/main manifests no matter which level of the tree they were found at.
		AndroidManifest manifest = selectFromCandidates(mergedCandidates, resolver, logger, library, classSuffix);
		if (manifest != null) {
			return manifest;
		}
		manifest = selectFromCandidates(sourceCandidates, resolver, logger, library, classSuffix);
		if (manifest != null) {
			return manifest;
		}

		Set<String> probedDirs = new HashSet<>();
		return probeUpward(new File(System.getProperty("user.dir", ".")), probedDirs, resolver, logger, library, classSuffix);
	}

	private static AndroidManifest probeUpward(File start, Set<String> probedDirs, Resolver resolver, KSPLogger logger, boolean library, String classSuffix) {
		for (File dir = start; dir != null && probedDirs.add(dir.getAbsolutePath()); dir = dir.getParentFile()) {
			AndroidManifest manifest = selectFromCandidates(candidatesIn(dir), resolver, logger, library, classSuffix);
			if (manifest != null) {
				return manifest;
			}
		}
		return null;
	}

	private static File moduleAnchor(KSFile sourceFile) {
		String path = sourceFile.getFilePath();
		if (path == null || path.isEmpty()) {
			path = sourceFile.getFileName();
		}
		if (path == null) {
			return null;
		}
		File file = new File(path);
		if (!file.isAbsolute()) {
			return null;
		}
		File parent = file.getParentFile();
		return parent != null ? parent : file;
	}

	private static final String MERGED_MARKER = "build/intermediates/merged_manifests/";

	private static void collectUpward(File start, Set<String> seenCandidates, List<File> mergedCandidates, List<File> sourceCandidates) {
		for (File dir = start; dir != null; dir = dir.getParentFile()) {
			for (File candidate : candidatesIn(dir)) {
				String key = candidate.getAbsolutePath();
				if (!seenCandidates.add(key)) {
					continue;
				}
				String normalized = candidate.getPath().replace('\\', '/') + "/";
				if (normalized.contains(MERGED_MARKER)) {
					mergedCandidates.add(candidate);
				} else {
					sourceCandidates.add(candidate);
				}
			}
		}
	}

	private static List<File> candidatesIn(File dir) {
		List<File> candidates = new ArrayList<>();
		candidates.addAll(mergedManifests(dir));
		File sourceManifest = new File(dir, SOURCE_MANIFEST_PATH);
		if (sourceManifest.isFile()) {
			candidates.add(sourceManifest);
		}
		return candidates;
	}

	private static AndroidManifest selectFromCandidates(List<File> candidates, Resolver resolver, KSPLogger logger, boolean library, String classSuffix) {
		AndroidManifest fallback = null;
		for (File candidate : candidates) {
			AndroidManifest manifest = parse(resolver, logger, candidate, library, classSuffix);
			if (manifest == null) {
				logger.warn("Could not parse the AndroidManifest.xml file at path " + candidate + ", trying the next candidate", null);
				continue;
			}
			if (manifest.hasApplicationPackage()) {
				return manifest;
			}
			if (fallback == null) {
				fallback = manifest;
			}
		}
		return fallback;
	}

	private static List<File> mergedManifests(File dir) {
		File base = new File(dir, MERGED_MANIFESTS_DIR);
		if (!base.isDirectory()) {
			return Collections.emptyList();
		}
		List<File> found = new ArrayList<>();
		collectMergedManifests(base, 0, found);
		found.sort((a, b) -> {
			String aPath = a.getPath().replace('\\', '/');
			String bPath = b.getPath().replace('\\', '/');
			boolean aDebug = aPath.contains("/debug");
			boolean bDebug = bPath.contains("/debug");
			if (aDebug != bDebug) {
				return aDebug ? -1 : 1;
			}
			return aPath.compareToIgnoreCase(bPath);
		});
		return found;
	}

	private static void collectMergedManifests(File node, int depth, List<File> out) {
		if (depth > 2 || !node.isDirectory()) {
			return;
		}
		File[] children = node.listFiles();
		if (children == null) {
			return;
		}
		for (File child : children) {
			if (child.isFile() && "AndroidManifest.xml".equals(child.getName())) {
				out.add(child);
			} else if (child.isDirectory()) {
				collectMergedManifests(child, depth + 1, out);
			}
		}
	}

	private static AndroidManifest parse(Resolver resolver, KSPLogger logger, File file, boolean library, String classSuffix) {
		try {
			DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
			Document doc = docBuilder.parse(file);
			Element documentElement = (Element) doc.getDocumentElement();
			documentElement.normalize();

			String applicationPackage = documentElement.getAttribute("package");

			int minSdkVersion = -1;
			int maxSdkVersion = -1;
			int targetSdkVersion = -1;
			NodeList sdkNodes = documentElement.getElementsByTagName("uses-sdk");
			if (sdkNodes.getLength() > 0) {
				Node sdkNode = sdkNodes.item(0);
				minSdkVersion = extractAttributeIntValue(sdkNode, "android:minSdkVersion", -1);
				maxSdkVersion = extractAttributeIntValue(sdkNode, "android:maxSdkVersion", -1);
				targetSdkVersion = extractAttributeIntValue(sdkNode, "android:targetSdkVersion", -1);
			}

			if (library) {
				return AndroidManifest.createLibraryManifest(file.getAbsolutePath(), applicationPackage, minSdkVersion, maxSdkVersion, targetSdkVersion);
			}

			String applicationClassName = null;
			boolean debuggable = false;
			NodeList applicationNodes = documentElement.getElementsByTagName("application");
			if (applicationNodes.getLength() > 0) {
				Element applicationNode = (Element) applicationNodes.item(0);
				Node nameAttribute = applicationNode.getAttributes().getNamedItem("android:name");
				applicationClassName = manifestNameToValidQualifiedName(resolver, classSuffix, applicationPackage, nameAttribute);
				if (applicationClassName == null && nameAttribute != null) {
					logger.warn("The class application declared in the AndroidManifest.xml cannot be found in the compile path: [" + nameAttribute.getNodeValue() + "]", null);
				}
				Node debuggableAttribute = applicationNode.getAttributes().getNamedItem("android:debuggable");
				if (debuggableAttribute != null) {
					debuggable = "true".equalsIgnoreCase(debuggableAttribute.getNodeValue());
				}
			}

			List<String> componentQualifiedNames = new ArrayList<>();
			componentQualifiedNames.addAll(extractComponentNames(resolver, logger, classSuffix, applicationPackage, documentElement.getElementsByTagName("activity"), "activity"));
			componentQualifiedNames.addAll(extractComponentNames(resolver, logger, classSuffix, applicationPackage, documentElement.getElementsByTagName("service"), "service"));
			componentQualifiedNames.addAll(extractComponentNames(resolver, logger, classSuffix, applicationPackage, documentElement.getElementsByTagName("receiver"), "receiver"));
			componentQualifiedNames.addAll(extractComponentNames(resolver, logger, classSuffix, applicationPackage, documentElement.getElementsByTagName("provider"), "provider"));

			Map<String, MetaDataInfo> metaDataQualifiedNames = extractMetaDataQualifiedNames(documentElement.getElementsByTagName("meta-data"), logger);
			List<String> permissionQualifiedNames = extractUsesPermissionNames(documentElement.getElementsByTagName("uses-permission"), logger);

			return AndroidManifest.create(file.getAbsolutePath(), applicationPackage, applicationClassName, componentQualifiedNames, metaDataQualifiedNames, permissionQualifiedNames, minSdkVersion,
					maxSdkVersion, targetSdkVersion, debuggable);
		} catch (Exception e) {
			return null;
		}
	}

	private static int extractAttributeIntValue(Node node, String attribute, int defaultValue) {
		try {
			org.w3c.dom.NamedNodeMap attributes = node.getAttributes();
			if (attributes.getLength() > 0) {
				Node attributeNode = attributes.getNamedItem(attribute);
				if (attributeNode != null) {
					return Integer.parseInt(attributeNode.getNodeValue());
				}
			}
		} catch (NumberFormatException ignored) {
			// we assume the manifest is well-formed
		}
		return defaultValue;
	}

	private static List<String> extractComponentNames(Resolver resolver, KSPLogger logger, String classSuffix, String applicationPackage, NodeList componentNodes, String tagName) {
		List<String> componentQualifiedNames = new ArrayList<>();
		for (int i = 0; i < componentNodes.getLength(); i++) {
			Element componentNode = (Element) componentNodes.item(i);
			Node nameAttribute = componentNode.getAttributes().getNamedItem("android:name");
			String qualifiedName = manifestNameToValidQualifiedName(resolver, classSuffix, applicationPackage, nameAttribute);
			if (qualifiedName != null) {
				componentQualifiedNames.add(qualifiedName);
			} else if (nameAttribute != null) {
				logger.warn("A " + tagName + " declared in the AndroidManifest.xml cannot be found in the compile path: [" + nameAttribute.getNodeValue() + "]", null);
			} else {
				logger.warn("The " + i + " " + tagName + " node in the AndroidManifest.xml has no android:name attribute", null);
			}
		}
		return componentQualifiedNames;
	}

	private static Map<String, MetaDataInfo> extractMetaDataQualifiedNames(NodeList metaDataNodes, KSPLogger logger) {
		Map<String, MetaDataInfo> metaDataQualifiedNames = new HashMap<>();
		for (int i = 0; i < metaDataNodes.getLength(); i++) {
			Element node = (Element) metaDataNodes.item(i);
			Node nameAttribute = node.getAttributes().getNamedItem("android:name");
			Node valueAttribute = node.getAttributes().getNamedItem("android:value");
			Node resourceAttribute = node.getAttributes().getNamedItem("android:resource");
			if (nameAttribute == null || (valueAttribute == null && resourceAttribute == null)) {
				if (nameAttribute != null) {
					logger.warn("A malformed <meta-data> has been found in the manifest with name " + nameAttribute.getNodeValue(), null);
				} else {
					logger.warn("A malformed <meta-data> has been found in the manifest", null);
				}
			} else {
				String name = nameAttribute.getNodeValue();
				metaDataQualifiedNames.put(name, new MetaDataInfo(name, valueAttribute != null ? valueAttribute.getNodeValue() : null,
						resourceAttribute != null ? resourceAttribute.getNodeValue() : null));
			}
		}
		return metaDataQualifiedNames;
	}

	private static List<String> extractUsesPermissionNames(NodeList usesPermissionNodes, KSPLogger logger) {
		List<String> permissionQualifiedNames = new ArrayList<>();
		for (int i = 0; i < usesPermissionNodes.getLength(); i++) {
			Element node = (Element) usesPermissionNodes.item(i);
			Node nameAttribute = node.getAttributes().getNamedItem("android:name");
			if (nameAttribute == null) {
				logger.warn("A <uses-permission> without an android:name attribute has been found in the manifest", null);
				continue;
			}
			permissionQualifiedNames.add(nameAttribute.getNodeValue());
		}
		return permissionQualifiedNames;
	}

	private static String manifestNameToValidQualifiedName(Resolver resolver, String classSuffix, String applicationPackage, Node nameAttribute) {
		if (nameAttribute == null) {
			return null;
		}
		String componentName = nameAttribute.getNodeValue();
		boolean hasPackage = applicationPackage != null && !applicationPackage.isEmpty();
		if (hasPackage && componentName.startsWith(applicationPackage)) {
			return classExistsOrNull(resolver, classSuffix, componentName);
		}
		if (componentName.startsWith(".")) {
			return classExistsOrNull(resolver, classSuffix, applicationPackage + componentName);
		}
		if (classExists(resolver, classSuffix, componentName)) {
			return componentName;
		}
		return classExistsOrNull(resolver, classSuffix, applicationPackage + "." + componentName);
	}

	private static String classExistsOrNull(Resolver resolver, String classSuffix, String className) {
		return classExists(resolver, classSuffix, className) ? className : null;
	}

	/** Mirrors APT {@code classOrModelClassExists}: a manifest entry for the generated class counts if the base class exists. */
	private static boolean classExists(Resolver resolver, String classSuffix, String className) {
		if (className == null || className.isEmpty()) {
			return false;
		}
		String baseName = className;
		if (classSuffix != null && !classSuffix.isEmpty() && baseName.endsWith(classSuffix)) {
			baseName = baseName.substring(0, baseName.length() - classSuffix.length());
		}
		if (baseName.isEmpty()) {
			return false;
		}
		try {
			return resolver.getClassDeclarationByName(resolver.getKSNameFromString(baseName)) != null;
		} catch (Exception e) {
			return false;
		}
	}
}