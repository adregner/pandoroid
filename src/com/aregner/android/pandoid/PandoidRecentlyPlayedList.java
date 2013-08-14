package com.aregner.android.pandoid;

import java.util.List;

import com.aregner.pandora.Song;

import android.app.Activity;
import android.app.ListActivity;
//import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;



public class PandoidRecentlyPlayedList extends ListActivity{
	public static final String LOG_TAG = "PandoidRecentlyPlayedList";
	private static PandoraRadioService pandora;
	RecentListAdapter adapter;
	List<Song> results;
	
	private static PlaySongTask playSongTask;	
//	private static ProgressDialog waiting;
	private static boolean loading = false;
	
	IntentFilter filter = new IntentFilter();
	private BroadcastReceiver receiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.i(LOG_TAG, "Song Change Broadcast Received");
			adapter.notifyDataSetChanged();
		}
	};

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		pandora = PandoraRadioService.getInstance(true);
		results = pandora.getRecentlyPlayed();
		adapter = new RecentListAdapter(results, this);
		
		final ListView lv = getListView();
		setListAdapter(adapter);
		registerForContextMenu(lv);
		lv.setTextFilterEnabled(true);
		
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				view.showContextMenu();
			}
		});
		
		if(loading){
			playSongTask = (PlaySongTask) getLastNonConfigurationInstance();
			playSongTask.attach(this);
		}
		
	}
	@Override
	public Object onRetainNonConfigurationInstance(){
		if(playSongTask != null)
			playSongTask.detach();
		
		return playSongTask;
	}
	@Override
	public void onSaveInstanceState(Bundle outState){
		outState.putBoolean("loading", loading);
	}
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState){
		loading = savedInstanceState.getBoolean("loading");
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		filter.addAction(PandoraRadioService.SONG_CHANGE);
		filter.addAction(Intent.ACTION_SCREEN_ON);
		registerReceiver(receiver, filter);
	}
	protected void onPause(){
		super.onPause();
		unregisterReceiver(receiver);
	}
	protected void onStop(){
		super.onStop();
	}
	protected void onDestroy(){
		super.onDestroy();
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
	    menu.setHeaderTitle("Options");
	    menu.add("Play");
	//    menu.add("Download");
	}
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		playSongTask = (PlaySongTask) new PlaySongTask(this).execute(results.get(info.position));
		
		return true;
	}
	
	private class RecentListAdapter extends BaseAdapter {
	
		private List<Song> results;
		private Context context;
	
		public RecentListAdapter(List<Song> results, Context context) {
			this.results = results;
			this.context = context;
		}
	
		public int getCount() {
			return results.size();
		}
	
		public Song getItem(int position) {
			return results.get(position);
		}
	
		public long getItemId(int position) {
			return position;
		}
	
		public View getView(int position, View convertView, ViewGroup parent) {
			String artist, title;
			Song result = results.get(position);
			
			View itemLayout = LayoutInflater.from(context).inflate(R.layout.search_item, parent, false);
			
			TextView tvName = (TextView) itemLayout.findViewById(R.id.item_name);
			
			artist = result.getArtist();
			title = result.getTitle();
			
			if(title != null){
				title += " by ";
			}
			else {
				title = "";
			}
			
			tvName.setText(title + artist);

			
			return itemLayout;
		}
	}
	
	static class PlaySongTask extends AsyncTask<Song, Void, Void> {
		
		private Activity activity;
		
		public PlaySongTask(Activity activity){
			this.activity = activity;
		}
		@Override
		protected void onPreExecute() {
			loading = true;
		//	waiting = ProgressDialog.show(activity, "", activity.getString(R.string.searching));
		}
		@Override
		protected Void doInBackground(Song... query) {
			pandora.play(query[0]);
			return null;
		}
		@Override
		protected void onPostExecute(Void result) {
			if(activity != null){
			//    `	waiting.dismiss();
			}
			loading = false;
		}
		public void detach(){
			this.activity = null;
		}
		public void attach(Activity activity){
			this.activity = activity;
			
	//		waiting = ProgressDialog.show(activity, "", activity.getString(R.string.searching));		
		}
	}	

}

