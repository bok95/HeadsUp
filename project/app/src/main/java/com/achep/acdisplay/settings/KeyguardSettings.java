/*
 * Copyright (C) 2014 AChep@xda <artemchep@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package com.achep.acdisplay.settings;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.widget.Switch;

import com.achep.acdisplay.Config;
import com.achep.headsup.R;

/**
 * Created by Artem on 09.02.14.
 */
public class KeyguardSettings extends PreferenceFragment {

    private Enabler mKeyguardEnabler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.lockscreen_settings);

        Activity activity = getActivity();
        ActionBar actionBar = activity.getActionBar();
        assert actionBar != null;

        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setCustomView(R.layout.layout_ab_switch);
        Switch switch_ = (Switch) actionBar.getCustomView().findViewById(R.id.switch_);
        mKeyguardEnabler = new Enabler(activity, switch_, Config.KEY_KEYGUARD);
    }

    @Override
    public void onResume() {
        super.onResume();
        mKeyguardEnabler.resume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mKeyguardEnabler.pause();
    }
}