package com.example.myapplication.components;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheKeyFactory;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager;
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl;

import com.example.myapplication.models.Video;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fast preload manager for the vertical video feed.
 *
 * The ViewPager2 swipe/play logic is intentionally left in VideoAdapter.
 * This class handles nearby preloading and persistent disk caching.
 *
 * Strategy:
 *  - current + 1 : preload 20 seconds into RAM/cache
 *  - current - 1 : preload 20 seconds into RAM/cache
 *  - +/- 2        : select tracks
 *  - +/- 3..4     : prepare the source
 *  - disk cache   : keep recently used video bytes with an LRU policy
 *
 * The same DefaultPreloadManager builder creates the real ExoPlayer.
 */
@OptIn(markerClass = UnstableApi.class)
public class VideoPreloadManager {

    private static final String TAG = "VIDEO_PRELOAD";

    // Persistent on-device video cache. LRU removes old content automatically.
    private static final long CACHE_SIZE =
            2L * 1024L * 1024L * 1024L; // 2 GB

    // Keep adjacent RAM/network preload moderate to avoid the memory pressure
    // caused by loading too much video at once.
    private static final long ADJACENT_PRELOAD_MS =
            20_000L;

    private static SimpleCache simpleCache;
    private static StandaloneDatabaseProvider databaseProvider;

    private final Context context;
    private final List<Video> videos;

    private final DefaultPreloadManager preloadManager;
    private final DefaultPreloadManager.Builder preloadManagerBuilder;

    private int currentPlayingIndex = 0;

    // Maps the currently known S2 URL to the permanent MongoDB videoId.
    // This lets the disk cache reuse the same video even when S2 gives the
    // app a different signed URL/token the next time the feed is loaded.
    private final Map<String, String> cacheKeyByVideoUrl =
            new HashMap<>();

    public VideoPreloadManager(
            Context context,
            List<Video> videos) {

        this.context =
                context.getApplicationContext();

        this.videos = videos;

        VideoPreloadManagerHolder.context =
                this.context;

        Cache cache = getSharedCache();

        TargetPreloadStatusControl<Integer,
                DefaultPreloadManager.PreloadStatus>
                targetControl =
                new TargetPreloadStatusControl<>() {

                    @Override
                    public DefaultPreloadManager.PreloadStatus
                    getTargetPreloadStatus(Integer index) {

                        if (index == null) {
                            return DefaultPreloadManager.PreloadStatus
                                    .PRELOAD_STATUS_NOT_PRELOADED;
                        }

                        int current =
                                currentPlayingIndex;

                        int distance =
                                index - current;

                        if (distance == 1
                                || distance == -1) {

                            return DefaultPreloadManager.PreloadStatus
                                    .specifiedRangeLoaded(
                                            ADJACENT_PRELOAD_MS
                                    );
                        }

                        if (Math.abs(distance) == 2) {

                            return DefaultPreloadManager.PreloadStatus
                                    .PRELOAD_STATUS_TRACKS_SELECTED;
                        }

                        if (Math.abs(distance) <= 4) {

                            return DefaultPreloadManager.PreloadStatus
                                    .PRELOAD_STATUS_SOURCE_PREPARED;
                        }

                        return DefaultPreloadManager.PreloadStatus
                                .PRELOAD_STATUS_NOT_PRELOADED;
                    }
                };

        preloadManagerBuilder =
                new DefaultPreloadManager.Builder(
                        this.context,
                        targetControl
                );

        preloadManagerBuilder
                .setCache(cache)
                .setDataSourceFactory(
                        createCacheDataSourceFactory(cache)
                );

        preloadManager =
                preloadManagerBuilder.build();

        addAllVideosToPreloadManager();
    }

    private static synchronized SimpleCache
    getSharedCache() {

        if (simpleCache != null) {
            return simpleCache;
        }

        Context appContext =
                VideoPreloadManagerHolder.context;

        if (appContext == null) {
            throw new IllegalStateException(
                    "VideoPreloadManager context not initialized"
            );
        }

        File cacheDir =
                new File(
                        appContext.getCacheDir(),
                        "scroll_video_cache"
                );

        databaseProvider =
                new StandaloneDatabaseProvider(
                        appContext
                );

        simpleCache =
                new SimpleCache(
                        cacheDir,
                        new LeastRecentlyUsedCacheEvictor(
                                CACHE_SIZE
                        ),
                        databaseProvider
                );

        Log.d(
                TAG,
                "Disk cache initialized: 2 GB LRU, dir="
                        + cacheDir.getAbsolutePath()
        );

        return simpleCache;
    }

