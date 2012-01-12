package org.adonai.nolife;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

public class PlaylistEditor extends Activity implements AdapterView.OnItemClickListener {
	
	private ListView mSongList;
	private MediaArrayAdapter mAdapter;
	private long mID;
	private ArrayList<Song> mSongs;

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
		mID = id;
		
		setContentView(R.layout.playlist_editor);	
		mSongList = (ListView)findViewById(R.id.SongList);
		mSongList.setOnItemClickListener(this);
		
		if(id == -1) {
			mSongs = PlaybackService.get(this).mTimeline.getAllSongs();
			mAdapter = new MediaArrayAdapter(this, R.layout.playlistitem, R.id.p_title, mSongs);
			mSongList.setAdapter(mAdapter);
		} else {
			mSongs = getPlaylistSongs(mID);
			mAdapter = new MediaArrayAdapter(this, R.layout.playlistitem, R.id.p_title, mSongs);
			mSongList.setAdapter(mAdapter);	
		}
	}

	@Override
	public void onItemClick(AdapterView<?> list, View view, int pos, long id) {
		
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
			
			if(mID == -1) {
				ImageButton up = (ImageButton)view.findViewById(R.id.p_upbutton);
				ImageButton down = (ImageButton)view.findViewById(R.id.p_downbutton);
				up.setVisibility(View.VISIBLE);
				down.setVisibility(View.VISIBLE);
				up.setOnClickListener(ListItemButtonClickListener);
				down.setOnClickListener(ListItemButtonClickListener);
			}
			
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
				default:
					break;
			}
		}
		
		
	};
	
}
