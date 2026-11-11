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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.devtools.ksp.processing.KSPLogger;
import com.google.devtools.ksp.processing.Resolver;
import com.google.devtools.ksp.symbol.ClassKind;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.KSDeclaration;
import com.google.devtools.ksp.symbol.KSFile;
import com.google.devtools.ksp.symbol.KSPropertyDeclaration;

import kotlin.sequences.SequencesKt;

/**
 * Index of the resource class (R or R2) for one resource package, mirroring the APT
 * {@code ProjectRClassFinder} + {@code RInnerClass} pair.
 * <p>
 * Field names are read from the compiled resource class through KSP ({@code resolver.getClassDeclarationByName});
 * int values are read from AGP's runtime symbol list ({@code build/intermediates/runtime_symbol_list/<variant>/R.txt}),
 * because the KSP public API exposes no constant value for classpath Java fields. The value map is what makes it
 * possible to emit {@code pkg.R.type.name} field refs instead of raw int constants and to validate int-valued
 * annotation parameters (mirroring APT {@code IdValidatorHelper.resIdsExist}).
 */
public final class RClassIndex {

	private static final String SYMBOL_LIST_DIR = "build" + File.separator + "intermediates" + File.separator + "runtime_symbol_list";

	private final String rQualifiedName;
	/** Resource type (id, layout, string, ...) to the set of field names declared in that nested class. */
	private final Map<String, Set<String>> fieldsByName = new HashMap<>();
	/** Resource type to (int value -&gt; field name); empty when no symbol list was found. */
	private final Map<String, Map<Integer, String>> fieldNameByValue = new HashMap<>();

	private RClassIndex(String rQualifiedName) {
		this.rQualifiedName = rQualifiedName;
	}

	/**
	 * Resolves the resource class for {@code resourcePackage.rClassName}. Returns an index with names only when the
	 * class is on the KSP compile path, or an empty (unresolved) index when it cannot be found. Never returns null.
	 */
	public static RClassIndex resolve(Resolver resolver, KSPLogger logger, List<KSFile> sourceFiles, String resourcePackage, String rClassName) {
		String qualifiedName = resourcePackage + "." + rClassName;
		RClassIndex index = new RClassIndex(qualifiedName);
		try {
			KSClassDeclaration declaration = resolver.getClassDeclarationByName(resolver.getKSNameFromString(qualifiedName));
			if (declaration == null) {
				logger.warn("AndroidAnnotations KSP: could not resolve the " + rClassName + " class at " + qualifiedName
						+ "; resource references cannot be validated and raw int values will be emitted.", null);
				return index;
			}
			for (KSDeclaration member : SequencesKt.toList(declaration.getDeclarations())) {
				if (!(member instanceof KSClassDeclaration)) {
					continue;
				}
				KSClassDeclaration inner = (KSClassDeclaration) member;
				if (inner.getClassKind() != ClassKind.CLASS && inner.getClassKind() != ClassKind.INTERFACE) {
					continue;
				}
				Set<String> names = new HashSet<>();
				for (KSDeclaration field : SequencesKt.toList(inner.getDeclarations())) {
					if (field instanceof KSPropertyDeclaration) {
						names.add(((KSPropertyDeclaration) field).getSimpleName().asString());
					}
				}
				if (!names.isEmpty()) {
					index.fieldsByName.put(inner.getSimpleName().asString(), names);
				}
			}
		} catch (Exception e) {
			logger.warn("AndroidAnnotations KSP: failed to read the " + rClassName + " class at " + qualifiedName + ": " + e, null);
		}
		if (!index.fieldsByName.isEmpty()) {
			index.loadValues(logger, sourceFiles);
		}
		return index;
	}

	/** Whether the resource class was found on the compile path (name-based validation is possible). */
	public boolean isResolved() {
		return !fieldsByName.isEmpty();
	}

