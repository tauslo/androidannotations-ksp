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
 * Unless required by applicable law or agreed To in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.androidannotations.ksp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.devtools.ksp.symbol.KSAnnotation;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.KSFunctionDeclaration;
import com.google.devtools.ksp.symbol.KSPropertyDeclaration;
import com.google.devtools.ksp.symbol.KSType;
import com.google.devtools.ksp.symbol.KSTypeArgument;
import com.google.devtools.ksp.symbol.KSTypeReference;
import com.google.devtools.ksp.symbol.KSValueArgument;
import com.google.devtools.ksp.symbol.KSValueParameter;

import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;

import org.androidannotations.ksp.env.RClassIndex;

public final class KspAnnotationUtils {

	private KspAnnotationUtils() {
	}

	public static KSAnnotation findAnnotation(Sequence<KSAnnotation> annotations, String qualifiedName) {
		for (KSAnnotation annotation : SequencesKt.toList(annotations)) {
			if (qualifiedName.equals(safeQualifiedName(annotation))) {
				return annotation;
			}
		}
		return null;
	}

	public static String safeQualifiedName(KSAnnotation annotation) {
		try {
			if (annotation.getAnnotationType().resolve().getDeclaration().getQualifiedName() != null) {
				return annotation.getAnnotationType().resolve().getDeclaration().getQualifiedName().asString();
			}
		} catch (Exception ignored) {
			// fall through
		}
		return annotation.getShortName().asString();
	}

	public static Object argValue(KSAnnotation annotation, String name) {
		for (KSValueArgument argument : annotation.getArguments()) {
			if (argument.getName() != null && name.equals(argument.getName().asString())) {
				return argument.getValue();
			}
		}
		return null;
	}

	public static int intArg(KSAnnotation annotation, String name, int defaultValue) {
		Object value = argValue(annotation, name);
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		return defaultValue;
	}

	public static long longArg(KSAnnotation annotation, String name, long defaultValue) {
		Object value = argValue(annotation, name);
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		return defaultValue;
	}

	public static String stringArg(KSAnnotation annotation, String name, String defaultValue) {
		Object value = argValue(annotation, name);
		if (value instanceof String) {
			return (String) value;
		}
		return defaultValue;
	}

	public static boolean booleanArg(KSAnnotation annotation, String name, boolean defaultValue) {
		Object value = argValue(annotation, name);
		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		return defaultValue;
	}

	/** Enum-typed annotation argument: KSP exposes it as a KSTypeReference to the enum entry. */
	public static String enumArg(KSAnnotation annotation, String name, String defaultValue) {
		Object value = argValue(annotation, name);
		if (value instanceof KSTypeReference) {
			try {
				KSType type = ((KSTypeReference) value).resolve();
				if (type.getDeclaration() != null && type.getDeclaration().getSimpleName() != null) {
					return type.getDeclaration().getSimpleName().asString();
				}
			} catch (Exception ignored) {
				// fall through to the toString fallback below
			}
		}
		if (value instanceof String) {
			return (String) value;
		}
		if (value != null) {
			String text = value.toString();
			int dot = text.lastIndexOf('.');
			return dot >= 0 ? text.substring(dot + 1) : text;
		}
		return defaultValue;
	}

	public static String typeName(KSTypeReference reference) {
		KSType type = reference.resolve();
		String qualified;
		if (type.getDeclaration().getQualifiedName() != null) {
			qualified = type.getDeclaration().getQualifiedName().asString();
		} else {
			qualified = type.toString();
		}
		return toJavaTypeName(qualified);
	}

