package com.example.myapplication.components;

import android.content.Context;
import android.graphics.Color;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.models.Video;
import com.example.myapplication.panels.VideoPanel;
import com.example.myapplication.services.SessionManager;
import com.example.myapplication.utils.ScreenController;
import androidx.media3.exoplayer.source.MediaSource;


import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Collection;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

@OptIn(markerClass = UnstableApi.class)
public class VideoAdapter
        extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    private final Context context;
    private final List<Video> videos;

    // One player for the feed
    private final ExoPlayer player;
    private final VideoPreloadManager preloadManager;

    private RecyclerView recyclerView;

    private int currentPosition = 0;
    private boolean firstVideoStarted = false;
    private VideoGestureListener videoGestureListener;

    private final Handler historyHandler =
            new Handler(Looper.getMainLooper());

    // Save the liked video's playback position to backend every 5 seconds.
    private static final long HISTORY_SAVE_INTERVAL_MS = 5000L;

    private final Runnable historyProgressRunnable =
            new Runnable() {
                @Override
                public void run() {

                    if (!SessionManager.isLoggedIn(context)
                            || currentPosition < 0
                            || currentPosition >= videos.size()) {
                        return;
                    }

                    String videoId =
                            videos.get(currentPosition).getVideoId();

                    if (!likedVideoIds.contains(videoId)) {
                        return;
                    }

                    // Save only while the current video is actually playing.
                    // A final save is still done when the video is switched.
                    if (player.isPlaying()) {
                        saveCurrentVideoProgress();
                    }

                    historyHandler.postDelayed(
                            this,
                            HISTORY_SAVE_INTERVAL_MS
                    );
                }
            };

    // A video is eligible for history only after the user clicks LIKE.
    // This state is kept for the current app session.
    private final Set<String> likedVideoIds = new HashSet<>();

    private final java.util.Map<String, Long> historyWatchedMs =
            new java.util.HashMap<>();

    // True only when Media3 actually repeats the current video.
    // This allows history watched time to reset to 0:00 on a real repeat,
    // without treating a normal position such as 0:23 as a completed video.
    private boolean historyRepeatResetPending = false;

    // Mirrors the authenticated user's persistent history video IDs.
    // The UI LIKE state is derived from this set after history is loaded.
    private HistoryDeleteListener historyDeleteListener;

    // History resume requested before ViewPager2 changes page.
    private int pendingHistoryPosition = -1;
    private String pendingHistoryWatched = null;
    private double pendingHistoryPercent = 0;

    private HistoryListener historyListener;

    // Controls immersive system bars and keeps the screen awake only while video plays.
    private ScreenController screenController;

    // Notifies MainActivity when a History -> WATCH video is actually ready.
    private HistoryPlaybackReadyListener historyPlaybackReadyListener;
    private boolean historyResumePending = false;


    public interface HistoryListener {
        void onHistoryReady(Video video, double percent, String watched, String length);
    }

    public interface VideoGestureListener {
        void onVideoDoubleTap();
    }

    public interface HistoryPlaybackReadyListener {
        void onHistoryPlaybackReady();
        void onHistoryPlaybackFailed();
    }

    public interface HistoryDeleteListener {
        void onHistoryDeleteRequested(String videoId);
    }

    public void setHistoryListener(HistoryListener listener) {
        this.historyListener = listener;
    }

    public void setHistoryPlaybackReadyListener(
            HistoryPlaybackReadyListener listener) {
        this.historyPlaybackReadyListener = listener;
    }

    public void setHistoryDeleteListener(
            HistoryDeleteListener listener) {
        this.historyDeleteListener = listener;
    }

    // Sync the LIKE icons with the user's persistent history array.
    // Called by MainActivity whenever /api/history is loaded.
    public void setHistoryVideoIds(Collection<String> historyVideoIds) {

        likedVideoIds.clear();

        if (historyVideoIds != null) {
            likedVideoIds.addAll(historyVideoIds);
        }

        notifyDataSetChanged();
        refreshCurrentLikeButton();
    }


    public void setHistoryWatchedPositions(
            java.util.Map<String, Long> watchedPositions) {

        historyWatchedMs.clear();

        if (watchedPositions != null) {
            historyWatchedMs.putAll(watchedPositions);
        }
    }
    public void addHistoryVideoId(String videoId) {
        if (videoId != null) {
            likedVideoIds.add(videoId);
        }
        refreshCurrentLikeButton();
    }

    public void removeHistoryVideoId(String videoId) {

        if (videoId == null) {
            return;
        }

        likedVideoIds.remove(videoId);

        // Also forget the old watched position.
        historyWatchedMs.remove(videoId);

        // Re-bind all visible video items.
        notifyDataSetChanged();

        refreshCurrentLikeButton();
    }

    // Clear ALL account-specific history/like state when the user signs out.
    // This is important because the same VideoAdapter instance can remain
    // alive after logout. A signed-out user must never see another user's
    // pink LIKE state or be treated as a history video.

    public void clearUserHistoryState() {

        // Remove all LIKE/HISTORY information of the old user
        likedVideoIds.clear();
        historyWatchedMs.clear();
        // Stop history saving
        historyHandler.removeCallbacksAndMessages(null);

        // Clear pending history state
        pendingHistoryPosition = -1;
        pendingHistoryWatched = null;
        pendingHistoryPercent = 0;
        historyResumePending = false;

        // IMPORTANT:
        // Re-bind every visible ViewHolder.
        // onBindViewHolder() will see that the user is logged out
        // and will set every heart to ♡ white.
        notifyDataSetChanged();

        // Also immediately refresh the current button if available.
        refreshCurrentLikeButton();
    }

    // Returns true only when the current logged-in user's history contains
    // this video. Used by MainActivity before showing any resume UI.
    public boolean isHistoryVideo(String videoId) {

        if (!SessionManager.isLoggedIn(context)) {
            return false;
        }

        return videoId != null
                && likedVideoIds.contains(videoId);
    }

    private void refreshCurrentLikeButton() {

        if (recyclerView == null
                || currentPosition < 0
                || currentPosition >= videos.size()) {
            return;
        }

        VideoViewHolder holder =
                (VideoViewHolder) recyclerView
                        .findViewHolderForAdapterPosition(currentPosition);

        if (holder == null || holder.likeButton == null) {
            return;
        }

        String videoId = videos.get(currentPosition).getVideoId();
        boolean liked =
                SessionManager.isLoggedIn(context)
                        && likedVideoIds.contains(videoId);

        holder.likeButton.setText(liked ? "♥" : "♡");
        holder.likeButton.setTextColor(
                liked
                        ? Color.rgb(255, 20, 147)
                        : Color.WHITE
        );
    }

    public void setScreenController(ScreenController screenController) {
        this.screenController = screenController;
        if (this.screenController != null) {
            this.screenController.attachPlayer(player);
        }
    }

    public VideoAdapter(
            Context context,
            List<Video> videos) {

        this.context = context;
        this.videos = videos;

        // Media3 DefaultPreloadManager creates the real player and the
        // preloaded MediaSources from the same builder, so the player can
        // immediately consume data that was prepared for adjacent items.
        preloadManager = new VideoPreloadManager(
                context,
                videos
        );

        player = preloadManager.buildPlaybackPlayer();
        player.setVolume(1.0f);

        player.setTrackSelectionParameters(
                player.getTrackSelectionParameters()
                        .buildUpon()
                        .setMaxVideoSize(854, 480)
                        .setTrackTypeDisabled(
                                androidx.media3.common.C.TRACK_TYPE_AUDIO,
                                false
                        )
                        .build()
        );
        player.addListener(new Player.Listener() {

            @Override
            public void onTracksChanged(
                    androidx.media3.common.Tracks tracks) {

                Log.d(
                        "AUDIO_DEBUG",
                        "Tracks = " + tracks
                );
            }
        });
        player.addListener(new Player.Listener() {

            @Override
            public void onTimelineChanged(
                    androidx.media3.common.Timeline timeline,
                    int reason) {

                androidx.media3.common.Timeline.Window window =
                        new androidx.media3.common.Timeline.Window();

                if (!timeline.isEmpty()) {

                    timeline.getWindow(0, window);

                    Log.d(
                            "TIMELINE_DEBUG",
                            "videoId="
                                    + videos.get(currentPosition).getVideoId()
                                    + ", durationMs="
                                    + window.getDurationMs()
                                    + ", windowCount="
                                    + timeline.getWindowCount()
                    );
                }
            }
        });
        // Show a loader only on the currently selected page while Media3 buffers.
        player.addListener(new Player.Listener() {

            @Override
            public void onPositionDiscontinuity(
                    Player.PositionInfo oldPosition,
                    Player.PositionInfo newPosition,
                    int reason) {

                // With REPEAT_MODE_ONE, Media3 reports AUTO_TRANSITION
                // when the same video wraps from the end back to 0:00.
                // Only this real repeat is allowed to reset history.
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
                        && player.getRepeatMode() == Player.REPEAT_MODE_ONE) {

                    historyRepeatResetPending = true;

                    Log.d(
                            "HISTORY_DEBUG",
                            "REAL REPEAT DETECTED: videoId="
                                    + (currentPosition >= 0
                                    && currentPosition < videos.size()
                                    ? videos.get(currentPosition).getVideoId()
                                    : "unknown")
                    );
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                updateCurrentBufferingUi(playbackState == Player.STATE_BUFFERING);
            }

            @Override
            public void onRenderedFirstFrame() {
                // The new page has actually rendered its own first frame.
                // Only now remove that page's loading layer.
                updateCurrentBufferingUi(false);
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (screenController != null) {
                    screenController.updateForPlayback(isPlaying);
                }
            }
        });

        // Video repeats automatically
        player.setRepeatMode(Player.REPEAT_MODE_ONE);

        // Used only for History -> WATCH. This does not control seeking.
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY
                        && historyResumePending) {
                    RecyclerView.ViewHolder viewHolder =
                            recyclerView.findViewHolderForAdapterPosition(currentPosition);

                    if (viewHolder instanceof VideoViewHolder) {
                        VideoViewHolder holder =
                                (VideoViewHolder) viewHolder;

                        holder.videoPanel.hideResuming();
                    }
                    // Start only after the History media source is READY and
                    // the correct PlayerView is already attached.
                    player.setPlayWhenReady(true);

                    // Continue saving the resumed history video every 5 seconds.
                    startHistoryProgressUpdates();

                    historyResumePending = false;

                    if (historyPlaybackReadyListener != null) {
                        historyPlaybackReadyListener.onHistoryPlaybackReady();
                    }
                }
            }

            @Override
            public void onPlayerError(
                    androidx.media3.common.PlaybackException error) {

                if (historyResumePending) {
                    historyResumePending = false;

                    if (historyPlaybackReadyListener != null) {
                        historyPlaybackReadyListener.onHistoryPlaybackFailed();
                    }
                }
            }
        });

        player.setTrackSelectionParameters(
                player.getTrackSelectionParameters()
                        .buildUpon()
                        .setMaxVideoSize(854, 480)
                        .build()
        );
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View view = LayoutInflater.from(context)
                .inflate(
                        R.layout.item_video,
                        parent,
                        false
                );

        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull VideoViewHolder holder,
            int position) {

        // Each ViewPager page owns its own VideoPanel/TextureView surface.
        // A recycled page is explicitly cleared before it can be reused for
        // another video, so the previous video's frame cannot leak into it.
        holder.videoPanel.hideBuffering();

        if (position == currentPosition) {
            holder.videoPanel.attachPlayer(player);
            holder.playerView.setControllerShowTimeoutMs(2000);
            holder.playerView.setControllerHideOnTouch(false);
            holder.playerView.setControllerAutoShow(true);
            holder.playerView.showController();
        } else {
            holder.videoPanel.detachPlayer();
        }

        // =================================================
        // LIKE BUTTON
        // =================================================
        // Like is intentionally kept outside PlayerView so the button
        // receives its own click and does not interfere with video gestures.
        holder.likeButton = holder.videoPanel.getLikeButton();
        holder.likeButton.bringToFront();

        String videoId = videos.get(position).getVideoId();

        boolean liked =
                SessionManager.isLoggedIn(context)
                        && likedVideoIds.contains(videoId);
        holder.likeButton.setText(liked ? "♥" : "♡");
        holder.likeButton.setTextColor(
                liked
                        ? Color.rgb(255, 20, 147)
                        : Color.WHITE
        );

        holder.likeButton.setOnClickListener(v -> {

            if (!SessionManager.isLoggedIn(context)) {

                Toast.makeText(
                        context,
                        "Sign in to like this video",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            // =========================================
            // UNLIKE
            // =========================================

            if (likedVideoIds.contains(videoId)) {

                // Remove immediately from local state
                likedVideoIds.remove(videoId);

                // Forget old watched position
                historyWatchedMs.remove(videoId);

                // Stop 5-second history updates
                historyHandler.removeCallbacks(historyProgressRunnable);

                // Immediately show UNLIKE
                refreshCurrentLikeButton();

                // Force RecyclerView to use the new state
                notifyDataSetChanged();

                // DELETE /api/history/{videoId}
                com.example.myapplication.services.ApiService apiService =
                        com.example.myapplication.services.RetrofitClient
                                .getApiService(context);

                apiService.deleteHistory(videoId).enqueue(
                        new retrofit2.Callback<String>() {

                            @Override
                            public void onResponse(
                                    retrofit2.Call<String> call,
                                    retrofit2.Response<String> response) {

                                Log.d(
                                        "HISTORY_DEBUG",
                                        "DELETE history: videoId="
                                                + videoId
                                                + ", code="
                                                + response.code()
                                );

                                if (response.isSuccessful()) {

                                    // =========================================
                                    // DELETE SUCCESS
                                    // =========================================

                                    likedVideoIds.remove(videoId);

                                    // Remove old watched-position cache too
                                    historyWatchedMs.remove(videoId);

                                    // Immediately change the current heart
                                    holder.likeButton.setText("♡");
                                    holder.likeButton.setTextColor(Color.WHITE);

                                    // IMPORTANT:
                                    // Tell MainActivity that history changed.
                                    if (historyDeleteListener != null) {

                                        historyDeleteListener
                                                .onHistoryDeleteRequested(videoId);
                                    }

                                } else {

                                    // =========================================
                                    // DELETE FAILED
                                    // =========================================

                                    likedVideoIds.add(videoId);

                                    holder.likeButton.setText("♥");
                                    holder.likeButton.setTextColor(
                                            Color.rgb(255, 20, 147)
                                    );
                                }
                            }

                            @Override
                            public void onFailure(
                                    retrofit2.Call<String> call,
                                    Throwable t) {

                                Log.e(
                                        "HISTORY_DEBUG",
                                        "DELETE history failed",
                                        t
                                );

                                // DELETE failed → restore LIKE

                            }
                        }
                );

                return;
            }
            // =========================================
            // LIKE
            // =========================================

            likedVideoIds.add(videoId);

            holder.likeButton.setText("♥");
            holder.likeButton.setTextColor(
                    Color.rgb(255, 20, 147)
            );

            // LIKE immediately creates/updates this video's history entry.
            // The current position is saved even when duration is not known yet.
            Log.d(
                    "HISTORY_DEBUG",
                    "LIKE -> create/update history immediately: videoId="
                            + videoId
            );

            saveCurrentVideoProgress();

            // Continue updating the same history entry every 5 seconds
            // while this liked video is playing.
            startHistoryProgressUpdates();
        });

        GestureDetector gestureDetector =
                new GestureDetector(
                        context,
                        new GestureDetector.SimpleOnGestureListener() {

                            @Override
                            public boolean onDown(MotionEvent e) {
                                return true;
                            }

                            // SINGLE TAP → PLAY / PAUSE
                            @Override
                            public boolean onSingleTapConfirmed(
                                    MotionEvent e) {

                                if (holder.playerView.getPlayer() != null) {

                                    if (holder.playerView
                                            .getPlayer()
                                            .isPlaying()) {

                                        holder.playerView
                                                .getPlayer()
                                                .pause();

                                        // Keep progress bar visible while paused.
                                        holder.playerView.setControllerShowTimeoutMs(0);
                                        holder.playerView.showController();

                                    } else {

                                        holder.playerView
                                                .getPlayer()
                                                .play();

                                        // Show progress bar for 5 seconds while playing.
                                        holder.playerView.setControllerShowTimeoutMs(2000);
                                        holder.playerView.showController();
                                    }
                                }

                                return true;
                            }

                            // DOUBLE TAP → MAIN ACTIVITY
                            @Override
                            public boolean onDoubleTap(
                                    MotionEvent e) {

                                if (videoGestureListener != null) {

                                    videoGestureListener
                                            .onVideoDoubleTap();
                                }

                                return true;
                            }
                        }
                );

        // Gesture detection is for the video area only.
        // The Media3 DefaultTimeBar must receive its own touch events.
        holder.playerView.setOnTouchListener(
                (v, event) -> {

                    gestureDetector.onTouchEvent(event);

                    return true;
                }
        );

    }

    // =========================================
    // DOUBLE TAP LISTENER
    // =========================================

    public void setVideoGestureListener(
            VideoGestureListener listener) {

        this.videoGestureListener = listener;
    }

    // =========================================
    // ITEM COUNT
    // =========================================

    @Override
    public int getItemCount() {

        return videos.size();
    }

    // =========================================
    // RECYCLER VIEW CONNECTED
    // =========================================

    @Override
    public void onAttachedToRecyclerView(
            @NonNull RecyclerView recyclerView) {

        super.onAttachedToRecyclerView(recyclerView);

        this.recyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(
            @NonNull RecyclerView recyclerView) {

        super.onDetachedFromRecyclerView(recyclerView);

        this.recyclerView = null;
    }

    // =========================================
    // PLAY SELECTED VIDEO
    // =========================================

    public void playVideo(int position) {

        // Before switching videos, save the latest position of the
        // previous video only if the user has clicked LIKE.
        // Save the final position of the previous video before switching.
        saveCurrentVideoProgress();

        // Stop the old video's periodic history timer.
        historyHandler.removeCallbacks(historyProgressRunnable);

        // The next video must not inherit a repeat-reset event from
        // the previous video.
        historyRepeatResetPending = false;

        // If this page was opened from History, NEVER start it through
        // the normal 0:00 path. The pending resume information belongs
        // to this exact page selection.
        if (pendingHistoryPosition == position) {

            long requestedResumeMs =
                    parseTimeToMillis(pendingHistoryWatched);

            double resumePercent = pendingHistoryPercent;

            pendingHistoryPosition = -1;
            pendingHistoryWatched = null;
            pendingHistoryPercent = 0;

            currentPosition = position;
            historyResumePending = true;

            resumeHistoryWhenHolderReady(
                    position,
                    requestedResumeMs,
                    resumePercent,
                    0
            );

            return;
        }

        currentPosition = position;
        historyResumePending = false;

        // Tell Media3 which item is now in focus so it prioritizes the
        // immediate next and previous videos.
        preloadManager.setCurrentPlayingIndex(position);

        if (recyclerView == null) {
            return;
        }

        VideoViewHolder holder =
                (VideoViewHolder) recyclerView
                        .findViewHolderForAdapterPosition(position);

        if (holder == null) {
            return;
        }

        for (int i = 0;
             i < recyclerView.getChildCount();
             i++) {

            View child = recyclerView.getChildAt(i);
            RecyclerView.ViewHolder viewHolder =
                    recyclerView.getChildViewHolder(child);

            if (viewHolder instanceof VideoViewHolder) {

                VideoViewHolder videoHolder =
                        (VideoViewHolder) viewHolder;

                if (videoHolder != holder) {
                    videoHolder.videoPanel.detachPlayer();
                }
            }
        }

        holder.videoPanel.prepareForNewVideo();
        holder.showBuffering();
        holder.videoPanel.attachPlayer(player);
        holder.playerView.setControllerShowTimeoutMs(2000);
        holder.playerView.setControllerHideOnTouch(false);
        holder.playerView.setControllerAutoShow(true);
        holder.playerView.showController();

        MediaItem mediaItem = MediaItem.fromUri(
                videos.get(position).getVideoUrl().trim()
        );

        if (!firstVideoStarted) {

            // FIRST VIDEO:
            // Use the normal MediaItem so audio initializes normally.
            Log.d(
                    "VIDEO_PRELOAD",
                    "First video - using normal MediaItem"
            );

            player.setMediaItem(mediaItem);

            player.prepare();

            firstVideoStarted = true;

        } else {

            // NEXT/PREVIOUS VIDEOS:
            // Use the MediaSource prepared by DefaultPreloadManager.
            MediaSource preloadedSource =
                    preloadManager.getPreloadedMediaSource(
                            mediaItem
                    );

            if (preloadedSource != null) {

                Log.d(
                        "VIDEO_PRELOAD",
                        "Using preloaded source for position="
                                + position
                );

                player.setMediaSource(
                        preloadedSource
                );

            } else {

                player.setMediaItem(
                        mediaItem
                );
            }

            player.prepare();
        }

        player.play();

        // If this video is already in the user's history, continue tracking it.
        if (likedVideoIds.contains(
                videos.get(position).getVideoId())) {
            startHistoryProgressUpdates();
        }

    }

    // Store the History -> WATCH request BEFORE ViewPager2 changes page.
    // onPageSelected() will consume this request instead of calling the
    // normal start-at-zero playback path.
    public void setPendingHistoryResume(
            int position,
            String watched,
            double percent) {

        if (!SessionManager.isLoggedIn(context)) {
            clearPendingHistoryResume();
            return;
        }

        pendingHistoryPosition = position;
        pendingHistoryWatched = watched;
        pendingHistoryPercent = percent;
    }

    // Resume a history item directly when the requested item is already
    // the selected page and therefore ViewPager2 will not fire onPageSelected.
    public void playVideoFromHistory(
            int position,
            String watched,
            double percent) {

        if (!SessionManager.isLoggedIn(context)) {
            clearPendingHistoryResume();
            return;
        }

        historyHandler.removeCallbacksAndMessages(null);
        currentPosition = position;
        historyResumePending = true;

        preloadManager.setCurrentPlayingIndex(position);

        pendingHistoryPosition = -1;
        pendingHistoryWatched = null;
        pendingHistoryPercent = 0;

        final long requestedResumeMs =
                parseTimeToMillis(watched);

        resumeHistoryWhenHolderReady(
                position,
                requestedResumeMs,
                percent,
                0
        );
    }

    /*
     * ViewPager2 may need a little time to create/attach the
     * ViewHolder after setCurrentItem(). Retry until the holder
     * exists, then prepare the video and seek to the saved time.
     */
    private void resumeHistoryWhenHolderReady(
            int position,
            long requestedResumeMs,
            double percent,
            int attempt) {

        if (recyclerView == null
                || position < 0
                || position >= videos.size()) {
            return;
        }

        VideoViewHolder holder =
                (VideoViewHolder) recyclerView
                        .findViewHolderForAdapterPosition(position);

        if (holder == null) {

            if (attempt < 15) {

                historyHandler.postDelayed(
                        () -> resumeHistoryWhenHolderReady(
                                position,
                                requestedResumeMs,
                                percent,
                                attempt + 1
                        ),
                        100
                );
            }

            return;
        }

        // Remove the player from every other visible video.
        for (int i = 0;
             i < recyclerView.getChildCount();
             i++) {

            View child = recyclerView.getChildAt(i);

            RecyclerView.ViewHolder viewHolder =
                    recyclerView.getChildViewHolder(child);

            if (viewHolder instanceof VideoViewHolder) {

                VideoViewHolder videoHolder =
                        (VideoViewHolder) viewHolder;

                if (videoHolder != holder) {
                    videoHolder.videoPanel.detachPlayer();
                }
            }
        }

        // Make absolutely sure the previous media item is no longer
        // producing audio before attaching the new History video.
        player.pause();
        player.setPlayWhenReady(false);
        player.stop();

// Show this video's buffering screen while the saved timeline
// is being prepared and resumed.
        holder.videoPanel.prepareForNewVideo();
        holder.videoPanel.showResuming();
        holder.videoPanel.attachPlayer(player);

        holder.playerView.setControllerShowTimeoutMs(2000);
        holder.playerView.setControllerHideOnTouch(false);
        holder.playerView.setControllerAutoShow(true);


        MediaItem mediaItem = MediaItem.fromUri(
                videos.get(position).getVideoUrl().trim()
        );

        long startPositionMs = requestedResumeMs;

        // If Media3 has already preloaded this item, reuse that MediaSource.
        MediaSource preloadedSource =
                preloadManager.getPreloadedMediaSource(mediaItem);

        if (preloadedSource != null) {
            player.setMediaSource(preloadedSource, startPositionMs);
        } else {
            player.setMediaItem(mediaItem, startPositionMs);
        }

        // Prepare while playback is explicitly disabled.
        // The global STATE_READY listener starts it only after the new
        // PlayerView and media source are fully ready.
        player.setPlayWhenReady(false);
        player.prepare();

        holder.playerView.showController();
    }

    // Save the current liked video's progress to backend.
    // This is called immediately on LIKE, every 5 seconds while playing,
    // and once more when switching away from the video.
    private boolean saveCurrentVideoProgress() {

        if (historyListener == null
                || currentPosition < 0
                || currentPosition >= videos.size()) {
            return false;
        }

        if (!SessionManager.isLoggedIn(context)) {
            return false;
        }

        String videoId =
                videos.get(currentPosition).getVideoId();

        // Only liked videos are stored in history.
        if (!likedVideoIds.contains(videoId)) {
            return false;
        }

        long positionMs = player.getCurrentPosition();
        long durationMs = player.getDuration();

        if (positionMs < 0) {
            return false;
        }

        // =================================================
        // REAL VIDEO REPEAT
        // =================================================
        // Only reset to 0:00 when Media3 has actually
        // repeated the video.
        if (historyRepeatResetPending) {

            Log.d(
                    "HISTORY_DEBUG",
                    "REAL REPEAT RESET: videoId="
                            + videoId
                            + ", current="
                            + formatTime(positionMs)
                            + " -> RESET TO 0:00"
            );

            positionMs = 0L;

            historyWatchedMs.put(
                    videoId,
                    0L
            );

            historyRepeatResetPending = false;

        } else {

            // =================================================
            // PREVIOUS WATCHED POSITION
            // =================================================
            Long previousWatchedMs =
                    historyWatchedMs.get(videoId);

            // If history already contains a higher timeline,
            // do NOT move history backwards.
            //
            // Example:
            // history = 1:00
            // player  = 0:00 / 0:20 / 0:45
            //
            // All of these are ignored.
            //
            // Only when player becomes > 1:00 will history update.
            if (previousWatchedMs != null
                    && positionMs <= previousWatchedMs) {

                Log.d(
                        "HISTORY_DEBUG",
                        "SKIP HISTORY UPDATE: videoId="
                                + videoId
                                + ", current="
                                + formatTime(positionMs)
                                + ", saved="
                                + formatTime(previousWatchedMs)
                );

                return false;
            }

            // Current position is now greater than the
            // previously saved history position.
            historyWatchedMs.put(
                    videoId,
                    positionMs
            );
        }

        // =================================================
        // CALCULATE PERCENT AND LENGTH
        // =================================================

        double percent = 0.0;
        String length = "0:00";

        if (durationMs > 0
                && durationMs != androidx.media3.common.C.TIME_UNSET) {

            percent =
                    (positionMs * 100.0) / durationMs;

            if (percent > 100) {
                percent = 100;
            }

            length = formatTime(durationMs);
        }

        String watched =
                formatTime(positionMs);

        Log.d(
                "HISTORY_DEBUG",
                "SAVE HISTORY: videoId="
                        + videoId
                        + ", watched=" + watched
                        + ", percent=" + percent
                        + ", length=" + length
        );

        historyListener.onHistoryReady(
                videos.get(currentPosition),
                percent,
                watched,
                length
        );

        return true;
    }
    private void startHistoryProgressUpdates() {

        historyHandler.removeCallbacks(historyProgressRunnable);

        historyHandler.postDelayed(
                historyProgressRunnable,
                HISTORY_SAVE_INTERVAL_MS
        );
    }

    // Remove any pending History -> WATCH request.
    // Used when the user chooses START instead of RESUME.
    public void clearPendingHistoryResume() {

        pendingHistoryPosition = -1;
        pendingHistoryWatched = null;
        pendingHistoryPercent = 0;
        historyResumePending = false;
    }

    private String formatTime(long milliseconds) {

        long totalSeconds = Math.max(0, milliseconds / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        return String.format(
                java.util.Locale.US,
                "%d:%02d",
                minutes,
                seconds
        );
    }

    private long parseTimeToMillis(String value) {

        if (value == null || value.trim().isEmpty()) {
            return 0;
        }

        try {
            String[] parts = value.trim().split(":");

            if (parts.length == 2) {
                long minutes = Long.parseLong(parts[0]);
                long seconds = Long.parseLong(parts[1]);
                return (minutes * 60 + seconds) * 1000;
            }

            return Long.parseLong(value.trim()) * 1000;

        } catch (Exception e) {
            return 0;
        }
    }

    private void updateCurrentBufferingUi(boolean buffering) {
        if (recyclerView == null) return;

        RecyclerView.ViewHolder vh =
                recyclerView.findViewHolderForAdapterPosition(currentPosition);

        if (!(vh instanceof VideoViewHolder)) return;

        VideoViewHolder holder = (VideoViewHolder) vh;

        if (buffering) {
            holder.showBuffering();
        } else {
            holder.hideBuffering();
        }
    }

    // =========================================
    // PAUSE VIDEO FOR HISTORY CHOICE POPUP
    // =========================================

    public void pauseVideo() {

        // Force the current player into a paused state.
        // This is used only while the History RESUME/START popup is open.
        player.pause();
        player.setPlayWhenReady(false);
    }

    // Stop the current media completely before switching from the
    // History popup to another playback request. This prevents the old
    // video's audio from continuing while the History video is loading.
    // This method is used only by the History RESUME/START flow.
    public void stopForHistoryTransition() {

        saveCurrentVideoProgress();
        historyHandler.removeCallbacks(historyProgressRunnable);

        player.pause();
        player.setPlayWhenReady(false);
        player.stop();
    }

    // =========================================
    // KEEP VIDEO PLAYING
    // =========================================

    public void keepPlaying() {

        if (!player.isPlaying()) {

            player.play();
        }
    }

    // =========================================
    // RELEASE PLAYER
    // =========================================

    public void releasePlayer() {

        saveCurrentVideoProgress();
        historyHandler.removeCallbacksAndMessages(null);

        player.stop();

        if (screenController != null) {
            screenController.detachPlayer();
        }

        player.release();

        preloadManager.release();
    }

    // =========================================
    // VIEW HOLDER
    // =========================================

    static class VideoViewHolder
            extends RecyclerView.ViewHolder {

        VideoPanel videoPanel;
        PlayerView playerView;
        Button likeButton;

        public VideoViewHolder(
                @NonNull View itemView) {

            super(itemView);

            videoPanel =
                    itemView.findViewById(
                            R.id.videoPanel
                    );

            playerView =
                    videoPanel.getPlayerView();

            likeButton =
                    videoPanel.getLikeButton();
        }

        void showBuffering() {
            videoPanel.showBuffering();
        }

        void hideBuffering() {
            videoPanel.hideBuffering();
        }
    }

    public void forceUnliked(String videoId) {

        if (videoId == null) {
            return;
        }

        likedVideoIds.remove(videoId);
        historyWatchedMs.remove(videoId);

        notifyDataSetChanged();
        refreshCurrentLikeButton();
    }
}