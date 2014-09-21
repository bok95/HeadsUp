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

    private final int mTouchSensitivityDelay;
    private long mStartTouchTime;

    private HeadsUpManager mManager;

    public HeadsUpView(Context context) {
        this(context, null);
    }

    public HeadsUpView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HeadsUpView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mTouchSensitivityDelay = getResources().getInteger(R.integer.heads_up_sensitivity_delay);
    }

    public void setHeadsUpManager(HeadsUpManager manager) {
        mManager = manager;
    }

    /**
     * Calling this method means that the view won't do any interactivity
     * such as touches for some {@link com.achep.headsup.R.integer#heads_up_sensitivity_delay time}.
     */
    public void preventInstantInteractivity() {
        mStartTouchTime = System.currentTimeMillis() + mTouchSensitivityDelay;
    }

    /**
     * @return {@code true} if this touch should be ignored
     * (mainly because of {@link #mTouchSensitivityDelay touch sensitivity delay}),
     * {@code false} otherwise.
     */
    private boolean ignoreAnyInteractivity() {
        return System.currentTimeMillis() < mStartTouchTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return ignoreAnyInteractivity() || super.onInterceptTouchEvent(event);
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

}
