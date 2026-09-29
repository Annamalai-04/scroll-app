package com.example.myapplication.utils;

import android.app.Activity;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.Player;

/**
 * Controls screen/system UI for video playback.
 * - While video is playing: screen stays awake.
 * - While paused/stopped: normal phone screen timeout applies.
 * - Status and navigation/system bars stay immersive and can appear transiently by swipe.
 */
public class ScreenController {

    private final Activity activity;
    private Player attachedPlayer;

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            updateForPlayback(isPlaying);
        }
    };

    public ScreenController(Activity activity) {
        this.activity = activity;
    }

    public void applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(
                        activity.getWindow(),
                        activity.getWindow().getDecorView());

        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    public void attachPlayer(Player player) {
        detachPlayer();
        attachedPlayer = player;
        if (attachedPlayer != null) {
            attachedPlayer.addListener(playerListener);
            updateForPlayback(attachedPlayer.isPlaying());
        }
    }

    public void detachPlayer() {
        if (attachedPlayer != null) {
            attachedPlayer.removeListener(playerListener);
            attachedPlayer = null;
        }
        allowNormalScreenTimeout();
    }

    public void updateForPlayback(boolean isPlaying) {
        if (isPlaying) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            allowNormalScreenTimeout();
        }
    }

    public void allowNormalScreenTimeout() {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            applyImmersiveMode();
        }
    }
}
