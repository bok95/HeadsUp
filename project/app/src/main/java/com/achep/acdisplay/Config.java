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
package com.achep.acdisplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.achep.acdisplay.interfaces.IOnLowMemory;
import com.achep.acdisplay.powertoggles.ToggleReceiver;
import com.achep.acdisplay.utils.AccessUtils;
import com.achep.headsup.R;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Saves all the configurations for the app.
 *
 * @author Artem Chepurnoy
 * @since 21.01.14
 */
@SuppressWarnings("ConstantConditions")
public class Config implements IOnLowMemory {

    private static final String TAG = "Config";

    private static final String PREFERENCES_FILE_NAME = "config";

    // master switch
    public static final String KEY_ENABLED = "enabled";

    // interface
    public static final String KEY_UI_THEME = "ui_theme";

    // behavior
    public static final String KEY_UX_HIDE_ON_TOUCH_OUTSIDE = "ux_hide_on_touch_outside";
    public static final String KEY_UX_ONLY_IN_FULLSCREEN = "ux_only_in_fullscreen";
    public static final String KEY_NOTIFY_MIN_PRIORITY = "notify_min_priority";
    public static final String KEY_NOTIFY_DECAY_TIME = "notify_decay_time";

    // triggers
    public static final String KEY_TRIG_PREVIOUS_VERSION = "trigger_previous_version";
    public static final String KEY_TRIG_HELP_READ = "trigger_help_read";

    private static Config sConfig;

    private boolean mEnabled;
    private boolean mUxHideOnTouchOutside;
    private boolean mUxOnlyInFullscreen;
    private int mNotifyMinPriority;
    private int mNotifyDecayTime;
    private String mUiTheme;

    private final Triggers mTriggers;
    private int mTrigPreviousVersion;
    private boolean mTrigHelpRead;

    @NonNull
    private SoftReference<HashMap<String, Option>> mHashMapRef = new SoftReference<>(null);

    public static class Option {
        private final String setterName;
        private final String getterName;
        private final Class clazz;
        private final int minSdkVersion;

        public Option(String setterName,
                      String getterName,
                      Class clazz) {
            this(setterName, getterName, clazz, 0);
        }

        public Option(String setterName,
                      String getterName,
                      Class clazz, int minSdkVersion) {
            this.setterName = setterName;
            this.getterName = getterName;
            this.clazz = clazz;
            this.minSdkVersion = minSdkVersion;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return new HashCodeBuilder(11, 31)
                    .append(setterName)
                    .append(getterName)
                    .append(clazz)
                    .toHashCode();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object o) {
            if (o == null)
                return false;
            if (o == this)
                return true;
            if (!(o instanceof Option))
                return false;

            Option option = (Option) o;
            return new EqualsBuilder()
                    .append(setterName, option.setterName)
                    .append(getterName, option.getterName)
                    .append(clazz, option.clazz)
                    .isEquals();
        }

        /**
         * Reads an option from given config instance.</br>
         * Reading is done using reflections!
         *
         * @param config a config to read from.
         * @throws java.lang.RuntimeException if failed to read given config.
         */
        @NonNull
        public Object read(@NonNull Config config) {
            Object configInstance = getConfigInstance(config);
            Class configClass = configInstance.getClass();
            try {
                Method method = configClass.getDeclaredMethod(getterName);
                method.setAccessible(true);
                return method.invoke(configInstance);
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException("Failed to access " + clazz.getName() + "." + getterName + " method.");
            }
        }


        /**
         * Writes new value to the option to given config instance.</br>
         * Writing is done using reflections!
         *
         * @param config a config to write to.
         * @throws java.lang.RuntimeException if failed to read given config.
         */
        public void write(@NonNull Config config, @NonNull Context context,
                          @NonNull Object newValue, @Nullable OnConfigChangedListener listener) {
            Object configInstance = getConfigInstance(config);
            Class configClass = configInstance.getClass();
            try {
                Method method = configClass.getDeclaredMethod(setterName,
                        Context.class, clazz,
                        Config.OnConfigChangedListener.class);
                method.setAccessible(true);
                method.invoke(configInstance, context, newValue, listener);
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException("Failed to access " + clazz.getName() + "." + setterName + " method.");
            }
        }

        @NonNull
        protected Object getConfigInstance(Config config) {
            return config;
        }

    }

    private ArrayList<OnConfigChangedListener> mListeners;
    private Context mContext;

    // //////////////////////////////////////////
    // /////////// -- LISTENERS -- //////////////
    // //////////////////////////////////////////

