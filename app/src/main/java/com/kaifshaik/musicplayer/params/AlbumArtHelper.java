package com.kaifshaik.musicplayer.params;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

public class AlbumArtHelper {
    public static String getAlbumArtPath(Context context, String songPath) {
        String albumArtPath = null;

        // Query to get the album ID from the song path
        String[] projection = {MediaStore.Audio.Media.ALBUM_ID};
        String selection = MediaStore.Audio.Media.DATA + " = ?";
        String[] selectionArgs = {songPath};

        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
        );

        if (cursor != null && cursor.moveToFirst()) {
            int albumIdIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
            long albumId = cursor.getLong(albumIdIndex);

            // Close the cursor
            cursor.close();

            // Query to get the album art path from the album ID
            projection = new String[]{MediaStore.Audio.Albums.ALBUM_ART};
            selection = MediaStore.Audio.Albums._ID + " = ?";
            selectionArgs = new String[]{String.valueOf(albumId)};

            cursor = context.getContentResolver().query(
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int albumArtIndex = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART);
                albumArtPath = cursor.getString(albumArtIndex);

                // Close the cursor
                cursor.close();
            }
        }

        return albumArtPath;
    }
}

