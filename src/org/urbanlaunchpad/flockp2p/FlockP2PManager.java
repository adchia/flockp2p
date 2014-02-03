package org.urbanlaunchpad.flockp2p;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;

public class FlockP2PManager {
	public static WiFiDirectHelper p2pNetworkHelper;

	// Booleans representing modes of p2p
	private boolean CHECK_ALREADY_UPLOADED = false;
	private boolean SIMPLE_MODE = true;

	// Keeping track of our message types and priorities
	private HashMap<String, Integer> messageTypeToPriorityMap;
	private HashMap<String, String> peerGroupNameToAESKeyMap;
	private ArrayList<String> messagePriorityList;
	private HashMap<String, PeerGroup> peerGroupMap;

	// Keys into JSON Object
	public static final String FLOCK_MESSAGE_TYPE = "flockMessageType";
	public static final String MESSAGE_TYPE = "messageType";
	public static final String TIMESTAMP = "timeStamp";
	public static final String REQUEST = "request";

	public static enum FlockMessageType {
		FLOOD, INCREMENTAL, DROP
	}

	public FlockP2PManager(Activity activity) {
		p2pNetworkHelper = new WiFiDirectHelper(activity,
				activity.getMainLooper(),
				(WifiP2pManager) activity
						.getSystemService(Context.WIFI_P2P_SERVICE));
		messageTypeToPriorityMap = new HashMap<String, Integer>();
		messagePriorityList = new ArrayList<String>();
		peerGroupNameToAESKeyMap = new HashMap<String, String>();
		peerGroupMap = new HashMap<String, PeerGroup>();
		
		// turn on wifi direct
		WifiManager wifiManager = (WifiManager)activity.getSystemService(Context.WIFI_SERVICE);
		wifiManager.setWifiEnabled(true);
	}

	public boolean enqueueMessage(FlockMessageType flockMessageType,
			String messageType, String request, String peerGroupName) {

		// Serialize into JSON Object and add to right linked list
		try {
			JSONObject messageObject = new JSONObject();
			Date timestamp = new Date();

			messageObject.put(FLOCK_MESSAGE_TYPE, flockMessageType);
			messageObject.put(MESSAGE_TYPE, messageType);
			messageObject.put(REQUEST, request);
			messageObject.put(TIMESTAMP, timestamp);
			peerGroupMap.get(peerGroupName).addMessageType(messageType);
			peerGroupMap.get(peerGroupName).enqueueMessageOfType(messageType, messageObject);
		} catch (JSONException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public void addPeerGroup(String peerGroupName, String key,
			Collection<String> deviceAddresses) {
		peerGroupNameToAESKeyMap.put(peerGroupName, key);
		peerGroupMap.put(peerGroupName, new PeerGroup(key, deviceAddresses));
		for (String messageType : messagePriorityList) {
			peerGroupMap.get(peerGroupName).addMessageType(messageType);
		}
	}

	public void removePeerGroup(String peerGroupName) {
		peerGroupNameToAESKeyMap.put(peerGroupName, null);
		peerGroupMap.put(peerGroupName, null);
		for (String messageType : messagePriorityList) {
			peerGroupMap.get(peerGroupName).removeMessageType(messageType);
		}
	}

	/*
	 * Message API Functions
	 */

	public void addMessageType(String messageType) {
		addMessageType(messageType, 1);
	}

	public void addMessageType(String messageType, Integer priority) {
		messageTypeToPriorityMap.put(messageType, priority);

		// Check if we already have the key.
		if (!messageTypeToPriorityMap.containsKey(messageType)) {
			messagePriorityList.add(messageType);
			for (PeerGroup group : peerGroupMap.values()) {
				group.addMessageType(messageType);
			}
		}
	}

	public void removeMessageType(String messageType) {
		messageTypeToPriorityMap.remove(messageType);

		// Check if we have the key.
		if (messageTypeToPriorityMap.containsKey(messageType)) {
			messagePriorityList.remove(messageType);
			for (PeerGroup group : peerGroupMap.values()) {
				group.removeMessageType(messageType);
			}
		}
	}

}
