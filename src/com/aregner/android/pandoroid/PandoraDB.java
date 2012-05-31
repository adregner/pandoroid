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
package com.aregner.android.pandoroid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import com.aregner.pandora.Station;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class PandoraDB extends SQLiteOpenHelper {

	public static final int DATABASE_VERSION = 1;
	public static final String STATION_TABLE_NAME = "stations";
	public static final String STATION_TABLE_CREATE =
		"CREATE TABLE " + STATION_TABLE_NAME + " (" +
		"stationId TEXT PRIMARY KEY, " +
		"stationIdToken TEXT, " +
		"isCreator INTEGER, " +
		"isQuickMix INTEGER, " +
		"stationName TEXT);";

	public PandoraDB(Context context) {
		super(context, "pandoradb", null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(STATION_TABLE_CREATE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		
	}
	
	/** */
	public void syncStations(ArrayList<Station> stations) {
		SQLiteDatabase write = getWritableDatabase();
		Iterator<Station> stationIter = stations.iterator();
		
		while(stationIter.hasNext()) {
			Station station = stationIter.next();
			
			ContentValues values = new ContentValues(5);
			values.put("stationId", station.getStationId());
			values.put("stationIdToken", station.getStationIdToken());
			//values.put("isCreator", station.isCreator());
			values.put("isQuickMix", station.isQuickMix());
			values.put("stationName", station.getName());
			
			write.insertWithOnConflict(PandoraDB.STATION_TABLE_NAME, null, values , SQLiteDatabase.CONFLICT_IGNORE);
		}
	}
	
	@SuppressWarnings("unchecked")
	public HashMap<String,Object>[] getStations() {
		Cursor records = getReadableDatabase().query(STATION_TABLE_NAME, null, null, null, null, null, null);
		HashMap<String, Object>[] stations = (HashMap<String, Object>[]) new HashMap<?,?>[records.getCount()];
		
		for(int s=0; s<stations.length; s++) {
			records.moveToPosition(s);

			stations[s].put("stationId", records.getString(0));
			stations[s].put("stationIdToken", records.getString(1));
			//stations[s].put("isCreator", records.getInt(2));
			stations[s].put("isQuickMix", records.getInt(3));
			stations[s].put("stationName", records.getString(4));
		}
		
		return stations;
	}
}
