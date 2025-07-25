package com.kaifshaik.musicplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;
import androidx.core.app.NotificationCompat;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import android.media.AudioManager;

public class MusicPlayerService extends Service implements AudioManager.OnAudioFocusChangeListener{

    public static final String ACTION_PLAY = "com.kaifshaik.musicplayer.PLAY";
    public static final String ACTION_PAUSE = "com.kaifshaik.musicplayer.PAUSE";
    public static final String ACTION_PREVIOUS = "com.kaifshaik.musicplayer.PREVIOUS";
    public static final String ACTION_NEXT = "com.kaifshaik.musicplayer.NEXT";

    public static final String ACTION_STOP = "com.kaifshaik.musicplayer.STOP";
    private static final String CHANNEL_ID = "MusicPlayerChannel";

    private MediaPlayer mediaPlayer;
    private int currentSongIndex = 0;
    private ArrayList<String> songPaths;
    private MediaSessionCompat mediaSessionCompat;
    private boolean isHandlingMediaAction = false;
    private static final long MEDIA_ACTION_DEBOUNCE_DELAY_MS = 500;

    private String title = "Unknown Title", artist = "<Unknown>";
    private Bitmap albumArt = null;
    private MediaActionReceiver mediaActionReceiver;
    private boolean isPlaying = false;
    private final IBinder binder = new LocalBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateSeekbarRunnable;
    private static final int LOOP_NONE = 0;
    private static final int LOOP_ONE = 1;
    private static final int LOOP_ALL = 2;
    private int loopMode;
    private static List<Integer> shufflePlayed;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;

    // Update loop mode based on user selection
    public void updateLoopMode() {
        loopMode = loadLoopState(); // Load the saved loop mode

        if (loopMode == LOOP_ONE) {
            mediaPlayer.setLooping(true);  // Loop the current song
        } else {
            mediaPlayer.setLooping(false); // Disable looping for the current song
        }
    }
    public long getCurrentPosition(){
        return mediaPlayer.getCurrentPosition();
    }

    public String getCurrentSongPath(){
        return getSongPath(currentSongIndex);
    }
    public int getCurrentSongIndex(){
        return currentSongIndex;
    }

