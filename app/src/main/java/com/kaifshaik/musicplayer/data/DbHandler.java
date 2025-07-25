package com.kaifshaik.musicplayer.data;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.kaifshaik.musicplayer.model.Song;
import com.kaifshaik.musicplayer.params.Params;
import java.io.Serializable;
import java.util.ArrayList;

public class DbHandler extends SQLiteOpenHelper implements Serializable{

    public DbHandler(Context context) {
        super(context, Params.DB_NAME, null, Params.DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String query = "CREATE TABLE " + Params.TABLE_NAME + "(" + Params.KEY_PATH +" TEXT, " + Params.KEY_TITLE+ " TEXT, "
                + Params.KEY_ARTIST + " TEXT, " + Params.KEY_ALBUM + " TEXT, " + Params.KEY_DURATION + " TEXT" + ")";
        db.execSQL(query);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public void addToFavourites(Song song){
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Params.KEY_PATH, song.getPath());
        values.put(Params.KEY_TITLE, song.getTitle());
        values.put(Params.KEY_ARTIST, song.getArtist());
        values.put(Params.KEY_ALBUM, song.getAlbum());
        values.put(Params.KEY_DURATION, song.getDuration());
        db.insert(Params.TABLE_NAME, null, values);
        db.close();
    }

    public ArrayList<Song> getAllFavouriteSongs(){
        ArrayList<Song> songs = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + Params.TABLE_NAME;
        @SuppressLint("Recycle") Cursor cursor = db.rawQuery(query, null);

        if(cursor.moveToFirst()){
            do{
                String path = cursor.getString(0);
                String title = cursor.getString(1);
                String artist = cursor.getString(2);
                String album = cursor.getString(3);
                String duration = cursor.getString(4);

                Song song = new Song(title, path, duration, album, artist);
                songs.add(song);
            }while (cursor.moveToNext());
        }
        return songs;
    }

    public void removeFromFavourites(Song song){
        SQLiteDatabase db = this.getWritableDatabase();
        String path = song.getPath();
        db.delete(Params.TABLE_NAME, Params.KEY_PATH + "=?", new String[] {String.valueOf(path)});
        db.close();
    }
    public boolean contains(Song song) {
        SQLiteDatabase db = this.getReadableDatabase();
        String path = song.getPath();

        // Query to check if the song exists in the database
        String query = "SELECT COUNT(*) FROM " + Params.TABLE_NAME + " WHERE " + Params.KEY_PATH + "=?";
        Cursor cursor = db.rawQuery(query, new String[]{path});

        boolean exists = false;
        if (cursor.moveToFirst()) {
            int count = cursor.getInt(0);
            exists = count > 0;
        }

        cursor.close();
        db.close();

        return exists;
    }


}
