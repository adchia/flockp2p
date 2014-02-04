package org.urbanlaunchpad.flockp2p;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.PriorityQueue;

import org.json.JSONObject;

public class PeerGroup {
	private String key;
	private HashMap<String, LinkedList<JSONObject>> messageTypeToQueueMap;
	public HashSet<String> deviceAddresses;
	public String name;
	public PriorityQueue<AddressToHopCount> bestPlacesToSend;
	public static final int MAX_NUM_ADDRESS_HOP_COUNTS = 10;
	
	public class AddressToHopCount implements Comparator<AddressToHopCount> {
		public final String address;
		public final int hopCount;
		
		public AddressToHopCount(String address, int hopCount) {
			this.address = address;
			this.hopCount = hopCount;
		}
			
		@Override
		public int compare(AddressToHopCount lhs, AddressToHopCount rhs) {
			if (rhs.hopCount < lhs.hopCount) return 1;
	        if (lhs.hopCount == rhs.hopCount) return 0;
	        return -1;
		}
		
	}

	public PeerGroup(String key, String name, Collection<String> deviceAddresses) {
		this.key = key;
		this.name = name;
		messageTypeToQueueMap = new HashMap<String, LinkedList<JSONObject>>();
		this.deviceAddresses = new HashSet<String>();
		this.bestPlacesToSend = new PriorityQueue<AddressToHopCount>();
	}
	
	public void receiveFlood(String deviceAddress, int hopCount) {
		bestPlacesToSend.add(new AddressToHopCount(deviceAddress, hopCount));
		if (bestPlacesToSend.size() > MAX_NUM_ADDRESS_HOP_COUNTS) {
			bestPlacesToSend.remove();
		}
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
