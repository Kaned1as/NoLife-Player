package org.adonai.nolife;

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

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.KeyNotFoundException;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class TagEditActivity extends Activity implements View.OnClickListener, TextWatcher  {
    private TextView mTitleEdit;
    private TextView mArtistEdit;
    private TextView mAlbumEdit;
    private TextView mLyricsEdit;
    private Button mSaveButton;
    private Button mFetchButton;
    private ProgressDialog pd;
    private AudioFile mf;


    @Override
    public void onCreate(Bundle state)
    {
        super.onCreate(state);

        if(getIntent() == null) {
            finish();
            return;
        }

        setContentView(R.layout.tag_edit);
        mTitleEdit = (TextView)findViewById(R.id.TitleEdit);
        mArtistEdit = (TextView)findViewById(R.id.ArtistEdit);
        mAlbumEdit = (TextView)findViewById(R.id.AlbumEdit);
        mLyricsEdit = (TextView)findViewById(R.id.LyricsEdit);
        mSaveButton = (Button)findViewById(R.id.Savebutton);
        mFetchButton = (Button)findViewById(R.id.fetchbutton);
        mSaveButton.setOnClickListener(this);
        mFetchButton.setOnClickListener(this);

        mTitleEdit.addTextChangedListener(this);
        mArtistEdit.addTextChangedListener(this);
        mAlbumEdit.addTextChangedListener(this);
        mLyricsEdit.addTextChangedListener(this);

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

                    // Commented out for future work
                    // TODO: Edit images
                    //Artwork art = tag.getFirstArtwork();
                    //Bitmap picture = BitmapFactory.decodeByteArray(art.getBinaryData(), 0, art.getBinaryData().length);
                    //mLyricsEdit.setBackgroundDrawable(new BitmapDrawable(this.getResources(), picture));

                    mTitleEdit.setText(tag.getFirst(FieldKey.TITLE));
                    mArtistEdit.setText(tag.getFirst(FieldKey.ARTIST));
                    mAlbumEdit.setText(tag.getFirst(FieldKey.ALBUM));
                    mLyricsEdit.setText(tag.getFirst(FieldKey.LYRICS));
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
                    tag.setField(FieldKey.TITLE, mTitleEdit.getText().toString());
                    tag.setField(FieldKey.ALBUM, mAlbumEdit.getText().toString());
                    tag.setField(FieldKey.ARTIST, mArtistEdit.getText().toString());
                    tag.setField(FieldKey.LYRICS, mLyricsEdit.getText().toString());
                    mf.commit();
                    mSaveButton.setText(getResources().getString(R.string.saved));
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
                try {
                    final String artistEncoded = URLEncoder.encode(mArtistEdit.getText().toString(), "UTF-8");
                    final String titleEncoded = URLEncoder.encode(mTitleEdit.getText().toString(), "UTF-8");
                    new LyricsParser().execute("http://lyrics.wikia.com/api.php?func=getSong&artist=" + artistEncoded + "&song=" + titleEncoded + "&fmt=html");
                    pd = ProgressDialog.show(TagEditActivity.this, getResources().getString(R.string.fetching), getResources().getString(R.string.requesting_server), true, true);
                } catch (UnsupportedEncodingException e) {
                    Toast.makeText(TagEditActivity.this, R.string.unsup_chars_tag, Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    private class LyricsParser extends MediaUtils.ParseSite {
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
            mLyricsEdit.setText(output);
        }
    }

    @Override
    public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
        mSaveButton.setText(R.string.savechanges);
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
