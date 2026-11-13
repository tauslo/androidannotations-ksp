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
	public static final String E_FRAGMENT = "org.androidannotations.annotations.EFragment";
	public static final String FRAGMENT_ARG = "org.androidannotations.annotations.FragmentArg";
	public static final String FRAGMENT_BY_ID = "org.androidannotations.annotations.FragmentById";
	public static final String FRAGMENT_BY_TAG = "org.androidannotations.annotations.FragmentByTag";
	public static final String VIEW_BY_ID = "org.androidannotations.annotations.ViewById";
	public static final String CLICK = "org.androidannotations.annotations.Click";
	public static final String LONG_CLICK = "org.androidannotations.annotations.LongClick";
	public static final String OPTIONS_MENU = "org.androidannotations.annotations.OptionsMenu";
	public static final String OPTIONS_ITEM = "org.androidannotations.annotations.OptionsItem";
	public static final String OPTIONS_MENU_ITEM = "org.androidannotations.annotations.OptionsMenuItem";
	public static final String INJECT_MENU = "org.androidannotations.annotations.InjectMenu";
	public static final String STRING_RES = "org.androidannotations.annotations.res.StringRes";
	public static final String COLOR_RES = "org.androidannotations.annotations.res.ColorRes";
	public static final String DRAWABLE_RES = "org.androidannotations.annotations.res.DrawableRes";
	public static final String COLOR_STATE_LIST_RES = "org.androidannotations.annotations.res.ColorStateListRes";
	public static final String BOOLEAN_RES = "org.androidannotations.annotations.res.BooleanRes";
	public static final String INTEGER_RES = "org.androidannotations.annotations.res.IntegerRes";
	public static final String STRING_ARRAY_RES = "org.androidannotations.annotations.res.StringArrayRes";
	public static final String TEXT_RES = "org.androidannotations.annotations.res.TextRes";
	public static final String TEXT_ARRAY_RES = "org.androidannotations.annotations.res.TextArrayRes";
	public static final String DIMENSION_RES = "org.androidannotations.annotations.res.DimensionRes";
	public static final String DIMENSION_PIXEL_SIZE_RES = "org.androidannotations.annotations.res.DimensionPixelSizeRes";
	public static final String DIMENSION_PIXEL_OFFSET_RES = "org.androidannotations.annotations.res.DimensionPixelOffsetRes";
	public static final String ANIMATION_RES = "org.androidannotations.annotations.res.AnimationRes";
	public static final String HTML_RES = "org.androidannotations.annotations.res.HtmlRes";
	public static final String LAYOUT_RES = "org.androidannotations.annotations.res.LayoutRes";
	public static final String EXTRA = "org.androidannotations.annotations.Extra";
	public static final String INSTANCE_STATE = "org.androidannotations.annotations.InstanceState";
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
			"org.androidannotations.annotations.sharedpreferences.SharedPref" // P1
	};

	private AnnotationNames() {
	}
}
