package de.robv.android.xposed.mods.tweakbox;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Iterator;

import android.app.AndroidAppHelper;
import android.content.SharedPreferences;
import android.content.res.XResources;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.telephony.SignalStrength;
import android.view.WindowManager;
import android.widget.TextView;
import de.robv.android.xposed.Callback;
import de.robv.android.xposed.XposedBridge;


public class XposedTweakbox {
	public static final String MY_PACKAGE_NAME = "de.robv.android.xposed.mods.tweakbox";
	private static int signalStrengthBars = 4;
	private static int statusBarClockColor = 0xffbebebe;
	
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
			
			if (pref.getBoolean("statusbar_color_enabled", false)) {
				// http://forum.xda-developers.com/showthread.php?t=1523703
				try {
					Constructor<?> constructLayoutParams = WindowManager.LayoutParams.class.getDeclaredConstructor(int.class, int.class, int.class, int.class, int.class);
					XposedBridge.hookMethod(constructLayoutParams, XposedTweakbox.class, "handleInitLayoutParams", Callback.PRIORITY_HIGHEST);
				} catch (Exception e) {	XposedBridge.log(e); }
			}
			
			if (pref.getBoolean("statusbar_clock_color_enabled", false)) {
				try {
					statusBarClockColor = pref.getInt("statusbar_clock_color", 0xffbebebe);
					Method updateClock =
							Class.forName("com.android.systemui.statusbar.policy.Clock", false, classLoader)
							.getDeclaredMethod("updateClock");
					XposedBridge.hookMethod(updateClock, XposedTweakbox.class, "handleUpdateClock", Callback.PRIORITY_DEFAULT);
				} catch (Exception e) { XposedBridge.log(e); }
			}
			
			if (pref.getInt("num_signal_bars", 4) > 4) {
				try {
					Method methodGetLevel = SignalStrength.class.getDeclaredMethod("getLevel");
					XposedBridge.hookMethod(methodGetLevel, XposedTweakbox.class, "handleSignalStrengthGetLevel", Callback.PRIORITY_DEFAULT);
					
					Method methodGsmGetLevel = SignalStrength.class.getDeclaredMethod("getGsmLevel");
					XposedBridge.hookMethod(methodGsmGetLevel, XposedTweakbox.class, "handleSignalStrengthGetGsmLevel", Callback.PRIORITY_DEFAULT);
				} catch (Exception e) { XposedBridge.log(e); }
			}
		}
	}
	
	@SuppressWarnings("unused")
	private static void handleInitPackageResources(String packageName, XResources res) {
		if (packageName.equals("com.android.systemui")) {
			SharedPreferences pref = AndroidAppHelper.getDefaultSharedPreferencesForPackage(MY_PACKAGE_NAME);
			
			try {
				signalStrengthBars = pref.getInt("num_signal_bars", 4);
				res.setReplacement("com.android.systemui", "integer", "config_maxLevelOfSignalStrengthIndicator",
						signalStrengthBars);
			} catch (Exception e) { XposedBridge.log(e); }
			
			if (pref.getBoolean("statusbar_color_enabled", false)) {
				try {
					int statusbarColor = pref.getInt("statusbar_color", Color.BLACK);
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
	
	// correction for signal strength level
	@SuppressWarnings("unused")
	private static Object handleSignalStrengthGetLevel(Iterator<Callback> iterator, Method method, Object thisObject, Object[] args) throws Throwable {
		Integer result = (Integer) XposedBridge.callNext(iterator, method, thisObject, args);
		// value was overridden by our more specific method already
		if (result > 10000)
			return result - 10000;
		
		// interpolate for other modes
		if (signalStrengthBars == 4 || result == 0) {
			return result;
			
		} else if (signalStrengthBars == 5) {
			if (result == 4) return 5;
			else if (result == 3) return 4;
			else if (result == 2) return 3;
			else if (result == 1) return 2;
			
		} else if (signalStrengthBars == 6) {
			if (result == 4) return 6;
			else if (result == 3) return 4;
			else if (result == 2) return 3;
			else if (result == 1) return 2;
		}
		// shouldn't get here
		return 0;
	}
	
	@SuppressWarnings("unused")
	private static Object handleSignalStrengthGetGsmLevel(Iterator<Callback> iterator, Method method, Object thisObject, Object[] args) throws Throwable {
		int asu = ((SignalStrength) thisObject).getGsmSignalStrength();
		switch (signalStrengthBars) {
			case 6:
		        if (asu <= 1 || asu == 99) return 10000;
		        else if (asu >= 12) return 10006;
		        else if (asu >= 10) return 10005;
		        else if (asu >= 8)  return 10004;
		        else if (asu >= 6)  return 10003;
		        else if (asu >= 4)  return 10002;
		        else return 10001;
		        
			case 5:
		        if (asu <= 1 || asu == 99) return 10000;
		        else if (asu >= 12) return 10005;
		        else if (asu >= 10) return 10004;
		        else if (asu >= 7)  return 10003;
		        else if (asu >= 4)  return 10002;
		        else return 10001;
		
			default:
				// original implementation (well, kind of. should not be needed anyway)
		        if (asu <= 2 || asu == 99) return 10000;
		        else if (asu >= 12) return 10004;
		        else if (asu >= 8)  return 10003;
		        else if (asu >= 5)  return 10002;
		        else return 10001;
		}
	}
	
	@SuppressWarnings("unused")
	private static Object handleUpdateClock(Iterator<Callback> iterator, Method method, Object thisObject, Object[] args) throws Throwable {
		Object result = XposedBridge.callNext(iterator, method, thisObject, args);
		TextView clock = (TextView) thisObject;
		clock.setTextColor(statusBarClockColor);
		return result;
	}
}
