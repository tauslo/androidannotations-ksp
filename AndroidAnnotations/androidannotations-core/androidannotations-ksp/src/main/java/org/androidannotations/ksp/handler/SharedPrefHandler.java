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
package org.androidannotations.ksp.handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.devtools.ksp.symbol.ClassKind;
import com.google.devtools.ksp.symbol.KSAnnotation;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.KSDeclaration;
import com.google.devtools.ksp.symbol.KSFunctionDeclaration;
import com.google.devtools.ksp.symbol.KSTypeReference;
import com.google.devtools.ksp.symbol.Origin;

import kotlin.sequences.SequencesKt;

import org.androidannotations.ksp.AnnotationNames;
import org.androidannotations.ksp.KspAnnotationUtils;
import org.androidannotations.ksp.SharedPrefModel;
import org.androidannotations.ksp.env.RClassIndex;

/**
 * Collects a {@code @SharedPref} interface into a {@link SharedPrefModel}, mirroring APT
 * {@code SharedPrefHandler} (scope, defaults, {@code keyRes}, {@code @DefaultRes}).
 */
public final class SharedPrefHandler {

	private static final String DEFAULT_BOOLEAN = "org.androidannotations.annotations.sharedpreferences.DefaultBoolean";
	private static final String DEFAULT_FLOAT = "org.androidannotations.annotations.sharedpreferences.DefaultFloat";
	private static final String DEFAULT_INT = "org.androidannotations.annotations.sharedpreferences.DefaultInt";
	private static final String DEFAULT_LONG = "org.androidannotations.annotations.sharedpreferences.DefaultLong";
	private static final String DEFAULT_STRING = "org.androidannotations.annotations.sharedpreferences.DefaultString";
	private static final String DEFAULT_STRING_SET = "org.androidannotations.annotations.sharedpreferences.DefaultStringSet";
	private static final String DEFAULT_RES = "org.androidannotations.annotations.sharedpreferences.DefaultRes";

	private static final List<String> INVALID_PREF_METHOD_NAMES = Arrays.asList("edit", "getSharedPreferences", "clear", "getEditor", "apply");

	private static final Map<String, TypeInfo> TYPES = new HashMap<String, TypeInfo>();

	static {
		TYPES.put("boolean", new TypeInfo("boolean", "BooleanPrefField", "BooleanPrefEditorField", "booleanField", DEFAULT_BOOLEAN, "bool", "false", "false", "boolean"));
		TYPES.put("float", new TypeInfo("float", "FloatPrefField", "FloatPrefEditorField", "floatField", DEFAULT_FLOAT, "integer", "0.0F", "0.0", "float"));
		TYPES.put("int", new TypeInfo("int", "IntPrefField", "IntPrefEditorField", "intField", DEFAULT_INT, "integer", "0", "0", "int"));
		TYPES.put("long", new TypeInfo("long", "LongPrefField", "LongPrefEditorField", "longField", DEFAULT_LONG, "integer", "0L", "0", "long"));
		TYPES.put("java.lang.String", new TypeInfo("string", "StringPrefField", "StringPrefEditorField", "stringField", DEFAULT_STRING, "string", "\"\"", "", "String"));
		TYPES.put("java.util.Set<java.lang.String>", new TypeInfo("stringSet", "StringSetPrefField", "StringSetPrefEditorField", "stringSetField", DEFAULT_STRING_SET, "array",
				"new java.util.HashSet<String>(0)", null, "Set<String>"));
	}

