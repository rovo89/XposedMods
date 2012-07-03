package de.robv.android.xposed.mods.tweakbox;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import java.lang.reflect.Constructor;
import java.util.Locale;

import android.app.AndroidAppHelper;
import android.content.SharedPreferences;
import android.content.res.XResources;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.preference.Preference;
import android.telephony.SignalStrength;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LayoutInflated;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import de.robv.android.xposed.callbacks.XCallback;


public class XposedTweakbox implements IXposedHookZygoteInit, IXposedHookInitPackageResources, IXposedHookLoadPackage {
	public static final String TAG = "Tweakbox";
	public static final String MY_PACKAGE_NAME = XposedTweakbox.class.getPackage().getName();
	private static SharedPreferences pref;
	private static int signalStrengthBars = 4;

	@Override
	public void initZygote(StartupParam startupParam) {
		pref = AndroidAppHelper.getDefaultSharedPreferencesForPackage(MY_PACKAGE_NAME);
		
		// this is not really necessary if no effects are wanted, but it speeds up turning off the screen
		XResources.setSystemWideReplacement("android", "bool", "config_animateScreenLights", false);

		try {
			XResources.setSystemWideReplacement("android", "bool", "config_unplugTurnsOnScreen",
					pref.getBoolean("unplug_turns_screen_on", true));
		} catch (Throwable t) { XposedBridge.log(t); }

		try {
			XResources.setSystemWideReplacement("android", "bool", "show_ongoing_ime_switcher",
					pref.getBoolean("show_ongoing_ime_switcher", true));
		} catch (Throwable t) { XposedBridge.log(t); }
		
		try {
			XResources.setSystemWideReplacement("android", "integer", "config_longPressOnHomeBehavior", pref.getInt("long_home_press_behaviour", 2));
		} catch (Throwable t) { XposedBridge.log(t); }

		try {
			XResources.setSystemWideReplacement("android", "integer", "config_criticalBatteryWarningLevel", pref.getInt("low_battery_critical", 5));
			XResources.setSystemWideReplacement("android", "integer", "config_lowBatteryWarningLevel", pref.getInt("low_battery_low", 15));
			XResources.setSystemWideReplacement("android", "integer", "config_lowBatteryCloseWarningLevel", pref.getInt("low_battery_close", 20));
		} catch (Throwable t) { XposedBridge.log(t); }

		if (pref.getBoolean("volume_keys_skip_track", false))
			VolumeKeysSkipTrack.init(pref.getBoolean("volume_keys_skip_track_screenon", false));
		
		AppSpecificConfiguration.initZygote(pref);
		PhoneTweaks.initZygote(pref);
	}


	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		AndroidAppHelper.reloadSharedPreferencesIfNeeded(pref);

		Locale packageLocale = AppSpecificConfiguration.getPackageSpecificLocale(lpparam.packageName);
		if (packageLocale != null)
			Locale.setDefault(packageLocale);

		if (lpparam.packageName.equals("com.android.systemui")) {
			if (!pref.getBoolean("battery_full_notification", true)) {
				try {
					findAndHookMethod("com.android.systemui.power.PowerUI", lpparam.classLoader, "notifyFullBatteryNotification",
							XC_MethodReplacement.DO_NOTHING);
				} catch (Throwable t) { XposedBridge.log(t); }
			}

			if (pref.getBoolean("statusbar_color_enabled", false)) {
				// http://forum.xda-developers.com/showthread.php?t=1523703
				try {
					Constructor<?> constructLayoutParams = WindowManager.LayoutParams.class.getDeclaredConstructor(
							int.class, int.class, int.class, int.class, int.class);
					XposedBridge.hookMethod(constructLayoutParams, new XC_MethodHook(XCallback.PRIORITY_HIGHEST) {
						@Override
						protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
							if ((Integer)param.args[4] == PixelFormat.RGB_565)
								param.args[4] = PixelFormat.TRANSLUCENT;
						}
					});
				} catch (Throwable t) { XposedBridge.log(t); }
			}

