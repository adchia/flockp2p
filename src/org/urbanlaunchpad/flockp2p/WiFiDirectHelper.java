package org.urbanlaunchpad.flockp2p;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
	private final int CLIENT_PORT = 8988;

	// The following lists are aligned.
	private LinkedList<PeerGroup> peerGroupQueue;
	private LinkedList<JSONObject> messageQueue;

	// Temporary information for each connection
	private static JSONObject currentMessage;
	private static String serverIP;

	// Listener that is fired when we request and get a peer list
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

					// Connects to device.
					p2pManager.connect(p2pChannel, config,
							new ActionListener() {

								@Override
								public void onSuccess() {
									// WiFiDirectBroadcastReceiver will notify
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
	};

	public WiFiDirectHelper(Context context, Looper looper,
			WifiP2pManager p2pManager) {
		this.p2pManager = p2pManager;
		this.p2pChannel = p2pManager.initialize(context, looper, this);
	}

	// Checks for internet connectivity and floods out to neighbors
	public void flood() {

	}

	public void sendMessage(JSONObject message, PeerGroup group) {
		this.peerGroupQueue.push(group);
		this.messageQueue.push(message);

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
			// Determine if Wifi P2P mode is enabled or not, alert
			// the Activity.
			int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
			if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
				// activity.setIsWifiP2pEnabled(true);
			} else {
				// activity.setIsWifiP2pEnabled(false);
			}
		} else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {

			// The peer list has changed! We should probably do something about
			// that.

			// Request available peers from the wifi p2p manager. This is an
			// asynchronous call and the calling activity is notified with a
			// callback on PeerListListener.onPeersAvailable()
			if (p2pManager != null) {
				p2pManager.requestPeers(p2pChannel, peerListListener);
			}
		} else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
				.equals(action)) {
			NetworkInfo networkInfo = (NetworkInfo) intent
					.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

			if (networkInfo.isConnected()) {

				// we are connected with the other device, request connection
				// info to find group owner IP
				p2pManager.requestConnectionInfo(p2pChannel, this);
			} else {
				// It's a disconnect
			}

		}
	}

	// TODO: set up server and client sockets to send and receive data
	// http://developer.android.com/guide/topics/connectivity/wifip2p.html

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

	// AsyncTask that sends messages to appropriate device address
	public static class ClientSocketTask extends
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

	public static void sendMessageThroughSocket(JSONObject message,
			String deviceAddress) {
		int port = 8988;
		Socket socket = new Socket();

		try {
			/**
			 * Create a client socket with the host, port, and timeout
			 * information.
			 */
			socket.bind(null);
			socket.connect((new InetSocketAddress(serverIP, port)), 500);

			/**
			 * Create a byte stream from a JPEG file and pipe it to the output
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

	@Override
	public void onConnectionInfoAvailable(WifiP2pInfo info) {
		// The owner IP is now known:

		// After the group negotiation, we assign the group owner as the file
		// server. The file server is single threaded, single connection server
		// socket.
		if (info.groupFormed && info.isGroupOwner) {
			new ServerSocketTask().execute();
		} else if (info.groupFormed) {
			// The other device acts as the client. In this case, we enable the
			// get file button.
		}
	}

}
