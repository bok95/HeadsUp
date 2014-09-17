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

package com.achep.acdisplay.services.headsup;

import android.content.Context;
import android.support.annotation.NonNull;

import com.achep.acdisplay.Config;
import com.achep.headsup.R;
import com.achep.acdisplay.services.BathService;
import com.achep.acdisplay.utils.PowerUtils;

/**
 * Created by achep on 24.08.14.
 */
public class HeadsUpService extends BathService.ChildService {

    private HeadsUpManager mHeadsUpManager;

    /**
     * Starts or stops this service as required by settings and device's state.
     */
    public static void handleState(@NonNull Context context) {
        Config config = Config.getInstance();

        boolean onlyWhileChangingOption = !config.isEnabledOnlyWhileCharging()
                || PowerUtils.isPlugged(context);

        if (config.isEnabled()
                && config.isHeadsUpEnabled()
                && onlyWhileChangingOption) {
            BathService.startService(context, HeadsUpService.class);
        } else {
            BathService.stopService(context, HeadsUpService.class);
        }
    }

    @Override
    public void onCreate() {
        mHeadsUpManager = new HeadsUpManager(getContext());
        mHeadsUpManager.start();
    }

    @Override
    public void onDestroy() {
        mHeadsUpManager.stop();
    }

    @Override
    public String getLabel() {
        return getContext().getString(R.string.service_bath_headsup);
    }
}