    public interface OnConfigChangedListener {
        public void onConfigChanged(
                @NonNull Config config,
                @NonNull String key,
                @NonNull Object value);
    }

    public void registerListener(@NonNull OnConfigChangedListener listener) {
        mListeners.add(listener);
    }

    public void unregisterListener(@NonNull OnConfigChangedListener listener) {
        mListeners.remove(listener);
    }

    // //////////////////////////////////////////
    // ///////////// -- INIT -- /////////////////
    // //////////////////////////////////////////

    @NonNull
    public static synchronized Config getInstance() {
        if (sConfig == null) {
            sConfig = new Config();
        }
        return sConfig;
    }

    private Config() {
        mTriggers = new Triggers();
    }

    @Override
    public void onLowMemory() {
        // Clear hash-map; it will be recreated on #getHashMap().
        mHashMapRef.clear();
    }

    /**
     * Loads saved values from shared preferences.
     * This is called on {@link App app's} create.
     */
    void init(@NonNull Context context) {
        mListeners = new ArrayList<>(6);

        Resources res = context.getResources();
        SharedPreferences prefs = getSharedPreferences(context);

        // master switch
        mEnabled = prefs.getBoolean(KEY_ENABLED,
                res.getBoolean(R.bool.config_default_enabled));

        // interface
        mUiTheme = prefs.getString(KEY_UI_THEME,
                res.getString(R.string.config_default_ui_theme));

        // behavior
        mNotifyMinPriority = prefs.getInt(KEY_NOTIFY_MIN_PRIORITY,
                res.getInteger(R.integer.config_default_notify_min_priority));
        mNotifyDecayTime = prefs.getInt(KEY_NOTIFY_DECAY_TIME,
                res.getInteger(R.integer.config_default_notify_decay_time));
        mUxHideOnTouchOutside = prefs.getBoolean(KEY_UX_HIDE_ON_TOUCH_OUTSIDE,
                res.getBoolean(R.bool.config_default_ux_hide_on_touch_outside));
        mUxOnlyInFullscreen = prefs.getBoolean(KEY_UX_ONLY_IN_FULLSCREEN,
                res.getBoolean(R.bool.config_default_ux_only_in_fullscreen));

        // triggers
        mTrigHelpRead = prefs.getBoolean(KEY_TRIG_HELP_READ, false);
        mTrigPreviousVersion = prefs.getInt(KEY_TRIG_PREVIOUS_VERSION, 0);
    }

