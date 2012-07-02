package de.robv.android.xposed.mods.tweakbox;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.setFloatField;

import java.util.Locale;

import android.app.AndroidAppHelper;
import android.content.SharedPreferences;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XResources;
import android.util.DisplayMetrics;
import android.view.Display;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

class AppSpecificConfiguration {
	private static SharedPreferences pref;
	private AppSpecificConfiguration() {}
	
	public static void initZygote(final SharedPreferences pref) {
		AppSpecificConfiguration.pref = pref;
		
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
	}
	

	public static Locale getPackageSpecificLocale(String packageName) {
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
