package com.kaifshaik.musicplayer;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.palette.graphics.Palette;
import android.animation.ObjectAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.snackbar.Snackbar;
import com.kaifshaik.musicplayer.data.DbHandler;
import com.kaifshaik.musicplayer.model.Song;
import com.kaifshaik.musicplayer.params.AlbumArtHelper;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Executors;

public class Player extends AppCompatActivity implements MusicPlayerService.OnSongChangeListener {
    ImageView backButton, playerAlbum, playerPlayPause, playerNext, playerPrevious, playerRepeatMode, playerShuffleMode, playerHeart, playerQueue;
    ImageView playerSkipPreviousSeconds, playerSkipNextSeconds, share;
    private MusicPlayerService musicPlayerService;
    TextView playerTitle, playerArtist;
    String currentPlayingSong;
    SeekBar playerSeekbar;
    boolean swipeAnimation;
    private boolean isBound = false;
    private static final int LOOP_NONE = 0;
    private static final int LOOP_ONE = 1;
    private static final int LOOP_ALL = 2;
    TextView currentTime, maxTime;
    private GestureDetector gestureDetector;
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        private static final int LONG_SWIPE_THRESHOLD = 300;  // Adjust threshold for long or short swipe
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            assert e1 != null;
            float diffY = e2.getY() - e1.getY();
            float diffX = e2.getX() - e1.getX();

