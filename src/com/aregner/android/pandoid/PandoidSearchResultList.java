package com.aregner.android.pandoid;

import java.util.List;

import com.aregner.pandora.SearchResult;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;


public class PandoidSearchResultList extends ListActivity {
	private PandoraRadioService pandora;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		pandora = PandoraRadioService.getInstance(true);
		final List<SearchResult> results = pandora.getSearchResults();
		ListView lv = getListView();
		setListAdapter(new ResultListAdapter(results, this));
		lv.setTextFilterEnabled(true);
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				String musicId = results.get(position).getMusicId();
				setResult(RESULT_OK, (new Intent()).putExtra("musicId", musicId));
				finish();
			}
		});
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		PandoidPlayer.dismissWaiting();
	}
	
	private class ResultListAdapter extends BaseAdapter {
	
		private List<SearchResult> results;
		private Context context;
	
		public ResultListAdapter(List<SearchResult> results, Context context) {
			this.results = results;
			this.context = context;
		}
	
		public int getCount() {
			return results.size();
		}
	
		public SearchResult getItem(int position) {
			return results.get(position);
		}
	
		public long getItemId(int position) {
			return position;
		}
	
		public View getView(int position, View convertView, ViewGroup parent) {
			String artist, title;
			SearchResult result = results.get(position);
			
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
			
			//ImageView ivImage = (ImageView) itemLayout.findViewById(R.id.stations_icon);
			//ImageDownloader imageDownloader = new ImageDownloader();
			//imageDownloader.download(station.getAlbumCoverUrl(), ivImage);
			
			return itemLayout;
		}
	
	}

}
