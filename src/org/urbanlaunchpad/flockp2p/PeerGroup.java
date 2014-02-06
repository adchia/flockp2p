package org.urbanlaunchpad.flockp2p;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import org.json.JSONObject;

import com.google.common.collect.MinMaxPriorityQueue;

public class PeerGroup {
	public String key;
	private WiFiDirectHelper p2pNetworkHelper;
	public HashMap<String, LinkedList<JSONObject>> messageTypeToQueueMap;
	public HashMap<String, Integer> messageTypeToPriorityCount;
	public HashSet<String> deviceAddresses;
	public String name;
	public MinMaxPriorityQueue<AddressToHopCount> bestPlacesToSend;
	public static final int MAX_NUM_ADDRESS_HOP_COUNTS = 10;
	public int numMessages = 0;

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

	public PeerGroup(String key, String name, Collection<String> deviceAddresses, WiFiDirectHelper p2pNetworkHelper) {
		this.key = key;
		this.name = name;
		this.messageTypeToQueueMap = new HashMap<String, LinkedList<JSONObject>>();
		this.messageTypeToPriorityCount = new HashMap<String, Integer>();
		this.deviceAddresses = new HashSet<String>(deviceAddresses);
		this.bestPlacesToSend = MinMaxPriorityQueue.create();
		this.p2pNetworkHelper = p2pNetworkHelper;
	}

	/**
	 * Updates our best places to send
	 * @param deviceAddress
	 * @param hopCount
	 * @return true if we should continue flooding
	 */
	public boolean receiveFlood(String deviceAddress, int hopCount) {
		bestPlacesToSend.add(new AddressToHopCount(deviceAddress, hopCount));
		if (bestPlacesToSend.size() > MAX_NUM_ADDRESS_HOP_COUNTS) {
			AddressToHopCount removed = bestPlacesToSend.removeLast();
			
			// Same as the initial
			if (removed.address.equals(deviceAddress) && removed.hopCount == hopCount) {
				return false;
			}
		}
		
		return true;
	}

	private void resetPriorityCounts() {
		for (String messageType : messageTypeToPriorityCount.keySet()) {
			messageTypeToPriorityCount.put(messageType,
					FlockP2PManager.messageTypeToPriorityMap.get(messageType));
		}
	}

	public void addMessageType(String messageType, Integer priority) {
		// Check if we already have the key. Else initialize queue of messages
		if (!messageTypeToQueueMap.containsKey(messageType)) {
			messageTypeToQueueMap
					.put(messageType, new LinkedList<JSONObject>());
			messageTypeToPriorityCount.put(messageType, priority);
		}
	}

	public void enqueueMessageOfType(String messageType, JSONObject message) {
		// Check if we already have the key. Else initialize queue of messages
		if (messageTypeToQueueMap.containsKey(messageType)) {
			messageTypeToQueueMap.get(messageType).push(message);
			numMessages++;
		}
	}

	public void removeMessageType(String messageType) {
		numMessages -= messageTypeToQueueMap.get(messageType).size();
		messageTypeToQueueMap.remove(messageType);
		messageTypeToPriorityCount.remove(messageType);
	}

	public boolean hasMessageOfType(String messageType) {
		return messageTypeToQueueMap.get(messageType).size() > 0;
	}

	public boolean hasMessages() {
		return numMessages > 0;
	}

	/**
	 * Uploads a single message
	 * Returns boolean telling whether we sent a message or not
	 */
	public boolean uploadMessage() {
		for (String messageType : FlockP2PManager.messagePriorityList) {
			// Check if this has priority
			int count = messageTypeToPriorityCount.get(messageType);
			if (hasMessageOfType(messageType) && count > 0) {
				messageTypeToPriorityCount.put(messageType, count - 1);
				numMessages--;
				uploadMessagesOfType(messageType);
				return true;
			}
		}
		
		// No messages to send
		resetPriorityCounts();
		return false;
	}
	
	/**
	 * Send a single message
	 * Returns boolean telling whether we sent a message or not
	 */
	public boolean sendMessage() {
		for (String messageType : FlockP2PManager.messagePriorityList) {
			// Check if this has priority
			int count = messageTypeToPriorityCount.get(messageType);
			if (hasMessageOfType(messageType) && count > 0) {
				messageTypeToPriorityCount.put(messageType, count - 1);
				numMessages--;
				sendMessagesOfType(messageType);
				return true;
			}
		}
		
		// No messages to send
		resetPriorityCounts();
		return false;
	}

	private void uploadMessagesOfType(String messageType) {
		JSONObject message = messageTypeToQueueMap.get(messageType)
				.removeLast();
		p2pNetworkHelper.uploadMessage(message, this);
	}
	
	private void sendMessagesOfType(String messageType) {
		JSONObject message = messageTypeToQueueMap.get(messageType)
				.removeLast();
		p2pNetworkHelper.sendMessage(message, this);
	}

}
