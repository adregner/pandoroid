package com.aregner.android.pandoid;

import com.aregner.pandora.Station;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;

public class PandoidSearchActivity extends Activity {
	
	private static final int SEARCH_RESULT = 0x01;
	private static PandoraRadioService pandora;
	private static boolean searching = false;
	private static SearchTask searchTask;
	private static String searchTextSave;
	
	private static ProgressDialog waiting;
	private EditText searchQuery;
	
	
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setContentView(R.layout.station_creator);
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		pandora = PandoraRadioService.getInstance(false);
		searchQuery = (EditText)findViewById(R.id.searchQuery);
		
		if(searching){
			searchTask = (SearchTask) getLastNonConfigurationInstance();
			searchTask.attach(this);
		}
	}
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		
		if(requestCode == SEARCH_RESULT && resultCode == RESULT_OK){
			String musicId = intent.getStringExtra("musicId");
			Station station = pandora.createStation(musicId);
			Intent data = new Intent().putExtra("stationId", station.getId());
			setResult(Activity.RESULT_OK, data);
			
			finish();
		}
	}
	@Override
	public Object onRetainNonConfigurationInstance(){
		if(searchTask != null)
			searchTask.detach();
		
		return searchTask;
	}
	@Override
	public void onSaveInstanceState(Bundle outState){
		outState.putBoolean("searching", searching);
		searchTextSave = searchQuery.getText().toString();
		outState.putString("searchSting", searchTextSave);
	}
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState){
		searching = savedInstanceState.getBoolean("waiting");
		searchTextSave = savedInstanceState.getString("searchString");
		
		if(searchTextSave != null){
			searchQuery.setText(searchTextSave);
		}
	}
	public void onSearchQuery(View button){
		String query = searchQuery.getText().toString();
		
		searchTask = (SearchTask) new SearchTask(this).execute(query);
	}
	protected void onStop(){
		super.onStop();
		if(waiting != null){
			waiting.dismiss();
		}
	}
	protected void onPause(){
		super.onPause();
			if(waiting != null){
				waiting.dismiss();
			}
	}
	static class SearchTask extends AsyncTask<String, Void, Void> {
		
		private Activity activity;
		
		public SearchTask(Activity activity){
			this.activity = activity;
		}
		@Override
		protected void onPreExecute() {
			searching = true;
			waiting = ProgressDialog.show(activity, "", activity.getString(R.string.searching));
		}
		@Override
		protected Void doInBackground(String... query) {
			Spinner spin = (Spinner)(activity.findViewById(R.id.queryType));
			String text = spin.getSelectedItem().toString();
			try{
				pandora.search(query[0], text);
				if(activity != null){
					activity.startActivityForResult(new Intent(activity, PandoidSearchResultList.class), SEARCH_RESULT);
				}
				searching = false;
			}
			catch(NullPointerException e){
				activity.setResult(PandoidPlayer.GET_STATIONS_FAILED);
				activity.finish();
			}
			return null;
		}
		@Override
		protected void onPostExecute(Void result) {
			if(activity != null){
				waiting.dismiss();
			}
			searching = false;
		}
		public void detach(){
			this.activity = null;
		}
		public void attach(Activity activity){
			this.activity = activity;
			
			waiting = ProgressDialog.show(activity, "", activity.getString(R.string.searching));		
		}
	}	
}
