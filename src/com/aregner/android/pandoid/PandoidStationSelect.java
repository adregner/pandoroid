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
package com.aregner.android.pandoid;

import java.util.ArrayList;
import java.util.List;

import com.aregner.pandora.Station;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class PandoidStationSelect extends ListActivity {
	private PandoraRadioService pandora;
	ArrayList<Station> stations;
	StationListAdapter adapter;
	private static final int CREATE_STATION = 1;
	private static final int GET_STATIONS_FAILED = 3;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		pandora = PandoraRadioService.getInstance(true);
		
		try{
			stations = pandora.getStations();
			ListView lv = getListView();
			adapter = new StationListAdapter(stations, this);
			setListAdapter(adapter);
			lv.setTextFilterEnabled(true);
			lv.setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
					//Station station = PandoraRadioService.getInstance().getStations().get(position);
					setResult(RESULT_OK, (new Intent()).putExtra("stationId", id));
					finish();
					//finishActivity(PandoidPlayer.REQUIRE_SELECT_STATION);
				}
			});
			registerForContextMenu(lv);
		}
		catch(NullPointerException e){
			setResult(GET_STATIONS_FAILED);
			finish();
		}
	}


	@Override
	protected void onResume() {
		super.onResume();
		PandoidPlayer.dismissWaiting();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.station_select_menu, menu);
		return true;
	}
	
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case R.id.create_station:			
			startActivity(new Intent(PandoidStationSelect.this, PandoidStationCreator.class).addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT));
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
	    menu.setHeaderTitle("Delete Station");
	    menu.add("Delete");
	}
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		pandora.deleteStation(stations.get(info.position));
		stations.remove(info.position);
		adapter.notifyDataSetChanged();
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