			if (pref.getInt("num_signal_bars", 4) > 4) {
				hookSignalLevelFixes();
			}
			
			
		} else if (lpparam.packageName.equals("com.android.vending")) {
			if (pref.getBoolean("vending_fake_240dpi", false)) {
				try {
					findAndHookMethod("com.google.android.vending.remoting.protos.DeviceConfigurationProto", lpparam.classLoader,
							"getScreenDensity", new XC_MethodReplacement() {
						@Override
						protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
							return 240;
						}
					});
				} catch (Throwable t) { XposedBridge.log(t); }
			}
			
		} else if (lpparam.packageName.equals("android")) {
			CrtEffect.loadPackage(pref, lpparam.classLoader);
			
		} else if (lpparam.packageName.equals("com.android.phone")) {
			PhoneTweaks.loadPackage(pref, lpparam.classLoader);
			
		} else if (lpparam.packageName.equals("de.robv.android.xposed.mods.tweakbox")) {
			try {
				// remove restriction to 4 summary lines for the Tweakbox settings app
				findAndHookMethod(Preference.class, "getView", View.class, ViewGroup.class, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						View summary = ((View) param.getResult()).findViewById(android.R.id.summary);
						if (summary instanceof TextView)
							((TextView) summary).setMaxLines(Integer.MAX_VALUE);
					}
				});
			} catch (Throwable t) { XposedBridge.log(t); }
		}
	}

	@Override
	public void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable {
		AndroidAppHelper.reloadSharedPreferencesIfNeeded(pref);
		
		if (resparam.packageName.equals("com.android.systemui")) {
			try {
				signalStrengthBars = pref.getInt("num_signal_bars", 4);
				resparam.res.setReplacement("com.android.systemui", "integer", "config_maxLevelOfSignalStrengthIndicator", signalStrengthBars);
			} catch (Throwable t) { XposedBridge.log(t); }

			if (pref.getBoolean("statusbar_color_enabled", false)) {
				try {
					int statusbarColor = pref.getInt("statusbar_color", Color.BLACK);
					resparam.res.setReplacement("com.android.systemui", "drawable", "status_bar_background", new ColorDrawable(statusbarColor));
				} catch (Throwable t) { XposedBridge.log(t); }
			}

			if (pref.getBoolean("statusbar_clock_color_enabled", false)) {
				try {
					resparam.res.hookLayout("com.android.systemui", "layout", "tw_status_bar", new XC_LayoutInflated() {
						@Override
						public void handleLayoutInflated(LayoutInflatedParam liparam) throws Throwable {
							try {
								TextView clock = (TextView) liparam.view.findViewById(
										liparam.res.getIdentifier("clock", "id", "com.android.systemui"));
								clock.setTextColor(pref.getInt("statusbar_clock_color", 0xffbebebe));
							} catch (Throwable t) { XposedBridge.log(t); }
						}
					}); 
				} catch (Throwable t) { XposedBridge.log(t); }
			}
		}
	}
	
	private static void hookSignalLevelFixes() {
		try {
			// correction for signal strength level
			findAndHookMethod(SignalStrength.class, "getLevel", new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) throws Throwable {
					param.setResult(getCorrectedLevel((Integer) param.getResult()));
				}
				private int getCorrectedLevel(int level) {
					// value was overridden by our more specific method already
					if (level >= 10000)
						return level - 10000;

					// interpolate for other modes
					if (signalStrengthBars == 4 || level == 0) {
						return level;

					} else if (signalStrengthBars == 5) {
						if (level == 4) return 5;
						else if (level == 3) return 4;
						else if (level == 2) return 3;
						else if (level == 1) return 2;

					} else if (signalStrengthBars == 6) {
						if (level == 4) return 6;
						else if (level == 3) return 4;
						else if (level == 2) return 3;
						else if (level == 1) return 2;
					}
					// shouldn't get here
					XposedBridge.log("could not determine signal level (original result was " + level + ")");
					return 0;
				}
			});

			findAndHookMethod(SignalStrength.class, "getGsmLevel", new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					int asu = ((SignalStrength) param.thisObject).getGsmSignalStrength();
					param.setResult(getSignalLevel(asu));
				}
				private int getSignalLevel(int asu) {
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
				};
			});
		} catch (Throwable t) { XposedBridge.log(t); }
    }	
}
