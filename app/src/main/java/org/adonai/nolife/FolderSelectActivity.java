package org.adonai.nolife;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import android.os.Bundle;
import android.os.Handler;

import android.os.HandlerThread;
import android.os.Message;
import android.provider.MediaStore;

import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class FolderSelectActivity extends Activity implements OnClickListener, Handler.Callback  {
	private ListView mFolderList;
	private TextView mFolderTitle;
	private ProgressDialog pd;
	
	private List<String> item = null;
	private List<String> path = null;
	private List<String> mFolders = null;
	private Handler mHandler;
	
	private String root="/mnt/sdcard";
	private File pwd = null;
	
	private static final int MSG_FINISH = 0;
	private static final int MSG_PROGRESS = 1;
	private static final int MSG_FAIL = 2;
	
	@Override
	public void onCreate(Bundle state)
	{
		super.onCreate(state);
		setContentView(R.layout.folder_selector);
		
	    mFolders = new ArrayList<String>();
		
		mFolderList = (ListView)findViewById(R.id.FolderList);
		mFolderTitle = (TextView)findViewById(R.id.FoldersTitle);
		Button selectButton = (Button)findViewById(R.id.select);
		Button clearButton = (Button)findViewById(R.id.clear);
		selectButton.setOnClickListener(this);
		clearButton.setOnClickListener(this);
		
		getDir(root);
		
		HandlerThread thread = new HandlerThread(getClass().getName());
		thread.start();
		mHandler = new Handler(this);
	}
	
	private void getDir(String dirPath)
    {
		mFolderTitle.setText("Location: " + dirPath);

     item = new ArrayList<String>();
     path = new ArrayList<String>();
     
     File f = new File(dirPath);
     File[] files = f.listFiles();
     
     if(!dirPath.equals(root))
     {   
      item.add("..");
      path.add(f.getParent());     
     }     

     for(int i=0; i < files.length; i++)
     {
       File file = files[i];
       if(file.isDirectory()) {
    	   item.add(file.getName());
           path.add(file.getAbsolutePath());
       }
     }
     ArrayAdapter<String> fileList = new FolderArrayAdapter(this, R.layout.folder_item, R.id.folderTitle, item);
     mFolderList.setAdapter(fileList);
     pwd = f;
    }
	
	private class FolderArrayAdapter extends ArrayAdapter<String> {
		
		public FolderArrayAdapter(Context context, int resource,
				int textViewResourceId, List<String> objects) {
			super(context, resource, textViewResourceId, objects);
		}
		
		@Override
		public View getView(int pos, View convertView, ViewGroup parent)
		{
			View view;
			if (convertView == null)
				view = View.inflate(getContext(), R.layout.folder_item, null);
			else
				view = convertView;
			
			
			TextView title = (TextView)view.findViewById(R.id.folderTitle);
			title.setText(getItem(pos));
			title.setOnClickListener(ListItemButtonClickListener);
			
			CheckBox selector = (CheckBox)view.findViewById(R.id.selector);
			if(mFolders.contains(path.get(pos)))
				selector.setChecked(true);
			else
				selector.setChecked(false);
			
			selector.setOnClickListener(ListItemButtonClickListener);
			
			ImageView folderpic = (ImageView)view.findViewById(R.id.folderImage);
			folderpic.setOnClickListener(ListItemButtonClickListener);			

			return view;
		}
		
	}
	
	private OnClickListener ListItemButtonClickListener = new OnClickListener() {

		@Override
		public void onClick(View view) 
		{
			View item = (View)view.getParent();
			CheckBox selector = (CheckBox)item.findViewById(R.id.selector);
			int pos = mFolderList.getPositionForView(item);
			
			switch(view.getId()) {
			case R.id.folderTitle:
			case R.id.folderImage:
				if(selector.isChecked())
					break;
				
				File file = new File(path.get(pos));
				if(file.canRead())
				   getDir(path.get(pos));
				break;
			case R.id.selector:
				File folder = new File(path.get(pos));
				if(folder.canRead())
					if(mFolders.contains(folder.getAbsolutePath()))
						mFolders.remove(folder.getAbsolutePath());
					else
						mFolders.add(folder.getAbsolutePath());
				break;
			}
		}		
	};

	private ArrayList<String> selectCurrentFolder(File folder, ArrayList<String> songs) {
		Assert.assertTrue(folder.isDirectory());
		
		File[] files = folder.listFiles();
		
		mHandler.sendMessage(mHandler.obtainMessage(MSG_PROGRESS, folder.getName()));
		for(int i=0; i < files.length; i++)
	    {
	       File file = files[i];
	       if(file.isFile() && (file.getName().toLowerCase().endsWith("mp3") || file.getName().toLowerCase().endsWith("ogg")))
	    	   songs.add(file.getAbsolutePath());
	       else if(file.isDirectory())
	    	   selectCurrentFolder(file, songs);
	    }
		
		return songs;
	}

	@Override
	public void onClick(View view) {
		switch(view.getId()) {
		case R.id.select:
			pd = ProgressDialog.show(this, getResources().getString(R.string.processing), "");
			new Thread() {
				public void run() {
					ArrayList<String> songs_paths = new ArrayList<String>();
					ArrayList<Song> tempSongs = new ArrayList<Song>();
					
					for(int i = 0; i < mFolders.size(); i++)
						selectCurrentFolder(new File(mFolders.get(i)), songs_paths);
					
					Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
					String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
					Cursor cursor = getContentResolver().query(media, Song.FILLED_PROJECTION, selection, null, null);
					
					if (cursor == null || cursor.getCount() == 0)
						return;
					
					while(cursor.moveToNext())
					{
						Song current = new Song(-1);
						current.populate(cursor);
						
						if(songs_paths.contains(current.path))
							tempSongs.add(current);
					}
					cursor.close();
					
					if(!tempSongs.isEmpty())
						PlaybackService.get(getApplicationContext()).mTimeline.convertFromSongArray(tempSongs);
					else
						mHandler.sendEmptyMessage(MSG_FAIL);
					mHandler.sendEmptyMessage(MSG_FINISH);
				}
			}.start();
			break;
		case R.id.clear:
			if(mFolders.isEmpty())
				finish();
			else {
				mFolders.clear();
				mFolderList.invalidateViews();
			}
			break;
		}
		
	}

	@Override
	public boolean handleMessage(Message msg) {
		switch(msg.what) {
		case MSG_FINISH:
			pd.dismiss();
			finish();
			break;
		case MSG_PROGRESS:
			String folder = (String)msg.obj;
			pd.setMessage(folder);
			break;
		case MSG_FAIL:
			Toast.makeText(this, R.string.no_songs, Toast.LENGTH_SHORT).show();
			break;
		}
		return false;
	}

	@Override
	public void onBackPressed() {
		if(pwd == null || pwd.getAbsolutePath().equals(root) || pwd.getParent() == null)
			super.onBackPressed();
		else getDir(pwd.getParent());
	}
	
}
