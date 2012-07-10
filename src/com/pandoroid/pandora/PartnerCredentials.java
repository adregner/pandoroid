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

//A simple struct like class
public class PartnerCredentials {
	public String rpc_url;
	public String device_model;
	public String username;
	public String password;
	public String d_cipher;
	public String e_cipher;
	
	PartnerCredentials(){
		rpc_url = null;
		device_model = null;
		username = null;
		password = null;
		d_cipher = null;
		e_cipher = null;
	}
}
