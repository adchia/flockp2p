package org.urbanlaunchpad.flockp2p;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.AsyncTask;
import android.os.Looper;

public class WiFiDirectHelper extends BroadcastReceiver implements
		ChannelListener, ConnectionInfoListener {
	private WifiP2pManager p2pManager;
	private Channel p2pChannel;
	private final static int CLIENT_PORT = 8988;

	// The following lists are aligned.
	private LinkedList<PeerGroup> peerGroupQueue;
	private LinkedList<JSONObject> messageQueue;

	// Temporary information for each connection
	private static JSONObject currentMessage;
	private static InetAddress groupOwnerAddress;

	// Locks
	private String mutex = "mutex";

	// 3) Listener that is fired when we request and get a peer list
	private PeerListListener peerListListener = new PeerListListener() {
		@Override
		public void onPeersAvailable(WifiP2pDeviceList peerList) {
			while (!peerGroupQueue.isEmpty()) {
				PeerGroup group = peerGroupQueue.removeLast();
				currentMessage = messageQueue.removeLast();

				// send out message to every device in peer group
				for (String deviceAddress : group.deviceAddresses) {
					WifiP2pConfig config = new WifiP2pConfig();
					config.deviceAddress = deviceAddress;
					config.wps.setup = WpsInfo.PBC;

					synchronized (mutex) {
						// Need to create group

						// Connects to device.
						p2pManager.connect(p2pChannel, config,
								new ActionListener() {

									@Override
									public void onSuccess() {
										// WiFiDirectBroadcastReceiver will
										// notify
										// us. Ignore for now.
									}

									@Override
									public void onFailure(int reason) {
									}
								});
					}
				}
				// Can have JSONObject{encrypted message, peer group id}

			}
		}
	};

	public WiFiDirectHelper(Context context, Looper looper,
			WifiP2pManager p2pManager) {
		this.p2pManager = p2pManager;
		this.p2pChannel = p2pManager.initialize(context, looper, this);
	}

	// 1) Have message to send. Request peer list and save message/group
	public void sendMessage(JSONObject message, PeerGroup group) {
		this.peerGroupQueue.push(group);
		this.messageQueue.push(message);

		// Searches for peers. Keeps going until connected or P2P group made
		p2pManager.discoverPeers(p2pChannel,
				new WifiP2pManager.ActionListener() {

					@Override
					public void onSuccess() {

					}

					@Override
					public void onFailure(int reasonCode) {
						// Code for when the discovery initiation fails goes
						// here.
						// Alert the user that something went wrong.
					}
				});
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
			int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
			if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
				// TODO: notify application WiFi P2P is good to go
			} else {
				// TODO: notify application we need WiFi P2P
			}
		} else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
			// 2) Discovered peers. Need to actually request
			p2pManager.requestPeers(p2pChannel, peerListListener);
		} else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
				.equals(action)) {
			/**
			 *  4) We requested to connect to someone and here we are!
			 *  Get connection information 
			 */

			NetworkInfo networkInfo = (NetworkInfo) intent
					.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

			if (networkInfo.isConnected()) {
				// we are connected with the other device, request connection
				// info to find group owner IP
				p2pManager.requestConnectionInfo(p2pChannel, this);
			} else {
				// TODO: Notify application we've disconnected from other device
			}

		}
	}

	@Override
	public void onChannelDisconnected() {
		// TODO Deal with disconnected WiFi Direct
	}

	/*
	 * Methods related to socket connections
	 */

	// AsyncTask that accepts socket connections waiting for messages
	public static class ServerSocketTask extends
			AsyncTask<String, Void, JSONObject> {

		@Override
		protected JSONObject doInBackground(String... params) {
			try {

				/**
				 * Create a server socket and wait for client connections. This
				 * call blocks until a connection is accepted from a client
				 */
				ServerSocket serverSocket = new ServerSocket(8888);
				Socket client = serverSocket.accept();

				/**
				 * If this code is reached, a client has connected and
				 * transferred data Save the input stream from the client as a
				 * JSONObject
				 */

				InputStream inputStream = client.getInputStream();
				BufferedReader streamReader = new BufferedReader(
						new InputStreamReader(inputStream, "UTF-8"));
				StringBuilder responseStrBuilder = new StringBuilder();

				String inputStr;
				while ((inputStr = streamReader.readLine()) != null)
					responseStrBuilder.append(inputStr);
				JSONObject incomingMessage = new JSONObject(
						responseStrBuilder.toString());
				serverSocket.close();
				return incomingMessage;
			} catch (IOException e) {
				return null;
			} catch (JSONException e) {
				e.printStackTrace();
				return null;
			}
		}
	}

	/**
	 * Client-side method that sends message to server
	 * @param message
	 */
	public static void sendMessageThroughSocket(JSONObject message) {
		Socket socket = new Socket();

		try {
			/**
			 * Create a client socket with the host, port, and timeout
			 * information.
			 */
			socket.bind(null);
			socket.connect((new InetSocketAddress(groupOwnerAddress, CLIENT_PORT)), 500);

			/**
			 * Create a byte stream from message and pipe it to the output
			 * stream of the socket. This data will be retrieved by the server
			 * device.
			 */
			OutputStream outputStream = socket.getOutputStream();
			outputStream.write(message.toString().getBytes());
			outputStream.close();
		} catch (FileNotFoundException e) {
			// catch logic
		} catch (IOException e) {
			// catch logic
		}

		/**
		 * Clean up any open sockets when done transferring or if an exception
		 * occurred.
		 */
		finally {
			if (socket != null) {
				if (socket.isConnected()) {
					try {
						socket.close();
					} catch (IOException e) {
						// catch logic
					}
				}
			}
		}
	}

	/**
	 * 5) Owner IP is known. Launch server or client threads
	 * @param info WiFi connection information
	 */
	@Override
	public void onConnectionInfoAvailable(WifiP2pInfo info) {		
		groupOwnerAddress = info.groupOwnerAddress;
        
		// This device is server
		if (info.groupFormed && info.isGroupOwner) {
			new ServerSocketTask().execute();
		} else if (info.groupFormed) {
			// This device is client
			new Thread(new Runnable() {
				@Override
				public void run() {
					sendMessageThroughSocket(currentMessage);
				}
			}).start();
		}
	}

}
