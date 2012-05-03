package de.robv.android.xposed.mods.tweakbox;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import de.robv.android.xposed.Callback;
import de.robv.android.xposed.XposedBridge;

// ported from https://github.com/CyanogenMod/android_frameworks_base/commit/fa0c6a58a44fd884d758d47eaa750c9c6476af1a
public class VolumeKeysSkipTrack {
	private static Method method_isMusicActive;
	private static Field field_mContext;
	private static Field field_mHandler;
	
	private static boolean alsoForScreenOn;
	
	private static boolean mIsLongPress = false;
	// there can only be one
	private static Object phoneWindowManager = null;
	
	static void init(boolean alsoForScreenOn) {
		try {
			VolumeKeysSkipTrack.alsoForScreenOn = false; //alsoForScreenOn; 
			
			Class<?> classPhoneWindowManager = Class.forName("com.android.internal.policy.impl.PhoneWindowManager");
			method_isMusicActive = classPhoneWindowManager.getDeclaredMethod("isMusicActive");
			
			Method method_handleInterceptKeyBeforeQueueing = classPhoneWindowManager
					.getDeclaredMethod("interceptKeyBeforeQueueing", KeyEvent.class, int.class, boolean.class);
			XposedBridge.hookMethod(method_handleInterceptKeyBeforeQueueing, VolumeKeysSkipTrack.class, "handleInterceptKeyBeforeQueueing", Callback.PRIORITY_HIGHEST);
			
			field_mContext = classPhoneWindowManager.getDeclaredField("mContext");
			field_mHandler = classPhoneWindowManager.getDeclaredField("mHandler");
			
			AccessibleObject.setAccessible(new AccessibleObject[] {
					method_isMusicActive,
					field_mContext,
					field_mHandler,
			}, true);
		} catch (Exception e) { XposedBridge.log(e); }
	}
	
	@SuppressWarnings("unused")
	private static Object handleInterceptKeyBeforeQueueing(Iterator<Callback> iterator, Method method, Object thisObject, Object[] args) throws Throwable {
		try {
			final boolean isScreenOn = (Boolean) args[2];
			if (!isScreenOn || alsoForScreenOn) {
				final KeyEvent event = (KeyEvent) args[0];
				final int keyCode = event.getKeyCode();
				if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
						&& (Boolean)method_isMusicActive.invoke(thisObject) == true) {
					phoneWindowManager = thisObject;
					if (event.getAction() == KeyEvent.ACTION_DOWN) {
						mIsLongPress = false;
						handleVolumeLongPress(keyCode);
						return 0;
					} else {
						handleVolumeLongPressAbort();
						if (mIsLongPress)
							return 0;
						
						// send an additional "key down" because the first one was eaten
						// the "key up" is what we are just processing
						Object[] newArgs = new Object[3];
						newArgs[0] = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
						newArgs[1] = args[1];
						newArgs[2] = args[2];
						XposedBridge.invokeOriginalMethod(method, thisObject, newArgs);
					}
				}
			}

		} catch (Exception e) {
			XposedBridge.log(e);
		}
		Object result = XposedBridge.callNext(iterator, method, thisObject, args);
		return result;
	}
	
	/**
	 * When a volumeup-key longpress expires, skip songs based on key press
	 */
	private static Runnable mVolumeUpLongPress = new Runnable() {
	    public void run() {
	        // set the long press flag to true
	        mIsLongPress = true;

	        // Shamelessly copied from Kmobs LockScreen controls, works for Pandora, etc...
	        sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_NEXT);
	    };
	};

	/**
	 * When a volumedown-key longpress expires, skip songs based on key press
	 */
	private static Runnable mVolumeDownLongPress = new Runnable() {
	    public void run() {
	        // set the long press flag to true
	        mIsLongPress = true;

	        // Shamelessly copied from Kmobs LockScreen controls, works for Pandora, etc...
	        sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
	    };
	};

	private static void sendMediaButtonEvent(int code) {
	    Context mContext = null;
		try {
			mContext = (Context) field_mContext.get(phoneWindowManager);
		} catch (Exception e) {
			XposedBridge.log(e);
		}
		
		long eventtime = SystemClock.uptimeMillis();
	    Intent keyIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
	    KeyEvent keyEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, code, 0);
	    keyIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
	    mContext.sendOrderedBroadcast(keyIntent, null);
	    keyEvent = KeyEvent.changeAction(keyEvent, KeyEvent.ACTION_UP);
	    keyIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
	    mContext.sendOrderedBroadcast(keyIntent, null);
	}

	private static void handleVolumeLongPress(int keycode) {
	    Handler mHandler = null;
		try {
			mHandler = (Handler) field_mHandler.get(phoneWindowManager);
		} catch (Exception e) {
			XposedBridge.log(e);
		}
		
	    mHandler.postDelayed(keycode == KeyEvent.KEYCODE_VOLUME_UP ? mVolumeUpLongPress :
	        mVolumeDownLongPress, ViewConfiguration.getLongPressTimeout());
	}

	private static void handleVolumeLongPressAbort() {
	    Handler mHandler = null;
		try {
			mHandler = (Handler) field_mHandler.get(phoneWindowManager);
		} catch (Exception e) {
			XposedBridge.log(e);
		}
		
	    mHandler.removeCallbacks(mVolumeUpLongPress);
	    mHandler.removeCallbacks(mVolumeDownLongPress);
	}
}
