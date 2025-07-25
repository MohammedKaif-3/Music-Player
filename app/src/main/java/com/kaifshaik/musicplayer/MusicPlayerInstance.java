package com.kaifshaik.musicplayer;

import android.app.Application;
import android.media.MediaPlayer;

public class MusicPlayerInstance extends Application {
    private static MusicPlayerService musicPlayerService;
    private static MediaPlayer mediaPlayer;
    private static boolean isAdded;
    private static boolean isRemoved;
    private static int playingQueue;

    public static int getPlayingQueue() {
        return playingQueue;
    }

    public static void setPlayingQueue(int playingQueue) {
        MusicPlayerInstance.playingQueue = playingQueue;
    }

    public static boolean isIsAdded() {
        return isAdded;
    }

    public static void setIsAdded(boolean isAdded) {
        MusicPlayerInstance.isAdded = isAdded;
    }

    public static boolean isIsRemoved() {
        return isRemoved;
    }

    public static void setIsRemoved(boolean isRemoved) {
        MusicPlayerInstance.isRemoved = isRemoved;
    }

    public static MusicPlayerService getMusicPlayerService() {
        return musicPlayerService;
    }

    public static MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    public static void setMediaPlayer(MediaPlayer mediaPlayer) {
        MusicPlayerInstance.mediaPlayer = mediaPlayer;
    }

    public static void setMusicPlayerService(MusicPlayerService service) {
        musicPlayerService = service;
    }
}
