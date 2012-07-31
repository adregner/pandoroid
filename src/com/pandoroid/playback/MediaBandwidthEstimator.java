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
package com.pandoroid.playback;

import java.util.LinkedList;

import android.util.Log;

/**
 * Description: A class to keep track of available bandwidth estimates.
 * @author Dylan Powers <dylan.kyle.powers@gmail.com>
 */
public class MediaBandwidthEstimator {
	
	/*
	 * Public
	 */
	
	//This is the number of data points we're using to calculate the bitrate.
	public static final int NUM_AVERAGED_DATA_POINTS = 30;
	
	public static final boolean DEBUG_INFO = true;
	
	/**
	 * Description: Constructor
	 */
	public MediaBandwidthEstimator(){
		m_active_audio_sessions = new LinkedList<AudioSession>();
		m_sum = 0;
		m_bitrate_queue = new LinkedList<Float>();
	}
	
	/**
	 * Description: Updates the bitrate with new data. Limited data is given
	 * 	so the approximation of bitrate is limited. This depends heavily
	 * 	on the fact that the buffering update dishes things out in consistent
	 * 	1 second interval.
	 * @param audio_session_id -The audioSessionId given by the MediaPlayer.
	 * @param buffer_position -The percent buffered as given in onBufferingUpdate.
	 * @param media_length -The length of the song being buffered in milliseconds.
	 * @param bitrate -The stated bitrate of the song.
	 */
	public void update(int audio_session_id, 
			           int buffer_position, 
			           int media_length,
			           int bitrate,
			           long time_stamp){
		
		//Let's be as efficient as possible and skip everything for 100% completions.
		if (buffer_position != 100){
			AudioSession session = getAudioSession(audio_session_id);
			float bandwidth = session.calcBitrate(buffer_position, media_length, bitrate, time_stamp);
			
			if (bandwidth >= 0){
				if (session.queued_flag == true){
					resetQueueFlags();
					if (m_bitrate_queue.size() == NUM_AVERAGED_DATA_POINTS){
						m_sum -= m_bitrate_queue.poll().floatValue();
					}
					m_bitrate_queue.add(Float.valueOf(bandwidth));
				}
				else{
					float total_bandwidth = 0F;
					if (m_bitrate_queue.size() != 0){
						total_bandwidth = m_bitrate_queue.pollLast().floatValue();
					}
					total_bandwidth += bandwidth;
					m_bitrate_queue.add(total_bandwidth);
				}
				session.queued_flag = true;
				m_sum += bandwidth;
	
				if (DEBUG_INFO){
					Log.d("Pandoroid", 
						  "Buffer: " + Integer.toString(buffer_position) + "%    " +
					      "Bitrate: " + Float.toString(bandwidth) + "kpbs    " +
						  "Avg: " + Integer.toString((int) m_sum/m_bitrate_queue.size()) + "kpbs"
					);
				}
			}
		}
		else{
			setAudioSessionFinished(audio_session_id);
		}
	}
	
	/**
	 * Description: The average bitrate calculated so far.
	 * @return The average bitrate.
	 */
	public int getBitrate(){
		if (m_bitrate_queue.size() != 0){
			return ((int) m_sum / m_bitrate_queue.size());
		}
		else{
			return 0;
		}
	}
	
