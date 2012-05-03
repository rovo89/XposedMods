package de.robv.android.xposed.mods.tweakbox;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceFragment;

public class XposedTweakboxSettings extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	setTitle(R.string.app_name);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);
    }
    
    public static class PrefsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // this is important because although the handler classes that read these settings
            // are in the same package, they are executed in the context of the hooked package
            getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
            addPreferencesFromResource(R.xml.preferences);
        }
    }
}