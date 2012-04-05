package de.robv.android.xposed.mods.crtoffeffect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;

import de.robv.android.xposed.Callback;
import de.robv.android.xposed.XposedBridge;

public class CrtOffEffect  {
	private static Field fieldAnimateScreenLights;
	
	public static void init(String startClassName) {
		if (startClassName != null)
			return;
		
		try {
			Class<?> classPowerManagerService = Class.forName("com.android.server.PowerManagerService");
			fieldAnimateScreenLights = classPowerManagerService.getDeclaredField("mAnimateScreenLights");
			fieldAnimateScreenLights.setAccessible(true);
			Method methodInitInThread = classPowerManagerService.getDeclaredMethod("initInThread");
			XposedBridge.hookMethod(methodInitInThread, CrtOffEffect.class, "handle_PowerManagerService_initInThread", Callback.PRIORITY_DEFAULT);
		} catch (Exception e) {
			XposedBridge.log(e);
		}
	}
	
	@SuppressWarnings("unused")
	private static Object handle_PowerManagerService_initInThread (Iterator<Callback> iterator, Method method, Object thisObject, Object[] args) throws Throwable {
		Object result = XposedBridge.callNext(iterator, method, thisObject, args);
		try {
			fieldAnimateScreenLights.setBoolean(thisObject, false);
		} catch (Exception e) {
			XposedBridge.log(e);
		}
		return result;
	}
}
