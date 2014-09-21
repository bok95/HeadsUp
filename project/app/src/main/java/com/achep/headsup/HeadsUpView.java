package com.achep.headsup;

import android.content.Context;
import android.content.res.Configuration;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Created by Artem Chepurnoy on 16.09.2014.
 */
public class HeadsUpView extends FrameLayout {

    private HeadsUpManager mManager;

    public HeadsUpView(Context context) {
        super(context);
    }

    public HeadsUpView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HeadsUpView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setHeadsUpManager(HeadsUpManager manager) {
        mManager = manager;
    }

    /**
     * @return {@code true} if this touch should be ignored
     * (mainly because of {@link #mTouchSensitivityDelay touch sensitivity delay}),
     * {@code false} otherwise.
     */
    private boolean ignoreAnyInteractivity() {
        return false;//System.currentTimeMillis() < mStartTouchTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (ignoreAnyInteractivity()) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_OUTSIDE:
                if (mManager.getConfig().isHideOnTouchOutsideEnabled()) {
                    mManager.hideHeadsUp();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (!ignoreAnyInteractivity()) {
            boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_BACK:
                    if (!down && !event.isCanceled()) {
                        mManager.hideHeadsUp();
                    }
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

}
