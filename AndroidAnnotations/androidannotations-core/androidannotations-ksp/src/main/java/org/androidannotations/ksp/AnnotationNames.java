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

public final class AnnotationNames {

	public static final String E_ACTIVITY = "org.androidannotations.annotations.EActivity";
	public static final String E_BEAN = "org.androidannotations.annotations.EBean";
	public static final String VIEW_BY_ID = "org.androidannotations.annotations.ViewById";
	public static final String STRING_RES = "org.androidannotations.annotations.res.StringRes";
	public static final String EXTRA = "org.androidannotations.annotations.Extra";
	public static final String AFTER_VIEWS = "org.androidannotations.annotations.AfterViews";
	public static final String BACKGROUND = "org.androidannotations.annotations.Background";
	public static final String UI_THREAD = "org.androidannotations.annotations.UiThread";
	public static final String AFTER_EXTRAS = "org.androidannotations.annotations.AfterExtras";
	public static final String BEAN = "org.androidannotations.annotations.Bean";
	public static final String AFTER_INJECT = "org.androidannotations.annotations.AfterInject";
	public static final String PARCEL_ANNOTATION = "org.parceler.Parcel";
	public static final String JVM_FIELD = "kotlin.jvm.JvmField";

	public static final int RES_DEFAULT = -1;

	/** AA annotations recognized by the KSP processor but not implemented yet; their use is reported with a warning. */
	public static final String[] RECOGNIZED_NOT_IMPLEMENTED = {
			"org.androidannotations.annotations.EFragment", // P0b
			"org.androidannotations.annotations.FragmentArg", // P0b
			"org.androidannotations.annotations.Click", // P0b
			"org.androidannotations.annotations.InstanceState", // P0b
			"org.androidannotations.annotations.OptionsMenu", // P0b
			"org.androidannotations.annotations.sharedpreferences.SharedPref" // P1
	};

	private AnnotationNames() {
	}
}