            // Detect horizontal swipes (left and right)
            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > LONG_SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        onSwipeRight(); // Swipe to the right
                    } else {
                        onSwipeLeft();  // Swipe to the left
                    }
                    return true;  // Indicate that the swipe was handled
                }
            } else {  // Detect vertical swipes (up and down)
                if (Math.abs(diffY) > LONG_SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        onSwipeDown(); // Swipe down
                    }
                    return true;  // Indicate that the swipe was handled
                }
            }
            return false;  // Indicate that the swipe was not handled
        }
    }

    private void onSwipeLeft() {
        swipeAnimation = true;
        if(isBound){
            musicPlayerService.playNextSong();
        }
    }

    private void onSwipeRight() {
        swipeAnimation = false;
        if(isBound){
            musicPlayerService.playPreviousSong();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Handle vertical swipes for the entire activity
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicPlayerService.LocalBinder binder = (MusicPlayerService.LocalBinder) service;
            musicPlayerService = binder.getService();
            isBound = true;
            Log.d("playeractivity", "Binding successful");

            // Register the Player activity as a listener
            musicPlayerService.addOnSongChangeListener(Player.this);

            String currentSong = musicPlayerService.getCurrentSongPath();
            currentPlayingSong = currentSong;
            Executors.newSingleThreadExecutor().execute(() -> {
                DbHandler dbHandler = new DbHandler(Player.this);
                ArrayList<Song> favoritesList = dbHandler.getAllFavouriteSongs();
                MediaMetaDataHelper.MediaMetadata metadata = MediaMetaDataHelper.extractMetadata(Player.this, currentSong);

                new Handler(Looper.getMainLooper()).post(() -> {
                    if(metadata.getAlbumArt() != null){
                        setBackgroundColors(metadata.getAlbumArt());
                    }else{
                        applyDefaultGradientBackground();
                    }
                    playerTitle.setText(metadata.getTitle());
                    playerArtist.setText(metadata.getArtist());
                    playerSeekbar.setMax((int) metadata.getDuration());
                    playerSeekbar.setProgress(musicPlayerService.getDuration());
                    maxTime.setText(millisecondsToMMSS(metadata.getDuration()));
                    if(musicPlayerService != null){
                        currentTime.setText(millisecondsToMMSS(musicPlayerService.getCurrentPosition()));
                    }
                    Glide.with(Player.this)
                            .load(metadata.getAlbumArt())
                            .apply(RequestOptions.placeholderOf(R.drawable.default_icon))
                            .into(playerAlbum);
                    int loopState = musicPlayerService.loadLoopState();
                    if(loopState == LOOP_ALL){
                        playerRepeatMode.setImageResource(R.drawable.repeat_all);
                        playerRepeatMode.setAlpha(1f);
                    }else if(loopState == LOOP_ONE){
                        playerRepeatMode.setImageResource(R.drawable.repeat_one);
                        playerRepeatMode.setAlpha(1f);
                    }else{
                        playerRepeatMode.setImageResource(R.drawable.no_repeat);
                        playerRepeatMode.setAlpha(0.5f);
                    }
                    if(musicPlayerService.loadShuffleState()){
                        playerShuffleMode.setAlpha(1f);
                    }else{
                        playerShuffleMode.setAlpha(0.5f);
                    }
                    updatePlayPauseButtonState();
                    for(Song song: favoritesList){
                        if(song.getPath().equalsIgnoreCase(currentSong)){
                            playerHeart.setImageResource(R.drawable.player_heart_filled);
                            break;
                        }else{
                            playerHeart.setImageResource(R.drawable.player_heart);
                        }
                    }
                });
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            musicPlayerService.removeOnSongChangeListener(Player.this);
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MusicPlayerService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        initializeComponents();

        backButton.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.no_animation, R.anim.slide_down);
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
                overridePendingTransition(R.anim.no_animation, R.anim.slide_down);
            }
        });

        if (isBound && musicPlayerService != null) {
            updatePlayPauseButtonState();
        }

        setClickListeners();

    }

    private void setClickListeners() {
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareSongFile(currentPlayingSong);
            }
        });

        playerAlbum.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);  // Handle horizontal swipes on ImageView
            }
        });


        playerSkipNextSeconds.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ObjectAnimator rotateAnimator = ObjectAnimator.ofFloat(playerSkipNextSeconds, "rotation", 0f, 360f);
                rotateAnimator.setDuration(200); // Duration of the rotation in milliseconds
                rotateAnimator.start();

                if(isBound){
                    int Position = (int) musicPlayerService.getCurrentPosition();
                    int newPos = Position + 10000;
                    if(newPos > musicPlayerService.getDuration()){
                        newPos = musicPlayerService.getDuration();
                    }
                    musicPlayerService.seekTo(newPos);
                }
            }
        });
        playerSkipPreviousSeconds.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ObjectAnimator rotateAnimator = ObjectAnimator.ofFloat(playerSkipPreviousSeconds, "rotation", 0f, -360f);
                rotateAnimator.setDuration(200); // Duration of the rotation in milliseconds
                rotateAnimator.start();

                if(isBound){
                    int Position = (int) musicPlayerService.getCurrentPosition();
                    int newPos = Position - 10000;
                    if(newPos < 0){
                        newPos = 0;
                    }
                    musicPlayerService.seekTo(newPos);
                }
            }
        });
         playerPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isBound) {
                    musicPlayerService.togglePlayPause();
                }
            }
        });

        playerNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animateButton(v, "next");
                if(isBound){
                    musicPlayerService.playNextSong();
                    swipeAnimation = true;
                }
            }
        });

        playerPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animateButton(v, "previous");
                if(isBound){
                    musicPlayerService.playPreviousSong();
                    swipeAnimation = false;
                }
            }
        });

        playerSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser){
                    if(isBound && musicPlayerService != null){
                        musicPlayerService.seekTo(progress);
                        currentTime.setText(millisecondsToMMSS(progress));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (isBound && musicPlayerService != null) {
                    if(musicPlayerService.IsMusicPlaying()){
                        musicPlayerService.togglePlayPause();
                    }
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (isBound && musicPlayerService != null) {
                    if(!musicPlayerService.IsMusicPlaying()){
                        musicPlayerService.togglePlayPause();
                    }
                }
            }
        });

        playerRepeatMode.setOnClickListener(v -> {
            if (isBound) {
                int state = musicPlayerService.loadLoopState();
                int newState;
                int iconResource;
                String message;

                switch (state) {
                    case LOOP_ALL:
                        newState = LOOP_NONE;
                        iconResource = R.drawable.no_repeat;
                        message = "No Repeat";
                        break;
                    case LOOP_NONE:
                        newState = LOOP_ONE;
                        iconResource = R.drawable.repeat_one;
                        message = "Repeat Current";
                        break;
                    case LOOP_ONE:
                    default: // Fallback to LOOP_ONE if state is unknown
                        newState = LOOP_ALL;
                        iconResource = R.drawable.repeat_all;
                        message = "Repeat All";
                        break;
                }

                // Update loop state and UI
                musicPlayerService.saveLoopState(newState);
                musicPlayerService.updateLoopMode();
                playerRepeatMode.setImageResource(iconResource);
                playerRepeatMode.setAlpha(newState == LOOP_NONE ? 0.5f : 1f);
                Snackbar.make(v, message, Snackbar.LENGTH_LONG).show();
            }
        });

        playerShuffleMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isBound){
                    if(musicPlayerService.loadShuffleState()){
                        playerShuffleMode.setAlpha(0.5f);
                        musicPlayerService.saveShuffleState(false);
                        Snackbar.make(v, "Shuffle Off", Snackbar.LENGTH_LONG).show();
                    }else{
                        playerShuffleMode.setAlpha(1f);
                        musicPlayerService.saveShuffleState(true);
                        Snackbar.make(v, "Shuffle On", Snackbar.LENGTH_LONG).show();
                    }
                }
            }
        });
        playerHeart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DbHandler dbHandler = new DbHandler(Player.this);
                MediaMetaDataHelper.MediaMetadata metadata = MediaMetaDataHelper.extractMetadata(Player.this, currentPlayingSong);
                String album = AlbumArtHelper.getAlbumArtPath(Player.this, currentPlayingSong);
                Song song = new Song(metadata.getTitle(), currentPlayingSong, metadata.getDuration() + "", album, metadata.getArtist());
                // Run on the main thread to update the UI

                if (dbHandler.contains(song)) {
                    // Remove from favorites
                    dbHandler.removeFromFavourites(song);
                    // Update the heart icon to an empty state
                    playerHeart.setImageDrawable(ContextCompat.getDrawable(Player.this, R.drawable.player_heart));
                    MusicPlayerInstance.setIsRemoved(true);
                    Snackbar.make(v, "Removed from Favourites", Snackbar.LENGTH_LONG).show();

                } else {
                    // Add to favorites
                    dbHandler.addToFavourites(song);
                    // Update the heart icon to a filled state
                    playerHeart.setImageDrawable(ContextCompat.getDrawable(Player.this, R.drawable.player_heart_filled));
                    MusicPlayerInstance.setIsAdded(true);
                    Snackbar.make(v, "Added to Favourites", Snackbar.LENGTH_LONG).show();
                }
            }
        });

        playerQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int value = MusicPlayerInstance.getPlayingQueue();
                String message;
                if(value == 1){
                    message = "All Songs Queue";
                }else if(value == 2){
                    message = "Downloads Queue";
                }else{
                    message = "Favourites Queue";
                }
                Snackbar.make(v, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void updatePlayPauseButtonState() {
        if (musicPlayerService.IsMusicPlaying()) {
            playerPlayPause.setImageResource(R.drawable.player_pause);
        } else {
            playerPlayPause.setImageResource(R.drawable.player_play);
        }
    }

    private void onSwipeDown() {
        // Perform your action on swipe down
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_down);
    }
    private void initializeComponents() {
        gestureDetector = new GestureDetector(this, new GestureListener());
        backButton = findViewById(R.id.backButton);
        playerAlbum = findViewById(R.id.playerAlbum);
        playerTitle = findViewById(R.id.player_title);
        playerTitle.setSelected(true);
        playerArtist = findViewById(R.id.player_artist);
        playerPlayPause = findViewById(R.id.player_play_pause);
        playerNext = findViewById(R.id.player_next);
        playerPrevious = findViewById(R.id.player_previous);
        playerSeekbar = findViewById(R.id.player_seekbar);
        playerRepeatMode = findViewById(R.id.player_repeatMode);
        playerShuffleMode = findViewById(R.id.player_shuffleMode);
        currentTime = findViewById(R.id.player_currenttime);
        maxTime = findViewById(R.id.player_maxtime);
        playerHeart = findViewById(R.id.player_heart);
        playerQueue = findViewById(R.id.player_queue);
        playerSkipNextSeconds = findViewById(R.id.player_skip_next_seconds);
        playerSkipPreviousSeconds = findViewById(R.id.player_skip_previous_seconds);
        share = findViewById(R.id.share);
    }

    private void setBackgroundColors(Bitmap albumArt) {
        Palette.from(albumArt).generate(palette -> {
            if (palette == null) {
                Log.e("Palette", "Palette is null, applying default background");
                applyDefaultGradientBackground();
                return;
            }

            // Extract colors with default fallback
            int vibrantColor = palette.getVibrantColor(0);
            int darkVibrantColor = palette.getDarkVibrantColor(0);
            int mutedColor = palette.getMutedColor(0);

            // Adjust colors to reduce whiteness
            vibrantColor = adjustWhiteness(vibrantColor);
            darkVibrantColor = adjustWhiteness(darkVibrantColor);
            mutedColor = adjustWhiteness(mutedColor);

            // Set alpha and apply gradient background
            if (vibrantColor != 0) {
                applyGradientBackground(
                        setColorAlpha(vibrantColor, 150),
                        setColorAlpha(darkVibrantColor, 150),
                        setColorAlpha(mutedColor, 150)
                );
            } else {
                Log.e("Palette", "No vibrant color extracted from palette, applying default background");
                applyDefaultGradientBackground();
            }
        });
    }

    private int setColorAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void applyDefaultGradientBackground() {
        applyGradientBackground(
                Color.parseColor("#CCCCCC"),
                Color.parseColor("#AAAAAA"),
                Color.parseColor("#888888")
        );
    }

    private int adjustWhiteness(int color) {
        if (color == 0) return color; // Early return if color is 0

        // Blend the color with a darker gray to reduce whiteness
        return blendColors(color, Color.rgb(50, 50, 50), 0.5f);
    }

    private int blendColors(int color1, int color2, float factor) {
        int r = (int) (Color.red(color1) * (1 - factor) + Color.red(color2) * factor);
        int g = (int) (Color.green(color1) * (1 - factor) + Color.green(color2) * factor);
        int b = (int) (Color.blue(color1) * (1 - factor) + Color.blue(color2) * factor);
        return Color.rgb(r, g, b);
    }

    private void applyGradientBackground(int startColor, int middleColor, int endColor) {
        GradientDrawable gradientDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        startColor,
                        (startColor == middleColor) ? endColor : middleColor,
                        (middleColor == endColor) ? startColor : endColor
                }
        );

        setRootViewBackground(gradientDrawable, startColor);
    }

    private void setRootViewBackground(GradientDrawable gradientDrawable, int statusBarColor) {
        View rootView = findViewById(R.id.root_layout);
        if (rootView != null) {
            rootView.setBackground(gradientDrawable);
            getWindow().setStatusBarColor(statusBarColor);
        } else {
            Log.e("GradientBackground", "Root view not found");
        }
    }


    @Override
    public void onSongChanged(String songPath) {
        currentPlayingSong = songPath;
        Executors.newSingleThreadExecutor().execute(() -> {
            DbHandler dbHandler = new DbHandler(Player.this);
            ArrayList<Song> favoritesList = dbHandler.getAllFavouriteSongs();
            MediaMetaDataHelper.MediaMetadata metadata = MediaMetaDataHelper.extractMetadata(this, currentPlayingSong);
            // Run on the main thread to update the UI

            new Handler(Looper.getMainLooper()).post(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    if(metadata.getAlbumArt() != null){
                        setBackgroundColors(metadata.getAlbumArt());
                    }else{
                        applyDefaultGradientBackground();
                    }
                    Glide.with(this)
                            .load(metadata.getAlbumArt())
                            .listener(new RequestListener<Drawable>() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                    // If the image load fails, show the default image
                                    playerAlbum.setImageResource(R.drawable.default_icon);
                                    return true;
                                }

                                @Override
                                public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                    // Set the album art image and apply swipe animation
                                    playerAlbum.setImageDrawable(resource);

                                    if(swipeAnimation){
                                        // Animate the image swipe from right to left
                                        playerAlbum.setTranslationX(200);  // Start off-screen (right)
                                        playerAlbum.setAlpha(0f);
                                        playerAlbum.animate()
                                                .translationX(0)  // Animate it back to its position (from right to left)
                                                .alpha(1f)
                                                .setDuration(300)  // Animation duration
                                                .start();
                                    }else{
                                        // Animate the image swipe from left to right
                                        playerAlbum.setTranslationX(-200);  // Start off-screen (left)
                                        playerAlbum.setAlpha(0f);
                                        playerAlbum.animate()
                                                .translationX(0)  // Animate it back to its position (from left to right)
                                                .alpha(1f)
                                                .setDuration(300)  // 500ms animation duration (can be adjusted)
                                                .start();
                                    }

                                    return true;  // Indicate that the load was handled
                                }
                            })
                            .into(playerAlbum);


                    playerTitle.setText(metadata.getTitle());
                    playerArtist.setText(metadata.getArtist());
                    playerSeekbar.setMax((int) metadata.getDuration());
                    maxTime.setText(millisecondsToMMSS(metadata.getDuration()));
                    for(Song song: favoritesList){
                        if(song.getPath().equalsIgnoreCase(songPath)){
                            playerHeart.setImageResource(R.drawable.player_heart_filled);
                            break;
                        }else{
                            playerHeart.setImageResource(R.drawable.player_heart);
                        }
                    }
                }
            });
        });
    }

    @Override
    public void onPlaybackStateChanged(boolean state) {
        if(state){
            playerPlayPause.setImageResource(R.drawable.player_pause);
        }else{
           playerPlayPause.setImageResource(R.drawable.player_play);
        }
        if(isBound){
            currentTime.setText(millisecondsToMMSS(musicPlayerService.getCurrentPosition()));
        }
    }

    @Override
    public void onSeekbarChanged(int position) {
        // Handle seekbar changes if needed
        runOnUiThread(() -> {
            // Update UI elements here
            playerSeekbar.setProgress(position);
            currentTime.setText(millisecondsToMMSS(position));
        });
    }

    @Override
    public void onLoopChanges(int state) {

    }

    @Override
    public void onShuffleChanges(boolean state) {

    }

    public String millisecondsToMMSS(long milliseconds) {
        long seconds = (milliseconds / 1000) % 60;
        long minutes = (milliseconds / (1000 * 60)) % 60;

        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void shareSongFile(String songFilePath) {
        File songFile = new File(songFilePath);

        if (songFile.exists()) {
            Uri songUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    songFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("audio/*"); // For sharing audio files
            shareIntent.putExtra(Intent.EXTRA_STREAM, songUri);

            // Granting temporary read permission to the URI
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Start the chooser for sharing
            startActivity(Intent.createChooser(shareIntent, "Share song via"));
        } else {
            Toast.makeText(this, "Song file does not exist", Toast.LENGTH_SHORT).show();
        }
    }


    private void animateButton(final View button, String pos) {
        float value;
        if(pos.equalsIgnoreCase("next")){
            value = 30f;
        }else{
            value = -30f;
        }
        // Move button to the right
        button.animate()
                .translationX(value) // Move 100 pixels to the right
                .setInterpolator(new OvershootInterpolator()) // Adds an overshoot effect
                .setDuration(300) // Duration for moving to the right
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        // Move button back to original position
                        button.animate()
                                .translationX(0f) // Move back to original position
                                .setInterpolator(new DecelerateInterpolator()) // Smooth deceleration
                                .setDuration(300) // Duration for moving back
                                .start();
                    }
                })
                .start();
    }

}