	public SharedPrefModel build(KSClassDeclaration declaration, String classSuffix, String resourcePackage, String rClassName, RClassIndex index) {
		String packageName = declaration.getPackageName() != null ? declaration.getPackageName().asString() : "";
		String simpleName = declaration.getSimpleName().asString();
		String enclosingQualifiedName = null;
		KSDeclaration parent = declaration.getParentDeclaration();
		if (parent instanceof KSClassDeclaration && parent.getQualifiedName() != null) {
			enclosingQualifiedName = parent.getQualifiedName().asString();
		}

		List<String> errors = new ArrayList<String>();
		KSAnnotation sharedPref = KspAnnotationUtils.findAnnotation(declaration.getAnnotations(), AnnotationNames.SHARED_PREF);
		String scopeName = sharedPref == null ? "ACTIVITY" : KspAnnotationUtils.enumArg(sharedPref, "value", "ACTIVITY");
		SharedPrefModel.Scope scope;
		try {
			scope = SharedPrefModel.Scope.valueOf(scopeName);
		} catch (IllegalArgumentException ex) {
			scope = SharedPrefModel.Scope.ACTIVITY;
			errors.add("Unknown @SharedPref scope: " + scopeName);
		}
		String rawName = sharedPref == null ? "" : KspAnnotationUtils.stringArg(sharedPref, "name", "");
		int mode = sharedPref == null ? 0 : KspAnnotationUtils.intArg(sharedPref, "mode", 0);
		String effectiveName = rawName.trim().isEmpty() ? simpleName : rawName.trim();
		if (!rawName.trim().isEmpty() && scope != SharedPrefModel.Scope.ACTIVITY && scope != SharedPrefModel.Scope.UNIQUE) {
			errors.add("SharedPref#name() is only supported for Scope.ACTIVITY and Scope.UNIQUE.");
		}
		if (declaration.getClassKind() != ClassKind.INTERFACE) {
			errors.add("@SharedPref can only be used on an interface");
		}

		List<SharedPrefModel.PrefMethod> methods = new ArrayList<SharedPrefModel.PrefMethod>();
		if (declaration.getClassKind() == ClassKind.INTERFACE) {
			rejectNonMethods(declaration, errors);
			List<KSFunctionDeclaration> functions = new ArrayList<KSFunctionDeclaration>();
			collectFunctions(declaration, functions, new HashSet<String>());
			for (KSFunctionDeclaration function : functions) {
				SharedPrefModel.PrefMethod method = buildMethod(function, resourcePackage, rClassName, index, errors);
				if (method != null) {
					methods.add(method);
				}
			}
		}

		boolean hasOuterContext = false;
		boolean hasEditorContext = false;
		for (SharedPrefModel.PrefMethod method : methods) {
			if (method.hasDefaultRes) {
				hasOuterContext = true;
			}
			if (method.keyResId != AnnotationNames.RES_DEFAULT) {
				hasOuterContext = true;
				hasEditorContext = true;
			}
		}

		SharedPrefModel model = new SharedPrefModel(packageName, simpleName, classSuffix, enclosingQualifiedName, scope, effectiveName, mode, hasOuterContext, hasEditorContext);
		model.methods.addAll(methods);
		model.errors.addAll(errors);
		return model;
	}

	private static void rejectNonMethods(KSClassDeclaration declaration, List<String> errors) {
		for (KSDeclaration member : SequencesKt.toList(declaration.getDeclarations())) {
			if (member instanceof KSFunctionDeclaration || member instanceof KSClassDeclaration) {
				continue;
			}
			errors.add("Only methods are allowed in an @SharedPref annotated interface");
			return;
		}
	}

	private static void collectFunctions(KSClassDeclaration declaration, List<KSFunctionDeclaration> out, Set<String> seen) {
		for (KSDeclaration member : SequencesKt.toList(declaration.getDeclarations())) {
			if (!(member instanceof KSFunctionDeclaration)) {
				continue;
			}
			KSFunctionDeclaration function = (KSFunctionDeclaration) member;
			if (function.getOrigin() == Origin.SYNTHETIC) {
				continue;
			}
			String name = function.getSimpleName().asString();
			if ("<init>".equals(name) || !seen.add(name)) {
				continue;
			}
			out.add(function);
		}
		for (KSTypeReference superType : SequencesKt.toList(declaration.getSuperTypes())) {
			KSDeclaration parentDecl;
			try {
				parentDecl = superType.resolve().getDeclaration();
			} catch (RuntimeException ex) {
				continue;
			}
			if (!(parentDecl instanceof KSClassDeclaration)) {
				continue;
			}
			KSClassDeclaration parent = (KSClassDeclaration) parentDecl;
			if (parent.getClassKind() != ClassKind.INTERFACE) {
				continue;
			}
			String qualifiedName = parent.getQualifiedName() != null ? parent.getQualifiedName().asString() : "";
			if ("java.lang.Object".equals(qualifiedName) || "kotlin.Any".equals(qualifiedName)) {
				continue;
			}
			collectFunctions(parent, out, seen);
		}
	}

