/* Pandoroid Radio - open source pandora.com client for android
 * Copyright (C) 2011  Andrew Regner <andrew@aregner.com>
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.pandoroid.android;

import java.util.ArrayList;
import java.util.List;

import com.pandoroid.pandora.Station;
import com.pandoroid.android.R;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class PandoroidStationSelect extends ListActivity {
	private PandoraRadioService pandora;
	private static ProgressDialog waiting;
	private SharedPreferences prefs;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		//Disable StrictMode for 3.0+
		//StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());
		super.onCreate(savedInstanceState);
		prefs = PreferenceManager.getDefaultSharedPreferences(PandoroidStationSelect.this);
		new StationFetcher().execute();
	}
	
	/**
	 * Task to get and fill the list
	 */
	private class StationFetcher extends AsyncTask<Void, Void, ArrayList<Station>>{
		@Override
		protected void onPreExecute(){
			waiting = ProgressDialog.show(PandoroidStationSelect.this, "",  getString(R.string.loading));
		}
		
		
		@Override
		protected ArrayList<Station> doInBackground(Void... params) {
			pandora = PandoraRadioService.getInstance(true);
			return pandora.getStations();
		}
		
		@Override
		protected void onPostExecute(ArrayList<Station> stations){
			ListView lv = getListView();
			setListAdapter(new StationListAdapter(stations, PandoroidStationSelect.this));
			lv.setTextFilterEnabled(true);
			lv.setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
					//Store it in the prefs
					prefs.edit().putLong("lastStationId", id).apply();
					setResult(RESULT_OK, (new Intent()).putExtra("stationId", id));
					finish();
					finishActivity(PandoroidPlayer.REQUIRE_SELECT_STATION);
				}
			});
			waiting.dismiss();
		}
		
	}
	
	
	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		//MenuInflater inflater = getMenuInflater();
		//inflater.inflate(R.menu.player_menu, menu);
		return true;
	}

	private class StationListAdapter extends BaseAdapter {

		private List<Station> stations;
		private Context context;

		public StationListAdapter(List<Station> StationList, Context context) {
			this.stations = StationList;
			this.context = context;
		}

		public int getCount() {
			return stations.size();
		}

		public Station getItem(int position) {
			return stations.get(position);
		}

		public long getItemId(int position) {
			return stations.get(position).getId();
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			Station station = stations.get(position);
			View itemLayout = LayoutInflater.from(context).inflate(R.layout.stations_item, parent, false);
			
			TextView tvName = (TextView) itemLayout.findViewById(R.id.stations_name);
			tvName.setText(station.getName());
			
			//ImageView ivImage = (ImageView) itemLayout.findViewById(R.id.stations_icon);
			//ImageDownloader imageDownloader = new ImageDownloader();
			//imageDownloader.download(station.getAlbumCoverUrl(), ivImage);
			
			return itemLayout;
		}

	}

}