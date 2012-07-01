package de.robv.android.xposed.mods.tweakbox;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findMethodExact;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getSurroundingThis;
import static de.robv.android.xposed.XposedHelpers.setIntField;

import java.lang.reflect.Method;

import android.app.AndroidAppHelper;
import android.content.SharedPreferences;
import android.os.SystemProperties;
import android.util.Log;
import android.view.WindowManagerPolicy;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Hooks for CRT animations
 */
public class CrtEffect {

	private static final int ANIM_SETTING_ON = 0x01;
	private static final int ANIM_SETTING_OFF = 0x10;

	// Flags for execution path in order to detect missing call to the native method
	private enum CallStackState {
		ENTERED_RUN,
		EXECUTED_NATIVE
	}


	// No construction of objects, pure static class
	private CrtEffect() { }



	/**
	 * Hook the methods related with the screen off event in order to use the active CRT On/Off preferences.
	 * Handles the missing call to "nativeStartSurfaceFlingerAnimation" on I9300.
	 */
	public static void hookScreenOff(final SharedPreferences pref, ClassLoader classLoader) throws Exception {

		final ThreadLocal<CallStackState> nestingStatus = new ThreadLocal<CallStackState>();
		
		Class<?> classBrightnessState = findClass("com.android.server.PowerManagerService$BrightnessState", classLoader);
		
		findAndHookMethod(classBrightnessState, "run", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				// About to turn off screen
				AndroidAppHelper.reloadSharedPreferencesIfNeeded(pref);
				Object powerManagerService = getSurroundingThis(param.thisObject);
				int animationSetting;
				animationSetting  = (pref.getBoolean("crt_off_effect", false)) ? ANIM_SETTING_OFF : 0;
				animationSetting |= (pref.getBoolean("crt_on_effect", false))  ? ANIM_SETTING_ON  : 0;
				setIntField(powerManagerService, "mAnimationSetting", animationSetting);

				Log.d(XposedTweakbox.TAG, "CRT: Setting entry on run");
				nestingStatus.set(CallStackState.ENTERED_RUN);
			}

			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				nestingStatus.set(null);
				Log.d(XposedTweakbox.TAG, "CRT: Clearing method run on exit");
			}
		});

		final Method methodNativeAnimation = findMethodExact("com.android.server.PowerManagerService", classLoader,
				"nativeStartSurfaceFlingerAnimation", int.class);
		XposedBridge.hookMethod(methodNativeAnimation, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				Log.d(XposedTweakbox.TAG, "Executing the native method");
				if (CallStackState.ENTERED_RUN.equals(nestingStatus.get())) {
					Log.d(XposedTweakbox.TAG, "CRT: Setting status to past native method");
					nestingStatus.set(CallStackState.EXECUTED_NATIVE);
				}
			}
		});

		findAndHookMethod(classBrightnessState, "jumpToTargetLocked", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				if (CallStackState.ENTERED_RUN.equals(nestingStatus.get())) {
					Log.i(XposedTweakbox.TAG, "CRT: Native method was not called, calling it now");
					
					// Run method didn't call the animation, do it now before executing the jump to target
					Object pmService = XposedHelpers.getSurroundingThis(param.thisObject);
					int animationSetting = 0;
					if (getIntField(pmService, "mScreenOffReason") != WindowManagerPolicy.OFF_BECAUSE_OF_PROX_SENSOR) {
						animationSetting  = (pref.getBoolean("crt_off_effect", false)) ? ANIM_SETTING_OFF : 0;
						animationSetting |= (pref.getBoolean("crt_on_effect", false))  ? ANIM_SETTING_ON  : 0;
					}
					methodNativeAnimation.invoke(pmService, animationSetting);
				}
			}
		});

		// Update a system property when the orientation changes.
		findAndHookMethod("com.android.internal.policy.impl.PhoneWindowManager$MyOrientationListener", classLoader,
				"onProposedRotationChanged", int.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				int rotation = (Integer) param.args[0];
				if (rotation >= 0) {
					AndroidAppHelper.reloadSharedPreferencesIfNeeded(pref);
					if (pref.getBoolean("crt_effect_orientation", false)) {
						SystemProperties.set("runtime.xposed.orientation", String.valueOf(rotation));
					} else {
						// Must always update, otherwise it might become stuck in landscape if turned Off without reboot
						SystemProperties.set("runtime.xposed.orientation", "0");
					}
				}
			}
		});
	}

}