    private static final class
    VideoPreloadManagerHolder {

        private static Context context;
    }

    private DefaultHttpDataSource.Factory
    createHttpDataSourceFactory() {

        return new DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(5_000)
                .setReadTimeoutMs(15_000)
                .setAllowCrossProtocolRedirects(true);
    }

    /**
     * Creates the data source used by BOTH preload and playback.
     *
     * The cache key ignores the query string. This is useful when the S2/media
     * server gives the same video a new signed URL/token when the user comes
     * back later. The cached bytes can then be reused instead of downloading
     * the same video again.
     *
     * This assumes the query string is authentication/signature information,
     * not a selector for a different video representation.
     */
    private DataSource.Factory
    createCacheDataSourceFactory(Cache cache) {

        CacheKeyFactory cacheKeyFactory =
                dataSpec -> {

                    Uri uri = dataSpec.uri;

                    // Prefer the permanent videoId when this URL belongs to
                    // one of the videos in the current feed. This is the
                    // important part for signed S2 URLs: a new token/URL for
                    // the same video still points to the same cache entry.
                    String videoId =
                            cacheKeyByVideoUrl.get(uri.toString());

                    if (videoId != null
                            && !videoId.trim().isEmpty()) {

                        return "video:" + videoId.trim();
                    }

                    // Fallback for media requests that are not in the feed
                    // map (for example child/segment requests).
                    Uri cacheUri =
                            uri.buildUpon()
                                    .clearQuery()
                                    .fragment(null)
                                    .build();

                    return cacheUri.toString();
                };

        return new CacheDataSource.Factory()
                .setCache(cache)
                .setCacheKeyFactory(cacheKeyFactory)
                .setUpstreamDataSourceFactory(
                        createHttpDataSourceFactory()
                );
    }

    private void addAllVideosToPreloadManager() {

        if (videos == null
                || videos.isEmpty()) {

            return;
        }

        for (int i = 0;
             i < videos.size();
             i++) {

            Video video =
                    videos.get(i);

            if (video == null
                    || video.getVideoUrl() == null
                    || video.getVideoUrl()
                    .trim()
                    .isEmpty()) {

                continue;
            }

            String videoUrl =
                    video.getVideoUrl().trim();

            String videoId = video.getVideoId();

            if (videoId != null
                    && !videoId.trim().isEmpty()) {

                cacheKeyByVideoUrl.put(
                        videoUrl,
                        videoId.trim()
                );
            }

            MediaItem mediaItem =
                    MediaItem.fromUri(
                            Uri.parse(videoUrl)
                    );

            preloadManager.add(
                    mediaItem,
                    i
            );
        }

        preloadManager.setCurrentPlayingIndex(0);
        preloadManager.invalidate();

        Log.d(
                TAG,
                "Preload initialized: videos="
                        + videos.size()
                        + ", adjacent="
                        + (ADJACENT_PRELOAD_MS / 1000)
                        + " seconds, diskCache=2GB"
        );
    }

    /**
     * Called when ViewPager2 selects a new video.
     * This keeps the original working swipe behavior.
     */
    public void setCurrentPlayingIndex(
            int position) {

        if (videos == null
                || videos.isEmpty()) {

            return;
        }

        if (position < 0
                || position >= videos.size()) {

            return;
        }

        currentPlayingIndex =
                position;

        preloadManager.setCurrentPlayingIndex(
                position
        );

        preloadManager.invalidate();

        Log.d(
                TAG,
                "Current=" + position
                        + ", preload next="
                        + (position + 1)
                        + ", preload previous="
                        + (position - 1)
        );
    }

    public androidx.media3.exoplayer.source.MediaSource
    getPreloadedMediaSource(
            MediaItem mediaItem) {

        return preloadManager.getMediaSource(
                mediaItem
        );
    }

    public ExoPlayer buildPlaybackPlayer() {

        ExoPlayer player =
                preloadManagerBuilder
                        .buildExoPlayer();

        player.setVolume(1.0f);

        return player;
    }

    public void release() {

        preloadManager.release();
    }

    public static void initializeContext(
            Context context) {

        VideoPreloadManagerHolder.context =
                context.getApplicationContext();
    }
}
