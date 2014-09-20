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

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.achep.acdisplay.Config;
import com.achep.acdisplay.Operator;
import com.achep.headsup.R;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Artem on 09.02.14.
 */
public class SettingsFragment extends PreferenceFragment implements
        Config.OnConfigChangedListener, Preference.OnPreferenceChangeListener {

    private ListPreference mThemePreference;
    private Preference mNotifyDecayTimePreference;
    private ListPreference mNotifyMinPriorityPreference;

    private boolean mBroadcasting;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        syncPreference(Config.KEY_UX_ONLY_IN_FULLSCREEN);

        mThemePreference = (ListPreference) findPreference(Config.KEY_UI_THEME);
        mNotifyDecayTimePreference = findPreference(Config.KEY_NOTIFY_DECAY_TIME);
        mNotifyMinPriorityPreference = (ListPreference) findPreference(Config.KEY_NOTIFY_MIN_PRIORITY);

        mThemePreference.setOnPreferenceChangeListener(this);
        mNotifyDecayTimePreference.setOnPreferenceChangeListener(this);
        mNotifyMinPriorityPreference.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        Config config = getConfig();
        config.registerListener(this);

        updateThemeSummary(config);
        updateNotifyDecayTimeSummary(config);
        updateNotifyMinPrioritySummary(config);
    }

    @Override
    public void onPause() {
        super.onPause();
        Config config = getConfig();
        config.unregisterListener(this);
    }

    @Override
    public void onConfigChanged(@NonNull Config config,
                                @NonNull String key,
                                @NonNull Object value) {
        mBroadcasting = true;
        switch (key) {
            case Config.KEY_UI_THEME:
                updateThemePreference(config);
                break;
            case Config.KEY_NOTIFY_MIN_PRIORITY:
                updateNotifyMinPriorityPreference(config);
                break;
            case Config.KEY_NOTIFY_DECAY_TIME:
                updateNotifyDecayTimeSummary(config);
                break;
        }
        mBroadcasting = false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (mBroadcasting) {
            return true;
        }

        final Config config = getConfig();
        if (preference == mThemePreference) {
            String theme = (String) newValue;
            config.setTheme(getActivity(), theme, null);

        } else if (preference == mNotifyMinPriorityPreference) {
            int priority = Integer.parseInt((String) newValue);
            config.setNotifyMinPriority(getActivity(), priority, null);

        } if (preference == mNotifyDecayTimePreference) {
            int decayTime = Integer.parseInt((String) newValue);
            config.setNotifyDecayTime(getActivity(), decayTime, this);
        } else
            return false;
        return true;
    }

    private void updateThemePreference(Config config) {
        mThemePreference.setValue(config.getTheme());
        updateThemeSummary(config);
    }

    private void updateThemeSummary(Config config) {
        CharSequence theme = config.getTheme();
        CharSequence[] themes = mThemePreference.getEntryValues();
        for (int i = 0; i < themes.length; i++) {
            if (TextUtils.equals(theme, themes[i])) {
                mThemePreference.setSummary(getString(
                        R.string.settings_theme_summary,
                        mThemePreference.getEntries()[i]));
            }
        }
    }

    private void updateNotifyDecayTimeSummary(Config config) {
        mNotifyDecayTimePreference.setSummary(getString(
                R.string.settings_notify_decay_time_summary,
                Float.toString(config.getNotifyDecayTime() / 1000f)));
    }

    private void updateNotifyMinPriorityPreference(Config config) {
        mNotifyMinPriorityPreference.setValue(Integer.toString(config.getNotifyMinPriority()));
        updateNotifyMinPrioritySummary(config);
    }

    private void updateNotifyMinPrioritySummary(Config config) {
        int pos = -config.getNotifyMinPriority() + 2;
        mNotifyMinPriorityPreference.setSummary(getString(
                R.string.settings_notify_min_priority_summary,
                mNotifyMinPriorityPreference.getEntries()[pos]));
    }
}
