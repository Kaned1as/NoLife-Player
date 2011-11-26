package org.kreed.vanilla;

import java.io.File;
import java.io.IOException;

import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.KeyNotFoundException;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.id3.ID3v24Frames;
import org.jaudiotagger.tag.id3.ID3v24Tag;

import android.app.Activity;
import android.content.ContentResolver;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class TagEditActivity extends Activity implements View.OnClickListener {
	private TextView TitleEdit;
	private TextView ArtistEdit;
	private TextView AlbumEdit;
	private TextView LyricsEdit;
	private Button SaveButton;
	private MP3File  mf;
	
	@Override
	public void onCreate(Bundle state)
	{
		super.onCreate(state);
		setContentView(R.layout.tag_edit);
		TitleEdit = (TextView)findViewById(R.id.TitleEdit);
		ArtistEdit = (TextView)findViewById(R.id.ArtistEdit);
		AlbumEdit = (TextView)findViewById(R.id.AlbumEdit);
		LyricsEdit = (TextView)findViewById(R.id.LyricsEdit);
		SaveButton = (Button)findViewById(R.id.Savebutton);
		SaveButton.setOnClickListener(this);
		
		Long id = this.getIntent().getLongExtra("SongId", -1);
		ContentResolver resolver = getContentResolver();
		String[] projection = new String [] { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA };
		Cursor cursor = MediaUtils.buildQuery(MediaUtils.TYPE_SONG, id, projection, null).runQuery(resolver);
		
		if (cursor != null) {
			while (cursor.moveToNext()) {
				File SongFile = new File(cursor.getString(1));
				try {
					mf = (MP3File)AudioFileIO.read(SongFile);
					ID3v24Tag tag = mf.getID3v2TagAsv24();
					
					TitleEdit.setText(tag.getFirst(ID3v24Frames.FRAME_ID_TITLE));
					ArtistEdit.setText(tag.getFirst(ID3v24Frames.FRAME_ID_ARTIST));
					AlbumEdit.setText(tag.getFirst(ID3v24Frames.FRAME_ID_ALBUM));
					LyricsEdit.setText(tag.getFirst(ID3v24Frames.FRAME_ID_UNSYNC_LYRICS));
				} catch (CannotReadException e) {
					this.finish();
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (TagException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ReadOnlyFileException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvalidAudioFrameException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			cursor.close();
		}
	}
	
	@Override
	public void onClick(View view)
	{
		SaveButton.setText("Saved");
		switch (view.getId()) {
		case R.id.Savebutton: {
			ID3v24Tag tag = mf.getID3v2TagAsv24();
			try {
				tag.setField(FieldKey.TITLE,TitleEdit.getText().toString());
				tag.setField(FieldKey.ALBUM,AlbumEdit.getText().toString());
				tag.setField(FieldKey.ARTIST,ArtistEdit.getText().toString());
				tag.setField(FieldKey.LYRICS,LyricsEdit.getText().toString());
				mf.commit();
			} catch (KeyNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (FieldDataInvalidException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (CannotWriteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
			SaveButton.setText("Saved");
			break;
			}
		}
	}

}
