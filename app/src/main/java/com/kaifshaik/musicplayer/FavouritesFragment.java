package com.kaifshaik.musicplayer;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.kaifshaik.musicplayer.adapters.FavoritesRecyclerViewAdapter;
import com.kaifshaik.musicplayer.data.DbHandler;
import com.kaifshaik.musicplayer.model.Song;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class FavouritesFragment extends Fragment {
    ArrayList<Song> audioFiles;
    RecyclerView recyclerView3;
    FavoritesRecyclerViewAdapter recyclerViewAdapter;
    SwipeRefreshLayout swipeRefreshLayout;
    ImageView scrollTop3;
    TextView songsCount3;
    LinearLayout noFav;
    public FavouritesFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DbHandler dbHandler = new DbHandler(requireContext());
        audioFiles = dbHandler.getAllFavouriteSongs();
        Set<Song> uniqueSongsSet = new LinkedHashSet<>(audioFiles); // Maintain order
        this.audioFiles = new ArrayList<>(uniqueSongsSet);
    }

    @Override
    public void onResume() {
        super.onResume();
        DbHandler dbHandler = new DbHandler(requireContext());
        audioFiles = dbHandler.getAllFavouriteSongs();
        Set<Song> uniqueSongsSet = new LinkedHashSet<>(audioFiles); // Maintain order
        this.audioFiles = new ArrayList<>(uniqueSongsSet);
        if(MusicPlayerInstance.isIsAdded()){
            MusicPlayerInstance.setIsAdded(false);
            recyclerViewAdapter.updateDataAdded(this.audioFiles);
        }else if(MusicPlayerInstance.isIsRemoved()){
            MusicPlayerInstance.setIsRemoved(false);
            recyclerViewAdapter.updateDataRemoved(this.audioFiles);
        }
        String count3 = audioFiles.size() + " Songs";
        songsCount3.setText(count3);

        if(audioFiles.size() == 0){
            noFav.setVisibility(View.VISIBLE);
        }else{
            noFav.setVisibility(View.GONE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_favourites, container, false);

        swipeRefreshLayout = view.findViewById(R.id.swiperefresh);
        scrollTop3 = view.findViewById(R.id.scrollTop3);
        songsCount3 = view.findViewById(R.id.songs_count3);
        String count3 = audioFiles.size() + " Songs";
        if(audioFiles.size() == 0){
            scrollTop3.setVisibility(View.GONE);
        }
        songsCount3.setText(count3);

        noFav = view.findViewById(R.id.no_fav);
        if(audioFiles.size() == 0){
            noFav.setVisibility(View.VISIBLE);
        }else{
            noFav.setVisibility(View.GONE);
        }

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                DbHandler dbHandler = new DbHandler(requireContext());
                audioFiles = dbHandler.getAllFavouriteSongs();
                Set<Song> uniqueSongsSet = new LinkedHashSet<>(audioFiles); // Maintain order
                audioFiles = new ArrayList<>(uniqueSongsSet);
                if(MusicPlayerInstance.isIsAdded()){
                    MusicPlayerInstance.setIsAdded(false);
                    recyclerViewAdapter.updateDataAdded(audioFiles);
                }else if(MusicPlayerInstance.isIsRemoved()){
                    MusicPlayerInstance.setIsRemoved(false);
                    recyclerViewAdapter.updateDataRemoved(audioFiles);
                }
                String count3 = audioFiles.size() + " Songs";
                songsCount3.setText(count3);
                swipeRefreshLayout.setRefreshing(false);

                if(audioFiles.size() == 0){
                    noFav.setVisibility(View.VISIBLE);
                }else{
                    noFav.setVisibility(View.GONE);
                }
            }
        });
        recyclerViewAdapter = new FavoritesRecyclerViewAdapter(requireContext(), audioFiles);
        recyclerView3 = view.findViewById(R.id.recyclerView3);
        recyclerView3.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView3.setAdapter(recyclerViewAdapter);

        recyclerView3.addOnScrollListener(new RecyclerView.OnScrollListener() {
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
                        scrollTop3.setVisibility(View.VISIBLE);
                    }else{
                        scrollTop3.setVisibility(View.GONE);
                    }
                }
            }
        });

        scrollTop3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recyclerView3.smoothScrollToPosition(0);
            }
        });

        return view;
    }
}