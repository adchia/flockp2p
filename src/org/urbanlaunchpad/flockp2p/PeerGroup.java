package org.urbanlaunchpad.flockp2p;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import org.json.JSONObject;

import com.google.common.collect.MinMaxPriorityQueue;

public class PeerGroup {
	public String key;
	public HashMap<String, LinkedList<JSONObject>> messageTypeToQueueMap;
	public HashSet<String> deviceAddresses;
	public String name;
	public MinMaxPriorityQueue<AddressToHopCount> bestPlacesToSend;
	public static final int MAX_NUM_ADDRESS_HOP_COUNTS = 10;
	
	public class AddressToHopCount implements Comparable<AddressToHopCount> {
		public final String address;
		public final int hopCount;
		
		public AddressToHopCount(String address, int hopCount) {
			this.address = address;
			this.hopCount = hopCount;
		}

		@Override
		public int compareTo(AddressToHopCount arg0) {
			if (hopCount < arg0.hopCount)
				return -1;
			if (hopCount == arg0.hopCount)
				return 0;
			return 1;
		}
		
	}

	public PeerGroup(String key, String name, Collection<String> deviceAddresses) {
		this.key = key;
		this.name = name;
		messageTypeToQueueMap = new HashMap<String, LinkedList<JSONObject>>();
		this.deviceAddresses = new HashSet<String>();
		this.bestPlacesToSend = MinMaxPriorityQueue.create();
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

	public boolean hasMessageOfType(String messageType) {
		return messageTypeToQueueMap.get(messageType).peekLast() != null;
	}
	
	public void sendMessagesOfType(String messageType, int count) {
		for (int i = 0; i < count; i++) {
			JSONObject message = messageTypeToQueueMap.get(messageType)
					.removeLast();
			FlockP2PManager.p2pNetworkHelper.sendMessage(message, this);
		}
	}

}