	/**
	 * Description: Checks to see if a media player exists in the current and
	 * 	for future bandwidth calculations.
	 * @param id
	 * @return A boolean if the specified id does exist.
	 */
	public boolean doesIdExist(int id){
		for (int i = 0; i < m_active_audio_sessions.size(); ++i){
			if (m_active_audio_sessions.get(i).getId() == id){
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Description: Resets the bandwidth estimator to 0.
	 */
	public void reset(){
		m_active_audio_sessions.clear();
		m_sum = 0;
		m_bitrate_queue.clear();
	}
	
	/**
	 * Description: Sets the audio session as finished so it can be destroyed,
	 * 	and forgotten about. 
	 * @param audio_session_id -The audio session id given by MediaPlayer.
	 */
	public void setAudioSessionFinished(int audio_session_id){
		
		//Realistically, there should never be more than a couple of ids running
		//at the same time so this loop should end lightening quick.
		for (int i = 0; i < m_active_audio_sessions.size(); ++i){
			if (m_active_audio_sessions.get(i).getId() == audio_session_id){
				m_active_audio_sessions.remove(i);
				return;
			}
		}
	}
	
	/**
	 * Description: A class for handling information particular to a single
	 * 	audio session.
	 * @author Dylan Powers <dylan.kyle.powers@gmail.com>
	 */
	public class AudioSession{
		
		//Variable for whether or not a bitrate from this class has been added
		//into the top most position of the queue yet.
		public boolean queued_flag;
		
		
		/**
		 * Description: Constructor
		 * @param audio_session_id -The audio session id given by MediaPlayer.
		 */
		public AudioSession(int audio_session_id){
			m_id = audio_session_id;
			m_prev_buffer_pos = 0;
			queued_flag = true; //Things have better consistency if this is began as true;
		}
		
		/**
		 * Description: Calculates a bandwidth estimate for the time period 
		 * 	given the buffer position, the media length, and the audio bitrate.
		 * @param buffer_position -The percentage buffered.
		 * @param media_length -The length of the song being buffered in milliseconds.
		 * @param bitrate -The stated bitrate of the song.
		 * @return The calculated bitrate or a value less than 0 if the calculation
		 * 	failed.
		 */
		public float calcBitrate(int buffer_position, 
				                 int media_length, 
				                 int bitrate,
				                 long time_stamp){
			float time_diff;
			float kilos_trans;
			float bandwidth = -1F;
			
			if (m_prev_buffer_pos != 0) {
				
				//Calculate the length of time buffered.
				time_diff = (media_length * (
						(float) (buffer_position - m_prev_buffer_pos) / 100F
						                    )) / 1000F;
				
				//Calculate the amount of kilobits transferred.
				kilos_trans = bitrate * time_diff;
				
				//Find the transferred kbps by dividing by the amount of real
				//time it took.
				bandwidth = kilos_trans / (
						(time_stamp - m_prev_time) / 1000F
						                  );
			}
			m_prev_buffer_pos = buffer_position;
			m_prev_time = time_stamp;
			
			return bandwidth;
		}
		
		/**
		 * Description: Simple accessor to the id.
		 * @return
		 */
		public int getId(){
			return m_id;
		}
		

		private int m_id;
		private long m_prev_time;
		private int m_prev_buffer_pos;
	}
	
	/* End Public */
	
	/*
	 * Private
	 */
	private float m_sum;
	private LinkedList<Float> m_bitrate_queue;
	private LinkedList<AudioSession> m_active_audio_sessions;
	
	/**
	 * Description: Gets an AudioSession by id and if it doesn't exist, it 
	 *  creates one.
	 * @param id -The identifying audioSessionId given by the MediaPlayer.
	 * @return An AudioSession with the specified id.
	 */
	private AudioSession getAudioSession(int id){
		//First stage, see if exists and return it.
		for (int i = 0; i < m_active_audio_sessions.size(); ++i){
			if (m_active_audio_sessions.get(i).getId() == id){
				return m_active_audio_sessions.get(i);
			}
		}
		
		//Second stage, it doesn't exist so make one!
		AudioSession new_session = new AudioSession(id);
		m_active_audio_sessions.add(new_session);
		return new_session;
	}
	
	/**
	 * Description: A new top value for the queue is being started, so reset
	 * 	the flags because nothing has been queued now.
	 */
	private void resetQueueFlags(){
		for (int i = 0; i < m_active_audio_sessions.size(); ++i){
			AudioSession tmp = m_active_audio_sessions.get(i);
			tmp.queued_flag = false;
			m_active_audio_sessions.set(i, tmp);
		}
	}	
	
	/* End Private */
}
