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
package com.pandoroid;

import java.util.ArrayList;
import java.util.List;

import com.pandoroid.pandora.Station;
import com.pandoroid.R;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
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
	private boolean m_is_bound;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		//Disable StrictMode for 3.0+
		//StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());
		super.onCreate(savedInstanceState);
		doBindService();
		prefs = PreferenceManager.getDefaultSharedPreferences(PandoroidStationSelect.this);


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
					String str_id = Long.toString(id);
					prefs.edit().putString("lastStationId", str_id).apply();
					setResult(RESULT_OK, (new Intent()).putExtra("stationId", str_id));
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

	protected void onDestroy(){
		super.onDestroy();
		doUnbindService();
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
			return Long.parseLong(stations.get(position).getStationId());
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
	
	//Necessary service stuff taken straight from the developer reference for Service
	private ServiceConnection m_connection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className, IBinder service) {
	        // This is called when the connection with the service has been
	        // established, giving us the service object we can use to
	        // interact with the service.  Because we have bound to a explicit
	        // service that we know is running in our own process, we can
	        // cast its IBinder to a concrete class and directly access it.
	        pandora = ((PandoraRadioService.PandoraRadioBinder)service).getService();
		    m_is_bound = true;
			new StationFetcher().execute();
	    }

	    public void onServiceDisconnected(ComponentName className) {
	        // This is called when the connection with the service has been
	        // unexpectedly disconnected -- that is, its process crashed.
	        // Because it is running in our same process, we should never
	        // see this happen.
	        pandora = null;
	        m_is_bound = false;
	    }  
	};

	void doBindService() {
	    // Establish a connection with the service.  We use an explicit
	    // class name because we want a specific service implementation that
	    // we know will be running in our own process (and thus won't be
	    // supporting component replacement by other applications).
	    bindService(new Intent(this, 
	                PandoraRadioService.class), 
	                m_connection, Context.BIND_AUTO_CREATE);
	}

	void doUnbindService() {
	    if (m_is_bound) {
	        // Detach our existing connection.
	        unbindService(m_connection);
	        m_is_bound = false;
	    }
	}

}