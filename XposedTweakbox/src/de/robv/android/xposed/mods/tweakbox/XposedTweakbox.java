package de.robv.android.xposed.mods.tweakbox;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Iterator;

import android.app.AndroidAppHelper;
import android.content.SharedPreferences;
import android.content.res.XResources;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.view.WindowManager;
import de.robv.android.xposed.Callback;
import de.robv.android.xposed.XposedBridge;


public class XposedTweakbox {
	public static final String MY_PACKAGE_NAME = "de.robv.android.xposed.mods.tweakbox";
	
	public static void init(String startClassName) throws Exception {
		if (startClassName != null)
			return;
		
		// we could save the preferences as static variable, but by fetching it everytime we use it,
		// we have a change to apply settings immediately or when the app is restarted
		SharedPreferences pref = AndroidAppHelper.getDefaultSharedPreferencesForPackage(MY_PACKAGE_NAME);
		
		try {
			if (pref.getBoolean("crt_off_effect", false)) {
				XResources.setSystemWideReplacement("android", "bool", "config_animateScreenLights", false);
			}
		} catch (Exception e) { XposedBridge.log(e); }
		
		try {
			if (!pref.getBoolean("unplug_turns_screen_on", true)) {
				XResources.setSystemWideReplacement("android", "bool", "config_unplugTurnsOnScreen", false);
			}
		} catch (Exception e) { XposedBridge.log(e); }
		
		try {
			XResources.setSystemWideReplacement("android", "integer", "config_longPressOnHomeBehavior", pref.getInt("long_home_press_behaviour", 2));
		} catch (Exception e) { XposedBridge.log(e); }
		
		try {
			XResources.setSystemWideReplacement("android", "integer", "config_criticalBatteryWarningLevel", pref.getInt("low_battery_critical", 5));
			XResources.setSystemWideReplacement("android", "integer", "config_lowBatteryWarningLevel", pref.getInt("low_battery_low", 15));
			XResources.setSystemWideReplacement("android", "integer", "config_lowBatteryCloseWarningLevel", pref.getInt("low_battery_close", 20));
		} catch (Exception e) { XposedBridge.log(e); }
		
		XposedBridge.hookLoadPackage(XposedTweakbox.class, "handleLoadPackage", Callback.PRIORITY_DEFAULT);
		XposedBridge.hookInitPackageResources(XposedTweakbox.class, "handleInitPackageResources", Callback.PRIORITY_DEFAULT);
		
		if (pref.getBoolean("volume_keys_skip_track", false))
			VolumeKeysSkipTrack.init(pref.getBoolean("volume_keys_skip_track_screenon", false));
	
	}
	
	@SuppressWarnings("unused")
	private static void handleLoadPackage(String packageName, ClassLoader classLoader) {
		if (packageName.equals("com.android.systemui")) {
			SharedPreferences pref = AndroidAppHelper.getDefaultSharedPreferencesForPackage(MY_PACKAGE_NAME);
			if (!pref.getBoolean("battery_full_notification", true)) {
				try {
					Class<?> classPowerUI = Class.forName("com.android.systemui.power.PowerUI", false, classLoader);
					Method methodNotifyFullBatteryNotification = classPowerUI.getDeclaredMethod("notifyFullBatteryNotification");
					XposedBridge.hookMethod(methodNotifyFullBatteryNotification, XposedTweakbox.class, "doNothing", Callback.PRIORITY_HIGHEST);
				} catch (NoSuchMethodException ignored) {
				} catch (Exception e) {
					XposedBridge.log(e);
				}
			}
			
			if (pref.getInt("statusbar_color", 0xdeadbeef) != 0xdeadbeef) {
				// http://forum.xda-developers.com/showthread.php?t=1523703
				try {
					Constructor<?> constructLayoutParams = WindowManager.LayoutParams.class.getDeclaredConstructor(int.class, int.class, int.class, int.class, int.class);
					XposedBridge.hookMethod(constructLayoutParams, XposedTweakbox.class, "handleInitLayoutParams", Callback.PRIORITY_HIGHEST);
				} catch (Exception e) {
					XposedBridge.log(e);
				}
			}
		}
	}
	
	@SuppressWarnings("unused")
	private static void handleInitPackageResources(String packageName, XResources res) {
		if (packageName.equals("com.android.systemui")) {
			SharedPreferences pref = AndroidAppHelper.getDefaultSharedPreferencesForPackage(MY_PACKAGE_NAME);
			
			try {
				res.setReplacement("com.android.systemui", "integer", "config_maxLevelOfSignalStrengthIndicator",
						pref.getInt("num_signal_bars", 4));
			} catch (Exception e) { XposedBridge.log(e); }
			
			int statusbarColor = pref.getInt("statusbar_color", 0xdeadbeef);
			if (statusbarColor != 0xdeadbeef) {
				try {
					res.setReplacement("com.android.systemui", "drawable", "status_bar_background", new ColorDrawable(statusbarColor));
				} catch (Exception e) { XposedBridge.log(e); }
			}
		}
	}
	
	@SuppressWarnings("unused")
	private static Object doNothing(Iterator<Callback> iterator, Method method, Object thisObject, Object[] args) throws Throwable {
		return null;
	}
	
	@SuppressWarnings("unused")
	private static void handleInitLayoutParams(Iterator<Callback> iterator, Constructor<?> method, Object thisObject, Object[] args) throws Throwable {
		if ((Integer)args[4] == PixelFormat.RGB_565)
			args[4] = PixelFormat.TRANSLUCENT;
		XposedBridge.callNext(iterator, method, thisObject, args);
	}
}