    static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE);
    }

    /**
     * You may get a context from here only on
     * {@link Config.OnConfigChangedListener#onConfigChanged(Config, String, Object) config change}.
     */
    public Context getContext() {
        return mContext;
    }

    @NonNull
    public HashMap<String, Option> getHashMap() {
        HashMap<String, Option> hashMap = mHashMapRef.get();
        if (hashMap == null) {
            hashMap = new HashMap<>();
            hashMap.put(KEY_ENABLED, new Option(
                    "setEnabled", "isEnabled", boolean.class));
            hashMap.put(KEY_UX_HIDE_ON_TOUCH_OUTSIDE, new Option(
                    "setHideOnTouchOutsideEnabled",
                    "isHideOnTouchOutsideEnabled", boolean.class));
            hashMap.put(KEY_UX_ONLY_IN_FULLSCREEN, new Option(
                    "setShownOnlyInFullscreen",
                    "isShownOnlyInFullscreen", boolean.class,
                    android.os.Build.VERSION_CODES.JELLY_BEAN_MR1));

            mHashMapRef = new SoftReference<>(hashMap);
        }
        return hashMap;
    }

    /**
     * Separated group of different internal triggers.
     */
    @NonNull
    public Triggers getTriggers() {
        return mTriggers;
    }

    private void notifyConfigChanged(String key, Object value, OnConfigChangedListener listener) {
        for (OnConfigChangedListener l : mListeners) {
            if (l == listener) continue;
            l.onConfigChanged(this, key, value);
        }
    }

    private void saveOption(Context context, String key, Object value,
                            OnConfigChangedListener listener, boolean changed) {
        if (!changed) {
            // Don't update preferences if this change is a lie.
            return;
        }

        if (Build.DEBUG) Log.d(TAG, "Writing \"" + key + "=" + value + "\" to config.");

        SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        } else throw new IllegalArgumentException("Unknown option type.");
        editor.apply();

        mContext = context;
        notifyConfigChanged(key, value, listener);
        mContext = null;
    }

    // //////////////////////////////////////////
    // ///////////// -- OPTIONS -- //////////////
    // //////////////////////////////////////////

    /**
     * Setter for the entire app enabler.
     */
    public boolean setEnabled(Context context, boolean enabled,
                              OnConfigChangedListener listener) {
        boolean changed = mEnabled != (mEnabled = enabled);

        if (!changed) {
            return true;
        } else if (enabled && !AccessUtils.isNotificationAccessGranted(context)) {
            // Do not allow enabling app while it
            // lacks some permissions!
            return false;
        }

        saveOption(context, KEY_ENABLED, enabled, listener, changed);
        ToggleReceiver.sendStateUpdate(ToggleReceiver.class, enabled, context);
        return true;
    }

    public void setTheme(Context context, String theme, OnConfigChangedListener listener) {
        boolean changed = !mUiTheme.equals(mUiTheme = theme);
        saveOption(context, KEY_UI_THEME, theme, listener, changed);
    }

    public void setNotifyMinPriority(Context context, int minPriority, OnConfigChangedListener listener) {
        boolean changed = mNotifyMinPriority != (mNotifyMinPriority = minPriority);
        saveOption(context, KEY_NOTIFY_MIN_PRIORITY, minPriority, listener, changed);
    }

    public void setNotifyDecayTime(Context context, int decayTime, OnConfigChangedListener listener) {
        boolean changed = mNotifyDecayTime != (mNotifyDecayTime = decayTime);
        saveOption(context, KEY_NOTIFY_DECAY_TIME, decayTime, listener, changed);
    }

    /**
     * @param enabled {@code true} to hide heads-up popup on touch outside.
     * @param listener a listener which will not be notified about this change.
     * @see #isHideOnTouchOutsideEnabled()
     */
    public void setHideOnTouchOutsideEnabled(Context context, boolean enabled,
                                         OnConfigChangedListener listener) {
        boolean changed = mUxHideOnTouchOutside != (mUxHideOnTouchOutside = enabled);
        saveOption(context, KEY_UX_HIDE_ON_TOUCH_OUTSIDE, enabled, listener, changed);
    }

    /**
     * @param enabled  {@code true} to allow showing popups only in fullscreen mode,
     *                 {@code false} to allow them anytime.
     * @param listener a listener which will not be notified about this change.
     * @see #isShownOnlyInFullscreen()
     */
    public void setShownOnlyInFullscreen(Context context, boolean enabled,
                                         OnConfigChangedListener listener) {
        boolean changed = mUxOnlyInFullscreen != (mUxOnlyInFullscreen = enabled);
        saveOption(context, KEY_UX_ONLY_IN_FULLSCREEN, enabled, listener, changed);
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public String getTheme() {
        return mUiTheme;
    }

    /**
     * @return minimal {@link android.app.Notification#priority} of notification to be shown.
     * @see #setNotifyMinPriority(Context, int, OnConfigChangedListener)
     * @see android.app.Notification#priority
     */
    public int getNotifyMinPriority() {
        return mNotifyMinPriority;
    }

    /**
     * @return how long notification should be shown (in millis).
     * @see #setNotifyDecayTime(Context, int, OnConfigChangedListener)
     */
    public int getNotifyDecayTime() {
        return mNotifyDecayTime;
    }

    /**
     * @return {@code true} if popups should hide on touch outside of it,
     * {@code false} otherwise.
     * @see #setHideOnTouchOutsideEnabled(Context, boolean, OnConfigChangedListener)
     */
    public boolean isHideOnTouchOutsideEnabled() {
        return mUxHideOnTouchOutside;
    }

    /**
     * @return {@code true} if popups should be shown only in fullscreen mode,
     * {@code false} if they can be shown anytime.
     * @see #setShownOnlyInFullscreen(Context, boolean, OnConfigChangedListener)
     */
    public boolean isShownOnlyInFullscreen() {
        return mUxOnlyInFullscreen;
    }

    /**
     * A class that syncs {@link android.preference.Preference} with its
     * value in config.
     *
     * @author Artem Chepurnoy
     */
    public static class Syncer {

        private final ArrayList<Group> mGroups;
        private final Context mContext;
        private final Config mConfig;

        private boolean mBroadcasting;
        private boolean mStarted;

        private final Preference.OnPreferenceChangeListener mPreferenceListener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (mBroadcasting) {
                    return true;
                }

                Group group = null;
                for (Group c : mGroups) {
                    if (preference == c.preference) {
                        group = c;
                        break;
                    }
                }

                assert group != null;

                group.option.write(mConfig, mContext, newValue, mConfigListener);
                return true;
            }
        };

        private final OnConfigChangedListener mConfigListener = new OnConfigChangedListener() {

            @Override
            public void onConfigChanged(@NonNull Config config, @NonNull String key,
                                        @NonNull Object value) {
                Group group = null;
                for (Group c : mGroups) {
                    if (key.equals(c.preference.getKey())) {
                        group = c;
                        break;
                    }
                }

                if (group == null) {
                    return;
                }

                setPreferenceValue(group, value);
            }

        };

        private void setPreferenceValue(@NonNull Group group, @NonNull Object value) {
            mBroadcasting = true;

            Option option = group.option;
            if (option.clazz.equals(boolean.class)) {
                CheckBoxPreference preference = (CheckBoxPreference) group.preference;
                preference.setChecked((boolean) value);
            }

            mBroadcasting = false;
        }

        /**
         * A class-merge of {@link android.preference.Preference}
         * and its {@link com.achep.acdisplay.Config.Option}.
         *
         * @author Artem Chepurnoy
         */
        private static class Group {
            final Preference preference;
            final Option option;

            public Group(@NonNull Config config, @NonNull Preference preference) {
                this.preference = preference;
                this.option = config.getHashMap().get(preference.getKey());
            }
        }

        public Syncer(@NonNull Context context, @NonNull Config config) {
            mGroups = new ArrayList<>(10);
            mContext = context;
            mConfig = config;
        }

        @NonNull
        public Syncer addPreference(@Nullable PreferenceScreen preferenceScreen,
                                    @NonNull Preference preference) {
            Group group;
            if (preference instanceof CheckBoxPreference) {
                group = new Group(mConfig, preference);
            } else {
                throw new IllegalArgumentException("Syncer only supports some kinds of Preferences");
            }

            // Remove preference from preference screen
            // if needed.
            if (preferenceScreen != null) {
                if (!Device.hasTargetApi(group.option.minSdkVersion)) {
                    preferenceScreen.removePreference(preference);
                    return this;
                }
            }

            mGroups.add(group);

            if (mStarted) {
                startListeningGroup(group);
            }

            return this;
        }

        /**
         * Updates all preferences and starts to listen to the changes.
         */
        public void start() {
            mStarted = true;
            mConfig.registerListener(mConfigListener);
            for (Group group : mGroups) {
                startListeningGroup(group);
            }
        }

        private void startListeningGroup(@NonNull Group group) {
            group.preference.setOnPreferenceChangeListener(mPreferenceListener);
            setPreferenceValue(group, group.option.read(mConfig));
        }

        /**
         * Stops to listen to the changes.
         */
        public void stop() {
            mStarted = false;
            mConfig.unregisterListener(mConfigListener);
            for (Group group : mGroups) {
                group.preference.setOnPreferenceChangeListener(null);
            }
        }
    }

    // //////////////////////////////////////////
    // //////////// -- TRIGGERS -- //////////////
    // //////////////////////////////////////////

    /**
     * Contains
     *
     * @author Artem Chepurnoy
     */
    public class Triggers {

        public void setPreviousVersion(Context context, int versionCode, OnConfigChangedListener listener) {
            boolean changed = mTrigPreviousVersion != (mTrigPreviousVersion = versionCode);
            saveOption(context, KEY_TRIG_PREVIOUS_VERSION, versionCode, listener, changed);
        }

        public void setHelpRead(Context context, boolean isRead, OnConfigChangedListener listener) {
            boolean changed = mTrigHelpRead != (mTrigHelpRead = isRead);
            saveOption(context, KEY_TRIG_HELP_READ, isRead, listener, changed);
        }

        /**
         * As set by {@link com.achep.acdisplay.activities.MainActivity}, it returns version
         * code of previously installed AcDisplay, {@code 0} if first install.
         *
         * @return version code of previously installed AcDisplay, {@code 0} if first install.
         * @see #setPreviousVersion(android.content.Context, int, Config.OnConfigChangedListener)
         */
        public int getPreviousVersion() {
            return mTrigPreviousVersion;
        }

        /**
         * @return {@code true} if {@link com.achep.acdisplay.fragments.HelpDialog} been read, {@code false} otherwise
         * @see #setHelpRead(android.content.Context, boolean, Config.OnConfigChangedListener)
         */
        public boolean isHelpRead() {
            return mTrigHelpRead;
        }

    }

}
