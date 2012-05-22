package de.robv.android.xposed.mods.tweakbox;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;

import java.lang.reflect.Constructor;
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
	private static boolean alsoForScreenOn;
	private static boolean mIsLongPress = false;
	
	static void init(boolean alsoForScreenOn) {
		try {
			VolumeKeysSkipTrack.alsoForScreenOn = false; //alsoForScreenOn; 
			
			Class<?> classPhoneWindowManager = Class.forName("com.android.internal.policy.impl.PhoneWindowManager");
			Method method_handleInterceptKeyBeforeQueueing = classPhoneWindowManager
					.getDeclaredMethod("interceptKeyBeforeQueueing", KeyEvent.class, int.class, boolean.class);
			XposedBridge.hookMethod(method_handleInterceptKeyBeforeQueueing, VolumeKeysSkipTrack.class, "handleInterceptKeyBeforeQueueing", Callback.PRIORITY_HIGHEST);
			
			XposedBridge.hookAllConstructors(classPhoneWindowManager, VolumeKeysSkipTrack.class, "handleConstructPhoneWindowManager", Callback.PRIORITY_DEFAULT);
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
						&& (Boolean) callMethod(thisObject, "isMusicActive") == true) {
					if (event.getAction() == KeyEvent.ACTION_DOWN) {
						mIsLongPress = false;
						handleVolumeLongPress(thisObject, keyCode);
						return 0;
					} else {
						handleVolumeLongPressAbort(thisObject);
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
	
	@SuppressWarnings("unused")
	private static void handleConstructPhoneWindowManager(Iterator<Callback> iterator, Constructor<?> method, final Object thisObject, Object[] args) throws Throwable {
		XposedBridge.callNext(iterator, method, thisObject, args);
		
		/**
		 * When a volumeup-key longpress expires, skip songs based on key press
		 */
		Runnable mVolumeUpLongPress = new Runnable() {
		    public void run() {
		        // set the long press flag to true
		        mIsLongPress = true;

		        // Shamelessly copied from Kmobs LockScreen controls, works for Pandora, etc...
		        sendMediaButtonEvent(thisObject, KeyEvent.KEYCODE_MEDIA_NEXT);
		    };
		};

		/**
		 * When a volumedown-key longpress expires, skip songs based on key press
		 */
		Runnable mVolumeDownLongPress = new Runnable() {
		    public void run() {
		        // set the long press flag to true
		        mIsLongPress = true;

		        // Shamelessly copied from Kmobs LockScreen controls, works for Pandora, etc...
		        sendMediaButtonEvent(thisObject, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
		    };
		};
		
		setAdditionalInstanceField(thisObject, "mVolumeUpLongPress", mVolumeUpLongPress);
		setAdditionalInstanceField(thisObject, "mVolumeDownLongPress", mVolumeDownLongPress);
	}
	
	

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
