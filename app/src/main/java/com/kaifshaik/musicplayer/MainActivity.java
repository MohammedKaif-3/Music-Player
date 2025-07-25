package com.kaifshaik.musicplayer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;
import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.kaifshaik.musicplayer.adapters.ViewPagerAdapter;
import com.kaifshaik.musicplayer.data.DbHandler;
import com.kaifshaik.musicplayer.model.Song;
import com.kaifshaik.musicplayer.params.AlbumArtHelper;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements MusicPlayerService.OnSongChangeListener{
    TabLayout tabLayout;
    ViewPager2 viewPager2;
    ArrayList<Song> audioFiles, downloadedAudioFiles;
    private static final int REQUEST_READ_PERMISSIONS = 100;
    private static final int REQUEST_POST_NOTIFICATIONS = 1002;
    TextView barTitle, barArtist;
    ImageView barIcon, barPlayPause, queue;
    ImageView barHeart;
    LinearProgressIndicator barSeekbar;
    LinearLayout barPlayer;
    private MusicPlayerService musicService;
    private boolean isBound = false;
    ConstraintLayout bottomPlayer;
    DbHandler dbHandler;
    String currentPlayingSong;
    CardView cardRepeat, cardShuffle;
    ImageView cardRepeatIcon, cardShuffleIcon;
    private static final int LOOP_NONE = 0;
    private static final int LOOP_ONE = 1;
    private static final int LOOP_ALL = 2;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicPlayerService.LocalBinder binder = (MusicPlayerService.LocalBinder) service;
            musicService = binder.getService();
            musicService.addOnSongChangeListener(MainActivity.this);
            isBound = true;

            String currentSong = musicService.getCurrentSongPath();
            Executors.newSingleThreadExecutor().execute(() -> {
                ArrayList<Song> favoritesList = dbHandler.getAllFavouriteSongs();
                MediaMetaDataHelper.MediaMetadata metadata = MediaMetaDataHelper.extractMetadata(MainActivity.this, currentSong);

                new Handler(Looper.getMainLooper()).post(() -> {
                    updatePlayPauseButtonState();
                    currentPlayingSong = currentSong;
                    barTitle.setText(metadata.getTitle());
                    barArtist.setText(metadata.getArtist());

                    // Update heart icon based on favorites
                    barHeart.setImageResource(favoritesList.stream()
                            .anyMatch(song -> song.getPath().equalsIgnoreCase(currentSong)) ?
                            R.drawable.bar_heart_filled :
                            R.drawable.bar_heart);

                    barSeekbar.setMax((int) metadata.getDuration());
                    Glide.with(MainActivity.this)
                            .load(metadata.getAlbumArt())
                            .apply(RequestOptions.placeholderOf(R.drawable.default_icon))
                            .into(barIcon);

                    // Show bottom player if title is not unknown
                    bottomPlayer.setVisibility(barTitle.getText().toString().equalsIgnoreCase("unknown title") ? View.GONE : View.VISIBLE);
                });
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicService = null;
            isBound = false;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MusicPlayerService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onSongChanged(String songPath) {
        Executors.newSingleThreadExecutor().execute(() -> {
            ArrayList<Song> favoritesList = dbHandler.getAllFavouriteSongs();
            MediaMetaDataHelper.MediaMetadata metadata = MediaMetaDataHelper.extractMetadata(this, songPath);

            new Handler(Looper.getMainLooper()).post(() -> {
                currentPlayingSong = songPath;
                barTitle.setText(metadata.getTitle());
                barArtist.setText(metadata.getArtist());
                barSeekbar.setMax((int) metadata.getDuration());

                // Update heart icon based on favorites
                barHeart.setImageResource(favoritesList.stream()
                        .anyMatch(song -> song.getPath().equalsIgnoreCase(songPath)) ?
                        R.drawable.bar_heart_filled :
                        R.drawable.bar_heart);

                Glide.with(this)
                        .load(metadata.getAlbumArt())
                        .apply(RequestOptions.placeholderOf(R.drawable.default_icon))
                        .into(barIcon);

                // Show bottom player if it's currently gone
                if (bottomPlayer.getVisibility() == View.GONE) {
                    bottomPlayer.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ArrayList<Song> favoritesList = dbHandler.getAllFavouriteSongs();
        barHeart.setImageResource(favoritesList.stream()
                .anyMatch(song -> song.getPath().equalsIgnoreCase(currentPlayingSong)) ?
                R.drawable.bar_heart_filled :
                R.drawable.bar_heart);
    }

    @Override
    public void onPlaybackStateChanged(boolean state) {
        barPlayPause.setImageResource(state ? R.drawable.bar_play : R.drawable.bar_pause);
    }

    @Override
    public void onSeekbarChanged(int position) {
        runOnUiThread(() -> barSeekbar.setProgress(position));
    }

    @Override
    public void onLoopChanges(int state) {
        updateLoop(state);
    }

    @Override
    public void onShuffleChanges(boolean state) {
        updateShuffle(state);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setting Status Bar Color
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary));
        setContentView(R.layout.activity_main);

        initializeUIComponents();
        dbHandler = new DbHandler(this);

        // Requesting Permission if not allowed
        requestReadPermissions();

        if (isBound && musicService != null) {
            updatePlayPauseButtonState();
        }

        // Load saved preferences
        SharedPreferences sharedPreferences = getSharedPreferences("MusicPlayerPreferences", Context.MODE_PRIVATE);
        updateLoop(sharedPreferences.getInt("loop_mode", LOOP_ALL));
        updateShuffle(sharedPreferences.getBoolean("isShuffleEnabled", false));

        barPlayPause.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                musicService.togglePlayPause();
            }
        });

        barPlayer.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, Player.class));
            overridePendingTransition(R.anim.slide_up, R.anim.no_animation);
        });

        barHeart.setOnClickListener(v -> handleHeartClick());
        cardRepeat.setOnClickListener(v -> handleRepeatClick());
        cardShuffle.setOnClickListener(v -> handleShuffleClick());
        queue.setOnClickListener(v -> handleQueueClick());
    }

    private void handleHeartClick() {
        MediaMetaDataHelper.MediaMetadata metadata = MediaMetaDataHelper.extractMetadata(this, currentPlayingSong);
        String album = AlbumArtHelper.getAlbumArtPath(this, currentPlayingSong);
        Song song = new Song(metadata.getTitle(), currentPlayingSong, String.valueOf(metadata.getDuration()), album, metadata.getArtist());

        if (dbHandler.contains(song)) {
            dbHandler.removeFromFavourites(song);
            barHeart.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.bar_heart));
            MusicPlayerInstance.setIsRemoved(true);
            showToast("Removed from Favourites");
        } else {
            dbHandler.addToFavourites(song);
            barHeart.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.bar_heart_filled));
            MusicPlayerInstance.setIsAdded(true);
            showToast("Added to Favourites");
        }
    }

    private void handleRepeatClick() {
        int state = musicService.loadLoopState();
        int newState;

        switch (state) {
            case LOOP_ALL:
                newState = LOOP_NONE;
                cardRepeatIcon.setImageResource(R.drawable.no_repeat);
                cardRepeatIcon.setAlpha(0.5f);
                break;
            case LOOP_NONE:
                newState = LOOP_ONE;
                cardRepeatIcon.setImageResource(R.drawable.yellow_repeat_one);
                cardRepeatIcon.setAlpha(1f);
                break;
            default:
                newState = LOOP_ALL;
                cardRepeatIcon.setImageResource(R.drawable.yellow_repeat_all);
                cardRepeatIcon.setAlpha(1f);
                break;
        }

        musicService.saveLoopState(newState);
        musicService.updateLoopMode();
    }

    private void handleShuffleClick() {
        if (isBound) {
            boolean shuffleEnabled = musicService.loadShuffleState();
            cardShuffleIcon.setImageResource(shuffleEnabled ? R.drawable.shuffle : R.drawable.yellow_shuffle);
            cardShuffleIcon.setAlpha(shuffleEnabled ? 0.5f : 1f);
            musicService.saveShuffleState(!shuffleEnabled);
        }
    }

    private void handleQueueClick() {
        int val = MusicPlayerInstance.getPlayingQueue() - 1;
        Log.d("myapp", String.valueOf(val));

        if (val == viewPager2.getCurrentItem()) {
            showToast("Playing this Queue Only");
        } else if (val == -1) {
            showToast("Not Playing any Queue");
        } else {
            viewPager2.setCurrentItem(val);
        }
    }

    private void showToast(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP, 0, 100); // Set gravity to center
        toast.show();
    }


    private void initializeUIComponents() {
        bottomPlayer = findViewById(R.id.bottom_player);
        bottomPlayer.setVisibility(View.GONE);
        tabLayout = findViewById(R.id.tablayout);
        viewPager2 = findViewById(R.id.viewpager);
        viewPager2.setOffscreenPageLimit(2);

        barHeart = findViewById(R.id.bar_heart);
        barSeekbar = findViewById(R.id.bar_seekbar);
        barTitle = findViewById(R.id.bar_title);
        barTitle.setSelected(true);
        barArtist = findViewById(R.id.bar_artist);
        barIcon = findViewById(R.id.bar_image);
        barPlayPause = findViewById(R.id.bar_playPause);
        barPlayer = findViewById(R.id.bar_player);

        cardRepeatIcon = findViewById(R.id.card_repeat_image);
        cardShuffleIcon = findViewById(R.id.card_shuffle_image);
        cardRepeat = findViewById(R.id.card_repeat);
        cardShuffle = findViewById(R.id.card_shuffle);

        queue = findViewById(R.id.queue);
    }

    private void updatePlayPauseButtonState() {
        if (musicService.IsMusicPlaying()) {
            barPlayPause.setImageResource(R.drawable.bar_play);
        } else {
            barPlayPause.setImageResource(R.drawable.bar_pause);
        }
    }

    public void updateLoop(int state){
        if(state == LOOP_ALL){
            cardRepeatIcon.setImageResource(R.drawable.yellow_repeat_all);
            cardRepeatIcon.setAlpha(1f);
        }else if(state == LOOP_ONE){
            cardRepeatIcon.setImageResource(R.drawable.yellow_repeat_one);
            cardRepeatIcon.setAlpha(1f);
        }else{
            cardRepeatIcon.setImageResource(R.drawable.no_repeat);
            cardRepeatIcon.setAlpha(0.5f);
        }
    }
    public void updateShuffle(boolean state){
        if(state){
            cardShuffleIcon.setImageResource(R.drawable.yellow_shuffle);
            cardShuffleIcon.setAlpha(1f);
        }else{
            cardShuffleIcon.setImageResource(R.drawable.shuffle);
            cardShuffleIcon.setAlpha(0.5f);
        }
    }

    private void requestReadPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 and above
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_MEDIA_AUDIO)) {
                    showPermissionRationale();
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQUEST_READ_PERMISSIONS);
                }
            } else {
                readAudioFiles();
            }
        } else {
            // Below Android 13
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    showPermissionRationale();
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_PERMISSIONS);
                }
            } else {
                readAudioFiles();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted
                readAudioFiles();
            } else {
                // Permission was denied
                showPermissionRationale();
            }
        }
    }

    private void showPermissionRationale() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Music Player needs permission to read audio files for proper functionality.")
                .setPositiveButton("Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create()
                .show();
    }

    private void readAudioFiles() {
        new Thread(() -> {
            audioFiles = new ArrayList<>();
            downloadedAudioFiles = new ArrayList<>();
            ContentResolver contentResolver = getContentResolver();
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA
            };
            String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";

            try (Cursor cursor = contentResolver.query(uri, projection, selection, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
                        String displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME));
                        String artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
                        String album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM));
                        long duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
                        String data = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));

                        File file = new File(data);
                        if (file.exists()) { // Ensure the file exists
                            Song song = new Song(displayName, data, String.valueOf(duration), album, artist);
                            audioFiles.add(song);
                            if (isFileDownloaded(song.getPath())) {
                                downloadedAudioFiles.add(song);
                            }
                        }
                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                Log.e("readAudioFiles", "Error reading audio files: " + e.getMessage());
            }

            runOnUiThread(() -> {
                Log.d("myapp", "Total Songs: " + audioFiles.size());
                setupViewPager(audioFiles, downloadedAudioFiles);
            });
        }).start();
    }

    private void setupViewPager(ArrayList<Song> allSongs, ArrayList<Song> downloadedSongs) {
        ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(this, allSongs, downloadedSongs);
        viewPager2.setAdapter(viewPagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager2, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("All Songs");
                    break;
                case 1:
                    tab.setText("Downloads");
                    break;
                case 2:
                    tab.setText("Favourites");
                    break;
            }
        }).attach();
    }

    private boolean isFileDownloaded(String filePath) {
        // Implement logic to check if the file is downloaded
        // For example, you could check if the file exists in a specific directory
        return filePath.startsWith("/storage/emulated/0/Download");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            musicService.removeOnSongChangeListener(MainActivity.this); // Detach the listener
            unbindService(serviceConnection);
            isBound = false;
            musicService.onDestroy();
        }
    }
}