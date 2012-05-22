package de.robv.android.xposed.mods.redclock;

import java.lang.reflect.Method;
import java.util.Iterator;

import android.graphics.Color;
import android.widget.TextView;
import de.robv.android.xposed.Callback;
import de.robv.android.xposed.MethodSignatureGuide;
import de.robv.android.xposed.XposedBridge;

/**
 * Example module which changes the color of the clock in the statusbar to red and
 * also appends a smiley to it. It does so by hooking the updateClock method of the
 * Clock class in the systemui as soon as the com.android.systemui is being loaded.
 * <br/>
 * This demonstrates how a very central component can be modified without changing
 * the APKs (including deodexing, recompiling, signing etc).
 */
public class RedClock {
	/**
	 * @see MethodSignatureGuide#init
	 */
	public static void init(String startClassName) {
		// only load for Zygote (the system process), not for command line tools		
		if (startClassName != null)
			return;
		
		try {
			XposedBridge.hookLoadPackage(RedClock.class, "handleLoadPackage", Callback.PRIORITY_DEFAULT);
		} catch (Throwable t) {
			XposedBridge.log(t);
		}
	}
	
	/**
	 * @see MethodSignatureGuide#handleLoadPackage
	 */
	@SuppressWarnings("unused")
	private static void handleLoadPackage(String packageName, ClassLoader classLoader) {
		// the status bar belongs to package com.android.systemui
		if (!packageName.equals("com.android.systemui"))
			return;
		
		try {
			Method updateClock =
				Class.forName("com.android.systemui.statusbar.policy.Clock", false, classLoader)
				.getDeclaredMethod("updateClock");
			XposedBridge.hookMethod(updateClock, RedClock.class, "handleUpdateClock", Callback.PRIORITY_DEFAULT);
		} catch (Exception e) {
			XposedBridge.log(e);
		}
	}
	
	@SuppressWarnings("unused")
	private static Object handleUpdateClock(Iterator<Callback> iterator, Method method, Object thisObject, Object[] args) throws Throwable {
		// first let the original implementation perform its work
		Object result = XposedBridge.callNext(iterator, method, thisObject, args);
		// then change text and color
		try {
			TextView tv = (TextView) thisObject;
			String text = tv.getText().toString();
			tv.setText(text + " :)");
			tv.setTextColor(Color.RED);
		} catch (Exception e) {
			// replacing did not work.. but no reason to crash the VM! Log the error and go on.
			XposedBridge.log(e);
		}
		return result;
	}
}
