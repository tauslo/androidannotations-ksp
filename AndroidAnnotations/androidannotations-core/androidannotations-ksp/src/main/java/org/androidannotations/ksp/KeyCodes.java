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
package org.androidannotations.ksp;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps {@code android.view.KeyEvent.KEYCODE_*} names and values the way APT {@code KeyCodeHelper} does,
 * including the method-name fallback ({@code onBack} → {@code KEYCODE_BACK}).
 */
public final class KeyCodes {

	public static final int NOT_FOUND = -1;

	private static final Pattern METHOD_NAME = Pattern.compile("^(on)?(.+?)(Pressed)?$");

	private static final Map<String, Integer> BY_NAME = new HashMap<String, Integer>();
	private static final Map<Integer, String> BY_CODE = new HashMap<Integer, String>();

	static {
		put("KEYCODE_UNKNOWN", 0);
		put("KEYCODE_SOFT_LEFT", 1);
		put("KEYCODE_SOFT_RIGHT", 2);
		put("KEYCODE_HOME", 3);
		put("KEYCODE_BACK", 4);
		put("KEYCODE_CALL", 5);
		put("KEYCODE_ENDCALL", 6);
		put("KEYCODE_0", 7);
		put("KEYCODE_1", 8);
		put("KEYCODE_2", 9);
		put("KEYCODE_3", 10);
		put("KEYCODE_4", 11);
		put("KEYCODE_5", 12);
		put("KEYCODE_6", 13);
		put("KEYCODE_7", 14);
		put("KEYCODE_8", 15);
		put("KEYCODE_9", 16);
		put("KEYCODE_STAR", 17);
		put("KEYCODE_POUND", 18);
		put("KEYCODE_DPAD_UP", 19);
		put("KEYCODE_DPAD_DOWN", 20);
		put("KEYCODE_DPAD_LEFT", 21);
		put("KEYCODE_DPAD_RIGHT", 22);
		put("KEYCODE_DPAD_CENTER", 23);
		put("KEYCODE_VOLUME_UP", 24);
		put("KEYCODE_VOLUME_DOWN", 25);
		put("KEYCODE_POWER", 26);
		put("KEYCODE_CAMERA", 27);
		put("KEYCODE_CLEAR", 28);
		for (int i = 0; i < 26; i++) {
			put("KEYCODE_" + (char) ('A' + i), 29 + i);
		}
		put("KEYCODE_COMMA", 55);
		put("KEYCODE_PERIOD", 56);
		put("KEYCODE_ALT_LEFT", 57);
		put("KEYCODE_ALT_RIGHT", 58);
		put("KEYCODE_SHIFT_LEFT", 59);
		put("KEYCODE_SHIFT_RIGHT", 60);
		put("KEYCODE_TAB", 61);
		put("KEYCODE_SPACE", 62);
		put("KEYCODE_SYM", 63);
		put("KEYCODE_EXPLORER", 64);
		put("KEYCODE_ENVELOPE", 65);
		put("KEYCODE_ENTER", 66);
		put("KEYCODE_DEL", 67);
		put("KEYCODE_GRAVE", 68);
		put("KEYCODE_MINUS", 69);
		put("KEYCODE_EQUALS", 70);
		put("KEYCODE_LEFT_BRACKET", 71);
		put("KEYCODE_RIGHT_BRACKET", 72);
		put("KEYCODE_BACKSLASH", 73);
		put("KEYCODE_SEMICOLON", 74);
		put("KEYCODE_APOSTROPHE", 75);
		put("KEYCODE_SLASH", 76);
		put("KEYCODE_AT", 77);
		put("KEYCODE_NUM", 78);
		put("KEYCODE_HEADSETHOOK", 79);
		put("KEYCODE_FOCUS", 80);
		put("KEYCODE_PLUS", 81);
		put("KEYCODE_MENU", 82);
		put("KEYCODE_NOTIFICATION", 83);
		put("KEYCODE_SEARCH", 84);
		put("KEYCODE_MEDIA_PLAY_PAUSE", 85);
		put("KEYCODE_MEDIA_STOP", 86);
		put("KEYCODE_MEDIA_NEXT", 87);
		put("KEYCODE_MEDIA_PREVIOUS", 88);
		put("KEYCODE_MEDIA_REWIND", 89);
		put("KEYCODE_MEDIA_FAST_FORWARD", 90);
		put("KEYCODE_MUTE", 91);
		put("KEYCODE_PAGE_UP", 92);
		put("KEYCODE_PAGE_DOWN", 93);
		put("KEYCODE_ESCAPE", 111);
		put("KEYCODE_FORWARD_DEL", 112);
		put("KEYCODE_CTRL_LEFT", 113);
		put("KEYCODE_CTRL_RIGHT", 114);
		put("KEYCODE_CAPS_LOCK", 115);
		put("KEYCODE_SCROLL_LOCK", 116);
		put("KEYCODE_META_LEFT", 117);
		put("KEYCODE_META_RIGHT", 118);
		put("KEYCODE_FUNCTION", 119);
		put("KEYCODE_SYSRQ", 120);
		put("KEYCODE_BREAK", 121);
		put("KEYCODE_MOVE_HOME", 122);
		put("KEYCODE_MOVE_END", 123);
		put("KEYCODE_INSERT", 124);
		put("KEYCODE_FORWARD", 125);
		put("KEYCODE_MEDIA_PLAY", 126);
		put("KEYCODE_MEDIA_PAUSE", 127);
		put("KEYCODE_MEDIA_CLOSE", 128);
		put("KEYCODE_MEDIA_EJECT", 129);
		put("KEYCODE_MEDIA_RECORD", 130);
		for (int i = 1; i <= 12; i++) {
			put("KEYCODE_F" + i, 130 + i);
		}
		put("KEYCODE_NUM_LOCK", 143);
		put("KEYCODE_NUMPAD_0", 144);
		put("KEYCODE_NUMPAD_1", 145);
		put("KEYCODE_NUMPAD_2", 146);
		put("KEYCODE_NUMPAD_3", 147);
		put("KEYCODE_NUMPAD_4", 148);
		put("KEYCODE_NUMPAD_5", 149);
		put("KEYCODE_NUMPAD_6", 150);
		put("KEYCODE_NUMPAD_7", 151);
		put("KEYCODE_NUMPAD_8", 152);
		put("KEYCODE_NUMPAD_9", 153);
		put("KEYCODE_NUMPAD_DIVIDE", 154);
		put("KEYCODE_NUMPAD_MULTIPLY", 155);
		put("KEYCODE_NUMPAD_SUBTRACT", 156);
		put("KEYCODE_NUMPAD_ADD", 157);
		put("KEYCODE_NUMPAD_DOT", 158);
		put("KEYCODE_NUMPAD_COMMA", 159);
		put("KEYCODE_NUMPAD_ENTER", 160);
		put("KEYCODE_NUMPAD_EQUALS", 161);
		put("KEYCODE_VOLUME_MUTE", 164);
		put("KEYCODE_INFO", 165);
		put("KEYCODE_CHANNEL_UP", 166);
		put("KEYCODE_CHANNEL_DOWN", 167);
		put("KEYCODE_ZOOM_IN", 168);
		put("KEYCODE_ZOOM_OUT", 169);
		put("KEYCODE_APP_SWITCH", 187);
		put("KEYCODE_LANGUAGE_SWITCH", 204);
		put("KEYCODE_MANNER_MODE", 205);
		put("KEYCODE_ASSIST", 219);
	}

	private KeyCodes() {
	}

	private static void put(String name, int code) {
		BY_NAME.put(name, code);
		if (!BY_CODE.containsKey(code)) {
			BY_CODE.put(code, name);
		}
	}

	public static int codeForName(String name) {
		Integer code = BY_NAME.get(name);
		return code == null ? NOT_FOUND : code.intValue();
	}

	public static String nameForCode(int code) {
		return BY_CODE.get(code);
	}

	/** APT {@code KeyCodeHelper.extractKeyCode} method-name fallback. Returns {@link #NOT_FOUND} when unresolved. */
	public static int fromMethodName(String methodName) {
		if (methodName == null) {
			return NOT_FOUND;
		}
		Matcher matcher = METHOD_NAME.matcher(methodName);
		if (!matcher.matches()) {
			return NOT_FOUND;
		}
		String extracted = matcher.group(2).toLowerCase(Locale.ROOT);
		String constant = KspAnnotationUtils.upperSnake("KEYCODE", extracted, "");
		return codeForName(constant);
	}

	public static List<Integer> singletonOrEmpty(int code) {
		if (code == NOT_FOUND) {
			return Collections.emptyList();
		}
		return Collections.singletonList(code);
	}
}