	private SharedPrefModel.PrefMethod buildMethod(KSFunctionDeclaration function, String resourcePackage, String rClassName, RClassIndex index, List<String> errors) {
		String name = function.getSimpleName().asString();
		if (!function.getParameters().isEmpty()) {
			errors.add("Method " + name + " should have no parameters in an @SharedPref annotated interface");
			return null;
		}
		String returnType;
		try {
			returnType = function.getReturnType() == null ? "void" : KspAnnotationUtils.fullJavaTypeName(function.getReturnType());
		} catch (RuntimeException ex) {
			errors.add("Method " + name + " should only return preference simple types in an @SharedPref annotated interface");
			return null;
		}
		TypeInfo info = TYPES.get(returnType);
		if (info == null) {
			errors.add("Method " + name + " should only return preference simple types in an @SharedPref annotated interface");
			return null;
		}
		if (INVALID_PREF_METHOD_NAMES.contains(name)) {
			errors.add("The method name " + name + " is forbidden in an @SharedPref annotated interface");
			return null;
		}
		checkDefaultAnnotations(function, info, errors);

		KSAnnotation typeAnnotation = KspAnnotationUtils.findAnnotation(function.getAnnotations(), info.defaultAnnotation);
		KSAnnotation resAnnotation = KspAnnotationUtils.findAnnotation(function.getAnnotations(), DEFAULT_RES);
		KSAnnotation setAnnotation = KspAnnotationUtils.findAnnotation(function.getAnnotations(), DEFAULT_STRING_SET);

		String defaultExpr;
		String defaultValueStr;
		boolean hasDefaultRes = false;
		int defaultResId = AnnotationNames.RES_DEFAULT;
		String defaultResName = null;
		String defaultResType = null;
		KSAnnotation keyAnnotation = null;

		if (typeAnnotation != null && !"stringSet".equals(info.typeKey)) {
			Object value = KspAnnotationUtils.argValue(typeAnnotation, "value");
			defaultExpr = literalExpr(info.typeKey, value);
			defaultValueStr = literalStr(info.typeKey, value);
			keyAnnotation = typeAnnotation;
		} else if (resAnnotation != null) {
			hasDefaultRes = true;
			defaultResType = info.resType;
			defaultResId = KspAnnotationUtils.intArg(resAnnotation, "value", AnnotationNames.RES_DEFAULT);
			defaultResName = KspAnnotationUtils.stringArg(resAnnotation, "resName", "");
			defaultExpr = resourceDefaultExpr(name, info, defaultResId, defaultResName, resourcePackage, rClassName, index, errors);
			defaultValueStr = info.builtinDefaultStr;
			keyAnnotation = resAnnotation;
			validateDefaultRes(name, info.resType, defaultResId, defaultResName, index, errors);
		} else if (setAnnotation != null && "stringSet".equals(info.typeKey)) {
			List<String> values = KspAnnotationUtils.stringArrayArg(setAnnotation, "value");
			defaultExpr = stringSetExpr(values);
			defaultValueStr = stringSetStr(values);
			keyAnnotation = setAnnotation;
		} else {
			defaultExpr = info.builtinExpr;
			defaultValueStr = info.builtinDefaultStr;
		}

		int keyResId = AnnotationNames.RES_DEFAULT;
		if (keyAnnotation != null) {
			keyResId = KspAnnotationUtils.intArg(keyAnnotation, "keyRes", AnnotationNames.RES_DEFAULT);
			validateKeyRes(name, keyResId, index, errors);
		}

		return new SharedPrefModel.PrefMethod(name, info.typeKey, info.fieldClassSimple, info.editorFieldClassSimple, info.helperMethodName, keyResId, hasDefaultRes,
				defaultResType, defaultResId, defaultResName, defaultExpr, defaultValueStr, normalizeDoc(function.getDocString()));
	}

