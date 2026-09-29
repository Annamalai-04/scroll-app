package com.example.myapplication.pages;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.viewpager2.widget.ViewPager2;

import android.content.pm.PackageInfo;
import com.example.myapplication.R;
import com.example.myapplication.components.VideoAdapter;
import com.example.myapplication.models.AuthResponse;
import com.example.myapplication.models.History;
import com.example.myapplication.models.HistoryRequest;
import com.example.myapplication.models.SigninRequest;
import com.example.myapplication.models.SignupRequest;
import com.example.myapplication.models.Video;
import com.example.myapplication.services.ApiService;
import com.example.myapplication.services.RetrofitClient;
import com.example.myapplication.services.SessionManager;
import com.example.myapplication.utils.SwipeFrameLayout;
import com.example.myapplication.utils.ScreenController;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import android.app.Dialog;
import android.view.Window;
import android.graphics.drawable.ColorDrawable;
import android.widget.Button;

import android.content.Intent;
import android.net.Uri;
import android.app.AlertDialog;

import com.example.myapplication.models.AppVersion;


public class MainActivity extends AppCompatActivity {


    // =================================================
    // PANELS
    // =================================================
    private boolean historyChoiceSelected = false;
    private View leftPanel;
    private View rightPanel;

    private ViewPager2 videoPager;

    private SwipeFrameLayout swipeContainer;

    private VideoAdapter adapter;
    private ScreenController screenController;

    // Black screen shown only while a History -> WATCH video is
    // being prepared and positioned at its saved timeline.
    private View historyBlackOverlay;

    // Videos loaded from Spring Boot
    private List<Video> videos = new ArrayList<>();


    // =================================================
    // PANEL POSITION
    // =================================================

    private float panelWidth;

    private boolean leftOpen = false;
    private boolean rightOpen = false;

    private TextView moviesButton;
    private TextView scenesButton;

    private TextView videoName;
    private TextView videoYear;
    private TextView videoLanguage;

    // null = all videos, MOVIE = movie filter, SCENE = scene filter
    private String activeVideoFilter = null;

    private float currentLeftTranslation;
    private float currentRightTranslation;


    // =================================================
    // RIGHT PANEL CONTENT
    // =================================================

    private FrameLayout rightPanelContent;


    // =================================================
    // CURRENT USER
    // =================================================

    private String currentUserName = null;
    private String currentUserEmail = null;

    // History content in the logged-in panel
    private LinearLayout historyContent;


    // =================================================
    // RIGHT PANEL STATES
    // =================================================

    private static final int RIGHT_LOGGED_OUT = 0;
    private static final int RIGHT_SIGNIN = 1;
    private static final int RIGHT_SIGNUP = 2;
    private static final int RIGHT_LOGGED_IN = 3;


    // =================================================
    // ON CREATE
    // =================================================

    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // Hide Android status bar
        WindowCompat.setDecorFitsSystemWindows(
                getWindow(),
                false
        );

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(
                        getWindow(),
                        getWindow().getDecorView()
                );

        controller.hide(
                WindowInsetsCompat.Type.statusBars()
        );

        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );

        setContentView(R.layout.activity_main);

        // Full immersive mode + YouTube-like screen-awake behavior.
        screenController = new ScreenController(this);
        screenController.applyImmersiveMode();


        // =================================================
        // FIND VIEWS
        // =================================================

        swipeContainer =
                findViewById(R.id.swipeContainer);


        videoPager =
                findViewById(R.id.videoPager);


        leftPanel =
                findViewById(R.id.leftPanel);

        moviesButton =
                findViewById(R.id.moviesButton);

        scenesButton =
                findViewById(R.id.scenesButton);

        videoName =
                findViewById(R.id.videoName);

        videoYear =
                findViewById(R.id.videoYear);

        videoLanguage =
                findViewById(R.id.videoLanguage);


        // =================================================
        // MOVIE / SCENE FILTER BUTTONS
        // =================================================
        // The button state is controlled by the selected FILTER,
        // not by the type of the currently playing video.
        // =================================================

        moviesButton.setOnClickListener(v ->
                toggleVideoFilter("MOVIE")
        );

        scenesButton.setOnClickListener(v ->
                toggleVideoFilter("SCENE")
        );

        updateFilterButtonState();


        rightPanel =
                findViewById(R.id.rightPanel);


        rightPanelContent =
                findViewById(R.id.rightPanelContent);

        // Keep the video feed behind the side panels. The Movie/Scene panel
        // and User/History panel remain above the video pages when opened.
        videoPager.setZ(0f);
        leftPanel.setZ(20f);
        rightPanel.setZ(20f);


        // =================================================
        // VERTICAL VIDEO FEED
        // =================================================

        videoPager.setOrientation(
                ViewPager2.ORIENTATION_VERTICAL
        );


        // =================================================
        // RIGHT PANEL STARTING STATE
        // =================================================

        if (SessionManager.isLoggedIn(this)) {

            currentUserName =
                    SessionManager.getName(this);

            currentUserEmail =
                    SessionManager.getEmail(this);

            showLoggedInPanel();

        } else {

            showLoggedOutPanel();
        }
