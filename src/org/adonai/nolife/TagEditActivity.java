package org.adonai.nolife;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.*;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.KeyNotFoundException;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.adonai.nolife.R;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class TagEditActivity extends Activity implements View.OnClickListener, TextWatcher  {
	private TextView TitleEdit;
	private TextView ArtistEdit;
	private TextView AlbumEdit;
	private TextView LyricsEdit;
	private Button SaveButton;
	private Button FetchButton;
	private ProgressDialog pd;
	private AudioFile  mf;

	
	@Override
	public void onCreate(Bundle state)
	{
		super.onCreate(state);
		
		if(getIntent() == null) {
			finish();
			return;
		}

		setContentView(R.layout.tag_edit);
		TitleEdit = (TextView)findViewById(R.id.TitleEdit);
		ArtistEdit = (TextView)findViewById(R.id.ArtistEdit);
		AlbumEdit = (TextView)findViewById(R.id.AlbumEdit);
		LyricsEdit = (TextView)findViewById(R.id.LyricsEdit);
		SaveButton = (Button)findViewById(R.id.Savebutton);
		FetchButton = (Button)findViewById(R.id.fetchbutton);
		SaveButton.setOnClickListener(this);
		FetchButton.setOnClickListener(this);
		
		TitleEdit.addTextChangedListener(this);
		ArtistEdit.addTextChangedListener(this);
		AlbumEdit.addTextChangedListener(this);
		LyricsEdit.addTextChangedListener(this);
			
		long id = getIntent().getLongExtra("SongId", -1);
		ContentResolver resolver = getContentResolver();
		String[] projection = new String [] { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA };
		Cursor cursor = MediaUtils.buildQuery(MediaUtils.TYPE_SONG, id, projection, null).runQuery(resolver);
		
		if (cursor != null) {
			while (cursor.moveToNext()) {
				File SongFile = new File(cursor.getString(1));
				try {
					mf = AudioFileIO.read(SongFile);
					Tag tag = mf.getTagOrCreateAndSetDefault();
					
					TitleEdit.setText(tag.getFirst(FieldKey.TITLE));
					ArtistEdit.setText(tag.getFirst(FieldKey.ARTIST));
					AlbumEdit.setText(tag.getFirst(FieldKey.ALBUM));
					LyricsEdit.setText(tag.getFirst(FieldKey.LYRICS));
				} catch (CannotReadException e) {
					Toast.makeText(getApplicationContext(), R.string.cant_read_file, Toast.LENGTH_SHORT).show();
					finish();
				} catch (IOException e) {
					finish();
				} catch (TagException e) {
					Toast.makeText(getApplicationContext(), R.string.unsup_tag, Toast.LENGTH_SHORT).show();
					finish();
				} catch (ReadOnlyFileException e) {
					Toast.makeText(getApplicationContext(), R.string.readonly, Toast.LENGTH_SHORT).show();
					finish();
				} catch (InvalidAudioFrameException e) {
					Toast.makeText(getApplicationContext(), R.string.invalid_audioframe, Toast.LENGTH_SHORT).show();
					finish();
				}
			}
			cursor.close();
		}
	}
	
	@Override
	public void onClick(View view)
	{
		switch (view.getId()) {
			case R.id.Savebutton: {
				Tag tag = mf.getTagOrCreateAndSetDefault();
				
				try {	
					tag.setField(FieldKey.TITLE,TitleEdit.getText().toString());
					tag.setField(FieldKey.ALBUM,AlbumEdit.getText().toString());
					tag.setField(FieldKey.ARTIST,ArtistEdit.getText().toString());
					tag.setField(FieldKey.LYRICS,LyricsEdit.getText().toString());
					mf.commit();
					SaveButton.setText(getResources().getString(R.string.saved));
				} catch (KeyNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NullPointerException e) {
					Toast.makeText(TagEditActivity.this, R.string.unsup_tag, Toast.LENGTH_SHORT).show();
				} catch (FieldDataInvalidException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (CannotWriteException e) {
					Toast.makeText(TagEditActivity.this, R.string.cant_write_tag, Toast.LENGTH_SHORT).show();
					e.printStackTrace();
				}	
				break;
				}
			case R.id.fetchbutton: {
				String title = TitleEdit.getText().toString();
				String artist = ArtistEdit.getText().toString();
				try {
					new parseForTag().execute("http://lyrics.wikia.com/api.php?func=getSong&artist="+URLEncoder.encode(artist, "UTF-8")+"&song="+URLEncoder.encode(title, "UTF-8")+"&fmt=html");
					pd = ProgressDialog.show(TagEditActivity.this, getResources().getString(R.string.fetching), getResources().getString(R.string.requesting_server), true, true);
				} catch (UnsupportedEncodingException e) {
					Toast.makeText(TagEditActivity.this, R.string.unsup_chars_tag, Toast.LENGTH_SHORT).show();
					e.printStackTrace();
				}
				break;
			}
		}
	}
	
	private class parseForTag extends MediaUtils.ParseSite {
		@Override
		protected void onProgressUpdate(Integer... Progress) {
			if (Progress[0] == 1)
				pd.setMessage(getResources().getString(R.string.retrieving_lyrics));
			if (Progress[0] == 2)
				Toast.makeText(TagEditActivity.this, R.string.cant_find_lyrics, Toast.LENGTH_SHORT).show();
		}
		
		@Override
		protected void onPostExecute(String output) {
	        pd.dismiss();
	        LyricsEdit.setText(output.toString());
	    }
	}

	@Override
	public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
		SaveButton.setText(R.string.savechanges);
	}
	
	@Override
	public void afterTextChanged(Editable arg0) 
	{		
	}

	@Override
	public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) 
	{
	}
}