	private static void checkDefaultAnnotations(KSFunctionDeclaration function, TypeInfo info, List<String> errors) {
		String[] typed = { DEFAULT_BOOLEAN, DEFAULT_FLOAT, DEFAULT_INT, DEFAULT_LONG, DEFAULT_STRING, DEFAULT_STRING_SET };
		for (String annotationName : typed) {
			if (KspAnnotationUtils.findAnnotation(function.getAnnotations(), annotationName) == null) {
				continue;
			}
			if (!annotationName.equals(info.defaultAnnotation)) {
				String simple = annotationName.substring(annotationName.lastIndexOf('.') + 1);
				errors.add(simple + " can only be used on a method that returns a " + info.expectedReturnLabel);
			}
		}
	}

	private static void validateDefaultRes(String methodName, String resType, int resId, String resName, RClassIndex index, List<String> errors) {
		if (index == null || !index.isResolved()) {
			return;
		}
		if (resId != AnnotationNames.RES_DEFAULT) {
			if (index.hasValues() && !index.containsIdValue(resType, resId)) {
				errors.add("Resource id value not found in R." + resType + ": " + resId);
			}
		} else {
			String name = resName != null && !resName.isEmpty() ? resName : methodName;
			if (!index.containsField(resType, name)) {
				errors.add("Resource name not found in R." + resType + ": " + name);
			}
		}
	}

	private static void validateKeyRes(String methodName, int keyResId, RClassIndex index, List<String> errors) {
		if (keyResId == AnnotationNames.RES_DEFAULT || index == null || !index.isResolved() || !index.hasValues()) {
			return;
		}
		if (!index.containsIdValue("string", keyResId)) {
			errors.add("Resource id value not found in R.string for keyRes of " + methodName + ": " + keyResId);
		}
	}

	private static String resourceDefaultExpr(String methodName, TypeInfo info, int resId, String resName, String resourcePackage, String rClassName, RClassIndex index,
			List<String> errors) {
		if (resourcePackage == null || resourcePackage.isEmpty()) {
			errors.add("Could not determine the resource package name for @DefaultRes on " + methodName);
			return info.builtinExpr;
		}
		String idExpr = KspAnnotationUtils.resExpression(resourcePackage, rClassName == null ? "R" : rClassName, info.resType, methodName, resId, resName, index);
		String getter;
		if ("bool".equals(info.resType)) {
			getter = "getBoolean";
		} else if ("string".equals(info.resType)) {
			getter = "getString";
		} else if ("array".equals(info.resType)) {
			getter = "getStringArray";
		} else {
			getter = "getInteger";
		}
		String call = "context_.getResources()." + getter + "(" + idExpr + ")";
		if ("array".equals(info.resType)) {
			return "new java.util.HashSet<String>(java.util.Arrays.asList(" + call + "))";
		}
		return call;
	}

	private static String literalExpr(String typeKey, Object value) {
		if ("boolean".equals(typeKey)) {
			return String.valueOf(booleanValue(value));
		}
		if ("float".equals(typeKey)) {
			return floatLiteral(floatValue(value));
		}
		if ("int".equals(typeKey)) {
			return Integer.toString(intValue(value));
		}
		if ("long".equals(typeKey)) {
			return longValue(value) + "L";
		}
		return javaString(value instanceof String ? (String) value : "");
	}