	public static String toJavaTypeName(String kotlinOrJavaName) {
		switch (kotlinOrJavaName) {
		case "kotlin.String":
			return "java.lang.String";
		case "kotlin.Int":
			return "int";
		case "kotlin.Long":
			return "long";
		case "kotlin.Boolean":
			return "boolean";
		case "kotlin.Float":
			return "float";
		case "kotlin.Double":
			return "double";
		case "kotlin.Short":
			return "short";
		case "kotlin.Byte":
			return "byte";
		case "kotlin.Char":
			return "char";
		case "kotlin.IntArray":
			return "int[]";
		case "kotlin.LongArray":
			return "long[]";
		case "kotlin.BooleanArray":
			return "boolean[]";
		case "kotlin.FloatArray":
			return "float[]";
		case "kotlin.DoubleArray":
			return "double[]";
		case "kotlin.ShortArray":
			return "short[]";
		case "kotlin.ByteArray":
			return "byte[]";
		case "kotlin.CharArray":
			return "char[]";
		case "kotlin.Unit":
			return "void";
		case "kotlin.Any":
			return "java.lang.Object";
		case "kotlin.collections.ArrayList":
			return "java.util.ArrayList";
		case "kotlin.collections.List":
			return "java.util.List";
		case "kotlin.collections.MutableList":
			return "java.util.List";
		case "kotlin.collections.Map":
			return "java.util.Map";
		case "kotlin.collections.MutableMap":
			return "java.util.Map";
		case "kotlin.collections.Set":
			return "java.util.Set";
		case "kotlin.collections.MutableSet":
			return "java.util.Set";
		default:
			return kotlinOrJavaName;
		}
	}

	public static String propertyTypeName(KSPropertyDeclaration property) {
		return typeName(property.getType());
	}

	/** Java canonical name including generic type arguments, e.g. java.util.ArrayList<java.lang.String>. */
	public static String fullTypeNameWithArgs(KSTypeReference reference) {
		KSType type = reference.resolve();
		StringBuilder sb = new StringBuilder(typeName(reference));
		List<KSTypeArgument> args = type.getArguments();
		if (!args.isEmpty()) {
			sb.append('<');
			for (int i = 0; i < args.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				KSTypeReference argType = args.get(i).getType();
				sb.append(argType != null ? fullTypeNameWithArgs(argType) : "java.lang.Object");
			}
			sb.append('>');
		}
		return sb.toString();
	}

	/** Mirrors APT BundleHelper.METHOD_SUFFIX_BY_TYPE_NAME (Java canonical names, incl. arrays and 3 ArrayList variants). */
	public static final Map<String, String> BUNDLE_GET_SUFFIX = new HashMap<>();

	static {
		BUNDLE_GET_SUFFIX.put("android.os.Bundle", "Bundle");
		BUNDLE_GET_SUFFIX.put("boolean", "Boolean");
		BUNDLE_GET_SUFFIX.put("boolean[]", "BooleanArray");
		BUNDLE_GET_SUFFIX.put("byte", "Byte");
		BUNDLE_GET_SUFFIX.put("byte[]", "ByteArray");
		BUNDLE_GET_SUFFIX.put("char", "Char");
		BUNDLE_GET_SUFFIX.put("char[]", "CharArray");
		BUNDLE_GET_SUFFIX.put("java.lang.CharSequence", "CharSequence");
		BUNDLE_GET_SUFFIX.put("java.lang.CharSequence[]", "CharSequenceArray");
		BUNDLE_GET_SUFFIX.put("java.util.ArrayList<java.lang.CharSequence>", "CharSequenceArrayList");
		BUNDLE_GET_SUFFIX.put("double", "Double");
		BUNDLE_GET_SUFFIX.put("double[]", "DoubleArray");
		BUNDLE_GET_SUFFIX.put("float", "Float");
		BUNDLE_GET_SUFFIX.put("float[]", "FloatArray");
		BUNDLE_GET_SUFFIX.put("int", "Int");
		BUNDLE_GET_SUFFIX.put("int[]", "IntArray");
		BUNDLE_GET_SUFFIX.put("java.util.ArrayList<java.lang.Integer>", "IntegerArrayList");
		BUNDLE_GET_SUFFIX.put("long", "Long");
		BUNDLE_GET_SUFFIX.put("long[]", "LongArray");
		BUNDLE_GET_SUFFIX.put("short", "Short");
		BUNDLE_GET_SUFFIX.put("short[]", "ShortArray");
		BUNDLE_GET_SUFFIX.put("java.lang.String", "String");
		BUNDLE_GET_SUFFIX.put("java.lang.String[]", "StringArray");
		BUNDLE_GET_SUFFIX.put("java.util.ArrayList<java.lang.String>", "StringArrayList");
	}

