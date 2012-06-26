package de.robv.android.xposed.library.ui;

import android.content.Context;
import android.graphics.Color;
import android.preference.Preference;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AbsListView;
import android.widget.ImageView;

public class SeparatorPreference extends Preference {
	int color = Color.GRAY;
	int height = 7;
	
	public SeparatorPreference(Context context) {
		super(context);
		setSelectable(false);
	}
	
	public void setColor(int color) {
		this.color = color;
	}
	
	public void setHeight(int height) {
		this.height = height;
	}
	
	@Override
	protected View onCreateView(ViewGroup parent) {
		ImageView iview = new ImageView(getContext());
		iview.setBackgroundColor(color);
		iview.setLayoutParams(new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT, height));
		return iview;
	}
}