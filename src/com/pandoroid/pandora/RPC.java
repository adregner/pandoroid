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

package com.pandoroid.pandora;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Map;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.impl.client.DefaultHttpClient;

/*
 * Description: This is the RPC client implementation for interfacing with 
 * 	Pandora's servers. At the moment it uses Pandora's JSON API, but will
 *  hopefully be useful for whatever Pandora throws at us in the future.
 */
public class RPC {
	private HttpClient client;
	private String entity_type;
	private String request_url;
	private String user_agent;
	
	/*
	 * Description: Our constructor class. This will set our default parameters
	 * 	for subsequent http requests, along with the MIME type for our entity
	 *  (i.e. 'text/plain' for the current JSON protocol), and the partial URL 
	 *  for the server (i.e. 'tuner.pandora.com/path/to/request/').
	 */
	public RPC(String default_url, 
			   String default_entity_type, 
			   String default_user_agent){
		client = new DefaultHttpClient();
		request_url = default_url;
		entity_type = default_entity_type;
		user_agent = default_user_agent;				
	}
	
	/*
	 * Description: This function contacts the remote server with a string
	 * 	type data package (could be JSON), and returns the remote server's 
	 * 	response in a string.
	 */
	public String call(Map<String, String> url_params, 
			           String entity_data,
			           boolean require_secure) 
			                            throws Exception, HttpResponseException{
		
		if (url_params == null || url_params.size() == 0){
			throw new Exception("Missing URL paramaters");
		}
		if (entity_data == null){
			throw new Exception("Missing data for HTTP entity.");
		}
		
		String full_url;
		
		if (require_secure){
			full_url = "https://" + request_url;
		}
		else{
			full_url = "http://" + request_url;
		}
		
		HttpPost request = new HttpPost();
		if (user_agent != null){
			request.addHeader("User-Agent", user_agent);
		}
		
		URI uri = new URI(full_url.concat(makeUrlParamString(url_params)));
		request.setURI(uri);
		StringEntity entity = null;
		
		try{
			entity = new StringEntity(entity_data);
			if (entity_type != null){
				entity.setContentType(entity_type);
			}
		}
		catch (Exception e){
			throw new Exception("Pandora RPC Http entity creation error");
		}
		
		request.setEntity(entity);
		
		//Send to the server and get our response 
		HttpResponse response = client.execute(request);
		int status_code = response.getStatusLine().getStatusCode();
		
		if (status_code != HttpStatus.SC_OK){
			throw new HttpResponseException(status_code, "HTTP status code: " 
		                                     + status_code + " != " 
					                         + HttpStatus.SC_OK);
		}
		
		//Read the response returned and turn it from a byte stream to a string.
		HttpEntity response_entity = response.getEntity();
		int BUFFER_BYTE_SIZE = 512;
		String ret_data = new String();
		byte[] bytes = new byte[BUFFER_BYTE_SIZE];
		
		//Check the entity type (usually 'text/plain'). Probably doesn't need
		//to be checked.
		if (response_entity.getContentType().getValue().equals(entity_type)){			
			InputStream content = response_entity.getContent();
			int bytes_read = BUFFER_BYTE_SIZE;
			
			//Rather than read an arbitrary amount of bytes, lets be sure to get
			//it all.
			while((bytes_read = content.read(bytes, 0, BUFFER_BYTE_SIZE)) != -1){				
				ret_data += new String(bytes, 0, bytes_read);
			}
		}
		else{
			throw new Exception("Improper server response entity type: " + 
								response_entity.getContentType().getValue());
		}
		
		return ret_data;
	}
	
	/*
	 * Description: Here we create a URL method string with the parameters
	 * 	given. It automatically applies the '?' character to the beginning
	 *  of strings, so multiple calls to this function will create an invalid 
	 *  URL request string.
	 */
	private String makeUrlParamString(Map<String, String> mapped_url_params){
		String url_string = "?";
		boolean first_loop = true;
		for (Map.Entry<String, String> entry : mapped_url_params.entrySet()){
			if (!first_loop){
				url_string += "&";
			}
			else{
				first_loop = false;
			}
			url_string += URLEncoder.encode(entry.getKey()) + "=" 
			              + URLEncoder.encode(entry.getValue());			
		}
		
		return url_string;
	}
}
