package com.kaifshaik.musicplayer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.kaifshaik.musicplayer.adapters.RecyclerViewAdapter;
import com.kaifshaik.musicplayer.model.Song;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class SongsFragment extends Fragment implements MusicPlayerService.OnSongChangeListener{
    ArrayList<Song> audioFiles;
    RecyclerView recyclerView;
    RecyclerViewAdapter recyclerViewAdapter;
    TextView songsCount;
    ImageView scrollTop;
    LinearLayout noData2;
    private MusicPlayerService musicService;
    private boolean isBound = false;
    public SongsFragment() {
        // Required empty public constructor
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicPlayerService.LocalBinder binder = (MusicPlayerService.LocalBinder) service;
            musicService = binder.getService();
            musicService.addOnSongChangeListener(SongsFragment.this);
            isBound = true;

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicService = null;
            isBound = false;
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isBound) {
            musicService.removeOnSongChangeListener(SongsFragment.this); // Detach the listener
            requireContext().unbindService(serviceConnection);
            isBound = false;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(requireContext(), MusicPlayerService.class);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        View view =  inflater.inflate(R.layout.fragment_songs, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        songsCount = view.findViewById(R.id.songs_count);
        noData2 = view.findViewById(R.id.no_data2);
        scrollTop = view.findViewById(R.id.scrollTop);
        scrollTop.setVisibility(View.GONE);

        if (getArguments() != null) {
            audioFiles = getArguments().getParcelableArrayList("allSongs");
            // Use audioFiles to update UI
            Set<Song> uniqueSongsSet = new LinkedHashSet<>(audioFiles); // Maintain order
            audioFiles = new ArrayList<>(uniqueSongsSet);
            recyclerViewAdapter = new RecyclerViewAdapter(getContext(), audioFiles);
            recyclerView.setAdapter(recyclerViewAdapter);
        }
        String count = audioFiles.size() + " Songs";
        songsCount.setText(count);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                // Get the layout manager of the RecyclerView
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();

                // Check if layoutManager is not null
                if (layoutManager != null) {
                    // Find the position of the first visible item
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                    // Perform your operation if the first item is not visible
                    if (firstVisibleItemPosition != 0) {
                        // Your operation here
                        scrollTop.setVisibility(View.VISIBLE);
                    }else{
                        scrollTop.setVisibility(View.GONE);
                    }
                }
            }
        });

        scrollTop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recyclerView.smoothScrollToPosition(0);
            }
        });

        if(audioFiles.size() == 0){
            noData2.setVisibility(View.VISIBLE);
        }else{
            noData2.setVisibility(View.GONE);
        }

        return view;
    }

    @Override
    public void onSongChanged(String songPath) {

    }

    @Override
    public void onPlaybackStateChanged(boolean state) {

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
}