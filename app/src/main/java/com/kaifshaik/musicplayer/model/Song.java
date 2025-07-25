package com.kaifshaik.musicplayer.model;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;

public class Song implements Parcelable {
    private String title;
    private String path;
    private String duration;
    private String album;
    private String artist;

    protected Song(Parcel in) {
        title = in.readString();
        path = in.readString();
        duration = in.readString();
        album = in.readString();
        artist = in.readString();
    }

    public static final Creator<Song> CREATOR = new Creator<Song>() {
        @Override
        public Song createFromParcel(Parcel in) {
            return new Song(in);
        }

        @Override
        public Song[] newArray(int size) {
            return new Song[size];
        }
    };

    public String getArtist() {
        return artist;
    }

    public Song(String title, String path, String duration, String album, String artist) {
        this.title = title;
        this.path = path;
        this.duration = duration;
        this.album = album;
        this.artist = artist;
    }

    public String getTitle() {
        return title;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDuration() {
        return duration;
    }


    public String getAlbum() {
        return album;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeLong(Long.parseLong(duration));
        dest.writeString(path);
    }

}
