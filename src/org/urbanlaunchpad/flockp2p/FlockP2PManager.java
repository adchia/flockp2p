package org.urbanlaunchpad.flockp2p;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;

public class FlockP2PManager {
	public static WiFiDirectHelper p2pNetworkHelper;
	public boolean prevConnectedToWiFi = false;
	public boolean connectedToWiFi = false;
	public Activity activity;
	private static int FLOOD_PERIOD = 30000;

	// Booleans representing modes of p2p
	private boolean CHECK_ALREADY_UPLOADED = false;
	private boolean SIMPLE_MODE = true;

	// Keeping track of our message types and priorities
	private HashMap<String, Integer> messageTypeToPriorityMap;
	private ArrayList<String> messagePriorityList;
	public static HashMap<String, PeerGroup> peerGroupMap;

	// Keys into JSON Object
	public static final String FLOCK_MESSAGE_TYPE = "flockMessageType";
	public static final String MESSAGE_TYPE = "messageType";
	public static final String TIMESTAMP = "timeStamp";
	public static final String REQUEST = "request";
	public static final String PEER_GROUP_ID = "peerGroupId";
	public static final String MESSAGE_JSON = "messageJSON";

	public static enum FlockMessageType {
		FLOOD, INCREMENTAL, DROP
	}

	public FlockP2PManager(Activity activity) {
		this.activity = activity;
		p2pNetworkHelper = new WiFiDirectHelper(activity,
				activity.getMainLooper(),
				(WifiP2pManager) activity
						.getSystemService(Context.WIFI_P2P_SERVICE));
		messageTypeToPriorityMap = new HashMap<String, Integer>();
		messagePriorityList = new ArrayList<String>();
		peerGroupMap = new HashMap<String, PeerGroup>();

		// turn on wifi direct
		WifiManager wifiManager = (WifiManager) activity
				.getSystemService(Context.WIFI_SERVICE);
		wifiManager.setWifiEnabled(true);

		// turn on periodic flooding
		new Thread(new Runnable() {
			@Override
			public void run() {
				flood();
				try {
					Thread.sleep(FLOOD_PERIOD);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}).start();

		// turn on sharing database info
		new Thread(new Runnable() {
			@Override
			public void run() {
				flood();
				try {
					for (PeerGroup group : peerGroupMap.values()) {
						for (String messageType : group.messageTypeToQueueMap.keySet()) {
							if (group.hasMessageOfType(messageType)) {
								group.sendMessagesOfType(messageType, 1);
							}
						}
					}
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}).start();
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
			peerGroupMap.get(peerGroupName).enqueueMessageOfType(messageType,
					messageObject);
		} catch (JSONException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public void addPeerGroup(String peerGroupName, String key,
			Collection<String> deviceAddresses) {
		peerGroupMap.put(peerGroupName, new PeerGroup(key, peerGroupName,
				deviceAddresses));
		for (String messageType : messagePriorityList) {
			peerGroupMap.get(peerGroupName).addMessageType(messageType);
		}
	}

	public void removePeerGroup(String peerGroupName) {
		peerGroupMap.put(peerGroupName, null);
		for (String messageType : messagePriorityList) {
			peerGroupMap.get(peerGroupName).removeMessageType(messageType);
		}
	}

	// TODO: call this flood message every 5 minutes
	/**
	 * Checks Wi-Fi connectivity and if connected to network, flood out to let
	 * others know
	 */
	private void flood() {
		ConnectivityManager cm = (ConnectivityManager) activity
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

		if (wifi.isConnected()) {
			prevConnectedToWiFi = connectedToWiFi;
			connectedToWiFi = true;

			// Get device MAC address
			WifiManager wifiManager = (WifiManager) activity
					.getSystemService(Context.WIFI_SERVICE);
			WifiInfo wInfo = wifiManager.getConnectionInfo();
			String macAddress = wInfo.getMacAddress();

			// Create flood message
			JSONObject message = new JSONObject();
			try {
				JSONObject floodMsg = new JSONObject();
				Date timestamp = new Date();
				floodMsg.put(FLOCK_MESSAGE_TYPE,
						FlockMessageType.FLOOD.toString());
				floodMsg.put(REQUEST, 0);
				floodMsg.put(TIMESTAMP, timestamp);
				message.put(MESSAGE_JSON, floodMsg);
			} catch (JSONException e) {
				e.printStackTrace();
			}

			// Send message to everyone, including self
			for (PeerGroup group : peerGroupMap.values()) {
				group.receiveFlood(macAddress, 0);
				try {
					message.put(PEER_GROUP_ID, group.name);
					p2pNetworkHelper.sendMessage(message, group);
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		} else {
			if (prevConnectedToWiFi) {
				// Get device MAC address
				WifiManager wifiManager = (WifiManager) activity
						.getSystemService(Context.WIFI_SERVICE);
				WifiInfo wInfo = wifiManager.getConnectionInfo();
				String macAddress = wInfo.getMacAddress();

				// Create flood message
				JSONObject message = new JSONObject();
				try {
					JSONObject floodMsg = new JSONObject();
					Date timestamp = new Date();
					floodMsg.put(FLOCK_MESSAGE_TYPE,
							FlockMessageType.FLOOD.toString());
					floodMsg.put(TIMESTAMP, timestamp);
					message.put(MESSAGE_JSON, floodMsg);

					// Flood next best to peer groups
					for (PeerGroup group : peerGroupMap.values()) {
						floodMsg.put(REQUEST, group.bestPlacesToSend.peekLast());

						group.receiveFlood(macAddress, 0);
						try {
							message.put(PEER_GROUP_ID, group.name);
							p2pNetworkHelper.sendMessage(message, group);
						} catch (JSONException e) {
							e.printStackTrace();
						}
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}

			prevConnectedToWiFi = connectedToWiFi;
			connectedToWiFi = false;
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
