package com.achep.headsup;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.achep.acdisplay.App;
import com.achep.acdisplay.Config;
import com.achep.acdisplay.Device;
import com.achep.acdisplay.animations.AnimationListenerAdapter;
import com.achep.acdisplay.compat.TransitionManager;
import com.achep.acdisplay.notifications.NotificationPresenter;
import com.achep.acdisplay.notifications.NotificationUtils;
import com.achep.acdisplay.notifications.OpenNotification;
import com.achep.acdisplay.receiver.Receiver;
import com.achep.acdisplay.utils.PendingIntentUtils;
import com.achep.acdisplay.utils.PowerUtils;
import com.achep.acdisplay.widgets.NotificationWidget;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Class that manages notifications and shows them in popups.
 * In best scenario this class should be inside of the {@link android.app.Service}.
 *
 * @author Artem Chepurnoy
 */
public class HeadsUpManager implements
        NotificationPresenter.OnNotificationListChangedListener {

    private static final String TAG = "HeadsUpManager";

    /**
     * Represents how long notification will be shown.
     */
    private static final long DURATION = 5000; // ms.

    private final Config mConfig;

    private Animation mEnterAnimation;
    private Animation mExitAnimation;

    private HeadsUpView mRootView;
    private ViewGroup mContainer;

    private ArrayList<NotificationWidget> mWidgetList;
    private HashMap<NotificationWidget, Runnable> mWidgetDecayMap;
    private Handler mHandler;

    private Context mContext;
    private boolean mAttached;
    private boolean mIgnoreShowing;

    private class DecayRunnable implements Runnable {

        private final NotificationWidget widget;

        public DecayRunnable(NotificationWidget widget) {
            this.widget = widget;
        }

        @Override
        public void run() {
            mWidgetList.remove(widget);
            mWidgetDecayMap.remove(widget);

            // Detach view from window, if there's
            // no content.
            if (mContainer.getChildCount() == 1) {
                hideHeadsUp();
            } else {
                mContainer.removeView(widget);
            }
        }
    }

    private BroadcastReceiver mReceiver =
            new Receiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    super.onReceive(context, intent);
                    switch (intent.getAction()) {
                        case App.ACTION_EAT_HOME_PRESS_START:
                            mIgnoreShowing = true;
                            detach();
                            break;
                        case App.ACTION_EAT_HOME_PRESS_STOP:
                            mIgnoreShowing = false;
                            break;
                    }
                }
            };

    public HeadsUpManager() {
        mConfig = Config.getInstance();

        mWidgetList = new ArrayList<>();
        mWidgetDecayMap = new HashMap<>();
        mHandler = new Handler();
    }

    public void start(@NonNull Context context) {
        mContext = context;

        // Load animations.
        mEnterAnimation = AnimationUtils.loadAnimation(context, R.anim.heads_up_enter);
        mExitAnimation = AnimationUtils.loadAnimation(context, R.anim.heads_up_exit);
        mExitAnimation.setAnimationListener(new AnimationListenerAdapter() {
            @Override
            public void onAnimationEnd(Animation animation) {
                super.onAnimationEnd(animation);
                detach();
            }
        });

        // Create root layouts.
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mRootView = (HeadsUpView) inflater.inflate(R.layout.heads_up, null, false);
        mRootView.setHeadsUpManager(this);
        mContainer = (ViewGroup) mRootView.findViewById(R.id.content);

        IntentFilter filter = new IntentFilter();
        filter.addAction(App.ACTION_EAT_HOME_PRESS_START);
        filter.addAction(App.ACTION_EAT_HOME_PRESS_STOP);
        mContext.registerReceiver(mReceiver, filter);
        NotificationPresenter.getInstance().registerListener(this);
    }

    public void stop() {
        mContext.unregisterReceiver(mReceiver);
        NotificationPresenter.getInstance().unregisterListener(this);
        detach();

        mContext = null;
        mRootView = null;
        mContainer = null;
    }

    public Config getConfig() {
        return mConfig;
    }

    @Override
    public void onNotificationListChanged(
            @NonNull NotificationPresenter np,
            @NonNull OpenNotification osbn, int event) {
        if (mIgnoreShowing || !PowerUtils.isScreenOn(mContext)) {
            return;
        }

        switch (event) {
            case NotificationPresenter.EVENT_POSTED:
                if (mConfig.isShownOnlyInFullscreen() && Device.hasJellyBeanMR1Api()) {
                    // TODO: Write a detector for fullscreen mode.
                    DisplayMetrics metrics = new DisplayMetrics();
                    DisplayMetrics metricsReal = new DisplayMetrics();

                    WindowManager wm = (WindowManager) mContext
                            .getSystemService(Context.WINDOW_SERVICE);
                    wm.getDefaultDisplay().getMetrics(metrics);
                    wm.getDefaultDisplay().getRealMetrics(metricsReal);

                    if (metrics.heightPixels != metricsReal.heightPixels) {
                        return;
                    }
                }
            case NotificationPresenter.EVENT_CHANGED:
                mContainer.clearAnimation();
                break;
        }

        NotificationWidget widget;

        switch (event) {
            case NotificationPresenter.EVENT_POSTED:
                postNotification(osbn);
                break;
            case NotificationPresenter.EVENT_CHANGED:
                int i = indexOf(osbn);
                if (i == -1) {
                    postNotification(osbn);
                } else {
                    TransitionManager.beginDelayedTransition(mContainer);

                    widget = mWidgetList.get(i);
                    widget.setNotification(osbn);

                    // Delay dismissing this notification.
                    Runnable runnable = mWidgetDecayMap.get(widget);
                    mHandler.removeCallbacks(runnable);
                    mHandler.postDelayed(runnable, DURATION);
                }
                break;
            case NotificationPresenter.EVENT_REMOVED:
                i = indexOf(osbn);
                if (i != -1) {
                    widget = mWidgetList.get(i);
                    removeImmediately(widget);
                }
                break;
            case NotificationPresenter.EVENT_BATH:
                // Fortunately there's no need to support bath
                // changing list of notification.
                break;
        }
    }

    /**
     * Dismisses given {@link NotificationWidget notification widget} and its notification.
     *
     * @param widget a widget to be dismissed.
     * @see OpenNotification#dismiss()
     */
    public void dismiss(@NonNull NotificationWidget widget) {
        widget.getNotification().dismiss();
    }

    /**
     * @return the position of given {@link com.achep.acdisplay.notifications.OpenNotification} in
     * {@link #mWidgetList list}, or {@code -1} if not found.
     */
    public int indexOf(@NonNull OpenNotification n) {
        final int size = mWidgetList.size();
        for (int i = 0; i < size; i++) {
            OpenNotification n2 = mWidgetList.get(i).getNotification();
            if (NotificationUtils.hasIdenticalIds(n, n2)) {
                return i;
            }
        }
        return -1;
    }

    public void hideHeadsUp() {
        mContainer.startAnimation(mExitAnimation);
    }

    public void resetHeadsUpDecayTimer(HeadsUpNotificationView widget) {
        Runnable runnable = mWidgetDecayMap.get(widget);

        if (runnable != null) {
            mHandler.removeCallbacks(runnable);
            mHandler.postDelayed(runnable, mConfig.getNotifyDecayTime());
        }
    }

    private void removeImmediately(@NonNull NotificationWidget widget) {
        Runnable runnable = mWidgetDecayMap.get(widget);

        if (runnable != null) {
            // Run dismissing runnable immediately.
            mHandler.removeCallbacks(runnable);
            runnable.run();
        }
    }

    /**
     * Posts new {@link com.achep.acdisplay.widgets.NotificationWidget widget} with
     * current notification and starts dismissing timer.
     *
     * @param n the notification to show
     */
    private void postNotification(@NonNull OpenNotification n) {
        // Get selected theme.
        final String theme = Config.getInstance().getTheme();
        final int themeRes = theme.equals("dark")
                ? R.style.HeadsUp_Theme_Dark
                : R.style.HeadsUp_Theme;

        // Create a context with selected style.
        Context context = new ContextThemeWrapper(mContext, themeRes);

        // Get layout resource.
        TypedArray typedArray = context.obtainStyledAttributes(
                new int[] {R.styleable.Theme_headsUpNotificationLayout});
        final int layoutRes = typedArray.getInt(0, R.layout.heads_up_notification);
        typedArray.recycle();
        typedArray = null;

        // Inflate notification widget.
        final LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final HeadsUpNotificationView widget = (HeadsUpNotificationView) inflater
                .inflate(layoutRes, mContainer, false);

        // Setup widget
        widget.setHeadsUpManager(this);
        widget.setNotification(n);
        widget.setOnClickListener(new NotificationWidget.OnClickListener() {
            @Override
            public void onClick(View v) {
                widget.getNotification().click();
            }

            @Override
            public void onActionButtonClick(View v, PendingIntent intent) {
                PendingIntentUtils.sendPendingIntent(intent);
                widget.getNotification().dismiss();
            }
        });

        mContainer.addView(widget);
        mWidgetList.add(widget);
        widget.setAlpha(0);
        widget.setRotationX(-15);
        widget.animate().alpha(1).rotationX(0).setDuration(300);

        // Attaches heads-up to window.
        attach();

        // Timed-out runnable.
        Runnable runnable = new DecayRunnable(widget);
        mWidgetDecayMap.put(widget, runnable);
        mHandler.postDelayed(runnable, mConfig.getNotifyDecayTime());
    }

    private void attach() {
        mContainer.clearAnimation();
        if (mAttached & (mAttached = true)) return;

        WindowManager wm = (WindowManager) mContext
                .getSystemService(Context.WINDOW_SERVICE);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PRIORITY_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        wm.addView(mRootView, lp);

        mContainer.startAnimation(mEnterAnimation);
    }

    private void detach() {
        if (!mAttached & !(mAttached = false)) return;

        WindowManager wm = (WindowManager) mContext
                .getSystemService(Context.WINDOW_SERVICE);
        wm.removeView(mRootView);

        // Clean everything.
        mHandler.removeCallbacksAndMessages(null);
        mContainer.clearAnimation();
        mContainer.removeAllViews();
        mWidgetList.clear();
        mWidgetDecayMap.clear();
    }

}
