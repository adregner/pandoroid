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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.HttpResponseException;

import com.pandoroid.pandora.RPCException;
import com.pandoroid.pandora.Station;
import com.pandoroid.PandoraRadioService.ServerAsyncTask;
import com.pandoroid.R;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
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
	private static AlertDialog m_alert;
	private static boolean m_alert_active_flag = false;
	private PandoraRadioService m_service;
	private StationFetcher m_station_fetch_task;
	private static boolean m_stations_current_flag = false;
	private static ProgressDialog m_waiting;
	private boolean m_is_bound;

	/*
	 * Activity start and end stuff
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		doBindService();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		if (m_is_bound){
			startup();
		}
		else {
			m_waiting = ProgressDialog.show(PandoroidStationSelect.this, 
	                						"",  
	            							getString(R.string.loading));
		}
	}

	protected void onStop(){
		super.onStop();
		if (m_alert_active_flag) {
			m_alert.dismiss();
		}
	}
	
	protected void onDestroy(){
		super.onDestroy();
		doUnbindService();
	}
	/* End Activity stuff */
	
	/*
	 * Fancy pants service stuff
	 */
	private ServiceConnection m_connection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className, IBinder service) {
	        // This is called when the connection with the service has been
	        // established, giving us the service object we can use to
	        // interact with the service.  Because we have bound to a explicit
	        // service that we know is running in our own process, we can
	        // cast its IBinder to a concrete class and directly access it.
	        m_service = ((PandoraRadioService.PandoraRadioBinder)service).getService();
		    m_is_bound = true;
		    m_waiting.dismiss();
			startup();
	    }

	    public void onServiceDisconnected(ComponentName className) {
	        // This is called when the connection with the service has been
	        // unexpectedly disconnected -- that is, its process crashed.
	        // Because it is running in our same process, we should never
	        // see this happen.
	        m_service = null;
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
	/* End service stuff */
	
	private void dismissAlert() {
		if (m_alert_active_flag) {
			m_alert.dismiss();
			m_alert_active_flag = false;
		}
	}	
	
	/**
	 * Description: Special command for interpreting a back command.
	 */
	public void onBackPressed(){
		m_stations_current_flag = false;
		if (m_service.getCurrentStation() == null){
			moveTaskToBack(true);
		}
		else{
			super.onBackPressed();
		}
	}

	private void showAlert(AlertDialog new_alert) {
		if (hasWindowFocus()){
			dismissAlert();
			m_alert = new_alert;
			m_alert.show();
			m_alert_active_flag = true;
		}
	}
	
	/**
	 * Description: Displays the stations to the screen.
	 */
	private void showStations(){
		ListView lv = getListView();
		setListAdapter(new StationListAdapter(m_service.getStations(), 
				                              PandoroidStationSelect.this));
		lv.setTextFilterEnabled(true);
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, 
					                View view, 
					                int position, 
					                long id) {
				String str_id = Long.toString(id);
				m_service.setCurrentStation(str_id);
				m_service.startPlayback();
				m_stations_current_flag = false;
				finish();
			}
		});
	}
	
	private void startup(){
		showStations();
		if (m_alert_active_flag){
			m_alert.show();
		}
		else if (!m_stations_current_flag 
				 && (m_station_fetch_task == null
					 || m_station_fetch_task.getStatus() == AsyncTask.Status.FINISHED)){
			
			m_station_fetch_task = new StationFetcher();
			m_station_fetch_task.execute();
		}
	}
	
	/**
	 * Description: Task to get and update the station list
	 */
	private class StationFetcher extends ServerAsyncTask<Void>{
		
		@Override
		protected Integer doInBackground(Void... void_params) {
			Integer success_flag = -1;
			try {
				m_service.updateStations();
				//exceptionTest();
			} catch (RPCException e) {
				Log.e("Pandoroid", "Error fetching stations.", e);
				success_flag = rpcExceptionHandler(e);
			} catch (HttpResponseException e) {
				Log.e("Pandoroid", "Error fetching stations.", e);
				success_flag = httpResponseExceptionHandler(e);
			} catch (IOException e) {
				Log.e("Pandoroid", "Error fetching stations.", e);
				success_flag = ioExceptionHandler(e);
			} catch (Exception e) {
				Log.e("Pandoroid", "Error fetching stations.", e);
				success_flag = generalExceptionHandler(e);
			}
			
			return success_flag;
		}
		
		protected void onPostExecute(Integer success){
			if (success.intValue() < 0){
				m_stations_current_flag = true;
				showStations();
			}
			else{
				AlertDialog.Builder 
					alert_builder = super.buildErrorDialog(success, 
							 							   PandoroidStationSelect.this);
				alert_builder.setPositiveButton("Back", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							dismissAlert();
							m_stations_current_flag = false;
							if (m_service.getCurrentStation() == null){
								moveTaskToBack(true);
							}
							else{
								finish();
							}
						}
					});
				alert_builder.setCancelable(true);
				alert_builder.setOnCancelListener(new OnCancelListener(){

					public void onCancel(DialogInterface arg0) {
						m_alert_active_flag = false;						
					}
					
				});
				showAlert(alert_builder.create());
			}
		}
		
		protected void retryAction(){
			if (!m_stations_current_flag 
					 && (m_station_fetch_task == null
						 || m_station_fetch_task.getStatus() == AsyncTask.Status.FINISHED)){
				
				m_station_fetch_task = new StationFetcher();
				m_station_fetch_task.execute();
			}
		}

		@Override
		//This is a half-assed quit
		protected void quit() {
			m_stations_current_flag = false;
			m_service.stopPlayback();
			doUnbindService();
			
			//The main activity is still bound so the service won't be destroyed
			//but we can sure try.
			stopService(new Intent(PandoroidStationSelect.this, PandoraRadioService.class));
			moveTaskToBack(true);
			finish();
		}

		@Override
		protected void reportAction() {
			String issue_url = "https://github.com/dylanPowers/pandoroid/issues";
			Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(issue_url));
			startActivity(i);
		}

		@Override
		protected void showAlert(AlertDialog new_alert) {
			PandoroidStationSelect.this.showAlert(new_alert);
		}		
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
			View itemLayout = LayoutInflater.from(context).inflate(R.layout.stations_item, 
					                                               parent, 
					                                               false);
			
			TextView tvName = (TextView) itemLayout.findViewById(R.id.stations_name);
			tvName.setText(station.getName());
			
			//ImageView ivImage = (ImageView) itemLayout.findViewById(R.id.stations_icon);
			//ImageDownloader imageDownloader = new ImageDownloader();
			//imageDownloader.download(station.getAlbumCoverUrl(), ivImage);
			
			return itemLayout;
		}

	}
	


}