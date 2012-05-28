package de.robv.android.xposed.mods.tweakbox;

import static de.robv.android.xposed.XposedHelpers.assetAsByteArray;
import static de.robv.android.xposed.XposedHelpers.getMD5Sum;
import static de.robv.android.xposed.XposedHelpers.setFloatField;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Iterator;

import android.app.AndroidAppHelper;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.content.res.XResources;
import android.content.res.XResources.ResourceNames;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.telephony.SignalStrength;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import de.robv.android.xposed.Callback;
import de.robv.android.xposed.XposedBridge;


public class XposedTweakbox {
	private static final String MODULE_PATH = null; // injected by XposedBridge
	public static final String MY_PACKAGE_NAME = "de.robv.android.xposed.mods.tweakbox";
	private static SharedPreferences pref;
	private static int signalStrengthBars = 4;
	
	public static void init(String startClassName) throws Exception {
		if (startClassName != null)
			return;
		
		pref = AndroidAppHelper.getDefaultSharedPreferencesForPackage(MY_PACKAGE_NAME);
		Resources tweakboxRes = XModuleResources.createInstance(MODULE_PATH, null);
		
		try {
			if (pref.getBoolean("crt_off_effect", false)) {
				XResources.setSystemWideReplacement("android", "bool", "config_animateScreenLights", false);
				
				if (pref.getBoolean("crt_off_library_fix", false)) {
					// apply CRT off fix by Tungstwenty
					String libsurfaceflingerMD5 = getMD5Sum(new File("/system/lib/libsurfaceflinger.so"));
					if (libsurfaceflingerMD5.equals("d506192d5049a4042fb84c0265edfe42")) {
						byte[] crtPatch = assetAsByteArray(tweakboxRes, "crtfix_samsung_d506192d5049a4042fb84c0265edfe42.bsdiff");
						if (!XposedBridge.patchNativeLibrary("/system/lib/libsurfaceflinger.so", crtPatch, "/system/bin/surfaceflinger"))
							XposedBridge.log("CRT patch could not be applied");
					} else if (libsurfaceflingerMD5.equals("7ab85f469baa14ed33ae57967cd16729")) {
						XposedBridge.log("CRT patch not necessary, library is already patched");
					} else {
						XposedBridge.log("CRT patch could not be applied because libsurfaceflinger.so has unknown MD5 sum " + libsurfaceflingerMD5);
					}
				}
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
		
		try {			
			Method displayInit = Display.class.getDeclaredMethod("init", int.class);
			XposedBridge.hookMethod(displayInit, XposedTweakbox.class, "handleDisplayInit", Callback.PRIORITY_DEFAULT);
			
			Class<?> classCompatibilityInfo = Class.forName("android.content.res.CompatibilityInfo");
			Method methodUpdateConfiguration = Resources.class.getDeclaredMethod("updateConfiguration",
					Configuration.class, DisplayMetrics.class, classCompatibilityInfo);
			XposedBridge.hookMethod(methodUpdateConfiguration, XposedTweakbox.class, "handleUpdateConfiguration", Callback.PRIORITY_DEFAULT);
		} catch (Exception e) { XposedBridge.log(e); }
		
		XposedBridge.hookLoadPackage(XposedTweakbox.class, "handleLoadPackage", Callback.PRIORITY_DEFAULT);
		XposedBridge.hookInitPackageResources(XposedTweakbox.class, "handleInitPackageResources", Callback.PRIORITY_DEFAULT);
		
		if (pref.getBoolean("volume_keys_skip_track", false))
			VolumeKeysSkipTrack.init(pref.getBoolean("volume_keys_skip_track_screenon", false));
	}
	
	@SuppressWarnings("unused")
	private static void handleLoadPackage(String packageName, ClassLoader classLoader) {
		if (packageName.equals("com.android.systemui")) {
			AndroidAppHelper.reloadSharedPreferencesIfNeeded(pref);
			
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
			AndroidAppHelper.reloadSharedPreferencesIfNeeded(pref);
			
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
			
			if (pref.getBoolean("statusbar_clock_color_enabled", false)) {
				try {
					res.hookLayout("com.android.systemui", "layout", "tw_status_bar", XposedTweakbox.class, "handleStatusbarInflated", Callback.PRIORITY_DEFAULT);
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
		if (result >= 10000)
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
		XposedBridge.log("could not determine signal level (original result was " + result);
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
	private static Object handleDisplayInit(Iterator<Callback> iterator, Method method, Object thisObject, Object[] args) throws Throwable {
		Object result = XposedBridge.callNext(iterator, method, thisObject, args);
		try {
			String packageName = AndroidAppHelper.currentPackageName();
			
			int packageDensity = pref.getInt("dpioverride/" + packageName + "/density", pref.getInt("dpioverride/default/density", 0));
			if (packageDensity > 0)
				setFloatField(thisObject, "mDensity", packageDensity / 160.0f);
			
		} catch (Exception e) {
			XposedBridge.log(e);
		}
		return result;
	}
	
	@SuppressWarnings("unused")
	private static Object handleUpdateConfiguration(Iterator<Callback> iterator, Method method, Object thisObject, Object[] args) throws Throwable {
		try {
			String packageName = AndroidAppHelper.currentPackageName();
			Configuration config = (Configuration) args[0];
			
			int swdp = pref.getInt("dpioverride/" + packageName + "/swdp", pref.getInt("dpioverride/default/swdp", 0));
			if (swdp > 0)
				config.smallestScreenWidthDp = swdp;
			
			int wdp = pref.getInt("dpioverride/" + packageName + "/wdp", pref.getInt("dpioverride/default/wdp", 0));
			if (wdp > 0)
				config.screenWidthDp = wdp;
			
			int hdp = pref.getInt("dpioverride/" + packageName + "/hdp", pref.getInt("dpioverride/default/hdp", 0));
			if (hdp > 0)
				config.screenHeightDp = hdp;
			
		} catch (Exception e) {
			XposedBridge.log(e);
		}
		return XposedBridge.callNext(iterator, method, thisObject, args);
	}
	
	@SuppressWarnings("unused")
	private static void handleStatusbarInflated(View view, ResourceNames resNames, String variant, XResources res) {
		if (pref.getBoolean("statusbar_clock_color_enabled", false)) {
			try {
				TextView clock = (TextView) view.findViewById(res.getIdentifier("clock", "id", "com.android.systemui"));
				clock.setTextColor(pref.getInt("statusbar_clock_color", 0xffbebebe));
			} catch (Exception e) { XposedBridge.log(e); }
		}
	}
}
