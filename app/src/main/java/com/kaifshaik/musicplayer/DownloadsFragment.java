package com.kaifshaik.musicplayer;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.kaifshaik.musicplayer.adapters.DownloadsRecyclerViewAdapter;
import com.kaifshaik.musicplayer.model.Song;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class DownloadsFragment extends Fragment {

    ArrayList<Song> audioFiles;
    RecyclerView recyclerView2;
    DownloadsRecyclerViewAdapter recyclerViewAdapter;
    ImageView scrollTop2;
    TextView songsCount2;
    LinearLayout noData;
    public DownloadsFragment() {
        // Required empty public constructor
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        View view =  inflater.inflate(R.layout.fragment_downloads, container, false);
        recyclerView2 = view.findViewById(R.id.recyclerView2);
        recyclerView2.setLayoutManager(new LinearLayoutManager(getContext()));
        scrollTop2 = view.findViewById(R.id.scrollTop2);
        songsCount2 = view.findViewById(R.id.songs_count2);
        noData = view.findViewById(R.id.no_data);

        if (getArguments() != null) {
            audioFiles = getArguments().getParcelableArrayList("downloadedSongs");
            // Use audioFiles to update UI
            Set<Song> uniqueSongsSet = new LinkedHashSet<>(audioFiles); // Maintain order
            audioFiles = new ArrayList<>(uniqueSongsSet);
            recyclerViewAdapter = new DownloadsRecyclerViewAdapter(requireContext(), audioFiles);
            recyclerView2.setAdapter(recyclerViewAdapter);
        }

        String count2 = audioFiles.size() + " Songs";
        songsCount2.setText(count2);

        recyclerView2.addOnScrollListener(new RecyclerView.OnScrollListener() {
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
                        scrollTop2.setVisibility(View.VISIBLE);
                    }else{
                        scrollTop2.setVisibility(View.GONE);
                    }
                }
            }
        });

        scrollTop2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recyclerView2.smoothScrollToPosition(0);
            }
        });

        if(audioFiles.size() == 0){
            noData.setVisibility(View.VISIBLE);
        }else{
            noData.setVisibility(View.GONE);
        }

        return view;
    }
}