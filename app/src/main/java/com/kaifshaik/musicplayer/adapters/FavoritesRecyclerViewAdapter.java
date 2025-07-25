package com.kaifshaik.musicplayer.adapters;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.airbnb.lottie.LottieAnimationView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.kaifshaik.musicplayer.MediaMetaDataHelper;
import com.kaifshaik.musicplayer.MusicPlayerInstance;
import com.kaifshaik.musicplayer.MusicPlayerService;
import com.kaifshaik.musicplayer.Player;
import com.kaifshaik.musicplayer.R;
import com.kaifshaik.musicplayer.model.Song;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FavoritesRecyclerViewAdapter extends RecyclerView.Adapter<FavoritesRecyclerViewAdapter.MyViewHolder> implements MusicPlayerService.OnSongChangeListener {

    private final Context context;
    private final ArrayList<Song> favoritesList;
    private MusicPlayerService musicPlayerService;
    private boolean isBound = false;
    int currentSongIndex = -1;
    boolean state;
    String currentSongPath;
    private ArrayList<String> pathsList;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final LruCache<String, MediaMetaDataHelper.MediaMetadata> metadataCache = new LruCache<>(100);

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicPlayerService.LocalBinder binder = (MusicPlayerService.LocalBinder) service;
            musicPlayerService = binder.getService();
            isBound = true;
            Log.d("playeractivity", "Binding successful");

            // Register the Player activity as a listener
            musicPlayerService.addOnSongChangeListener(FavoritesRecyclerViewAdapter.this);
            currentSongPath = musicPlayerService.getCurrentSongPath();
            currentSongIndex = musicPlayerService.getCurrentSongIndex();
            notifyItemChanged(currentSongIndex);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        Intent intent = new Intent(context, MusicPlayerService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        if(isBound){
            currentSongIndex = musicPlayerService.getCurrentSongIndex();
            currentSongPath = musicPlayerService.getCurrentSongPath();
            notifyItemChanged(currentSongIndex);
        }
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        if (isBound) {
            musicPlayerService.removeOnSongChangeListener(FavoritesRecyclerViewAdapter.this);
            context.unbindService(serviceConnection);
            isBound = false;
        }
        executorService.shutdown();
    }

    public FavoritesRecyclerViewAdapter(Context context, ArrayList<Song> favoritesList) {
        this.context = context;
        this.favoritesList = favoritesList;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.row_item, parent, false);
        pathsList = new ArrayList<>();
        for (Song song1 : favoritesList) {
            pathsList.add(song1.getPath());
        }
        return new MyViewHolder(view);
    }
    @Override
    public void onBindViewHolder(@NonNull FavoritesRecyclerViewAdapter.MyViewHolder holder, int position, @NonNull List<Object> payloads) {
        Song song = favoritesList.get(position);

        if (!payloads.isEmpty()) {
            Object payload = payloads.get(0);

            if (payload instanceof Boolean) {
                boolean shouldAnimate = (Boolean) payload;
                if (shouldAnimate) {
                    holder.itemView.setAnimation(AnimationUtils.loadAnimation(context, R.anim.no_animation));
                } else {
                    holder.itemView.clearAnimation();
                }
            } else {
                // Handle other types if necessary or log a warning
                Log.w("RecyclerViewAdapter", "Unexpected payload type: " + payload.getClass().getSimpleName());
            }
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }

        if (song.getPath().equals(currentSongPath)) {
            holder.titleTextView.setTextColor(ContextCompat.getColor(context, R.color.yellow));
            holder.iconImageView.setAlpha(0.4f);
            holder.lottieAnimationView.setVisibility(View.VISIBLE);
            if(state){
                holder.lottieAnimationView.resumeAnimation();
            }else{
                holder.lottieAnimationView.pauseAnimation();
            }
        } else {
            holder.titleTextView.setTextColor(ContextCompat.getColor(context, R.color.secondary));
            holder.iconImageView.setAlpha(1f);
            holder.lottieAnimationView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        if(isBound){
            currentSongIndex = musicPlayerService.getCurrentSongIndex();
        }
        Song song = favoritesList.get(position);

        // Clear previous content
        if (!song.getPath().equals(holder.titleTextView.getTag())) {
            holder.titleTextView.setText("");
            holder.artistTextView.setText("");
            holder.iconImageView.setImageResource(R.drawable.default_icon);

            holder.titleTextView.setTag(song.getPath());
        }
        holder.itemView.setAnimation(AnimationUtils.loadAnimation(context, R.anim.recycle));

        MediaMetaDataHelper.MediaMetadata metadata = metadataCache.get(song.getPath());
        if (metadata != null) {
            updateViewHolder(holder, metadata);
        } else {
            // Use an executor for background thread
            executorService.execute(() -> {
                MediaMetaDataHelper.MediaMetadata extractedMetadata = MediaMetaDataHelper.extractMetadata(context, song.getPath());
                metadataCache.put(song.getPath(), extractedMetadata);

                new Handler(Looper.getMainLooper()).post(() -> updateViewHolder(holder, extractedMetadata));
            });
        }
        updateCurrentSongUI(holder, song.getPath());

        // Set a click listener for the item
        holder.itemView.setOnClickListener(v -> {
            if(song.getPath().equals(currentSongPath)){
                if(MusicPlayerInstance.getMusicPlayerService() != null){
                    Intent playerIntent = new Intent(context, Player.class);
                    context.startActivity(playerIntent);
                    if (context instanceof Activity) {
                        ((Activity) context).overridePendingTransition(R.anim.slide_up, R.anim.no_animation);
                    }
                }
            }else{
                Executors.newSingleThreadExecutor().execute(() -> {
                    // Stop the existing service if it is running
                    if (musicPlayerService != null && isBound) {
                        musicPlayerService.stopSelf();
                    }

                    // Start the service to play the selected song
                    Intent serviceIntent = new Intent(context, MusicPlayerService.class);
                    MusicPlayerInstance.setPlayingQueue(3);
                    serviceIntent.setAction(MusicPlayerService.ACTION_PLAY);
                    serviceIntent.putExtra("songIndex", position);
                    serviceIntent.putStringArrayListExtra("songsList", pathsList);
                    context.startService(serviceIntent);
                });
            }
        });
    }
    private void updateViewHolder(FavoritesRecyclerViewAdapter.MyViewHolder holder, MediaMetaDataHelper.MediaMetadata metadata) {
        holder.titleTextView.setText(metadata.getTitle());
        holder.artistTextView.setText(metadata.getArtist());

        Glide.with(context)
                .load(metadata.getAlbumArt())
                .apply(RequestOptions.placeholderOf(R.drawable.default_icon))
                .into(holder.iconImageView);
    }

    private void updateCurrentSongUI(FavoritesRecyclerViewAdapter.MyViewHolder holder, String songPath) {
        if (songPath.equals(currentSongPath)) {
            holder.titleTextView.setTextColor(ContextCompat.getColor(context, R.color.yellow));
            holder.iconImageView.setAlpha(0.4f);
            holder.lottieAnimationView.setVisibility(View.VISIBLE);
            if(state){
                holder.lottieAnimationView.resumeAnimation();
            }else{
                holder.lottieAnimationView.pauseAnimation();
            }
        } else {
            holder.titleTextView.setTextColor(ContextCompat.getColor(context, R.color.secondary));
            holder.iconImageView.setAlpha(1f);
            holder.lottieAnimationView.setVisibility(View.GONE);
        }
    }

    public void updateDataAdded(List<Song> newAudioFiles) {
        this.favoritesList.clear();
        this.favoritesList.addAll(newAudioFiles);
        notifyItemChanged(this.favoritesList.size(), false);
    }
    public void updateDataRemoved(List<Song> newAudioFiles) {
        this.favoritesList.clear();
        this.favoritesList.addAll(newAudioFiles);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return favoritesList != null ? favoritesList.size() : 0;
    }

    @Override
    public void onSongChanged(String songPath) {
        // Check if pathsList is not null and contains songs
        if (pathsList == null || pathsList.isEmpty()) {
            // Handle case when pathsList is null or empty
            return;
        }

        // Ensure songPath is not null
        if (songPath == null) {
            // Handle case when songPath is null
            return;
        }

        // Update the current song path
        currentSongPath = songPath;

        // Get the index of the new current song
        int newSongIndex = pathsList.indexOf(songPath);

        // Only proceed if the song exists in the list
        if (newSongIndex != -1) {
            // Notify the adapter to update the previous item without animation, if applicable
            if (currentSongIndex != -1 && currentSongIndex != newSongIndex) {
                // Unhighlight other songs
                for (int i = 0; i < pathsList.size(); i++) {
                    if (i != newSongIndex) {
                        notifyItemChanged(i, false); // Remove highlight from other songs
                    }
                }
            }

            // Update the index for the new song
            currentSongIndex = newSongIndex;

            // Notify the adapter to update the new item
            notifyItemChanged(currentSongIndex, true); // Highlight the new song
        } else {
            // Handle the case where the songPath is not found in pathsList
        }
    }


    @Override
    public void onPlaybackStateChanged(boolean state) {
        this.state = state;
        executorService.execute(() -> {
            int position = findPositionByPath(currentSongPath);
            new Handler(Looper.getMainLooper()).post(() -> {
                if(position != -1){
                    notifyItemChanged(position);
                }
            });
        });
    }
    public int findPositionByPath(String targetPath) {
        for (int i = 0; i < favoritesList.size(); i++) {
            if (favoritesList.get(i).getPath().equals(targetPath)) {
                return i; // Return the position if the path matches
            }
        }
        return -1; // Return -1 if the song was not found
    }

    @Override
    public void onSeekbarChanged(int position) {

    }

    @Override
    public void onLoopChanges(int state) {

    }

    @Override
    public void onShuffleChanges(boolean state) {

    }



    public static class MyViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView, artistTextView;
        ImageView iconImageView;
        LottieAnimationView lottieAnimationView;
        public MyViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.title);
            artistTextView = itemView.findViewById(R.id.artist);
            iconImageView = itemView.findViewById(R.id.icon);
            lottieAnimationView = itemView.findViewById(R.id.lottieAnimationView);
        }
    }
}
