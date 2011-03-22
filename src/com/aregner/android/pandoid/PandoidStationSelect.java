package com.aregner.android.pandoid;

import java.util.ArrayList;
import java.util.List;

import com.aregner.pandora.PandoraRadio.Station;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class PandoidStationSelect extends ListActivity {
	private PandoraRadioService pandora;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		pandora = PandoraRadioService.getInstance(true);
		ArrayList<Station> stations = pandora.getStations();

		ListView lv = getListView();
		setListAdapter(new StationListAdapter(stations, this));
		lv.setTextFilterEnabled(true);
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				//Station station = PandoraRadioService.getInstance().getStations().get(position);
				setResult(RESULT_OK, (new Intent()).putExtra("stationId", id));
				finish();
				//finishActivity(PandoidPlayer.REQUIRE_SELECT_STATION);
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		PandoidPlayer.dismissWaiting();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.player_menu, menu);
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