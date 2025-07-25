package com.kaifshaik.musicplayer.adapters;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.kaifshaik.musicplayer.DownloadsFragment;
import com.kaifshaik.musicplayer.FavouritesFragment;
import com.kaifshaik.musicplayer.SongsFragment;
import com.kaifshaik.musicplayer.model.Song;
import java.util.ArrayList;

public class ViewPagerAdapter extends FragmentStateAdapter {
    ArrayList<Song> allSongs;
    ArrayList<Song> downloadedSongs;

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity,
                            ArrayList<Song> allSongs,
                            ArrayList<Song> downloadedSongs) {
        super(fragmentActivity);
        this.allSongs = allSongs;
        this.downloadedSongs = downloadedSongs;
    }

    @Override
    public Fragment createFragment(int position) {
        Fragment fragment;
        switch (position) {
            case 0:
                SongsFragment songsFragment = new SongsFragment();
                Bundle songsBundle = new Bundle();
                songsBundle.putParcelableArrayList("allSongs", allSongs);
                songsFragment.setArguments(songsBundle);
                fragment = songsFragment;
                break;
            case 1:
                DownloadsFragment downloadsFragment = new DownloadsFragment();
                Bundle downloadsBundle = new Bundle();
                downloadsBundle.putParcelableArrayList("downloadedSongs", downloadedSongs);
                downloadsFragment.setArguments(downloadsBundle);
                fragment = downloadsFragment;
                break;
            case 2:
                fragment = new FavouritesFragment();
                break;
            default:
                fragment = new Fragment(); // Fallback fragment
                break;
        }
        return fragment;
    }

    @Override
    public int getItemCount() {
        return 3; // Number of tabs
    }

}

