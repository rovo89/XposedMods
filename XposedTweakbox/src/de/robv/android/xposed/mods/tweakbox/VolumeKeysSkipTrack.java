package de.robv.android.xposed.mods.tweakbox;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;

import java.lang.reflect.Method;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XCallback;

// ported from https://github.com/CyanogenMod/android_frameworks_base/commit/fa0c6a58a44fd884d758d47eaa750c9c6476af1a
public class VolumeKeysSkipTrack {
	private static boolean alsoForScreenOn;
	private static boolean mIsLongPress = false;

	static void init(boolean alsoForScreenOn) {
		try {
			VolumeKeysSkipTrack.alsoForScreenOn = false; //alsoForScreenOn; 

			Class<?> classPhoneWindowManager = Class.forName("com.android.internal.policy.impl.PhoneWindowManager");
			XposedBridge.hookAllConstructors(classPhoneWindowManager, handleConstructPhoneWindowManager);

			Method method_handleInterceptKeyBeforeQueueing = classPhoneWindowManager
					.getDeclaredMethod("interceptKeyBeforeQueueing", KeyEvent.class, int.class, boolean.class);
			XposedBridge.hookMethod(method_handleInterceptKeyBeforeQueueing, handleInterceptKeyBeforeQueueing);
		} catch (Exception e) { XposedBridge.log(e); }
	}

	private static XC_MethodHook handleInterceptKeyBeforeQueueing = new XC_MethodHook(XCallback.PRIORITY_HIGHEST) {
		@Override
		protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
			final boolean isScreenOn = (Boolean) param.args[2];
			if (!isScreenOn || alsoForScreenOn) {
				final KeyEvent event = (KeyEvent) param.args[0];
				final int keyCode = event.getKeyCode();
				if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
						&& (Boolean) callMethod(param.thisObject, "isMusicActive") == true) {
					if (event.getAction() == KeyEvent.ACTION_DOWN) {
						mIsLongPress = false;
						handleVolumeLongPress(param.thisObject, keyCode);
						param.setResult(0);
						return;
					} else {
						handleVolumeLongPressAbort(param.thisObject);
						if (mIsLongPress) {
							param.setResult(0);
							return;
						}

						// send an additional "key down" because the first one was eaten
						// the "key up" is what we are just processing
						Object[] newArgs = new Object[3];
						newArgs[0] = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
						newArgs[1] = param.args[1];
						newArgs[2] = param.args[2];
						XposedBridge.invokeOriginalMethod(param.method, param.thisObject, newArgs);
					}
				}
			}
		}
	};

	private static XC_MethodHook handleConstructPhoneWindowManager = new XC_MethodHook() {
		@Override
		protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
			/**
			 * When a volumeup-key longpress expires, skip songs based on key press
			 */
			Runnable mVolumeUpLongPress = new Runnable() {
				@Override
				public void run() {
					// set the long press flag to true
					mIsLongPress = true;

					// Shamelessly copied from Kmobs LockScreen controls, works for Pandora, etc...
					sendMediaButtonEvent(param.thisObject, KeyEvent.KEYCODE_MEDIA_NEXT);
				};
			};

			/**
			 * When a volumedown-key longpress expires, skip songs based on key press
			 */
			Runnable mVolumeDownLongPress = new Runnable() {
				@Override
				public void run() {
					// set the long press flag to true
					mIsLongPress = true;

					// Shamelessly copied from Kmobs LockScreen controls, works for Pandora, etc...
					sendMediaButtonEvent(param.thisObject, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
				};
			};

			setAdditionalInstanceField(param.thisObject, "mVolumeUpLongPress", mVolumeUpLongPress);
			setAdditionalInstanceField(param.thisObject, "mVolumeDownLongPress", mVolumeDownLongPress);
		}
	};

	private static void sendMediaButtonEvent(Object phoneWindowManager, int code) {
		Context mContext = (Context) getObjectField(phoneWindowManager, "mContext");
		long eventtime = SystemClock.uptimeMillis();
		Intent keyIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
		KeyEvent keyEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, code, 0);
		keyIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
		mContext.sendOrderedBroadcast(keyIntent, null);
		keyEvent = KeyEvent.changeAction(keyEvent, KeyEvent.ACTION_UP);
		keyIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
		mContext.sendOrderedBroadcast(keyIntent, null);
	}

	private static void handleVolumeLongPress(Object phoneWindowManager, int keycode) {
		Handler mHandler = (Handler) getObjectField(phoneWindowManager, "mHandler");
		Runnable mVolumeUpLongPress = (Runnable) getAdditionalInstanceField(phoneWindowManager, "mVolumeUpLongPress");
		Runnable mVolumeDownLongPress = (Runnable) getAdditionalInstanceField(phoneWindowManager, "mVolumeDownLongPress");

		mHandler.postDelayed(keycode == KeyEvent.KEYCODE_VOLUME_UP ? mVolumeUpLongPress :
			mVolumeDownLongPress, ViewConfiguration.getLongPressTimeout());
	}

	private static void handleVolumeLongPressAbort(Object phoneWindowManager) {
		Handler mHandler = (Handler) getObjectField(phoneWindowManager, "mHandler");
		Runnable mVolumeUpLongPress = (Runnable) getAdditionalInstanceField(phoneWindowManager, "mVolumeUpLongPress");
		Runnable mVolumeDownLongPress = (Runnable) getAdditionalInstanceField(phoneWindowManager, "mVolumeDownLongPress");

		mHandler.removeCallbacks(mVolumeUpLongPress);
		mHandler.removeCallbacks(mVolumeDownLongPress);
	}
}
