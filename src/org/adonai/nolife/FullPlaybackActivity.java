/*
 * Copyright (C) 2010, 2011 Christopher Eby <kreed@kreed.org>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.adonai.nolife;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;

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
import org.adonai.nolife.R;
import org.adonai.nolife.RangeSeekBar.OnRangeSeekBarChangeListener;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The primary playback screen with playback controls and large cover display.
 */
public class FullPlaybackActivity extends PlaybackActivity
	implements SeekBar.OnSeekBarChangeListener
	         , View.OnLongClickListener
{
	public static final int DISPLAY_INFO_OVERLAP = 0;
	public static final int DISPLAY_INFO_BELOW = 1;
	public static final int DISPLAY_INFO_WIDGETS = 2;
	public static final int DISPLAY_INFO_WIDGETS_ZOOMED = 3;

	/**
	 * A Handler running on the UI thread, in contrast with mHandler which runs
	 * on a worker thread.
	 */
	private final Handler mUiHandler = new Handler(this);

	private TextView mOverlayText;
	private View mControlsBottom;

	private SeekBar mSeekBar;
	private RangeSeekBar<Integer> mRangeSeekBar;
	private TextView mElapsedView;
	private TextView mDurationView;

	private TextView mTitle;
	private TextView mAlbum;
	private TextView mArtist;

	private ImageButton mShuffleButton;
	private ImageButton mEndButton;

	/**
	 * True if the controls are visible (play, next, seek bar, etc).
	 */
	private boolean mControlsVisible;

	/**
	 * Current song duration in milliseconds.
	 */
	private long mDuration;
	private boolean mSeekBarTracking;
	private boolean mPaused;

	/**
	 * The current display mode, which determines layout and cover render style.
	 */
	private int mDisplayMode;

	private Action mCoverPressAction;
	private Action mCoverLongPressAction;

	/**
	 * Cached StringBuilder for formatting track position.
	 */
	private final StringBuilder mTimeBuilder = new StringBuilder();
	
	private ProgressDialog pd;
	private Tag tag;
	private AudioFile mf;

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		SharedPreferences settings = PlaybackService.getSettings(this);
		int displayMode = Integer.parseInt(settings.getString("display_mode", "0"));
		mDisplayMode = displayMode;

		int layout = R.layout.full_playback;
		int coverStyle;

		switch (displayMode) {
		default:
			Log.w("VanillaMusic", "Invalid display mode given. Defaulting to overlap.");
			// fall through
		case DISPLAY_INFO_OVERLAP:
			coverStyle = CoverBitmap.STYLE_OVERLAPPING_BOX;
			break;
		case DISPLAY_INFO_BELOW:
			coverStyle = CoverBitmap.STYLE_INFO_BELOW;
			break;
		case DISPLAY_INFO_WIDGETS:
			coverStyle = CoverBitmap.STYLE_NO_INFO;
			layout = R.layout.full_playback_alt;
			break;
		case DISPLAY_INFO_WIDGETS_ZOOMED:
			coverStyle = CoverBitmap.STYLE_NO_INFO_ZOOMED;
			layout = R.layout.full_playback_alt;
			break;
		}

		setContentView(layout);
		
		mRangeSeekBar = new RangeSeekBar<Integer>(0, 1000, this);
		mRangeSeekBar.setOnRangeSeekBarChangeListener(new OnRangeSeekBarChangeListener<Integer>() {
		    @Override
		    public void rangeSeekBarValuesChanged(Integer minValue, Integer maxValue) {
		        // handle changed range values
		        PlaybackService.get(FullPlaybackActivity.this).getCut().mCutStart = minValue;
		        PlaybackService.get(FullPlaybackActivity.this).getCut().mCutEnd = maxValue;
		        PlaybackService.get(FullPlaybackActivity.this).getCut().mEnabled = true;
		        PlaybackService.get(FullPlaybackActivity.this).updateForCutTimes();
		        mUiHandler.sendEmptyMessage(MSG_UPDATE_PROGRESS);
		    }
		});

		mRangeSeekBar.setPadding(15, 5, 15, 0);
		mRangeSeekBar.setVisibility(View.GONE);
		mRangeSeekBar.setNotifyWhileDragging(true);
		
		LinearLayout merge = (LinearLayout)findViewById(R.id.manageLayout);
		merge.addView(mRangeSeekBar);

		CoverView coverView = (CoverView)findViewById(R.id.cover_view);
		coverView.setup(mLooper, this, coverStyle);
		coverView.setOnClickListener(this);
		coverView.setOnLongClickListener(this);
		mCoverView = coverView;

		mControlsBottom = findViewById(R.id.controls_bottom);
		View previousButton = findViewById(R.id.previous);
		previousButton.setOnClickListener(this);
		mPlayPauseButton = (ImageButton)findViewById(R.id.play_pause);
		mPlayPauseButton.setOnClickListener(this);
		View nextButton = findViewById(R.id.next);
		nextButton.setOnClickListener(this);

		mTitle = (TextView)findViewById(R.id.title);
		mAlbum = (TextView)findViewById(R.id.album);
		mArtist = (TextView)findViewById(R.id.artist);

		mElapsedView = (TextView)findViewById(R.id.elapsed);
		mDurationView = (TextView)findViewById(R.id.duration);
		mSeekBar = (SeekBar)findViewById(R.id.seek_bar);
		mSeekBar.setMax(1000);
		mSeekBar.setOnSeekBarChangeListener(this);

		mShuffleButton = (ImageButton)findViewById(R.id.shuffle);
		mShuffleButton.setOnClickListener(this);
		registerForContextMenu(mShuffleButton);
		mEndButton = (ImageButton)findViewById(R.id.end_action);
		mEndButton.setOnClickListener(this);
		registerForContextMenu(mEndButton);

		setControlsVisible(settings.getBoolean("visible_controls", true));
		setDuration(0);
	}

	@Override
	public void onStart()
	{
		super.onStart();

		SharedPreferences settings = PlaybackService.getSettings(this);
		if (mDisplayMode != Integer.parseInt(settings.getString("display_mode", "0"))) {
			finish();
			startActivity(new Intent(this, FullPlaybackActivity.class));
		}

		mCoverPressAction = getAction(settings, "cover_press_action", Action.ToggleControls);
		mCoverLongPressAction = getAction(settings, "cover_longpress_action", Action.ShowLyrics);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		mPaused = false;
		updateProgress();
	}

	@Override
	public void onPause()
	{
		super.onPause();
		mPaused = true;
	}

	/**
	 * Hide the message overlay, if it exists.
	 */
	private void hideMessageOverlay()
	{
		if (mOverlayText != null)
			mOverlayText.setVisibility(View.GONE);
	}

	/**
	 * Show some text in a message overlay.
	 *
	 * @param text Resource id of the text to show.
	 */
	private void showOverlayMessage(int text)
	{
		if (mOverlayText == null) {
			TextView view = new TextView(this);
			view.setBackgroundColor(Color.BLACK);
			view.setTextColor(Color.WHITE);
			view.setGravity(Gravity.CENTER);
			view.setPadding(25, 25, 25, 25);
			// Make the view clickable so it eats touch events
			view.setClickable(true);
			view.setOnClickListener(this);
			addContentView(view,
					new ViewGroup.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
							LinearLayout.LayoutParams.FILL_PARENT));
			mOverlayText = view;
		} else {
			mOverlayText.setVisibility(View.VISIBLE);
		}

		mOverlayText.setText(text);
	}

	@Override
	protected void onStateChange(int state, int toggled)
	{
		super.onStateChange(state, toggled);

		if ((toggled & (PlaybackService.FLAG_NO_MEDIA|PlaybackService.FLAG_EMPTY_QUEUE)) != 0) {
			if ((state & PlaybackService.FLAG_NO_MEDIA) != 0) {
				showOverlayMessage(R.string.no_songs);
			} else if ((state & PlaybackService.FLAG_EMPTY_QUEUE) != 0) {
				showOverlayMessage(R.string.empty_queue);
			} else {
				hideMessageOverlay();
			}
		}

		if ((state & PlaybackService.FLAG_PLAYING) != 0)
			updateProgress();

		if ((toggled & PlaybackService.MASK_FINISH) != 0) {
			switch (PlaybackService.finishAction(state)) {
			case SongTimeline.FINISH_STOP:
				mEndButton.setImageResource(R.drawable.repeat_inactive);
				break;
			case SongTimeline.FINISH_REPEAT:
				mEndButton.setImageResource(R.drawable.repeat_active);
				break;
			case SongTimeline.FINISH_REPEAT_CURRENT:
				mEndButton.setImageResource(R.drawable.repeat_current_active);
				break;
			case SongTimeline.FINISH_RANDOM:
				mEndButton.setImageResource(R.drawable.random_active);
				break;
			}
		}

		if ((toggled & PlaybackService.MASK_SHUFFLE) != 0) {
			switch (PlaybackService.shuffleMode(state)) {
			case SongTimeline.SHUFFLE_NONE:
				mShuffleButton.setImageResource(R.drawable.shuffle_inactive);
				break;
			case SongTimeline.SHUFFLE_SONGS:
				mShuffleButton.setImageResource(R.drawable.shuffle_active);
				break;
			case SongTimeline.SHUFFLE_ALBUMS:
				mShuffleButton.setImageResource(R.drawable.shuffle_album_active);
				break;
			}
		}
	}

	@Override
	protected void onSongChange(final Song song)
	{
		super.onSongChange(song);

		setDuration(song == null ? 0 : song.duration);

		if (mTitle != null) {
			if (song == null) {
				mTitle.setText(null);
				mAlbum.setText(null);
				mArtist.setText(null);
			} else {
				mTitle.setText(song.title);
				mAlbum.setText(song.album);
				mArtist.setText(song.artist);
			}
		}

		updateProgress();
	}

	/**
	 * Update the current song duration fields.
	 *
	 * @param duration The new duration, in milliseconds.
	 */
	private void setDuration(long duration)
	{
		mDuration = duration;
		mDurationView.setText(DateUtils.formatElapsedTime(mTimeBuilder, duration / 1000));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		menu.add(0, MENU_LIBRARY, 0, R.string.library).setIcon(R.drawable.ic_menu_music_library);
		if (PlaybackService.get(this).getSong(0) != null) {
			menu.add(0, MENU_GET_LYRICS, 0, R.string.fetch_lyrics).setIcon(android.R.drawable.ic_input_get);
			menu.addSubMenu(0, MENU_MORE, 0, R.string.more).setIcon(android.R.drawable.ic_menu_more);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId()) {
		case MENU_LIBRARY:
			openLibrary();
			return true;
		case MENU_EDIT_SONG:		
			Intent edit = new Intent(this, TagEditActivity.class);
			edit.putExtra("SongId", PlaybackService.get(this).getSong(0).id);
			startActivity(edit);
			return true;
		case MENU_GET_LYRICS:
			File af = new File(PlaybackService.get(this).getSong(0).path);
			try {
				mf = AudioFileIO.read(af);
				tag = mf.getTagOrCreateAndSetDefault();

				String lyrics = tag.getFirst(FieldKey.LYRICS);
				if(!lyrics.equals("")) {
					AlertDialog alertDialog;
					alertDialog = new AlertDialog.Builder(FullPlaybackActivity.this).create();
					alertDialog.setTitle(getResources().getString(R.string.song_lyrics));
					alertDialog.setMessage(lyrics);
					alertDialog.show();
					return true;
				}
				
				String title = tag.getFirst(FieldKey.TITLE).toString();
				String artist = tag.getFirst(FieldKey.ARTIST).toString();
				new parseForTag().execute("http://lyrics.wikia.com/api.php?func=getSong&artist="+URLEncoder.encode(artist, "UTF-8")+"&song="+URLEncoder.encode(title, "UTF-8")+"&fmt=html");
				pd = ProgressDialog.show(FullPlaybackActivity.this, getResources().getString(R.string.fetching), getResources().getString(R.string.requesting_server), true, true);
			} catch (CannotReadException e) {
				Toast.makeText(FullPlaybackActivity.this, R.string.cant_read_file, Toast.LENGTH_SHORT).show();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (NullPointerException e) {
				Toast.makeText(FullPlaybackActivity.this, R.string.unsup_tag, Toast.LENGTH_SHORT).show();
			} catch (TagException e) {
				Toast.makeText(FullPlaybackActivity.this, R.string.unsup_tag, Toast.LENGTH_SHORT).show();
			} catch (ReadOnlyFileException e) {
				Toast.makeText(FullPlaybackActivity.this, R.string.readonly, Toast.LENGTH_SHORT).show();
			} catch (InvalidAudioFrameException e) {
				Toast.makeText(FullPlaybackActivity.this, R.string.invalid_audioframe, Toast.LENGTH_SHORT).show();
			}
			return true;
		case MENU_MORE:
			Menu moreMenu = item.getSubMenu();
			moreMenu.clear();
			moreMenu.add(0, MENU_EDIT_SONG, 0, R.string.edit);
			moreMenu.add(0, MENU_CUT_SONG, 0, R.string.cutsong);
			moreMenu.add(0, MENU_FULL_DELETE, 0, R.string.fulldelete);
			moreMenu.add(0, MENU_PLAYLIST_DELETE, 0, R.string.pllstdelete);
			return true;
		case MENU_FULL_DELETE:
			PlaybackService.get(this).deleteMedia(MediaUtils.TYPE_SONG, PlaybackService.get(this).getSong(0).id);
			return true;
		case MENU_PLAYLIST_DELETE:
			PlaybackService.get(this).deleteMediaFromPlaylist(MediaUtils.TYPE_SONG, PlaybackService.get(this).getSong(0).id);
			return true;
		case MENU_CUT_SONG:
			if(!PlaybackService.get(this).getCut().mEnabled) {
				PlaybackService.get(this).getCut().mEnabled = true;
		        PlaybackService.get(this).updateForCutTimes();
		        mUiHandler.sendEmptyMessage(MSG_UPDATE_PROGRESS);
			} else {
				PlaybackService.get(this).getCut().mEnabled = false;
				PlaybackService.get(this).updateForCutTimes();
				mUiHandler.sendEmptyMessage(MSG_UPDATE_PROGRESS);
			}
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	private class parseForTag extends MediaUtils.ParseSite {
		@Override
		protected void onProgressUpdate(Integer... Progress) {
			if (Progress[0] == 1)
				pd.setMessage(getResources().getString(R.string.retrieving_lyrics));
			if (Progress[0] == 2)
				Toast.makeText(FullPlaybackActivity.this, getResources().getString(R.string.cant_find_lyrics), Toast.LENGTH_SHORT).show();
		}
		
		@Override
		protected void onPostExecute(String output) {
	        pd.dismiss();
	        
	        AlertDialog alertDialog;
			alertDialog = new AlertDialog.Builder(FullPlaybackActivity.this).create();
			alertDialog.setTitle(getResources().getString(R.string.song_lyrics));
			alertDialog.setMessage(output);
			alertDialog.show();
			
	        try {
				tag.setField(FieldKey.LYRICS, output);
				mf.commit();
			} catch (KeyNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NullPointerException e) {
				Toast.makeText(FullPlaybackActivity.this, R.string.unsup_tag, Toast.LENGTH_SHORT).show();
			} catch (FieldDataInvalidException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (CannotWriteException e) {
				Toast.makeText(FullPlaybackActivity.this, getResources().getString(R.string.cant_write_tag), Toast.LENGTH_SHORT).show();
				e.printStackTrace();
			}
	    }
	}

	@Override
	public boolean onSearchRequested()
	{
		openLibrary();
		return false;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			nextSong();
			findViewById(R.id.next).requestFocus();
			return true;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			previousSong(false);
			findViewById(R.id.previous).requestFocus();
			return true;
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_CENTER:
		case KeyEvent.KEYCODE_ENTER:
			setControlsVisible(!mControlsVisible);
			mHandler.sendEmptyMessage(MSG_SAVE_CONTROLS);
			return true;
		}

		return super.onKeyUp(keyCode, event);
	}

	/**
	 * Update seek bar progress and schedule another update in one second
	 */
	private void updateProgress()
	{
		int position = PlaybackService.hasInstance() ? PlaybackService.get(this).getPosition() : 0;
		

		if (!mSeekBarTracking) {
			long duration = mDuration;
			mSeekBar.setProgress(duration == 0 ? 0 : (int)(1000 * position / duration));
		}
		
		if(PlaybackService.hasInstance()) {
			mRangeSeekBar.setVisibility(PlaybackService.get(this).getCut().mEnabled && mControlsVisible ? View.VISIBLE : View.GONE);
			mRangeSeekBar.setSelectedMaxValue(PlaybackService.get(this).getCut().mCutEnd);
			mRangeSeekBar.setSelectedMinValue(PlaybackService.get(this).getCut().mCutStart);
		}
		

		mElapsedView.setText(DateUtils.formatElapsedTime(mTimeBuilder, position / 1000));

		if (!mPaused && mControlsVisible && (mState & PlaybackService.FLAG_PLAYING) != 0) {
			// Try to update right when the duration increases by one second
			long next = 1000 - position % 1000;
			mUiHandler.removeMessages(MSG_UPDATE_PROGRESS);
			mUiHandler.sendEmptyMessageDelayed(MSG_UPDATE_PROGRESS, next);
		}
	}

	/**
	 * Toggles the visibility of the playback controls.
	 */
	private void setControlsVisible(boolean visible)
	{
		int mode = visible ? View.VISIBLE : View.GONE;
		mSeekBar.setVisibility(mode);
		mElapsedView.setVisibility(mode);
		mDurationView.setVisibility(mode);
		mControlsBottom.setVisibility(mode);
		mControlsVisible = visible;

		if (visible) {
			mPlayPauseButton.requestFocus();
		}
		
		updateProgress();
	}

	/**
	 * Update the seekbar progress with the current song progress. This must be
	 * called on the UI Handler.
	 */
	private static final int MSG_UPDATE_PROGRESS = 10;
	/**
	 * Save the hidden_controls preference to storage.
	 */
	private static final int MSG_SAVE_CONTROLS = 14;

	@Override
	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case MSG_SAVE_CONTROLS: {
			SharedPreferences settings = PlaybackService.getSettings(this);
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean("visible_controls", mControlsVisible);
			editor.commit();
			break;
		}
		case MSG_UPDATE_PROGRESS:
			updateProgress();
			break;
		default:
			return super.handleMessage(message);
		}

		return true;
	}

	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		if (fromUser)
		{
			PlaybackService.get(this).seekToProgress(progress);
			PlaybackService.get(this).updateForCutTimes();
		}
		
	}

	public void onStartTrackingTouch(SeekBar seekBar)
	{
		mSeekBarTracking = true;
	}

	public void onStopTrackingTouch(SeekBar seekBar)
	{
		mSeekBarTracking = false;
	}

	@Override
	public void performAction(Action action)
	{
		if (action == Action.ToggleControls) {
			setControlsVisible(!mControlsVisible);
			mHandler.sendEmptyMessage(MSG_SAVE_CONTROLS);
		} else {
			super.performAction(action);
		}
	}

	@Override
	public void onClick(View view)
	{
		if (view == mOverlayText && (mState & PlaybackService.FLAG_EMPTY_QUEUE) != 0) {
			setState(PlaybackService.get(this).setFinishAction(SongTimeline.FINISH_RANDOM));
			return;
		}

		switch (view.getId()) {
		case R.id.cover_view:
			performAction(mCoverPressAction);
			break;
		case R.id.end_action:
			cycleFinishAction();
			break;
		case R.id.shuffle:
			cycleShuffle();
			break;
		default:
			super.onClick(view);
			break;
		}
	}

	@Override
	public boolean onLongClick(View view)
	{
		if (view.getId() == R.id.cover_view) {
			performAction(mCoverLongPressAction);
			return true;
		}

		return false;
	}

	private static final int GROUP_SHUFFLE = 0;
	private static final int GROUP_FINISH = 1;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo)
	{
		if (view == mShuffleButton) {
			menu.add(GROUP_SHUFFLE, SongTimeline.SHUFFLE_NONE, 0, R.string.no_shuffle);
			menu.add(GROUP_SHUFFLE, SongTimeline.SHUFFLE_SONGS, 0, R.string.shuffle_songs);
			menu.add(GROUP_SHUFFLE, SongTimeline.SHUFFLE_ALBUMS, 0, R.string.shuffle_albums);
		} else if (view == mEndButton) {
		    menu.add(GROUP_FINISH, SongTimeline.FINISH_STOP, 0, R.string.no_repeat);
			menu.add(GROUP_FINISH, SongTimeline.FINISH_REPEAT, 0, R.string.repeat);
			menu.add(GROUP_FINISH, SongTimeline.FINISH_REPEAT_CURRENT, 0, R.string.repeat_current_song);
			menu.add(GROUP_FINISH, SongTimeline.FINISH_RANDOM, 0, R.string.random);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		int group = item.getGroupId();
		int id = item.getItemId();
		if (group == GROUP_SHUFFLE)
			setState(PlaybackService.get(this).setShuffleMode(id));
		else if (group == GROUP_FINISH)
			setState(PlaybackService.get(this).setFinishAction(id));
		return true;
	}
}
