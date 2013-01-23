package de.robv.android.xposed.mods.tweakbox;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

import java.util.Map;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.FeatureInfo;
import android.content.res.XResources;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Message;
import android.os.Vibrator;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.gsm.SuppServiceNotification;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

class PhoneTweaks {
	private PhoneTweaks() {};
	
	public static void initZygote(final SharedPreferences pref) {
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
	}
	
	public static void loadPackage(final XSharedPreferences pref, ClassLoader classLoader) {
		// Handle vibrate on Call Wait
		try {
			findAndHookMethod("com.android.phone.PhoneUtils", classLoader, "displaySSInfo",
					Phone.class, Context.class, SuppServiceNotification.class, Message.class, AlertDialog.class, new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) throws Throwable {
					int notificationType = getIntField(param.args[2], "notificationType");
					int code = getIntField(param.args[2], "code");

					// Waiting for target party
					if (notificationType == 0 && code == 3) {
						pref.reload();
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
			findAndHookMethod("com.android.phone.Ringer$1", classLoader, "handleMessage", Message.class, new XC_MethodHook() {
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
						pref.reload();
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
				findAndHookMethod("com.android.phone.PhoneFeature", classLoader, "hasFeature", String.class, new XC_MethodHook() {
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
