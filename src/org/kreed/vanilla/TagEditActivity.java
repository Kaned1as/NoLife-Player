package org.kreed.vanilla;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;

import org.htmlcleaner.ContentNode;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.*;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.KeyNotFoundException;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Html;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class TagEditActivity extends Activity implements View.OnClickListener {
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

		setContentView(R.layout.tag_edit);
		TitleEdit = (TextView)findViewById(R.id.TitleEdit);
		ArtistEdit = (TextView)findViewById(R.id.ArtistEdit);
		AlbumEdit = (TextView)findViewById(R.id.AlbumEdit);
		LyricsEdit = (TextView)findViewById(R.id.LyricsEdit);
		SaveButton = (Button)findViewById(R.id.Savebutton);
		FetchButton = (Button)findViewById(R.id.fetchbutton);
		SaveButton.setOnClickListener(this);
		FetchButton.setOnClickListener(this);
		
		Long id = this.getIntent().getLongExtra("SongId", -1);
		ContentResolver resolver = getContentResolver();
		String[] projection = new String [] { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA };
		Cursor cursor = MediaUtils.buildQuery(MediaUtils.TYPE_SONG, id, projection, null).runQuery(resolver);
		
		if (cursor != null) {
			while (cursor.moveToNext()) {
				File SongFile = new File(cursor.getString(1));
				try {
					mf = AudioFileIO.read(SongFile);
					Tag tag = mf.getTag();
					
					if (tag == null)
						return;
					
					TitleEdit.setText(tag.getFirst(FieldKey.TITLE).toString());
					ArtistEdit.setText(tag.getFirst(FieldKey.ARTIST).toString());
					AlbumEdit.setText(tag.getFirst(FieldKey.ALBUM).toString());
					LyricsEdit.setText(tag.getFirst(FieldKey.LYRICS).toString());
				} catch (CannotReadException e) {
					Toast.makeText(TagEditActivity.this, getResources().getString(R.string.cant_read_file), Toast.LENGTH_SHORT);
					this.finish();
				} catch (IOException e) {
					this.finish();
				} catch (TagException e) {
					Toast.makeText(TagEditActivity.this, getResources().getString(R.string.unsup_tag), Toast.LENGTH_SHORT);
					this.finish();
				} catch (ReadOnlyFileException e) {
					Toast.makeText(TagEditActivity.this, getResources().getString(R.string.readonly), Toast.LENGTH_SHORT);
					this.finish();
				} catch (InvalidAudioFrameException e) {
					Toast.makeText(TagEditActivity.this, getResources().getString(R.string.invalid_audioframe), Toast.LENGTH_SHORT);
					this.finish();
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
				} catch (KeyNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (FieldDataInvalidException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (CannotWriteException e) {
					Toast.makeText(TagEditActivity.this, getResources().getString(R.string.cant_write_tag), Toast.LENGTH_SHORT);
					e.printStackTrace();
				}	
				SaveButton.setText("Saved");
				break;
				}
			case R.id.fetchbutton: {
				Tag tag = mf.getTag();
				String title = tag.getFirst(FieldKey.TITLE).toString();
				String artist = tag.getFirst(FieldKey.ARTIST).toString();
				try {
					new ParseSite().execute("http://lyrics.wikia.com/api.php?func=getSong&artist="+URLEncoder.encode(artist, "UTF-8")+"&song="+URLEncoder.encode(title, "UTF-8")+"&fmt=html");
					pd = ProgressDialog.show(TagEditActivity.this, "Fetching...", getResources().getString(R.string.requesting_server), true, true);
				} catch (UnsupportedEncodingException e) {
					Toast.makeText(TagEditActivity.this, getResources().getString(R.string.unsup_chars_tag), Toast.LENGTH_SHORT).show();
					e.printStackTrace();
				}
				break;
			}
		}
	}
	
	private class ParseSite extends AsyncTask<String, Integer, String> {
		private TagNode rootNode;
		private TagNode lyricbox;
		private URL full_url;
		
		protected String doInBackground(String... arg) {
			HtmlCleaner cleaner = new HtmlCleaner();
			cleaner.getProperties().setOmitComments(true);
			cleaner.getProperties().setRecognizeUnicodeChars(true);
			
			try {
				rootNode = cleaner.clean(new URL(arg[0]));
				TagNode linkElements[] = rootNode.getElementsByName("a", true);
				if(linkElements.length >= 3) {
					full_url = new URL(linkElements[2].getAttributeByName("href"));
					publishProgress(1);
					rootNode = cleaner.clean(full_url);
					lyricbox = rootNode.findElementByAttValue("class", "lyricbox", true, false);
					
					if(lyricbox == null) {
						publishProgress(2);
						return new String("");
					}
					
					boolean done = false;
					while(!done) {
					TagNode ad = lyricbox.findElementByAttValue("class", "rtMatcher", true, false);
					if(ad != null)
						ad.removeFromTree();
					else
						done = true;
					}
					
					StringBuffer output = new StringBuffer("");
					@SuppressWarnings("unchecked")
					List<Object> children = lyricbox.getChildren();
					for (int i = 0; i < children.size(); i++) {
						Object item = children.get(i);
						if (item instanceof ContentNode) {
							output.append(((ContentNode)item).getContent().toString());
						} else if (item instanceof TagNode)
							output.append("<br>");
					}
					
			        return Html.fromHtml(output.toString()).toString();
				} else publishProgress(2);
					
					
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				publishProgress(2);
			}
			
	        return new String("");
	    }
		
		protected void onProgressUpdate(Integer... Progress) {
			if (Progress[0] == 1)
				pd.setMessage(getResources().getString(R.string.retrieving_lyrics));
			if (Progress[0] == 2)
				Toast.makeText(TagEditActivity.this, getResources().getString(R.string.cant_find_lyrics), Toast.LENGTH_SHORT).show();
		}

	    protected void onPostExecute(String output) {
	        pd.dismiss();
	        LyricsEdit.setText(output.toString());
	    }
	}

}