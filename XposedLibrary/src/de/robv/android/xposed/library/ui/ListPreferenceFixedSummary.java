package de.robv.android.xposed.library.ui;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

public class ListPreferenceFixedSummary extends ListPreference {
	public ListPreferenceFixedSummary(Context context) {
		super(context);
	}
	
	public ListPreferenceFixedSummary(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	@Override
	public void setValue(String value) {
		super.setValue(value);
		notifyChanged();
	}
}
