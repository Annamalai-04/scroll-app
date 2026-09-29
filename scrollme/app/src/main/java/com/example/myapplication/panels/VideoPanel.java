package com.example.myapplication.panels;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;

import com.example.myapplication.R;

/**
 * One completely isolated video page.
 *
 * Each ViewPager2 item gets its own VideoPanel and therefore its own
 * PlayerView/TextureView surface. The feed still uses one shared ExoPlayer;
 * only the rendering surface changes from page to page.
 */
public class VideoPanel extends FrameLayout {

    private PlayerView playerView;
    private View bufferingOverlay;
    private Button likeButton;

    private TextView resumingText;

    public VideoPanel(@NonNull Context context) {
        super(context);
        init(context);
    }

    public VideoPanel(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public VideoPanel(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setBackgroundColor(Color.BLACK);
        LayoutInflater.from(context).inflate(R.layout.video_panel, this, true);

        playerView = findViewById(R.id.playerView);
        bufferingOverlay = findViewById(R.id.bufferingOverlay);
        likeButton = findViewById(R.id.likeButton);
        resumingText = findViewById(R.id.resumingText);
        playerView.setKeepContentOnPlayerReset(false);
        playerView.setShutterBackgroundColor(Color.BLACK);
        hideBuffering();
    }

    public PlayerView getPlayerView() {
        return playerView;
    }

    public Button getLikeButton() {
        return likeButton;
    }
    public void showResuming() {
        if (resumingText != null) {
            resumingText.setVisibility(VISIBLE);
            resumingText.bringToFront();
        }
    }
    public void hideResuming() {
        if (resumingText != null) {
            resumingText.setVisibility(GONE);
        }
    }
    /**
     * Detach the shared player and hide this page's surface while it is not
     * the selected page. This prevents a recycled page from showing an old
     * video frame before the new video renders.
     */
    public void detachPlayer() {
        playerView.setPlayer(null);
        playerView.setVisibility(INVISIBLE);
        hideBuffering();
    }

    /**
     * Clear this page before a new MediaItem/MediaSource is attached.
     */
    public void prepareForNewVideo() {
        playerView.setPlayer(null);
        playerView.setKeepContentOnPlayerReset(false);
        playerView.setShutterBackgroundColor(Color.BLACK);
        playerView.setVisibility(INVISIBLE);
        hideBuffering();
    }

    /**
     * Attach the shared ExoPlayer to this page's private rendering surface.
     */
    public void attachPlayer(Player player) {
        playerView.setKeepContentOnPlayerReset(false);
        playerView.setShutterBackgroundColor(Color.BLACK);
        playerView.setVisibility(VISIBLE);
        playerView.setPlayer(player);
    }

    public void showBuffering() {
        if (bufferingOverlay != null) {
            bufferingOverlay.setVisibility(VISIBLE);
            bufferingOverlay.bringToFront();
            if (likeButton != null) {
                likeButton.bringToFront();
            }
        }
    }

    public void hideBuffering() {
        if (bufferingOverlay != null) {
            bufferingOverlay.setVisibility(GONE);
        }
    }
}
