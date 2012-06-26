package de.robv.android.xposed.library.ui;

import android.content.Context;
import android.preference.Preference;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class TextViewPreference extends Preference {
	private TextView textView = null;
	
	public TextViewPreference(Context context) {
		super(context);
	}

	@Override
	protected View onCreateView(ViewGroup parent) {
		return getTextView();
	}
	
	public TextView getTextView() {
		if (textView == null) {
			textView = new TextView(getContext());
			textView.setId(android.R.id.title);
			textView.setPadding(5,5,5,5);
		}
		return textView;
	}
}
