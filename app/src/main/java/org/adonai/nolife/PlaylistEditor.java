package org.adonai.nolife;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

public class PlaylistEditor extends Activity implements Callback, View.OnClickListener {

    static final int MENU_SAVE_AS_PLIST = 1;

    static final int MSG_NEW_PLAYLIST = 1;

    private ListView mSongList;
    private MediaArrayAdapter mAdapter;
    private long mID;
    private ArrayList<Song> mSongs;
    private Handler mHandler;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        if (getIntent() == null) {
            finish();
            return;
        }

        long id = getIntent().getLongExtra("id", 0);
        if(id == 0) {
            finish();
            return;
        }

        mSongs = new ArrayList<Song>();
        mHandler = new Handler(this);
        mID = id;

        setContentView(R.layout.playlist_editor);
        mSongList = (ListView)findViewById(R.id.SongList);
        Button mSaveButton = (Button)findViewById(R.id.save_plist);
        mSaveButton.setOnClickListener(this);

        if(id == -1) {
            mSongs = PlaybackService.get(this).mTimeline.getAllSongsCopy();
            mAdapter = new MediaArrayAdapter(this, R.layout.playlistitem, R.id.p_title, mSongs);
            mSongList.setAdapter(mAdapter);
        } else {
            mSongs = getPlaylistSongs(mID);
            mAdapter = new MediaArrayAdapter(this, R.layout.playlistitem, R.id.p_title, mSongs);
            mSongList.setAdapter(mAdapter);
        }
    }

    private ArrayList<Song> getPlaylistSongs(long id)
    {
        ArrayList<Song> output = new ArrayList<Song>();
        String[] projection = Song.FILLED_PLAYLIST_PROJECTION;
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", id);
        Cursor cursor = getContentResolver().query(uri, projection, null, null, MediaStore.Audio.Playlists.Members.PLAY_ORDER);
        while (cursor.moveToNext()) {
            Song song = new Song(-1);
            song.populate(cursor);
            output.add(song);
        }
        return output;
    }

    private class MediaArrayAdapter extends ArrayAdapter<Song> {

        public MediaArrayAdapter(Context context, int resource,
                int textViewResourceId, List<Song> objects) {
            super(context, resource, textViewResourceId, objects);
        }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent)
        {
            View view;
            if (convertView == null)
                view = View.inflate(getContext(), R.layout.playlistitem, null);
            else
                view = convertView;

            ImageButton delete = (ImageButton)view.findViewById(R.id.p_delete);
            TextView title = (TextView)view.findViewById(R.id.p_title);
            TextView artist = (TextView)view.findViewById(R.id.p_artist);
            TextView album = (TextView)view.findViewById(R.id.p_album);

            ImageButton up = (ImageButton)view.findViewById(R.id.p_upbutton);
            ImageButton down = (ImageButton)view.findViewById(R.id.p_downbutton);
            up.setOnClickListener(ListItemButtonClickListener);
            down.setOnClickListener(ListItemButtonClickListener);

            delete.setOnClickListener(ListItemButtonClickListener);
            title.setText(getItem(pos).title);
            artist.setText(getItem(pos).artist);
            album.setText(getItem(pos).album);

            return view;
        }

    }

    private OnClickListener ListItemButtonClickListener = new OnClickListener() {

        @Override
        public void onClick(View view)
        {
            int pos = mSongList.getPositionForView((View)view.getParent());
            Song song = mAdapter.getItem(pos);
            switch (view.getId()) {
            case R.id.p_delete:
                if(mID == -1)
                    mAdapter.remove(song);
                else {
                    String[] selected = { String.format("%d", song.id) };
                    Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", mID);
                    getContentResolver().delete(uri, "AUDIO_ID = ?", selected);
                    mAdapter.remove(song);
                }
                break;
            case R.id.p_upbutton:
                if(pos > 0) {
                    mAdapter.remove(song);
                    mAdapter.insert(song, pos - 1);
                }
                break;
            case R.id.p_downbutton:
                if(pos < mAdapter.getCount() - 1) {
                    mAdapter.remove(song);
                    mAdapter.insert(song, pos + 1);
                }
                break;
            }
        }
    };

    /* (non-Javadoc)
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_SAVE_AS_PLIST, 0, R.string.save_as_plist).setIcon(android.R.drawable.ic_menu_save);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
        case MENU_SAVE_AS_PLIST:
            NewPlaylistDialog dialog = new NewPlaylistDialog(this, null, R.string.create, null);
            dialog.setDismissMessage(mHandler.obtainMessage(MSG_NEW_PLAYLIST, dialog));
            dialog.show();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch(msg.what) {
        case MSG_NEW_PLAYLIST: {
            NewPlaylistDialog dialog = (NewPlaylistDialog)msg.obj;
            if (dialog.isAccepted()) {
                String name = dialog.getText();
                long playlistId = Playlist.createPlaylist(getContentResolver(), name);
                Playlist.addToPlaylist(getContentResolver(), playlistId, mSongs);
                return true;
            }
            return false;
        }
        default:
            return false;
        }

    }

    @Override
    public void onClick(View view) {
        switch(view.getId()) {
        case R.id.save_plist:
            if(mID == -1)
                PlaybackService.get(getApplicationContext()).mTimeline.convertFromSongArray(mSongs);
            else
                Playlist.replacePlaylist(getContentResolver(), mID, mSongs);
            break;
        }
        finish();
    }

}