	/** Whether int values are available (value-based validation and field-ref emission for raw ints are possible). */
	public boolean hasValues() {
		for (Map<Integer, String> byValue : fieldNameByValue.values()) {
			if (!byValue.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	public String getRQualifiedName() {
		return rQualifiedName;
	}

	/** Mirrors APT {@code RInnerClass.containsField}: exact name first, then the snake_case form. */
	public boolean containsField(String resType, String name) {
		return resolveFieldName(resType, name) != null;
	}

	/** Returns the field name actually declared in R.{@code resType} for {@code name}, or null when missing. */
	public String resolveFieldName(String resType, String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		Set<String> names = fieldsByName.get(resType);
		if (names == null) {
			return null;
		}
		if (names.contains(name)) {
			return name;
		}
		String snakeCaseName = camelCaseToSnakeCase(name);
		if (!snakeCaseName.equals(name) && names.contains(snakeCaseName)) {
			return snakeCaseName;
		}
		return null;
	}

	public boolean containsIdValue(String resType, int idValue) {
		Map<Integer, String> byValue = fieldNameByValue.get(resType);
		return byValue != null && byValue.containsKey(idValue);
	}

	/** Reverse lookup of an int constant: the R field name carrying {@code idValue}, or null. */
	public String fieldNameForValue(String resType, int idValue) {
		Map<Integer, String> byValue = fieldNameByValue.get(resType);
		return byValue == null ? null : byValue.get(idValue);
	}

	/**
	 * Loads int values from the newest AGP runtime symbol list ({@code R.txt}) found under
	 * {@code build/intermediates/runtime_symbol_list} of any module rooted at the given source files.
	 */
	private void loadValues(KSPLogger logger, List<KSFile> sourceFiles) {
		File symbolList = findNewestSymbolList(sourceFiles);
		if (symbolList == null) {
			logger.warn("AndroidAnnotations KSP: no AGP runtime symbol list (R.txt) found; int-valued resource references "
					+ "cannot be validated and will be emitted as raw ints.", null);
			return;
		}
		try {
			for (String line : Files.readAllLines(symbolList.toPath(), StandardCharsets.UTF_8)) {
				String[] parts = line.trim().split("\\s+");
				if (parts.length != 4 || !parts[0].equals("int")) {
					continue;
				}
				try {
					int value = Integer.decode(parts[3]);
					fieldNameByValue.computeIfAbsent(parts[1], k -> new HashMap<>()).put(value, parts[2]);
				} catch (NumberFormatException ignored) {
					// skip malformed lines
				}
			}
		} catch (IOException e) {
			logger.warn("AndroidAnnotations KSP: could not read the AGP runtime symbol list " + symbolList + ": " + e, null);
		}
	}

	private static File findNewestSymbolList(List<KSFile> sourceFiles) {
		Set<String> seenDirs = new HashSet<>();
		List<File> candidates = new ArrayList<>();
		for (KSFile sourceFile : sourceFiles) {
			File anchor = moduleAnchor(sourceFile);
			if (anchor == null) {
				continue;
			}
			for (File dir = anchor; dir != null && seenDirs.add(dir.getAbsolutePath()); dir = dir.getParentFile()) {
				File symbolDir = new File(dir, SYMBOL_LIST_DIR);
				if (!symbolDir.isDirectory()) {
					continue;
				}
				collectSymbolLists(symbolDir, candidates);
			}
		}
		File newest = null;
		for (File candidate : candidates) {
			if (newest == null || candidate.lastModified() > newest.lastModified()) {
				newest = candidate;
			}
		}
		return newest;
	}

	private static void collectSymbolLists(File dir, List<File> out) {
		File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (File child : children) {
			if (child.isDirectory()) {
				collectSymbolLists(child, out);
			} else if (child.getName().equals("R.txt")) {
				out.add(child);
			}
		}
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

	/** Mirrors APT {@code CaseHelper.camelCaseToSnakeCase}. */
	static String camelCaseToSnakeCase(String camelCase) {
		List<String> tokens = new ArrayList<>();
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([A-Z]|[a-z])[a-z0-9]*").matcher(camelCase);
		String acronym = "";
		while (matcher.find()) {
			String found = matcher.group();
			if (found.matches("^[A-Z]$")) {
				acronym += found;
			} else {
				if (acronym.length() > 0) {
					tokens.add(acronym);
					acronym = "";
				}
				tokens.add(found.toLowerCase(java.util.Locale.ROOT));
			}
		}
		if (acronym.length() > 0) {
			tokens.add(acronym);
		}
		if (tokens.isEmpty()) {
			return camelCase;
		}
		StringBuilder sb = new StringBuilder(tokens.remove(0));
		for (String token : tokens) {
			sb.append('_').append(token);
		}
		return sb.toString();
	}
}
