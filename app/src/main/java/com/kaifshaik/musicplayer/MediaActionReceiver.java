package com.kaifshaik.musicplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Objects;

public class MediaActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        MusicPlayerService service = MusicPlayerInstance.getMusicPlayerService();

        if (service != null) {
            switch (Objects.requireNonNull(action)) {
                case MusicPlayerService.ACTION_PLAY:
                    service.togglePlayPause();  // Implement this method in your service
                    break;
                case MusicPlayerService.ACTION_PREVIOUS:
                    service.playPreviousSong();  // Implement this method in your service
                    break;
                case MusicPlayerService.ACTION_NEXT:
                    service.playNextSong();  // Implement this method in your service
                    break;
                case MusicPlayerService.ACTION_STOP:
                    service.onDestroy();
                    break;
            }
        }
    }
}
