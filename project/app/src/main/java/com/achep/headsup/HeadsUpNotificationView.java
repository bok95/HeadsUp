package com.achep.headsup;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.achep.acdisplay.Device;
import com.achep.acdisplay.notifications.OpenNotification;
import com.achep.acdisplay.widgets.NotificationWidget;

import dreamers.graphics.RippleDrawable;

/**
 * Created by Artem Chepurnoy on 16.09.2014.
 */
public class HeadsUpNotificationView extends NotificationWidget implements
        ExpandHelper.Callback, SwipeHelper.Callback {

    private static final String TAG = "HeadsUpNotificationView";

    private final int mRippleColor;
    private final boolean mRipple;

    private SwipeHelper mSwipeHelper;
    private ExpandHelper mExpandHelper;

    private HeadsUpManager mManager;

    public HeadsUpNotificationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HeadsUpNotificationView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.HeadsUpNotificationView);
        mRippleColor = a.getColor(R.styleable.HeadsUpNotificationView_rippleColor, 0);
        mRipple = a.getBoolean(R.styleable.HeadsUpNotificationView_ripple, false);
        a.recycle();
    }

    public void setHeadsUpManager(HeadsUpManager manager) {
        mManager = manager;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        if (mRipple) {
            View content = findViewById(R.id.content);
            RippleDrawable.createRipple(content, mRippleColor);
        }
    }

    @Override
    protected View initActionView(View view) {
        if (mRipple) {
            RippleDrawable.createRipple(view, mRippleColor);
        }
        return super.initActionView(view);
    }

    @Override
    public void onAttachedToWindow() {
        float densityScale = getResources().getDisplayMetrics().density;
        float pagingTouchSlop = ViewConfiguration.get(getContext()).getScaledPagingTouchSlop();
        mSwipeHelper = new SwipeHelper(SwipeHelper.X, this, densityScale, pagingTouchSlop);

        int minHeight = getResources().getDimensionPixelSize(R.dimen.notification_row_min_height);
        int maxHeight = getResources().getDimensionPixelSize(R.dimen.notification_row_max_height);
        mExpandHelper = new ExpandHelper(getContext(), this, minHeight, maxHeight);
        mExpandHelper.setForceOneFinger(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return mSwipeHelper.onInterceptTouchEvent(event)
                || mExpandHelper.onInterceptTouchEvent(event)
                || super.onInterceptTouchEvent(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_OUTSIDE:
                mManager.hideHeadsUp();
                return true;
            default:
                // Do not let notification to be timed-out while
                // we are touching it.
                mManager.resetHeadsUpDecayTimer(this);

                // Translate touch event too to correspond with
                // view's translation changes and prevent lags
                // while swiping.
                final MotionEvent ev = MotionEvent.obtainNoHistory(event);
                ev.offsetLocation(getTranslationX(), getTranslationY());
                boolean handled = mSwipeHelper.onTouchEvent(ev) || mExpandHelper.onTouchEvent(ev);
                ev.recycle();

                return handled || super.onTouchEvent(event);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        float densityScale = getResources().getDisplayMetrics().density;
        mSwipeHelper.setDensityScale(densityScale);
        float pagingTouchSlop = ViewConfiguration.get(getContext()).getScaledPagingTouchSlop();
        mSwipeHelper.setPagingTouchSlop(pagingTouchSlop);
    }

    //-- EXPAND HELPER'S METHODS ----------------------------------------------

    @Override
    public View getChildAtRawPosition(float x, float y) {
        return null;
    }

    @Override
    public View getChildAtPosition(float x, float y) {
        return null;
    }

    @Override
    public boolean canChildBeExpanded(View v) {
        return true;
    }

    @Override
    public void setUserExpandedChild(View v, boolean userExpanded) {

    }

    @Override
    public void setUserLockedChild(View v, boolean userLocked) {

    }

    //-- SWIPE HELPER'S METHODS -----------------------------------------------

    @Override
    public View getChildAtPosition(MotionEvent ev) {
        return this;
    }

    @Override
    public View getChildContentView(View v) {
        return this;
    }

    @Override
    public boolean canChildBeDismissed(View v) {
        return getNotification().isDismissible();
    }

    @Override
    public void onBeginDrag(View v) {
        requestDisallowInterceptTouchEvent(true);
    }

    @Override
    public void onChildDismissed(View v) {
        mManager.dismiss(this);
    }

    @Override
    public void onDragCancelled(View v) {
        setAlpha(1f); // sometimes this isn't quite reset
    }
}
