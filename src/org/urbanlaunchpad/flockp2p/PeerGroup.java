package org.urbanlaunchpad.flockp2p;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import org.json.JSONObject;

public class PeerGroup {
	private String key;
	private HashMap<String, LinkedList<JSONObject>> messageTypeToQueueMap;
	public HashSet<String> deviceAddresses;

	public PeerGroup(String key, Collection<String> deviceAddresses) {
		this.key = key;
		messageTypeToQueueMap = new HashMap<String, LinkedList<JSONObject>>();
		this.deviceAddresses = new HashSet<String>();
	}

	public void addMessageType(String messageType) {
		// Check if we already have the key. Else initialize queue of messages
		if (!messageTypeToQueueMap.containsKey(messageType)) {
			messageTypeToQueueMap
					.put(messageType, new LinkedList<JSONObject>());
		}
	}

	public void enqueueMessageOfType(String messageType, JSONObject message) {
		// Check if we already have the key. Else initialize queue of messages
		if (messageTypeToQueueMap.containsKey(messageType)) {
			messageTypeToQueueMap.get(messageType).push(message);
		}
	}

	public void removeMessageType(String messageType) {
		messageTypeToQueueMap.remove(messageType);
	}

	public void sendMessagesOfType(String messageType, int count) {
		for (int i = 0; i < count; i++) {
			JSONObject message = messageTypeToQueueMap.get(messageType)
					.removeLast();
			FlockP2PManager.p2pNetworkHelper.sendMessage(message, this);
		}
	}

}
