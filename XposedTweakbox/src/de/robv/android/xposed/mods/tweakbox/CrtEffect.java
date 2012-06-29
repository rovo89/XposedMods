package de.robv.android.xposed.mods.tweakbox;

import java.lang.reflect.Method;

import android.app.AndroidAppHelper;
import android.content.SharedPreferences;
import android.os.SystemProperties;
import android.util.Log;
import android.view.WindowManagerPolicy;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import static de.robv.android.xposed.XposedHelpers.getSurroundingThis;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.setIntField;

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
        
        Class<?> clsBrightnessState = Class.forName("com.android.server.PowerManagerService$BrightnessState");
        Method methodRun = clsBrightnessState.getDeclaredMethod("run");
        XposedBridge.hookMethod(methodRun, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // About to turn off screen
                AndroidAppHelper.reloadSharedPreferencesIfNeeded(pref);
                Object powerManagerService = getSurroundingThis(param.thisObject);
                int animationSetting;
                animationSetting  = (pref.getBoolean("crt_off_effect", false)) ? ANIM_SETTING_OFF : 0;
                animationSetting |= (pref.getBoolean("crt_on_effect", false))  ? ANIM_SETTING_ON  : 0;
                setIntField(powerManagerService, "mAnimationSetting", animationSetting);
                
                Log.i("Tweakbox", "Setting entry on run");
                nestingStatus.set(CallStackState.ENTERED_RUN);
            }
            
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                nestingStatus.set(null);
                Log.i("Tweakbox", "Clearing method run on exit");
            }
        });

        Class<?> clsPowerManager = Class.forName("com.android.server.PowerManagerService");
        final Method methodNativeAnimation = clsPowerManager.getDeclaredMethod("nativeStartSurfaceFlingerAnimation", int.class);
        XposedBridge.hookMethod(methodNativeAnimation, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Log.i("Tweakbox", "Executing the native method");
                if (CallStackState.ENTERED_RUN.equals(nestingStatus.get())) {
                    Log.i("Tweakbox", "Setting status to past native method");
                    nestingStatus.set(CallStackState.EXECUTED_NATIVE);
                }
            }
        });

        Method methodJumpTargetLocked = clsBrightnessState.getDeclaredMethod("jumpToTargetLocked");
        XposedBridge.hookMethod(methodJumpTargetLocked, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (CallStackState.ENTERED_RUN.equals(nestingStatus.get())) {
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
        Class<?> clsOrientationListener = Class.forName("com.android.internal.policy.impl.PhoneWindowManager$MyOrientationListener", false, classLoader);
        XposedBridge.hookMethod(clsOrientationListener.getDeclaredMethod("onProposedRotationChanged", int.class), new XC_MethodHook() {
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
