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

package com.achep.acdisplay.services;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.RemoteController;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.achep.acdisplay.App;
import com.achep.acdisplay.Config;
import com.achep.acdisplay.Device;
import com.achep.headsup.R;
import com.achep.acdisplay.notifications.NotificationPresenter;
import com.achep.acdisplay.notifications.OpenNotification;

/**
 * Created by achep on 07.06.14.
 *
 * @author Artem Chepurnoy
 */
@SuppressLint("NewApi")
public class MediaService extends NotificationListenerService {

    private static final String TAG = "MediaService";

    public static MediaService sService;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    public IBinder onBind(Intent intent) {
        switch (intent.getAction()) {
            default:
                sService = this;

                // What is the idea of init notification?
                // Well the main goal is to access #getActiveNotifications()
                // what seems to be not possible without dirty and buggy
                // workarounds.
                NotificationPresenter.getInstance().tryStartInitProcess();
                NotificationPresenter.getInstance().setHeadsUpEnabled(
                                getApplicationContext(),
                                Config.getInstance().isEnabled());

                return super.onBind(intent);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        switch (intent.getAction()) {
            default:
                sService = null;

                NotificationPresenter
                        .getInstance()
                        .setHeadsUpEnabled(
                                getApplicationContext(),
                                false);
                break;
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification notification) {
        rockNotification(notification, true);
    }

    @Override
    public void onNotificationRemoved(final StatusBarNotification notification) {
        rockNotification(notification, false);
    }

    private void rockNotification(final StatusBarNotification sbn, final boolean post) {
        final StatusBarNotification[] activeNotifies = getActiveNotifications();
        runOnMainLooper(new Runnable() {
            @Override
            public void run() {
                OpenNotification n = OpenNotification.newInstance(sbn);
                NotificationPresenter np = NotificationPresenter.getInstance();

                if (post) {
                    np.postNotification(MediaService.this, n, 0);
                } else {
                    np.removeNotification(n);
                }

                np.tryInit(MediaService.this, sbn, activeNotifies);
            }
        });
    }

    private void runOnMainLooper(Runnable runnable) {
        mHandler.post(runnable);
    }

}
