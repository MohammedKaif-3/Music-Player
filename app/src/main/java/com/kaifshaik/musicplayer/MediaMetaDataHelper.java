package com.kaifshaik.musicplayer;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import java.io.File;
import java.io.InputStream;

public class MediaMetaDataHelper {

    public static final String TAG = "MediaMetaDataHelper";

    public static MediaMetadata extractMetadata(Context context, String songPath) {
        String title = "Unknown Title";
        String artist = "<Unknown>";
        Bitmap albumArt = null;
        long duration = 0;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10 and above
                // Use MediaStore API for Android 10 and above
                Uri contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                String[] projection = {
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.ALBUM_ID,
                        MediaStore.Audio.Media.DURATION,
                };
                String selection = MediaStore.Audio.Media.DATA + "=?";
                String[] selectionArgs = new String[]{songPath};

                try (Cursor cursor = context.getContentResolver().query(
                        contentUri, projection, selection, selectionArgs, null)) {

                    if (cursor != null && cursor.moveToFirst()) {
                        int titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                        int artistIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                        int albumIdIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
                        int durationIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);

                        if (titleIndex != -1) title = cursor.getString(titleIndex);
                        if (artistIndex != -1) artist = cursor.getString(artistIndex);
                        if (durationIndex != -1) duration = cursor.getLong(durationIndex);

                        if (albumIdIndex != -1) {
                            long albumId = cursor.getLong(albumIdIndex);
                            Uri albumArtUri = ContentUris.withAppendedId(
                                    Uri.parse("content://media/external/audio/albumart"), albumId);

                            try (InputStream in = context.getContentResolver().openInputStream(albumArtUri)) {
                                if (in != null) {
                                    albumArt = BitmapFactory.decodeStream(in);
                                }
                            }
                        }
                    }
                }

            } else { // For Android 9 and below
                // Use MediaMetadataRetriever for Android 9 and below
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(songPath);

                String retrievedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                String retrievedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                String durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

                if (retrievedTitle != null) title = retrievedTitle;
                if (retrievedArtist != null) artist = retrievedArtist;
                if (durationString != null) duration = Long.parseLong(durationString);

                byte[] art = retriever.getEmbeddedPicture();
                if (art != null) {
                    albumArt = BitmapFactory.decodeByteArray(art, 0, art.length);
                }

                retriever.release();
            }

            // Fallback to extracting title from the file name if metadata is still unknown
            if ("Unknown Title".equals(title)) {
                title = new File(songPath).getName();
                // Remove file extension if present
                int dotIndex = title.lastIndexOf('.');
                if (dotIndex > 0) {
                    title = title.substring(0, dotIndex);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error extracting metadata", e);
        }

        return new MediaMetadata(title, artist, albumArt, duration);
    }

    public static class MediaMetadata {
        public final String title;
        public final String artist;
        public final Bitmap albumArt;
        public final long duration; // Duration in milliseconds

        public MediaMetadata(String title, String artist, Bitmap albumArt, long duration) {
            this.title = title;
            this.artist = artist;
            this.albumArt = albumArt;
            this.duration = duration;
        }

        public String getTitle() {
            return title;
        }

        public String getArtist() {
            return artist;
        }

        public Bitmap getAlbumArt() {
            return albumArt;
        }

        public long getDuration() {
            return duration;
        }

    }
}