	public static boolean hasParcelerAnnotation(KSClassDeclaration declaration) {
		for (KSAnnotation annotation : SequencesKt.toList(declaration.getAnnotations())) {
			if (AnnotationNames.PARCEL_ANNOTATION.equals(safeQualifiedName(annotation))) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasJvmField(KSPropertyDeclaration property) {
		return findAnnotation(property.getAnnotations(), AnnotationNames.JVM_FIELD) != null;
	}

	public static List<MvpModel.Param> methodParams(KSFunctionDeclaration function) {
		List<MvpModel.Param> params = new ArrayList<>();
		for (KSValueParameter parameter : function.getParameters()) {
			params.add(new MvpModel.Param(parameter.getName().asString(), typeName(parameter.getType())));
		}
		return params;
	}

	public static String setterName(String propertyName) {
		if (propertyName.length() == 1) {
			return "set" + propertyName.toUpperCase(Locale.US);
		}
		return "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
	}

	public static String extraKeyConstant(String fieldName) {
		// Mirrors APT CaseHelper.camelCaseToUpperSnakeCase(null, fieldName, "Extra"): the "_Extra" suffix is only
		// appended when the field name does not already end with "extra" (case-insensitive), then snake-cased.
		String name = fieldName;
		if (!name.toLowerCase(Locale.ROOT).endsWith("extra")) {
			name = name + "_Extra";
		}
		return camelCaseToSnake(name).toUpperCase(Locale.ROOT);
	}

	public static String fragmentArgKeyConstant(String fieldName) {
		// Mirrors APT CaseHelper.camelCaseToUpperSnakeCase(null, fieldName, "Arg"): the "_Arg" suffix is only
		// appended when the field name does not already end with "arg" (case-insensitive), then snake-cased.
		String name = fieldName;
		if (!name.toLowerCase(Locale.ROOT).endsWith("arg")) {
			name = name + "_Arg";
		}
		return camelCaseToSnake(name).toUpperCase(Locale.ROOT);
	}

	/** Mirrors APT CaseHelper.camelCaseToSnakeCase: ([A-Z]|[a-z])[a-z0-9]* tokens, single-uppercase runs as acronyms. */
	private static String camelCaseToSnake(String input) {
		List<String> tokens = new ArrayList<>();
		Matcher matcher = Pattern.compile("([A-Z]|[a-z])[a-z0-9]*").matcher(input);
		String acronym = "";
		while (matcher.find()) {
			String found = matcher.group();
			if (found.length() == 1 && Character.isUpperCase(found.charAt(0))) {
				acronym += found;
			} else {
				if (!acronym.isEmpty()) {
					tokens.add(acronym);
					acronym = "";
				}
				tokens.add(found.toLowerCase(Locale.ROOT));
			}
		}
		if (!acronym.isEmpty()) {
			tokens.add(acronym);
		}
		if (tokens.isEmpty()) {
			return input;
		}
		StringBuilder sb = new StringBuilder(tokens.remove(0));
		for (String token : tokens) {
			sb.append('_').append(token);
		}
		return sb.toString();
	}

	public static String resExpression(String resourcePackage, String rClassName, String resType, String fieldName, int resId, String resName, RClassIndex index) {
		// APT parity: emitted refs always point at R (extractIdStaticRef strips the trailing "2" of R2).
		String emitR = rClassName.endsWith("2") ? rClassName.substring(0, rClassName.length() - 1) : rClassName;
		if (resId != AnnotationNames.RES_DEFAULT) {
			if (index != null) {
				String nameForValue = index.fieldNameForValue(resType, resId);
				if (nameForValue != null) {
					return resourcePackage + "." + emitR + "." + resType + "." + nameForValue;
				}
			}
			return Integer.toString(resId);
		}
		String name = (resName != null && !resName.isEmpty()) ? resName : fieldName;
		if (index != null) {
			String resolvedName = index.resolveFieldName(resType, name);
			if (resolvedName != null) {
				return resourcePackage + "." + emitR + "." + resType + "." + resolvedName;
			}
		}
		return resourcePackage + "." + emitR + "." + resType + "." + name;
	}
}
