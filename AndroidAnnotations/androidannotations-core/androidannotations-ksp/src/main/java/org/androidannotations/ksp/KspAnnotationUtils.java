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
import java.util.List;
import java.util.Locale;

import com.google.devtools.ksp.symbol.KSAnnotation;
import com.google.devtools.ksp.symbol.KSFunctionDeclaration;
import com.google.devtools.ksp.symbol.KSPropertyDeclaration;
import com.google.devtools.ksp.symbol.KSType;
import com.google.devtools.ksp.symbol.KSTypeReference;
import com.google.devtools.ksp.symbol.KSValueArgument;
import com.google.devtools.ksp.symbol.KSValueParameter;

import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;

final class KspAnnotationUtils {

	private KspAnnotationUtils() {
	}

	static KSAnnotation findAnnotation(Sequence<KSAnnotation> annotations, String qualifiedName) {
		for (KSAnnotation annotation : SequencesKt.toList(annotations)) {
			if (qualifiedName.equals(safeQualifiedName(annotation))) {
				return annotation;
			}
		}
		return null;
	}

	static String safeQualifiedName(KSAnnotation annotation) {
		try {
			if (annotation.getAnnotationType().resolve().getDeclaration().getQualifiedName() != null) {
				return annotation.getAnnotationType().resolve().getDeclaration().getQualifiedName().asString();
			}
		} catch (Exception ignored) {
			// fall through
		}
		return annotation.getShortName().asString();
	}

	static Object argValue(KSAnnotation annotation, String name) {
		for (KSValueArgument argument : annotation.getArguments()) {
			if (argument.getName() != null && name.equals(argument.getName().asString())) {
				return argument.getValue();
			}
		}
		return null;
	}

	static int intArg(KSAnnotation annotation, String name, int defaultValue) {
		Object value = argValue(annotation, name);
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		return defaultValue;
	}

	static long longArg(KSAnnotation annotation, String name, long defaultValue) {
		Object value = argValue(annotation, name);
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		return defaultValue;
	}

	static String stringArg(KSAnnotation annotation, String name, String defaultValue) {
		Object value = argValue(annotation, name);
		if (value instanceof String) {
			return (String) value;
		}
		return defaultValue;
	}

	static String typeName(KSTypeReference reference) {
		KSType type = reference.resolve();
		String qualified;
		if (type.getDeclaration().getQualifiedName() != null) {
			qualified = type.getDeclaration().getQualifiedName().asString();
		} else {
			qualified = type.toString();
		}
		return toJavaTypeName(qualified);
	}

	static String toJavaTypeName(String kotlinOrJavaName) {
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
		case "kotlin.Unit":
			return "void";
		case "kotlin.Any":
			return "java.lang.Object";
		default:
			return kotlinOrJavaName;
		}
	}

	static String propertyTypeName(KSPropertyDeclaration property) {
		return typeName(property.getType());
	}

	static boolean hasJvmField(KSPropertyDeclaration property) {
		return findAnnotation(property.getAnnotations(), AnnotationNames.JVM_FIELD) != null;
	}

	static List<MvpModel.Param> methodParams(KSFunctionDeclaration function) {
		List<MvpModel.Param> params = new ArrayList<>();
		for (KSValueParameter parameter : function.getParameters()) {
			params.add(new MvpModel.Param(parameter.getName().asString(), typeName(parameter.getType())));
		}
		return params;
	}

	static String setterName(String propertyName) {
		if (propertyName.length() == 1) {
			return "set" + propertyName.toUpperCase(Locale.US);
		}
		return "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
	}

	static String extraKeyConstant(String fieldName) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < fieldName.length(); i++) {
			char c = fieldName.charAt(i);
			if (Character.isUpperCase(c) && i > 0) {
				builder.append('_');
			}
			builder.append(Character.toUpperCase(c));
		}
		builder.append("_EXTRA");
		return builder.toString();
	}

	static String resExpression(String resourcePackage, String rClassName, String resType, String fieldName, int resId, String resName) {
		if (resId != AnnotationNames.RES_DEFAULT) {
			return Integer.toString(resId);
		}
		String name = (resName != null && !resName.isEmpty()) ? resName : fieldName;
		return resourcePackage + "." + rClassName + "." + resType + "." + name;
	}
}
