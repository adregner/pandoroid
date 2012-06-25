/* This file is part of Pandoroid
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
package com.tortel.pandora;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import org.json.JSONArray;
import org.json.JSONObject;

/*
 * Description: An abstract class for a logical organization of the following
 *  two functions.
 */
public abstract class JSONHelper {

	/*
	 * Description: I have no clue why the JSON object doesn't implement this
	 * 	within itself. It seems so logical to do so, but here's my
	 * 	implementation, and it seems very darn complete to me. --Dylan Powers
	 */
	public static Map<String, Object> toMap(JSONObject object){
		Map<String, Object> mapping = new HashMap<String, Object>();
		
		//These iterators are like gimped up shadows of what iterators should be.
		@SuppressWarnings("rawtypes")
		Iterator keys = object.keys();
		
		//Java's naming conventions are horrible. This will in fact get the
        //the first key, and won't skip anything.
		while (keys.hasNext()) { 
            String key = (String) keys.next();
            Object item = object.opt(key);
            if (item instanceof JSONObject){
            	
            	//Recursive call if we come across a JSONObject inside.
            	mapping.put(key, toMap((JSONObject) item));
            }
            else if (item instanceof JSONArray){
            	mapping.put(key, toVector((JSONArray) item));
            }
            
            //All other types won't be anything funky.
            else{
            	mapping.put(key, object.opt(key));
            }
        }
        return mapping;
	}
	
	/*
	 * Description: Same as above except for JSONArrays.
	 */
	public static Vector<Object> toVector(JSONArray object){
		Vector<Object> array = new Vector<Object>();
		int object_len = object.length();
		for (int i = 0; i != object_len; ++i){
			Object item = object.opt(i);
			if (item instanceof JSONArray){
				array.add(toVector((JSONArray) item));
			}
			else if(item instanceof JSONObject){
				array.add(toMap((JSONObject) item));
			}
			else{
				array.add(item);
			}
		}
		return array;
	}
}
