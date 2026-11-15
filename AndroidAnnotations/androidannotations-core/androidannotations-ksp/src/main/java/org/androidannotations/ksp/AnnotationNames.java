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

	public static final String E_APPLICATION = "org.androidannotations.annotations.EApplication";
	public static final String E_ACTIVITY = "org.androidannotations.annotations.EActivity";
	public static final String E_BEAN = "org.androidannotations.annotations.EBean";
	public static final String E_FRAGMENT = "org.androidannotations.annotations.EFragment";
	public static final String E_SERVICE = "org.androidannotations.annotations.EService";
	public static final String FRAGMENT_ARG = "org.androidannotations.annotations.FragmentArg";
	public static final String FRAGMENT_BY_ID = "org.androidannotations.annotations.FragmentById";
	public static final String FRAGMENT_BY_TAG = "org.androidannotations.annotations.FragmentByTag";
	public static final String VIEW_BY_ID = "org.androidannotations.annotations.ViewById";
	public static final String CLICK = "org.androidannotations.annotations.Click";
	public static final String LONG_CLICK = "org.androidannotations.annotations.LongClick";
	public static final String TOUCH = "org.androidannotations.annotations.Touch";
	public static final String FOCUS_CHANGE = "org.androidannotations.annotations.FocusChange";
	public static final String CHECKED_CHANGE = "org.androidannotations.annotations.CheckedChange";
	public static final String TEXT_CHANGE = "org.androidannotations.annotations.TextChange";
	public static final String BEFORE_TEXT_CHANGE = "org.androidannotations.annotations.BeforeTextChange";
	public static final String AFTER_TEXT_CHANGE = "org.androidannotations.annotations.AfterTextChange";
	public static final String EDITOR_ACTION = "org.androidannotations.annotations.EditorAction";
	public static final String ITEM_CLICK = "org.androidannotations.annotations.ItemClick";
	public static final String ITEM_LONG_CLICK = "org.androidannotations.annotations.ItemLongClick";
	public static final String ITEM_SELECT = "org.androidannotations.annotations.ItemSelect";
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
	public static final String ON_ACTIVITY_RESULT = "org.androidannotations.annotations.OnActivityResult";
	public static final String ON_ACTIVITY_RESULT_EXTRA = "org.androidannotations.annotations.OnActivityResult.Extra";
	public static final String EXTRA = "org.androidannotations.annotations.Extra";
	public static final String INSTANCE_STATE = "org.androidannotations.annotations.InstanceState";
	public static final String AFTER_VIEWS = "org.androidannotations.annotations.AfterViews";
	public static final String BACKGROUND = "org.androidannotations.annotations.Background";
	public static final String UI_THREAD = "org.androidannotations.annotations.UiThread";
	public static final String AFTER_EXTRAS = "org.androidannotations.annotations.AfterExtras";
	public static final String SYSTEM_SERVICE = "org.androidannotations.annotations.SystemService";
	public static final String APP = "org.androidannotations.annotations.App";
	public static final String BEAN = "org.androidannotations.annotations.Bean";
	public static final String AFTER_INJECT = "org.androidannotations.annotations.AfterInject";
	public static final String PARCEL_ANNOTATION = "org.parceler.Parcel";
	public static final String JVM_FIELD = "kotlin.jvm.JvmField";
	public static final String SHARED_PREF = "org.androidannotations.annotations.sharedpreferences.SharedPref";
	public static final String PREF = "org.androidannotations.annotations.sharedpreferences.Pref";
	public static final String E_VIEW = "org.androidannotations.annotations.EView";
	public static final String E_VIEW_GROUP = "org.androidannotations.annotations.EViewGroup";
	public static final String VIEWS_BY_ID = "org.androidannotations.annotations.ViewsById";
	public static final String FROM_HTML = "org.androidannotations.annotations.FromHtml";
	public static final String RECEIVER = "org.androidannotations.annotations.Receiver";
	public static final String RECEIVER_EXTRA = "org.androidannotations.annotations.Receiver.Extra";
	public static final String IGNORE_WHEN = "org.androidannotations.annotations.IgnoreWhen";
	public static final String NON_CONFIGURATION_INSTANCE = "org.androidannotations.annotations.NonConfigurationInstance";
	public static final String WINDOW_FEATURE = "org.androidannotations.annotations.WindowFeature";
	public static final String FULLSCREEN = "org.androidannotations.annotations.Fullscreen";

	public static final int RES_DEFAULT = -1;

	/**
	 * AA annotations the processor knows about but does not generate yet. Using one produces a KSP warning.
	 * MovieRes (and the other rare res types here) stayed out of P1; dynamic {@code @Receiver} is implemented,
	 * {@code @EReceiver} / {@code @ReceiverAction} are not.
	 */
	public static final String[] RECOGNIZED_NOT_IMPLEMENTED = {
			"org.androidannotations.annotations.DataBound",
			"org.androidannotations.annotations.BindingObject",
			"org.androidannotations.annotations.CustomTitle",
			"org.androidannotations.annotations.EIntentService",
			"org.androidannotations.annotations.EProvider",
			"org.androidannotations.annotations.EReceiver",
			"org.androidannotations.annotations.HierarchyViewerSupport",
			"org.androidannotations.annotations.HttpsClient",
			"org.androidannotations.annotations.KeyDown",
			"org.androidannotations.annotations.KeyLongPress",
			"org.androidannotations.annotations.KeyMultiple",
			"org.androidannotations.annotations.KeyUp",
			"org.androidannotations.annotations.PageScrollStateChanged",
			"org.androidannotations.annotations.PageScrolled",
			"org.androidannotations.annotations.PageSelected",
			"org.androidannotations.annotations.PreferenceByKey",
			"org.androidannotations.annotations.PreferenceChange",
			"org.androidannotations.annotations.PreferenceClick",
			"org.androidannotations.annotations.PreferenceHeaders",
			"org.androidannotations.annotations.PreferenceScreen",
			"org.androidannotations.annotations.AfterPreferences",
			"org.androidannotations.annotations.ReceiverAction",
			"org.androidannotations.annotations.RootContext",
			"org.androidannotations.annotations.RootFragment",
			"org.androidannotations.annotations.SeekBarProgressChange",
			"org.androidannotations.annotations.SeekBarTouchStart",
			"org.androidannotations.annotations.SeekBarTouchStop",
			"org.androidannotations.annotations.ServiceAction",
			"org.androidannotations.annotations.SupposeBackground",
			"org.androidannotations.annotations.SupposeUiThread",
			"org.androidannotations.annotations.Trace",
			"org.androidannotations.annotations.Transactional",
			"org.androidannotations.annotations.WakeLock",
			"org.androidannotations.annotations.res.IntArrayRes",
			"org.androidannotations.annotations.res.MovieRes"
	};

	private AnnotationNames() {
	}
}