	private static String literalStr(String typeKey, Object value) {
		if ("boolean".equals(typeKey)) {
			return String.valueOf(booleanValue(value));
		}
		if ("float".equals(typeKey)) {
			return Float.toString(floatValue(value));
		}
		if ("int".equals(typeKey)) {
			return Integer.toString(intValue(value));
		}
		if ("long".equals(typeKey)) {
			return Long.toString(longValue(value));
		}
		return value instanceof String ? (String) value : "";
	}

	private static boolean booleanValue(Object value) {
		if (value instanceof Boolean) {
			return ((Boolean) value).booleanValue();
		}
		return false;
	}

	private static float floatValue(Object value) {
		if (value instanceof Number) {
			return ((Number) value).floatValue();
		}
		return 0f;
	}

	private static int intValue(Object value) {
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		return 0;
	}

	private static long longValue(Object value) {
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		return 0L;
	}

	private static String floatLiteral(float value) {
		if (Float.isNaN(value)) {
			return "Float.NaN";
		}
		if (value == Float.POSITIVE_INFINITY) {
			return "Float.POSITIVE_INFINITY";
		}
		if (value == Float.NEGATIVE_INFINITY) {
			return "Float.NEGATIVE_INFINITY";
		}
		return Float.toString(value) + "F";
	}

	private static String stringSetExpr(List<String> values) {
		if (values.isEmpty()) {
			return "new java.util.HashSet<String>(0)";
		}
		StringBuilder sb = new StringBuilder("new java.util.HashSet<String>(java.util.Arrays.asList(");
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(javaString(values.get(i)));
		}
		sb.append("))");
		return sb.toString();
	}

	private static String stringSetStr(List<String> values) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(values.get(i));
		}
		sb.append(']');
		return sb.toString();
	}

	public static String javaString(String value) {
		StringBuilder sb = new StringBuilder(value.length() + 2);
		sb.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
			case '\\':
				sb.append("\\\\");
				break;
			case '"':
				sb.append("\\\"");
				break;
			case '\n':
				sb.append("\\n");
				break;
			case '\r':
				sb.append("\\r");
				break;
			case '\t':
				sb.append("\\t");
				break;
			default:
				if (c < 0x20) {
					sb.append(String.format("\\u%04x", Integer.valueOf(c)));
				} else {
					sb.append(c);
				}
				break;
			}
		}
		sb.append('"');
		return sb.toString();
	}

	private static String normalizeDoc(String doc) {
		if (doc == null) {
			return null;
		}
		String cleaned = doc.replace("\r\n", "\n").replace('\r', '\n').trim();
		if (cleaned.isEmpty()) {
			return null;
		}
		String[] lines = cleaned.split("\n", -1);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();
			if (line.startsWith("*")) {
				line = line.substring(1);
				if (line.startsWith(" ")) {
					line = line.substring(1);
				}
			}
			if (i > 0) {
				sb.append('\n');
			}
			sb.append(line);
		}
		String result = sb.toString().trim();
		return result.isEmpty() ? null : result;
	}

	private static final class TypeInfo {
		final String typeKey;
		final String fieldClassSimple;
		final String editorFieldClassSimple;
		final String helperMethodName;
		final String defaultAnnotation;
		final String resType;
		final String builtinExpr;
		final String builtinDefaultStr;
		final String expectedReturnLabel;

		TypeInfo(String typeKey, String fieldClassSimple, String editorFieldClassSimple, String helperMethodName, String defaultAnnotation, String resType, String builtinExpr,
				String builtinDefaultStr, String expectedReturnLabel) {
			this.typeKey = typeKey;
			this.fieldClassSimple = fieldClassSimple;
			this.editorFieldClassSimple = editorFieldClassSimple;
			this.helperMethodName = helperMethodName;
			this.defaultAnnotation = defaultAnnotation;
			this.resType = resType;
			this.builtinExpr = builtinExpr;
			this.builtinDefaultStr = builtinDefaultStr;
			this.expectedReturnLabel = expectedReturnLabel;
		}
	}
}