    private BroadcastReceiver noisyAudioStreamReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                if(mediaPlayer.isPlaying()){
                    togglePlayPause();
                }
            }
        }
    };

    @Override
    public void onAudioFocusChange(int focusChange) {
        if(mediaPlayer != null){
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS:
                    // Permanent focus loss, stop playback
                    stopMusic();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    // Focus loss for a short time (e.g., incoming call), pause playback
                    pauseMusic();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    // Focus loss with possibility of lowering volume ("ducking")
                    lowerVolume();
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    // Focus regained, resume playback
                    resumeMusic();
                    break;
            }
        }
    }
    private void stopMusic() {
        if(mediaPlayer.isPlaying()){
            togglePlayPause();

            // Release audio focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // For API 26 and above
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                // For API 25 and below
                audioManager.abandonAudioFocus(this);
            }
        }
    }

    private void pauseMusic() {
        if(mediaPlayer.isPlaying()){
            togglePlayPause();
        }
    }

    private void resumeMusic() {
        if(!mediaPlayer.isPlaying()){
            togglePlayPause();

            // Restore the volume to its normal level
            mediaPlayer.setVolume(1.0f, 1.0f);
        }
    }

    private void lowerVolume() {
        // Your logic to lower volume ("ducking")
        if (mediaPlayer.isPlaying()) {
            // Reduce the volume to 0.1 (or any lower value) to duck the audio
            mediaPlayer.setVolume(0.1f, 0.1f);
        }
    }

    // Existing methods...
    // Inner class to return the instance of the service
    public class LocalBinder extends Binder {
        public MusicPlayerService getService() {
            return MusicPlayerService.this;
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // Instance of LocalBinder

    public interface OnSongChangeListener {
        void onSongChanged(String songPath);
        void onPlaybackStateChanged(boolean state);
        void onSeekbarChanged(int position);
        void onLoopChanges(int state);
        void onShuffleChanges(boolean state);
    }

    private final List<OnSongChangeListener> listeners = new ArrayList<>();

    public void addOnSongChangeListener(OnSongChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeOnSongChangeListener(OnSongChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifySongChange(String songPath) {
        for (OnSongChangeListener listener : listeners) {
            listener.onSongChanged(songPath);
        }
    }

    private void notifyPlaybackStateChanged(boolean state) {
        for (OnSongChangeListener listener : listeners) {
            listener.onPlaybackStateChanged(state);
        }
    }

    private void notifySeekbarChanged(int position) {
        for (OnSongChangeListener listener : listeners) {
            listener.onSeekbarChanged(position);
        }
    }
    private void notifyLoopChanged(int state){
        for(OnSongChangeListener listener : listeners){
            listener.onLoopChanges(state);
        }
    }
    private void notifyShuffleChanged(boolean state){
        for(OnSongChangeListener listener : listeners){
            listener.onShuffleChanges(state);
        }
    }

    private void startSeekbarUpdates() {
        if(mediaPlayer == null && MusicPlayerInstance.getMusicPlayerService() == null && !mediaSessionCompat.isActive()){
            setMediaSession();
        }
        updateSeekbarRunnable = new Runnable() {
            @Override
            public void run() {
                if(mediaPlayer != null){
                    notifySeekbarChanged(mediaPlayer.getCurrentPosition());
                }
                handler.postDelayed(this, 100); // Update every second
            }
        };
        handler.post(updateSeekbarRunnable);
    }
    public void seekTo(int position){
        mediaPlayer.seekTo(position);
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
    }

    private void setMediaSession(){
        mediaPlayer = new MediaPlayer();
        loopMode = loadLoopState();
        MusicPlayerInstance.setMusicPlayerService(this);
        mediaSessionCompat = new MediaSessionCompat(this, "MusicPlayerService");
        mediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        IntentFilter filter = new IntentFilter();

        filter.addAction(ACTION_PLAY);
        filter.addAction(ACTION_PAUSE);
        filter.addAction(ACTION_PREVIOUS);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_STOP);
        mediaActionReceiver = new MediaActionReceiver();
        registerReceiver(mediaActionReceiver, filter);
        IntentFilter filter2 = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(noisyAudioStreamReceiver, filter2);

        mediaSessionCompat.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                if (!mediaPlayer.isPlaying()) {
                    isPlaying = true;
                    mediaPlayer.start();
                    startSeekbarUpdates();
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
                    updateNotification();

                    // request audio focus again
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioManager.requestAudioFocus(audioFocusRequest);
                    } else {
                        audioManager.requestAudioFocus(MusicPlayerService.this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                    }
                }
            }

            @Override
            public void onPause() {
                if (mediaPlayer.isPlaying()) {
                    isPlaying = false;
                    mediaPlayer.pause();
                    handler.removeCallbacks(updateSeekbarRunnable);
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
                    updateNotification();
                }
            }
            @Override
            public void onSeekTo(long pos) {
                Log.d("MusicPlayerService", "SeekTo called with position: " + pos);
                super.onSeekTo(pos);
                mediaPlayer.seekTo((int) pos);
                if(mediaPlayer.isPlaying()){
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
                }else{
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
                }
                updateNotification();
            }

            @Override
            public void onSkipToNext() {
                playNextSong();
            }

            @Override
            public void onSkipToPrevious() {
                playPreviousSong();
            }

            @Override
            public void onStop() {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
                    updateNotification();
                }
                try{
                    unregisterReceiver(mediaActionReceiver);
                }catch (Exception e){
                    Log.d("TAG", "No Receiver");
                }
                // Clear the instance
                MusicPlayerInstance.setMusicPlayerService(null);
                mediaSessionCompat.release();
                stopForeground(true);
                stopSelf();
            }

            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                KeyEvent keyEvent = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                    Log.d("MediaButtonReceiver", "KeyEvent received: " + keyEvent.getKeyCode());
                    switch (keyEvent.getKeyCode()) {
                        case KeyEvent.KEYCODE_MEDIA_PLAY:
                        case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                            togglePlayPause();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_NEXT:
                            playNextSong();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                            playPreviousSong();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_STOP:
                            onStop();
                            break;
                    }
                }
                return super.onMediaButtonEvent(mediaButtonIntent);
            }

        });
        mediaSessionCompat.setActive(true);
    }
    @Override
    public void onCreate() {
        super.onCreate();

        songPaths = new ArrayList<>();
        shufflePlayed = new ArrayList<>();
        setMediaSession();

        createNotificationChannel();
        startForeground(1, getNotification());

        mediaPlayer.setOnCompletionListener(mp -> {
            if (loopMode == LOOP_ALL) {
                // Loop through all songs
                playNextSong(); // Move to the next song
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
            } else if (loopMode == LOOP_ONE) {
                // Loop the current song
                mediaPlayer.seekTo(0); // Restart the current song
                mediaPlayer.start();
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
            } else if (loopMode == LOOP_NONE) {
                // No looping, stop playback
                mediaPlayer.pause();
                isPlaying = false;
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
            }

            updateCurrentMetadata(currentSongIndex);
            updateMediaSessionMetadata();
            updateNotification();
        });

        if(mediaPlayer.isPlaying()){
            startSeekbarUpdates();
        }else{
            handler.removeCallbacks(updateSeekbarRunnable);
        }
        MusicPlayerInstance.setMediaPlayer(mediaPlayer);
    }

    public void saveLoopState(int loopMode) {
        SharedPreferences sharedPreferences = getSharedPreferences("MusicPlayerPreferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("loop_mode", loopMode);
        editor.apply();
        notifyLoopChanged(loopMode);
    }
    public void saveShuffleState(boolean mode){
        SharedPreferences prefs = getSharedPreferences("MusicPlayerPreferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("isShuffleEnabled", mode);
        editor.apply();
        notifyShuffleChanged(mode);
    }
    public boolean loadShuffleState(){
        SharedPreferences sharedPreferences = getSharedPreferences("MusicPlayerPreferences", MODE_PRIVATE);
        return sharedPreferences.getBoolean("isShuffleEnabled", false);
    }
    public int loadLoopState() {
        SharedPreferences sharedPreferences = getSharedPreferences("MusicPlayerPreferences", MODE_PRIVATE);
        return sharedPreferences.getInt("loop_mode", LOOP_ALL);
    }

    private void updateMediaSessionMetadata() {
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer.getDuration())
                .build();
        mediaSessionCompat.setMetadata(metadata);
    }

    private void updatePlaybackState(int state) {
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SEEK_TO |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(
                        state,
                        mediaPlayer.getCurrentPosition(),
                        1.0f
                );
        if(mediaPlayer.isPlaying()){
            notifyPlaybackStateChanged(true);
        }else{
            notifyPlaybackStateChanged(false);
        }
        mediaSessionCompat.setPlaybackState(stateBuilder.build());
    }

    public boolean IsMusicPlaying(){
        if(mediaPlayer.isPlaying()){
            return true;
        }else{
            return false;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Music Player Service Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification getNotification() {
        // Create PendingIntents with FLAG_IMMUTABLE
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent previousIntent = PendingIntent.getBroadcast(
                this, 1, new Intent(ACTION_PREVIOUS), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent playPauseIntent = PendingIntent.getBroadcast(
                this, 2, new Intent(ACTION_PLAY), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent nextIntent = PendingIntent.getBroadcast(
                this, 3, new Intent(ACTION_NEXT), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

//        PendingIntent StopIntent = PendingIntent.getBroadcast(
//                this, 4, new Intent(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, MusicPlayerService.class);

        stopIntent.setAction(ACTION_STOP);
//        PendingIntent stopPendingIntent = PendingIntent.getService(
//                this, 5, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Prepare large icon
        Bitmap largeIcon = (albumArt != null) ? albumArt : BitmapFactory.decodeResource(getResources(), R.drawable.default_icon);

        // Build notification
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(artist)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setLargeIcon(largeIcon)
                .setSilent(true)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.service_skip_previous, "Previous", previousIntent)
                .addAction(isPlaying ? R.drawable.service_pause : R.drawable.service_play, "Play/Pause", playPauseIntent)
                .addAction(R.drawable.service_skip_next, "Next", nextIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                        .setMediaSession(mediaSessionCompat.getSessionToken()))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
    }

    public void togglePlayPause() {
        if(mediaPlayer == null && MusicPlayerInstance.getMusicPlayerService() == null && !mediaSessionCompat.isActive()){
            setMediaSession();
        }
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            handler.removeCallbacks(updateSeekbarRunnable);
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
            isPlaying = false;
        } else {
            mediaPlayer.start();
            startSeekbarUpdates();
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
            isPlaying = true;

            // Optionally, request audio focus again if needed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.requestAudioFocus(audioFocusRequest);
            } else {
                audioManager.requestAudioFocus(MusicPlayerService.this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }
        }
        updateNotification();
    }
    public int getDuration(){
        return mediaPlayer.getDuration();
    }

    private void updateCurrentMetadata(int songIndex) {
        String songPath = getSongPath(songIndex);

        if (songPath != null) {
            Log.d("MusicPlayerService", "Fetching metadata for song at path: " + songPath);

            // Run on the main thread to update the UI

            Executors.newSingleThreadExecutor().execute(() -> {
                // Extract metadata
                MediaMetaDataHelper.MediaMetadata metadata = MediaMetaDataHelper.extractMetadata(this, songPath);
                title = metadata.title;
                artist = metadata.artist;
                new Handler(Looper.getMainLooper()).post(() -> {
                    Glide.with(this)
                            .asBitmap()
                            .load(metadata.albumArt) // Assume you're returning a Uri from metadata
                            .placeholder(R.drawable.default_icon)
                            .into(new SimpleTarget<Bitmap>() {
                                @Override
                                public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                                    albumArt = resource;
                                    // Update MediaSession metadata and notification
                                    updateMediaSessionMetadata();
                                    updateNotification();
                                }

                                @Override
                                public void onLoadFailed(Drawable errorDrawable) {
                                    albumArt = BitmapFactory.decodeResource(getResources(), R.drawable.default_icon);
                                    updateMediaSessionMetadata();
                                    updateNotification();
                                }
                            });
                });
            });

        } else {
            Log.e("MusicPlayerService", "Song path is null for index: " + songIndex);
        }
    }

    private void updateNotification() {
        Notification notification = getNotification();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(1, notification);
        }
        Log.d("MusicPlayerService", "Notification updated with Title: " + title + ", Artist: " + artist);
        startForeground(1, notification); // Optional: force update foreground service notification
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            requestAudioFocus();
            String action = intent.getAction();
            if (ACTION_PLAY.equals(action)) {
                isPlaying = true;
                int index = intent.getIntExtra("songIndex", 0);
                songPaths = intent.getStringArrayListExtra("songsList");
                playSong(index);
            } else if (ACTION_STOP.equals(action)) {
                stopSelf();
            }
        }
        return START_STICKY;
    }
    private void requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setOnAudioFocusChangeListener(this)
                    .build();

            int result = audioManager.requestAudioFocus(audioFocusRequest);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                // Focus gained, start or continue playback
//                playMusic();
            }
        } else {
            // For pre-Oreo devices
            int result = audioManager.requestAudioFocus(this,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                // Focus gained, start or continue playback
//                playMusic();
            }
        }
    }

    private void playSong(int index) {
        if(mediaPlayer == null){
            setMediaSession();
        }
        String songPath = getSongPath(index);
        Log.d("MusicPlayerService", "Playing song: " + songPath);
        if (songPath != null) {
            mediaPlayer.reset();
            try {
                mediaPlayer.setDataSource(songPath);
                mediaPlayer.prepare();
                mediaPlayer.start();
                currentSongIndex = index;

                // Update the metadata and notification before setting the playback state
                startSeekbarUpdates();
                updateCurrentMetadata(index);
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
                updateNotification();
                notifySongChange(songPath);

            } catch (Exception e) {
                Log.e("MusicPlayerService", "Error playing song", e);
            }
        }
    }

    public void playNextSong() {
        if (!isHandlingMediaAction) {
            isHandlingMediaAction = true;
            isPlaying = true;

            if (loadShuffleState()) {
                // Play a random song if shuffle is enabled
                if(shufflePlayed.size() == getSongsCount()){
                    shufflePlayed.clear();
                    Log.d("shuffleCheck", "List Cleared");
                }
                int nextIndex;
                do{
                    nextIndex = new Random().nextInt(getSongsCount());
                }while (shufflePlayed.contains(nextIndex) && shufflePlayed.size() < getSongsCount());

                shufflePlayed.add(nextIndex);
                Log.d("shuffleCheck", "Playing : " + nextIndex);
                playSong(nextIndex);
            } else {
                // Play the next song in the sequence if shuffle is not enabled
                int nextIndex = currentSongIndex + 1;
                if (nextIndex < getSongsCount()) {
                    playSong(nextIndex);
                } else {
                    playSong(0); // Loop back to the first song
                }
            }

            new Handler().postDelayed(() -> isHandlingMediaAction = false, MEDIA_ACTION_DEBOUNCE_DELAY_MS);
        }
    }

    public void playPreviousSong() {
        if (!isHandlingMediaAction) {
            isHandlingMediaAction = true;
            isPlaying = true;

            if (loadShuffleState()) {
                // Play a random song if shuffle is enabled
                if(shufflePlayed.size() == getSongsCount()){
                    shufflePlayed.clear();
                    Log.d("shuffleCheck", "List Cleared");
                }
                int prevIndex;
                do{
                    prevIndex = new Random().nextInt(getSongsCount());
                }while (shufflePlayed.contains(prevIndex) && shufflePlayed.size() < getSongsCount());

                shufflePlayed.add(prevIndex);
                Log.d("shuffleCheck", "Playing : " + prevIndex);
                playSong(prevIndex);
            } else {
                // Play the previous song in the sequence if shuffle is not enabled
                int prevIndex = currentSongIndex - 1;
                if (prevIndex < 0) {
                    prevIndex = getSongsCount() - 1; // Loop back to the last song
                }
                playSong(prevIndex);
            }

            new Handler().postDelayed(() -> isHandlingMediaAction = false, MEDIA_ACTION_DEBOUNCE_DELAY_MS);
        }
    }


    private String getSongPath(int index) {
        if (index >= 0 && index < songPaths.size()) {
            return songPaths.get(index);
        }
        return null;
    }

    private int getSongsCount() {
        return songPaths.size();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateSeekbarRunnable);
        // Release the MediaPlayer if it's not null
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (IllegalStateException e) {
                Log.e("MusicPlayerService", "Error stopping MediaPlayer", e);
            } finally {
                try {
                    mediaPlayer.release();
                } catch (IllegalStateException e) {
                    Log.e("MusicPlayerService", "Error releasing MediaPlayer", e);
                } finally {
                    mediaPlayer = null;
                }
            }
        }

        // Release the MediaSessionCompat
        mediaSessionCompat.setActive(false);
        mediaSessionCompat.release();

        try{
            unregisterReceiver(mediaActionReceiver);
            unregisterReceiver(noisyAudioStreamReceiver);
        }catch (Exception e){
            Log.d("TAG", "No Receiver");
        }
        // Clear the instance
        MusicPlayerInstance.setMusicPlayerService(null);

        // Stop foreground service and stop self
        stopForeground(true);
        stopSelf();
    }
}