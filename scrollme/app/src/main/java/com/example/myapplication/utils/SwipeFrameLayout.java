package com.example.myapplication.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

public class SwipeFrameLayout extends FrameLayout {

    public interface OnHorizontalSwipeListener {
        void onSwipeProgress(float deltaX);
        void onSwipeEnd();
    }

    private float startX;
    private float startY;

    private boolean horizontalSwipe = false;

    private final int touchSlop;

    private OnHorizontalSwipeListener listener;

    public SwipeFrameLayout(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public SwipeFrameLayout(
            Context context,
            @Nullable AttributeSet attrs) {

        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public SwipeFrameLayout(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr) {

        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setOnHorizontalSwipeListener(
            OnHorizontalSwipeListener listener) {

        this.listener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:

                startX = event.getRawX();
                startY = event.getRawY();

                horizontalSwipe = false;

                // Do not intercept DOWN.
                // This allows Buttons, EditTexts and video
                // controls to receive normal touch events.
                return false;

            case MotionEvent.ACTION_MOVE:

                float dx = event.getRawX() - startX;
                float dy = event.getRawY() - startY;

                if (!horizontalSwipe
                        && Math.abs(dx) > touchSlop
                        && Math.abs(dx) > Math.abs(dy)) {

                    horizontalSwipe = true;

                    // A real horizontal swipe has started.
                    // Intercept it so the panels can move.
                    return true;
                }

                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:

                horizontalSwipe = false;

                break;
        }

        return horizontalSwipe;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (!horizontalSwipe) {
            return false;
        }

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_MOVE:

                float deltaX =
                        event.getRawX() - startX;

                if (listener != null) {
                    listener.onSwipeProgress(deltaX);
                }

                return true;

            case MotionEvent.ACTION_UP:

                if (listener != null) {
                    listener.onSwipeEnd();
                }

                horizontalSwipe = false;

                return true;

            case MotionEvent.ACTION_CANCEL:

                if (listener != null) {
                    listener.onSwipeEnd();
                }

                horizontalSwipe = false;

                return true;
        }

        return true;
    }
}