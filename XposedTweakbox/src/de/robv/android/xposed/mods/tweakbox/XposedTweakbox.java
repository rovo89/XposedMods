package de.robv.android.xposed.mods.tweakbox;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setFloatField;

import java.lang.reflect.Constructor;
import java.util.Locale;
import java.util.Map;

import android.app.AlertDialog;
import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.FeatureInfo;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XResources;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Message;
import android.os.Vibrator;
import android.telephony.SignalStrength;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import android.widget.TextView;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.gsm.SuppServiceNotification;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
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
			if (pref.getBoolean("phone_enable_sip", false)) {
				XResources.setSystemWideReplacement("android", "bool", "config_built_in_sip_phone", true);
				XResources.setSystemWideReplacement("android", "bool", "config_sip_wifi_only", false);
				
				findAndHookMethod("com.android.server.pm.PackageManagerService", null, "readPermissions", new XC_MethodHook() {
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						@SuppressWarnings("unchecked")
						Map<String, FeatureInfo> mAvailableFeatures = (Map<String, FeatureInfo>) getObjectField(param.thisObject, "mAvailableFeatures");

						FeatureInfo fi = new FeatureInfo();
						fi.name = "android.software.sip";
						mAvailableFeatures.put(fi.name, fi);

						fi = new FeatureInfo();
						fi.name = "android.software.sip.voip";
						mAvailableFeatures.put(fi.name, fi);
					};
				});
			}
		} catch (Throwable t) { XposedBridge.log(t); }

		try {
			XResources.setSystemWideReplacement("android", "integer", "config_longPressOnHomeBehavior", pref.getInt("long_home_press_behaviour", 2));
		} catch (Throwable t) { XposedBridge.log(t); }

		try {
			XResources.setSystemWideReplacement("android", "integer", "config_criticalBatteryWarningLevel", pref.getInt("low_battery_critical", 5));
			XResources.setSystemWideReplacement("android", "integer", "config_lowBatteryWarningLevel", pref.getInt("low_battery_low", 15));
			XResources.setSystemWideReplacement("android", "integer", "config_lowBatteryCloseWarningLevel", pref.getInt("low_battery_close", 20));
		} catch (Throwable t) { XposedBridge.log(t); }

		// density / resource configuration manipulation
		try {
			findAndHookMethod(Display.class, "init", int.class, new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) throws Throwable {
					AndroidAppHelper.reloadSharedPreferencesIfNeeded(pref);
					String packageName = AndroidAppHelper.currentPackageName();

					int packageDensity = pref.getInt("dpioverride/" + packageName + "/density", pref.getInt("dpioverride/default/density", 0));
					if (packageDensity > 0)
						setFloatField(param.thisObject, "mDensity", packageDensity / 160.0f);
				};
			});

			findAndHookMethod(Resources.class, "updateConfiguration",
					Configuration.class, DisplayMetrics.class, CompatibilityInfo.class, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					Configuration config = (Configuration) param.args[0];
					if (config != null && param.thisObject instanceof XResources) {
						String packageName = ((XResources) param.thisObject).getPackageName();
						if (packageName != null) {
							AndroidAppHelper.reloadSharedPreferencesIfNeeded(pref);

							int swdp = pref.getInt("dpioverride/" + packageName + "/swdp", pref.getInt("dpioverride/default/swdp", 0));
							int wdp = pref.getInt("dpioverride/" + packageName + "/wdp", pref.getInt("dpioverride/default/wdp", 0));
							int hdp = pref.getInt("dpioverride/" + packageName + "/hdp", pref.getInt("dpioverride/default/hdp", 0));
							Locale packageLocale = getPackageSpecificLocale(packageName);

							if (swdp > 0 || wdp > 0 || hdp > 0 || packageLocale != null) {
								Configuration newConfig = new Configuration(config);
								if (swdp > 0)
									newConfig.smallestScreenWidthDp = swdp;

								if (wdp > 0)
									newConfig.screenWidthDp = wdp;

								if (hdp > 0)
									newConfig.screenHeightDp = hdp;

								if (packageLocale != null) {
									newConfig.locale = packageLocale;
									// AndroidAppHelper.currentPackageName() is the package name of the current process,
									// in contrast to the package name for these settings (that might be loaded by a different
											// process as well)
									if (AndroidAppHelper.currentPackageName().equals(packageName))
										Locale.setDefault(packageLocale);
								}

								param.args[0] = newConfig;
							}
						}
					}
				}
			});
		} catch (Throwable t) { XposedBridge.log(t); }

		if (pref.getBoolean("volume_keys_skip_track", false))
			VolumeKeysSkipTrack.init(pref.getBoolean("volume_keys_skip_track_screenon", false));
	}


	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		AndroidAppHelper.reloadSharedPreferencesIfNeeded(pref);

		Locale packageLocale = getPackageSpecificLocale(lpparam.packageName);
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
			try {
				CrtEffect.hookScreenOff(pref, lpparam.classLoader);
			} catch (Throwable t) { XposedBridge.log(t); }
			
			
		} else if (lpparam.packageName.equals("com.android.phone")) {
			// Handle vibrate on Call Wait
			try {
				findAndHookMethod("com.android.phone.PhoneUtils", lpparam.classLoader, "displaySSInfo",
						Phone.class, Context.class, SuppServiceNotification.class, Message.class, AlertDialog.class, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						int notificationType = getIntField(param.args[2], "notificationType");
						int code = getIntField(param.args[2], "code");
						
						 // Waiting for target party
						if (notificationType == 0 && code == 3) {
							AndroidAppHelper.reloadSharedPreferencesIfNeeded(pref);
							if (pref.getBoolean("phone_vibrate_waiting", false)) {
								Context context = (Context) param.args[1];
	
								// Get instance of Vibrator from current Context
								Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
								v.vibrate(new long[] { 0, 200, 200, 200, 200, 800 }, -1); // Vibrate with a simple pattern
							}
						}
					}
				});
			} catch (Throwable t) { XposedBridge.log(t); }

			// Handle increasing ringer tone
			try {
				final ThreadLocal<Object> insideRingerHandler = new ThreadLocal<Object>();

				// Control whether the execution is inside handleMessage or not()
				findAndHookMethod("com.android.phone.Ringer$1", lpparam.classLoader, "handleMessage", Message.class, new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
						insideRingerHandler.set(new Object());
					}

					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						insideRingerHandler.set(null);
					}
				});

				findAndHookMethod(AudioManager.class, "setStreamVolume", int.class, int.class, int.class, new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
						if (insideRingerHandler.get() != null) {
							// Within execution of handleMessage()
							AndroidAppHelper.reloadSharedPreferencesIfNeeded(pref);
							if (!pref.getBoolean("phone_increasing_ringer", true)) {
								// No increasing ringer; skip changing the ringer volume
								param.setResult(null);
							}
						}
					}
				});
			} catch (Throwable t) { XposedBridge.log(t); }


			// Handle call recording
			if (pref.getBoolean("phone_call_recording", false)) {
				try {
					findAndHookMethod("com.android.phone.PhoneFeature", lpparam.classLoader, "hasFeature", String.class, new XC_MethodHook() {
						@Override
						protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
							if ("voice_call_recording".equals(param.args[0])) {
								param.setResult(Boolean.TRUE);
							}
						}
					});

					findAndHookMethod(MediaRecorder.class, "prepare", new XC_MethodHook() {
						@Override
						protected void afterHookedMethod(MethodHookParam param) throws Throwable {
							XposedHelpers.callMethod(param.thisObject, "start");
						}
					});
				} catch (Throwable t) { XposedBridge.log(t); }
			}
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

	private static Locale getPackageSpecificLocale(String packageName) {
		String locale = pref.getString("dpioverride/" + packageName + "/locale", pref.getString("dpioverride/default/locale", null));
		if (locale == null || locale.isEmpty())
			return null;

		String[] localeParts = locale.split("_", 3);
		String language = localeParts[0];
		String region = (localeParts.length >= 2) ? localeParts[1] : "";
		String variant = (localeParts.length >= 3) ? localeParts[2] : "";
		return new Locale(language, region, variant);
	}
}