// =================================================
// CHECK APP VERSION
// =================================================

        checkAppVersion();

        // =================================================
        // LOAD VIDEOS FROM SPRING BOOT
        // =================================================

        loadVideos();


        // =================================================
        // VIDEO PAGE CHANGES
        // =================================================

        videoPager.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {

                    @Override
                    public void onPageSelected(
                            int position) {

                        super.onPageSelected(position);


                        Log.d(
                                "HISTORY_DEBUG",
                                "onPageSelected: position="
                                        + position
                        );


                        // Play selected video
                        if (adapter != null) {

                            adapter.playVideo(position);


                            // Update left panel information
                            updateVideoInformation(
                                    videos,
                                    position
                            );
                        }
                    }
                }
        );


        // =================================================
        // START FIRST VIDEO
        // =================================================

        videoPager.post(() -> {

            if (adapter != null
                    && videos != null
                    && !videos.isEmpty()) {

                adapter.playVideo(0);

                updateVideoInformation(
                        videos,
                        0
                );
            }

        });


        // =================================================
        // PANEL WIDTH = 40%
        // =================================================

        leftPanel.post(() -> {

            int screenWidth =
                    getResources()
                            .getDisplayMetrics()
                            .widthPixels;


            panelWidth =
                    screenWidth * 0.40f;


            setPanelWidth(
                    leftPanel,
                    (int) panelWidth
            );


            setPanelWidth(
                    rightPanel,
                    (int) panelWidth
            );


            // ---------------------------------------------
            // LEFT PANEL STARTS OUTSIDE SCREEN
            // ---------------------------------------------

            leftPanel.setTranslationX(
                    -panelWidth
            );


            // ---------------------------------------------
            // RIGHT PANEL STARTS OUTSIDE SCREEN
            // ---------------------------------------------

            rightPanel.setTranslationX(
                    panelWidth
            );


            currentLeftTranslation =
                    -panelWidth;


            currentRightTranslation =
                    panelWidth;
        });


        // =================================================
        // HORIZONTAL SWIPE LISTENER
        // =================================================

        swipeContainer.setOnHorizontalSwipeListener(

                new SwipeFrameLayout
                        .OnHorizontalSwipeListener() {

                    @Override
                    public void onSwipeProgress(
                            float deltaX) {

                        movePanelsWithFinger(
                                deltaX
                        );
                    }


                    @Override
                    public void onSwipeEnd() {

                        finishPanelMovement();
                    }
                }
        );
    }


    // =================================================
    // SET PANEL WIDTH
    // =================================================

    private void setPanelWidth(
            View panel,
            int width) {

        ViewGroup.LayoutParams params =
                panel.getLayoutParams();


        params.width = width;


        panel.setLayoutParams(params);
    }


    // =================================================
    // MOVE PANEL WITH FINGER
    // =================================================

    private void movePanelsWithFinger(
            float deltaX) {


        // ---------------------------------------------
        // SWIPE RIGHT
        // ---------------------------------------------

        if (deltaX > 0) {

            // LEFT PANEL OPENS

            float newTranslation =
                    -panelWidth + deltaX;


            if (leftOpen) {

                newTranslation =
                        Math.min(
                                deltaX,
                                0
                        );
            }


            newTranslation =
                    Math.max(
                            -panelWidth,
                            Math.min(
                                    0,
                                    newTranslation
                            )
                    );


            leftPanel.setTranslationX(
                    newTranslation
            );


            currentLeftTranslation =
                    newTranslation;


            // RIGHT PANEL STAYS CLOSED

            rightPanel.setTranslationX(
                    panelWidth
            );


            currentRightTranslation =
                    panelWidth;
        }


        // ---------------------------------------------
        // SWIPE LEFT
        // ---------------------------------------------

        else if (deltaX < 0) {

            // RIGHT PANEL OPENS

            float newTranslation =
                    panelWidth + deltaX;


            if (rightOpen) {

                newTranslation =
                        Math.max(
                                deltaX,
                                0
                        );
            }


            newTranslation =
                    Math.max(
                            0,
                            Math.min(
                                    panelWidth,
                                    newTranslation
                            )
                    );


            rightPanel.setTranslationX(
                    newTranslation
            );


            currentRightTranslation =
                    newTranslation;


            // LEFT PANEL STAYS CLOSED

            leftPanel.setTranslationX(
                    -panelWidth
            );


            currentLeftTranslation =
                    -panelWidth;
        }
    }


    // =================================================
    // LOGGED OUT PANEL
    // =================================================

    private void showLoggedOutPanel() {

        rightPanelContent.removeAllViews();

        View view =
                getLayoutInflater().inflate(
                        R.layout.right_logged_out,
                        rightPanelContent,
                        false
                );

        rightPanelContent.addView(view);

        Button signinButton =
                view.findViewById(
                        R.id.signinButton
                );

        Button signupButton =
                view.findViewById(
                        R.id.signupButton
                );


        signinButton.setOnClickListener(v -> {

            showSigninPanel();

        });


        signupButton.setOnClickListener(v -> {

            showSignupPanel();

        });
    }


    // =================================================
    // SIGN IN PANEL
    // =================================================

    private void showSigninPanel() {

        rightPanelContent.removeAllViews();

        View view =
                LayoutInflater.from(this)
                        .inflate(
                                R.layout.right_signin,
                                rightPanelContent,
                                false
                        );

        rightPanelContent.addView(view);

        EditText signinEmail =
                view.findViewById(
                        R.id.signinEmail
                );

        EditText signinPassword =
                view.findViewById(
                        R.id.signinPassword
                );

        Button signinSubmit =
                view.findViewById(
                        R.id.signinSubmit
                );

        Button goToSignupButton =
                view.findViewById(
                        R.id.goToSignupButton
                );


        // NEW USER → SIGNUP

        goToSignupButton.setOnClickListener(v -> {

            showSignupPanel();

        });


        // SIGN IN

        signinSubmit.setOnClickListener(v -> {

            String emailText =
                    signinEmail
                            .getText()
                            .toString()
                            .trim();

            String passwordText =
                    signinPassword
                            .getText()
                            .toString()
                            .trim();


            if (!emailText.isEmpty()
                    && !passwordText.isEmpty()) {

                signIn(
                        emailText,
                        passwordText
                );

            } else {

                Toast.makeText(
                        MainActivity.this,
                        "Enter email and password",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }


    // =================================================
    // SIGN UP PANEL
    // =================================================

    private void showSignupPanel() {

        rightPanelContent.removeAllViews();

        View view =
                LayoutInflater.from(this)
                        .inflate(
                                R.layout.right_signup,
                                rightPanelContent,
                                false
                        );

        rightPanelContent.addView(view);

        EditText name =
                view.findViewById(
                        R.id.signupName
                );

        EditText email =
                view.findViewById(
                        R.id.signupEmail
                );

        EditText password =
                view.findViewById(
                        R.id.signupPassword
                );

        Button signupSubmit =
                view.findViewById(
                        R.id.signupSubmit
                );

        Button goToSigninButton =
                view.findViewById(
                        R.id.goToSigninButton
                );


        // ALREADY SIGNED UP → SIGNIN

        goToSigninButton.setOnClickListener(v -> {

            showSigninPanel();

        });


        // SIGN UP

        signupSubmit.setOnClickListener(v -> {

            String nameText =
                    name.getText()
                            .toString()
                            .trim();

            String emailText =
                    email.getText()
                            .toString()
                            .trim();

            String passwordText =
                    password.getText()
                            .toString()
                            .trim();


            if (!nameText.isEmpty()
                    && !emailText.isEmpty()
                    && !passwordText.isEmpty()) {

                signUp(
                        nameText,
                        emailText,
                        passwordText
                );

            } else {

                Toast.makeText(
                        MainActivity.this,
                        "Enter name, email and password",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }


    // =================================================
    // LOGGED IN PANEL
    // =================================================

    private void showLoggedInPanel() {

        rightPanelContent.removeAllViews();


        View view =
                LayoutInflater.from(this)
                        .inflate(
                                R.layout.right_logged_in,
                                rightPanelContent,
                                false
                        );


        rightPanelContent.addView(view);

        historyContent =
                view.findViewById(
                        R.id.historyContent
                );
        Button refresh = view.findViewById(
                R.id.refreshHistoryButton
        );

        refresh.setOnClickListener(v -> {
            loadHistory();
        });

        // Load user's saved history
        loadHistory();


        // ---------------------------------------------
        // NAME
        // ---------------------------------------------

        TextView name =
                view.findViewById(
                        R.id.loggedInUserName
                );


        // ---------------------------------------------
        // LOGOUT
        // ---------------------------------------------

        Button logout =
                view.findViewById(
                        R.id.logoutButton
                );


        if (currentUserName == null) {

            currentUserName =
                    SessionManager.getName(this);
        }


        if (currentUserName == null
                || currentUserName.isEmpty()) {

            currentUserName = "User";
        }


        name.setText(
                "name: " + currentUserName
        );
        logout.setOnClickListener(v -> {

            // First clear the Android adapter's old user's state
            if (adapter != null) {
                adapter.clearUserHistoryState();
            }

            // Then remove the logged-in user/session
            SessionManager.clearSession(
                    MainActivity.this
            );

            currentUserName = null;
            currentUserEmail = null;

            showLoggedOutPanel();
        });
    }


    // =================================================
    // VIDEO FILTER
    // =================================================

    private void toggleVideoFilter(String type) {

        // Clicking the already-selected button removes the filter.
        if (type.equalsIgnoreCase(
                activeVideoFilter == null ? "" : activeVideoFilter)) {

            loadVideos(null, null);

        } else {

            loadVideos(type, null);
        }
    }

    private void updateFilterButtonState() {

        boolean movieSelected =
                "MOVIE".equalsIgnoreCase(activeVideoFilter);

        boolean sceneSelected =
                "SCENE".equalsIgnoreCase(activeVideoFilter);

        moviesButton.setSelected(movieSelected);
        scenesButton.setSelected(sceneSelected);
    }

    // =================================================
    // LOAD VIDEOS FROM SPRING BOOT
    // =================================================

    private void loadVideos() {
        loadVideos(null, null);
    }

    /**
     * type == null -> all videos in backend order
     * type == MOVIE -> backend returns only movies
     * type == SCENE -> backend returns only scenes
     *
     * onLoaded is used by History -> WATCH when a filtered list
     * has to be cleared first so the history item can be found.
     */
    private void loadVideos(
            String type,
            Runnable onLoaded) {

        ApiService apiService =
                RetrofitClient.getApiService(this);

        Call<List<Video>> call;

        if (type == null || type.trim().isEmpty()) {
            call = apiService.getVideos();
        } else {
            call = apiService.getVideosByType(type);
        }

        call.enqueue(
                new Callback<List<Video>>() {

                    @Override
                    public void onResponse(
                            Call<List<Video>> call,
                            Response<List<Video>> response) {

                        if (response.isSuccessful()
                                && response.body() != null) {

                            List<Video> newVideos =
                                    response.body();

                            if (newVideos.isEmpty()) {

                                Toast.makeText(
                                        MainActivity.this,
                                        type == null
                                                ? "No videos found"
                                                : "No " + type.toLowerCase()
                                                  + " videos found",
                                        Toast.LENGTH_SHORT
                                ).show();

                                return;
                            }

                            // Stop the old feed/player before replacing it.
                            if (adapter != null) {
                                adapter.releasePlayer();
                            }

                            videos = newVideos;
                            activeVideoFilter = type;
                            updateFilterButtonState();

                            adapter =
                                    new VideoAdapter(
                                            MainActivity.this,
                                            videos
                                    );

                            adapter.setScreenController(screenController);
                            adapter.setHistoryDeleteListener(
                                    new VideoAdapter.HistoryDeleteListener() {

                                        @Override
                                        public void onHistoryDeleteRequested(
                                                String videoId) {

                                            // DELETE was successful in VideoAdapter.
                                            // Remove only this history item locally.
                                            // Do not reload /api/history here.
                                            removeHistoryItemLocally(videoId);
                                        }
                                    }
                            );
                            // Sync history with the new adapter
                            if (SessionManager.isLoggedIn(MainActivity.this)) {
                                loadHistory();
                            }

                            adapter.setVideoGestureListener(
                                    new VideoAdapter.VideoGestureListener() {

                                        @Override
                                        public void onVideoDoubleTap() {

                                            if (leftOpen
                                                    || rightOpen) {

                                                closeAllPanels();
                                                adapter.keepPlaying();
                                            }
                                        }
                                    }
                            );

                            // History -> WATCH: keep the screen black until
                            // the requested video is ready.
                            adapter.setHistoryPlaybackReadyListener(
                                    new VideoAdapter.HistoryPlaybackReadyListener() {

                                        @Override
                                        public void onHistoryPlaybackReady() {
                                            hideHistoryBlackOverlay();
                                        }

                                        @Override
                                        public void onHistoryPlaybackFailed() {

                                            hideHistoryBlackOverlay();

                                            Toast.makeText(
                                                    MainActivity.this,
                                                    "Unable to load video",
                                                    Toast.LENGTH_SHORT
                                            ).show();
                                        }
                                    }
                            );

                            // Save history after 21 seconds.
                            adapter.setHistoryListener(
                                    new VideoAdapter.HistoryListener() {

                                        @Override
                                        public void onHistoryReady(
                                                Video video,
                                                double percent,
                                                String watched,
                                                String length) {

                                            saveVideoHistory(
                                                    video.getVideoId(),
                                                    percent,
                                                    watched,
                                                    length
                                            );
                                        }
                                    }
                            );

                            videoPager.setAdapter(adapter);

                            // IMPORTANT:
// The adapter is now created, so history video IDs
// can be applied to its LIKE buttons.
                            if (SessionManager.isLoggedIn(MainActivity.this)) {
                                loadHistory();
                            }

                            videoPager.setCurrentItem(0, false);

                            videoPager.post(() -> {

                                if (adapter != null
                                        && !videos.isEmpty()) {

                                    adapter.playVideo(0);

                                    updateVideoInformation(
                                            videos,
                                            0
                                    );

                                    if (onLoaded != null) {
                                        onLoaded.run();
                                    }
                                }
                            });

                        } else {

                            Toast.makeText(
                                    MainActivity.this,
                                    "Failed to load videos: "
                                            + response.code(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }

                    @Override
                    public void onFailure(
                            Call<List<Video>> call,
                            Throwable t) {

                        Toast.makeText(
                                MainActivity.this,
                                "Cannot connect to server: "
                                        + t.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();

                        t.printStackTrace();
                    }
                }
        );
    }

    // =================================================
    // SIGN IN
    // =================================================

    private void signIn(
            String email,
            String password) {

        SigninRequest request =
                new SigninRequest(
                        email,
                        password
                );


        ApiService apiService =
                RetrofitClient.getApiService(this);


        apiService.signin(request).enqueue(

                new Callback<AuthResponse>() {

                    @Override
                    public void onResponse(
                            Call<AuthResponse> call,
                            Response<AuthResponse> response) {

                        if (response.isSuccessful()
                                && response.body() != null) {

                            AuthResponse auth =
                                    response.body();


                            SessionManager.saveSession(
                                    MainActivity.this,
                                    auth.getToken(),
                                    auth.getUserId(),
                                    auth.getName(),
                                    auth.getEmail()
                            );


                            currentUserName =
                                    auth.getName();

                            currentUserEmail =
                                    auth.getEmail();


                            Toast.makeText(
                                    MainActivity.this,
                                    "Sign in successful",
                                    Toast.LENGTH_SHORT
                            ).show();


                            showLoggedInPanel();

                        } else {

                            Toast.makeText(
                                    MainActivity.this,
                                    "Sign in failed: "
                                            + response.code(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }


                    @Override
                    public void onFailure(
                            Call<AuthResponse> call,
                            Throwable t) {

                        Toast.makeText(
                                MainActivity.this,
                                "Cannot connect to server",
                                Toast.LENGTH_LONG
                        ).show();

                        t.printStackTrace();
                    }
                }
        );
    }


    // =================================================
    // SIGN UP
    // =================================================

    private void signUp(
            String name,
            String email,
            String password) {

        SignupRequest request =
                new SignupRequest(
                        name,
                        email,
                        password
                );


        ApiService apiService =
                RetrofitClient.getApiService(this);


        apiService.signup(request).enqueue(

                new Callback<AuthResponse>() {

                    @Override
                    public void onResponse(
                            Call<AuthResponse> call,
                            Response<AuthResponse> response) {

                        if (response.isSuccessful()
                                && response.body() != null) {

                            AuthResponse auth =
                                    response.body();


                            SessionManager.saveSession(
                                    MainActivity.this,
                                    auth.getToken(),
                                    auth.getUserId(),
                                    auth.getName(),
                                    auth.getEmail()
                            );


                            currentUserName =
                                    auth.getName();

                            currentUserEmail =
                                    auth.getEmail();


                            Toast.makeText(
                                    MainActivity.this,
                                    "Sign up successful",
                                    Toast.LENGTH_SHORT
                            ).show();


                            showLoggedInPanel();

                        } else {

                            Toast.makeText(
                                    MainActivity.this,
                                    "Sign up failed: "
                                            + response.code(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }


                    @Override
                    public void onFailure(
                            Call<AuthResponse> call,
                            Throwable t) {

                        Toast.makeText(
                                MainActivity.this,
                                "Cannot connect to server",
                                Toast.LENGTH_LONG
                        ).show();

                        t.printStackTrace();
                    }
                }
        );
    }


    // Remove one history card from the currently displayed history panel.
    // This keeps the UI synchronized with an unlike without performing a
    // network refresh that could race with an in-flight history POST.
    private void removeHistoryItemLocally(String videoId) {

        if (historyContent == null || videoId == null) {
            return;
        }

        String targetText = "VIDEO: " + videoId;

        for (int i = historyContent.getChildCount() - 1; i >= 0; i--) {

            View item = historyContent.getChildAt(i);

            TextView historyVideo =
                    item.findViewById(R.id.historyVideo);

            if (historyVideo != null
                    && targetText.equals(historyVideo.getText().toString())) {

                historyContent.removeViewAt(i);
                break;
            }
        }

        if (historyContent.getChildCount() == 0) {

            TextView emptyText =
                    new TextView(MainActivity.this);

            emptyText.setText("No history yet");
            emptyText.setTextColor(android.graphics.Color.WHITE);
            emptyText.setTextSize(18);
            emptyText.setPadding(10, 20, 10, 20);

            historyContent.addView(emptyText);
        }
    }


    // =================================================
    // SAVE VIDEO HISTORY
    // =================================================

    private void saveVideoHistory(
            String videoId,
            double percent,
            String watched,
            String length) {

        if (!SessionManager.isLoggedIn(this)) {
            Log.d(
                    "HISTORY_DEBUG",
                    "saveVideoHistory skipped: not logged in"
            );
            return;
        }

        Log.d(
                "HISTORY_DEBUG",
                "saveVideoHistory: videoId=" + videoId
                        + ", percent=" + percent
                        + ", watched=" + watched
                        + ", length=" + length
        );

        HistoryRequest request =
                new HistoryRequest(
                        videoId,
                        percent,
                        watched,
                        length
                );

        ApiService apiService =
                RetrofitClient.getApiService(this);

        apiService.saveHistory(request).enqueue(

                new Callback<String>() {

                    @Override
                    public void onResponse(
                            Call<String> call,
                            Response<String> response) {

                        Log.d(
                                "HISTORY_DEBUG",
                                "saveHistory response: code="
                                        + response.code()
                                        + ", successful="
                                        + response.isSuccessful()
                        );

                        if (response.isSuccessful()) {

                            // IMPORTANT:
                            // The user may have unliked this video while this
                            // POST request was still running.
                            //
                            // If it is no longer liked, do NOT allow this old
                            // save request to recreate the history entry.
                            if (adapter != null
                                    && !adapter.isHistoryVideo(videoId)) {

                                Log.d(
                                        "HISTORY_DEBUG",
                                        "SAVE completed after UNLIKE. "
                                                + "Deleting stale history: "
                                                + videoId
                                );

                                ApiService deleteApi =
                                        RetrofitClient.getApiService(
                                                MainActivity.this
                                        );

                                deleteApi.deleteHistory(videoId).enqueue(
                                        new Callback<String>() {

                                            @Override
                                            public void onResponse(
                                                    Call<String> call,
                                                    Response<String> deleteResponse) {

                                                Log.d(
                                                        "HISTORY_DEBUG",
                                                        "STALE DELETE: videoId="
                                                                + videoId
                                                                + ", code="
                                                                + deleteResponse.code()
                                                );

                                                // The local LIKE state is already the
                                                // source of truth for the current UI.
                                                // Do not reload history here because a
                                                // delayed GET can restore stale state.
                                                removeHistoryItemLocally(videoId);
                                            }

                                            @Override
                                            public void onFailure(
                                                    Call<String> call,
                                                    Throwable t) {

                                                Log.e(
                                                        "HISTORY_DEBUG",
                                                        "STALE DELETE failed",
                                                        t
                                                );
                                            }
                                        }
                                );

                            } else {

                                // Video is still liked.
                                // The history POST is already saved on the backend.
                                // Keep the current local LIKE state; do not reload
                                // history on every 5-second progress save.
                            }

                        }  else {

                            Log.d(
                                    "HISTORY_DEBUG",
                                    "History save FAILED: code="
                                            + response.code()
                                            + ", message="
                                            + response.message()
                            );
                        }
                    }

                    @Override
                    public void onFailure(
                            Call<String> call,
                            Throwable t) {

                        Log.e(
                                "HISTORY_DEBUG",
                                "History save request failed",
                                t
                        );
                    }
                }
        );
    }

    // =================================================
    // LOAD VIDEO HISTORY
    // =================================================

    private void loadHistory() {

        if (!SessionManager.isLoggedIn(this)) {
            return;
        }


        ApiService apiService =
                RetrofitClient.getApiService(this);


        apiService.getHistory().enqueue(

                new Callback<List<History>>() {

                    @Override
                    public void onResponse(
                            Call<List<History>> call,
                            Response<List<History>> response) {

                        if (!response.isSuccessful()
                                || response.body() == null
                                || historyContent == null) {

                            return;
                        }


                        List<History> historyList =
                                response.body();

                        // The LIKE icons must come only from the currently
                        // signed-in user's history. Never reuse stale state
                        // from the previous account/session.
                        if (adapter != null
                                && SessionManager.isLoggedIn(MainActivity.this)) {

                            java.util.List<String> historyVideoIds =
                                    new java.util.ArrayList<>();

                            java.util.Map<String, Long> historyWatchedPositions =
                                    new java.util.HashMap<>();

                            for (History history : historyList) {

                                if (history.getVideoId() != null) {

                                    // Used for LIKE icon
                                    historyVideoIds.add(history.getVideoId());

                                    // Used to prevent watched time from going backwards
                                    long watchedMs =
                                            parseTimeToMillis(history.getWatched());

                                    historyWatchedPositions.put(
                                            history.getVideoId(),
                                            watchedMs
                                    );
                                }
                            }

                            adapter.setHistoryVideoIds(historyVideoIds);

                            adapter.setHistoryWatchedPositions(
                                    historyWatchedPositions
                            );
                        }


                        historyContent.removeAllViews();


                        if (historyList.isEmpty()) {

                            TextView emptyText =
                                    new TextView(
                                            MainActivity.this
                                    );


                            emptyText.setText(
                                    "No history yet"
                            );

                            emptyText.setTextColor(
                                    android.graphics.Color.WHITE
                            );

                            emptyText.setTextSize(18);

                            emptyText.setPadding(
                                    10,
                                    20,
                                    10,
                                    20
                            );


                            historyContent.addView(
                                    emptyText
                            );

                            return;
                        }


                        for (History history :
                                historyList) {

                            View item =
                                    getLayoutInflater()
                                            .inflate(
                                                    R.layout.item_history,
                                                    historyContent,
                                                    false
                                            );


                            TextView historyVideo =
                                    item.findViewById(
                                            R.id.historyVideo
                                    );


                            TextView historyWatched =
                                    item.findViewById(
                                            R.id.historyWatched
                                    );


                            TextView historyDate =
                                    item.findViewById(
                                            R.id.historyDate
                                    );


                            Button historyWatchButton =
                                    item.findViewById(
                                            R.id.historyWatchButton
                                    );


                            historyVideo.setText(
                                    "VIDEO: "
                                            + history.getVideoId()
                            );


                            historyWatched.setText(
                                    "WATCHED: "
                                            + String.format(
                                            java.util.Locale.US,
                                            "%.1f",
                                            history.getPercent()
                                    )
                                            + "%"
                            );


                            historyDate.setText(
                                    "DATE: "
                                            + history.getDate()
                            );


                            // =================================================
                            // WATCH BUTTON
                            // =================================================

                            historyWatchButton.setOnClickListener(
                                    v -> {

                                        if (!SessionManager.isLoggedIn(MainActivity.this)) {
                                            return;
                                        }

                                        if (adapter == null
                                                || videos == null) {
                                            return;
                                        }

                                        showHistoryPlaybackDialog(history);
                                    }
                            );


                            historyContent.addView(item);
                        }
                    }


                    @Override
                    public void onFailure(
                            Call<List<History>> call,
                            Throwable t) {

                        t.printStackTrace();
                    }
                }
        );
    }

    private long parseTimeToMillis(String value) {

        if (value == null || value.trim().isEmpty()) {
            return 0;
        }

        try {

            String[] parts =
                    value.trim().split(":");

            if (parts.length == 2) {

                long minutes =
                        Long.parseLong(parts[0]);

                long seconds =
                        Long.parseLong(parts[1]);

                return (minutes * 60 + seconds) * 1000;
            }

            return Long.parseLong(value.trim()) * 1000;

        } catch (Exception e) {

            return 0;
        }
    }
    // =================================================
    // PLAY HISTORY VIDEO
    // =================================================

    private void playHistoryVideo(History history) {

        if (!SessionManager.isLoggedIn(this)) {
            return;
        }

        if (adapter == null || videos == null) {
            return;
        }

        int targetPosition = -1;

        for (int i = 0; i < videos.size(); i++) {

            if (history.getVideoId() != null
                    && history.getVideoId().equals(
                    videos.get(i).getVideoId())) {

                targetPosition = i;
                break;
            }
        }

        if (targetPosition == -1
                && activeVideoFilter != null) {

            loadVideos(
                    null,
                    () -> playHistoryVideo(history)
            );

            return;
        }

        if (targetPosition == -1) {

            Toast.makeText(
                    MainActivity.this,
                    "Video not found",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        final int finalTargetPosition = targetPosition;
        final double resumePercent = history.getPercent();

        adapter.setPendingHistoryResume(
                finalTargetPosition,
                history.getWatched(),
                resumePercent
        );

        closeAllPanels();
        showHistoryBlackOverlay();

        int currentPage = videoPager.getCurrentItem();

        videoPager.setCurrentItem(
                finalTargetPosition,
                false
        );

        if (currentPage == finalTargetPosition) {

            videoPager.post(() -> {

                if (adapter != null) {

                    adapter.playVideoFromHistory(
                            finalTargetPosition,
                            history.getWatched(),
                            resumePercent
                    );
                }
            });
        }
    }


    // =================================================
    // HISTORY WATCH BLACK OVERLAY
    // =================================================

    private void showHistoryBlackOverlay() {

        if (swipeContainer == null) {
            return;
        }

        if (historyBlackOverlay == null) {

            historyBlackOverlay = new View(this);

            historyBlackOverlay.setBackgroundColor(
                    android.graphics.Color.BLACK
            );

            historyBlackOverlay.setClickable(true);
            historyBlackOverlay.setFocusable(true);

            swipeContainer.addView(
                    historyBlackOverlay,
                    new android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
            );
        }

        historyBlackOverlay.setVisibility(View.VISIBLE);
        historyBlackOverlay.bringToFront();
    }

    private void hideHistoryBlackOverlay() {

        if (historyBlackOverlay != null) {
            historyBlackOverlay.setVisibility(View.GONE);
        }
    }


    // =================================================
    // CLOSE ALL PANELS
    // =================================================

    private void closeAllPanels() {

        leftOpen = false;

        rightOpen = false;


        leftPanel.animate()
                .translationX(-panelWidth)
                .setDuration(200)
                .start();


        rightPanel.animate()
                .translationX(panelWidth)
                .setDuration(200)
                .start();


        currentLeftTranslation =
                -panelWidth;


        currentRightTranslation =
                panelWidth;
    }


    // =================================================
    // FINISH PANEL MOVEMENT
    // =================================================

    private void finishPanelMovement() {


        // =============================================
        // LEFT PANEL
        // =============================================

        float leftProgress =
                1f -
                        (
                                Math.abs(
                                        currentLeftTranslation
                                )
                                        / panelWidth
                        );


        // =============================================
        // RIGHT PANEL
        // =============================================

        float rightProgress =
                1f -
                        (
                                currentRightTranslation
                                        / panelWidth
                        );


        // =============================================
        // LEFT PANEL DECISION
        // =============================================

        if (leftProgress > 0.5f) {

            openLeftPanel();

        } else {

            closeLeftPanel();
        }


        // =============================================
        // RIGHT PANEL DECISION
        // =============================================

        if (rightProgress > 0.5f) {

            openRightPanel();

        } else {

            closeRightPanel();
        }
    }


    // =================================================
    // OPEN LEFT
    // =================================================

    private void openLeftPanel() {

        leftOpen = true;

        rightOpen = false;


        rightPanel.animate()
                .translationX(panelWidth)
                .setDuration(200)
                .start();


        leftPanel.animate()
                .translationX(0)
                .setDuration(200)
                .start();


        currentLeftTranslation = 0;

        currentRightTranslation =
                panelWidth;
    }


    // =================================================
    // CLOSE LEFT
    // =================================================

    private void closeLeftPanel() {

        leftOpen = false;


        leftPanel.animate()
                .translationX(-panelWidth)
                .setDuration(200)
                .start();


        currentLeftTranslation =
                -panelWidth;
    }


    // =================================================
    // OPEN RIGHT
    // =================================================

    private void openRightPanel() {

        leftOpen = false;

        rightOpen = true;


        leftPanel.animate()
                .translationX(-panelWidth)
                .setDuration(200)
                .start();


        rightPanel.animate()
                .translationX(0)
                .setDuration(200)
                .start();


        currentLeftTranslation =
                -panelWidth;


        currentRightTranslation = 0;
    }


    // =================================================
    // CLOSE RIGHT
    // =================================================

    private void closeRightPanel() {

        rightOpen = false;


        rightPanel.animate()
                .translationX(panelWidth)
                .setDuration(200)
                .start();


        currentRightTranslation =
                panelWidth;
    }


    // =================================================
    // RELEASE VIDEO PLAYER
    // =================================================

    @Override
    protected void onDestroy() {

        if (adapter != null) {

            adapter.releasePlayer();
        }


        super.onDestroy();
    }


    // =================================================
    // UPDATE VIDEO INFORMATION
    // =================================================

    private void updateVideoInformation(
            List<Video> videos,
            int position) {

        if (position < 0
                || position >= videos.size()) {

            return;
        }


        Video video =
                videos.get(position);


        // =========================================
        // UPDATE TEXT
        // =========================================

        videoName.setText(video.getName());

        videoYear.setText(String.valueOf(video.getYear()));

        videoLanguage.setText(video.getLanguage());


        // Button selection represents the ACTIVE FILTER only.
        // It must not change just because the next video has a
        // different type.
        updateFilterButtonState();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (screenController != null) {
            screenController.onWindowFocusChanged(hasFocus);
        }
    }

    private void showHistoryPlaybackDialog(History history) {

        if (adapter != null) {
            adapter.pauseVideo();
        }

        historyChoiceSelected = false;

        Dialog dialog = new Dialog(this);

        dialog.setContentView(
                R.layout.dialog_history_playback
        );

        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();

        if (window != null) {

            window.setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT
                    )
            );

            window.setDimAmount(0.7f);

            window.addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND
            );
        }

        Button resumeButton =
                dialog.findViewById(R.id.resumeButton);

        Button startOverButton =
                dialog.findViewById(R.id.startOverButton);

        resumeButton.setOnClickListener(v -> {

            historyChoiceSelected = true;

            dialog.dismiss();

            playHistoryVideo(history);
        });

        startOverButton.setOnClickListener(v -> {

            historyChoiceSelected = true;

            dialog.dismiss();

            playHistoryVideoFromStart(history);
        });

        dialog.setOnDismissListener(d -> {

            if (!historyChoiceSelected) {

                // User touched outside the popup.
                // Continue the video that was playing.
                if (adapter != null) {
                    adapter.keepPlaying();
                }
            }
        });

        dialog.show();
    }

    private void playHistoryVideoFromStart(History history) {

        if (!SessionManager.isLoggedIn(this)) {
            return;
        }

        if (adapter == null || videos == null) {
            return;
        }

        int targetPosition = IntStream.range(0, videos.size()).filter(i -> history.getVideoId() != null
                && history.getVideoId().equals(
                videos.get(i).getVideoId())).findFirst().orElse(-1);

        if (targetPosition == -1) {

            Toast.makeText(
                    MainActivity.this,
                    "Video not found",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        // Start Over = 0:00.
        // Use the same History playback flow as Resume,
        // but give it a zero starting position.
        adapter.setPendingHistoryResume(
                targetPosition,
                "0:00",
                0.0
        );

        closeAllPanels();

        // Keep screen black while the new video is prepared.
        showHistoryBlackOverlay();

        int currentPage =
                videoPager.getCurrentItem();

        videoPager.setCurrentItem(
                targetPosition,
                false
        );

        if (currentPage == targetPosition) {

            videoPager.post(() -> {

                if (adapter != null) {

                    adapter.playVideoFromHistory(
                            targetPosition,
                            "0:00",
                            0.0
                    );
                }
            });
        }
    }

    private void checkAppVersion() {

        ApiService apiService =
                RetrofitClient.getApiService(this);

        apiService.getAppVersion().enqueue(
                new Callback<AppVersion>() {

                    @Override
                    public void onResponse(
                            Call<AppVersion> call,
                            Response<AppVersion> response) {

                        if (!response.isSuccessful()
                                || response.body() == null) {

                            Log.d(
                                    "APP_VERSION",
                                    "Version check failed: " + response.code()
                            );

                            return;
                        }

                        AppVersion serverVersion = response.body();

                        try {

                            PackageInfo packageInfo =
                                    getPackageManager().getPackageInfo(
                                            getPackageName(),
                                            0
                                    );

                            int installedVersionCode =
                                    packageInfo.versionCode;

                            int latestVersionCode =
                                    serverVersion.getLatestVersionCode();

                            int minimumVersionCode =
                                    serverVersion.getMinimumVersionCode();

                            Log.d(
                                    "APP_VERSION",
                                    "Installed=" + installedVersionCode
                                            + ", Latest=" + latestVersionCode
                                            + ", Minimum=" + minimumVersionCode
                            );

                            if (installedVersionCode < minimumVersionCode) {

                                showUpdateDialog(
                                        serverVersion,
                                        true
                                );

                            } else if (installedVersionCode < latestVersionCode) {

                                showUpdateDialog(
                                        serverVersion,
                                        false
                                );
                            }

                        } catch (Exception e) {

                            Log.e(
                                    "APP_VERSION",
                                    "Could not read installed app version",
                                    e
                            );
                        }
                    }

                    @Override
                    public void onFailure(
                            Call<AppVersion> call,
                            Throwable t) {

                        Log.e(
                                "APP_VERSION",
                                "Version check connection failed",
                                t
                        );
                    }
                }
        );
    }

    private void showUpdateDialog(
            AppVersion serverVersion,
            boolean mandatory) {

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this);

        builder.setTitle("New update available");

        builder.setMessage(
                "A new version "
                        + serverVersion.getLatestVersionName()
                        + " is available."
        );

        builder.setPositiveButton(
                "UPDATE",
                (dialog, which) -> {

                    String apkUrl =
                            serverVersion.getApkUrl();

                    if (apkUrl == null
                            || apkUrl.trim().isEmpty()
                            || apkUrl.contains("YOUR-S3-APK-URL")) {

                        Toast.makeText(
                                MainActivity.this,
                                "APK download URL is not configured yet.",
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    try {

                        Intent intent =
                                new Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(apkUrl)
                                );

                        startActivity(intent);

                    } catch (Exception e) {

                        Toast.makeText(
                                MainActivity.this,
                                "Unable to open update link.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );

        if (!mandatory) {

            builder.setNegativeButton(
                    "LATER",
                    (dialog, which) -> dialog.dismiss()
            );
        }

        AlertDialog dialog =
                builder.create();

        dialog.setCancelable(!mandatory);

        dialog.show();
    }
}