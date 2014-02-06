package org.urbanlaunchpad.flockp2p;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;
import org.urbanlaunchpad.flockp2p.FlockP2PManager.FlockMessageType;
import org.urbanlaunchpad.flockp2p.PeerGroup.AddressToHopCount;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
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
import android.os.Build;
import android.os.Looper;
import android.util.Log;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class WiFiDirectHelper extends BroadcastReceiver implements
		ConnectionInfoListener, ChannelListener {
	private static WifiP2pManager p2pManager;
	private static Channel p2pChannel;
	private final static int CLIENT_PORT = 8988;
	private static FlockP2PManager flockManager;

	// The following lists are aligned.
	private LinkedList<PeerGroup> peerGroupQueue;
	private LinkedList<JSONObject> messageQueue;

	// Temporary information for each connection
	private static JSONObject currentMessage;
	private static InetAddress groupOwnerAddress;

	// Locks
	private static String mutex = "mutex";
	private static boolean alreadyConnected = false;
	private static boolean isFlooding = false;
	private static String otherDeviceAddress;
	private static Random random = new Random();

	// 3) Listener that is fired when we request and get a peer list
	private PeerListListener peerListListener = new PeerListListener() {
		@Override
		public void onPeersAvailable(WifiP2pDeviceList peerList) {
			Log.d("got peer list!", "yay");
			Log.d("peerList: ", peerList.getDeviceList().toString());
			if (peerList.getDeviceList().isEmpty())
				return;
			
			while (!peerGroupQueue.isEmpty()) {
				if (!alreadyConnected) {

					PeerGroup group = peerGroupQueue.peekLast();
					currentMessage = messageQueue.peekLast();

					// send out message to one of top ten devices in peer
					// group inversely weighted by hopCounts
					ArrayList<String> listOfAddresses = new ArrayList<String>();
					for (AddressToHopCount addressToHopCount : group.bestPlacesToSend) {
						if (peerList.get(addressToHopCount.address) != null) {
							for (int i = 0; i < (group.deviceAddresses.size() - addressToHopCount.hopCount); i++) {
								listOfAddresses.add(addressToHopCount.address);
							}
						}
					}

					String tmpDeviceAddress = null;
					if (listOfAddresses.size() == 0) { // pick any address
														// in peer group
														// then
						if (!WiFiDirectHelper.isFlooding) // if not
															// flooding, no
															// good places
															// to send
							return;

						for (String address : group.deviceAddresses) {
							if (peerList.get(address) != null) {
								tmpDeviceAddress = address;
								break;
							}
						}

						if (tmpDeviceAddress == null)
							return;
					} else {
						int randInt = random.nextInt(listOfAddresses.size());
						tmpDeviceAddress = listOfAddresses.get(randInt);
					}

					final String deviceAddress = tmpDeviceAddress;

					WifiP2pConfig config = new WifiP2pConfig();
					config.deviceAddress = deviceAddress;
					config.wps.setup = WpsInfo.PBC;

					Log.d("connecting to device!", "yay");
					// Connects to device.
					alreadyConnected = true;
					p2pManager.connect(p2pChannel, config,
							new ActionListener() {

								@Override
								public void onSuccess() {
									otherDeviceAddress = deviceAddress;
									peerGroupQueue.removeLast();
									currentMessage = messageQueue.removeLast();
								}

								@Override
								public void onFailure(int reason) {
								}
							});

				}
			}

		}
	};

	public WiFiDirectHelper(FlockP2PManager flockP2PManager, Context context,
			Looper looper, WifiP2pManager manager) {
		flockManager = flockP2PManager;
		WiFiDirectHelper.p2pManager = manager;
		Log.d("initializing", "initializing");
		WiFiDirectHelper.p2pChannel = WiFiDirectHelper.p2pManager.initialize(
				context, looper, this);
		this.peerGroupQueue = new LinkedList<PeerGroup>();
		this.messageQueue = new LinkedList<JSONObject>();
	}

	// 1) Have message to send. Request peer list and save message/group
	public void sendMessage(JSONObject message, PeerGroup group) {
		Log.d("SEND_MESSAGE", "sendMessage: " + message.toString() + ", "
				+ group.name);

		// construct message wrapper
		try {
			JSONObject networkMessage = new JSONObject();
			networkMessage.put(FlockP2PManager.MESSAGE_JSON, SecurityHelper
					.encryptMessage(message.toString(), group.key));
			networkMessage.put(FlockP2PManager.PEER_GROUP_ID, group.name);

			JSONObject actualMessage = new JSONObject(
					message.getString(FlockP2PManager.MESSAGE_JSON));
			WiFiDirectHelper.isFlooding = actualMessage.getString(
					FlockP2PManager.FLOCK_MESSAGE_TYPE).equals(
					FlockMessageType.FLOOD.toString());
			this.peerGroupQueue.push(group);
			this.messageQueue.push(networkMessage);

			if (!alreadyConnected) {
				// Searches for peers. Keeps going until connected or P2P group
				// made
				p2pManager.discoverPeers(p2pChannel, new ActionListener() {

					@Override
					public void onSuccess() {
						Log.d("FOUND PEERS", "yay");
					}

					@Override
					public void onFailure(int reasonCode) {
						// Code for when the discovery initiation fails goes
						// here.
						// Alert the user that something went wrong.
						Log.d("FOUND PEERS", "nope :( " + reasonCode);
					}
				});
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}

	}

	public void uploadMessage(JSONObject message, PeerGroup group) {
		Log.d("UPLOAD_MESSAGE", "sendMessage: " + message.toString() + ", "
				+ group.name);

		// Submit POST request
		try {
			JSONObject actualMessage = new JSONObject(
					message.getString(FlockP2PManager.MESSAGE_JSON));

			JSONObject requestParams = new JSONObject(
					actualMessage.getString(FlockP2PManager.REQUEST));
			HttpClient httpclient = new DefaultHttpClient();
			HttpPost httppost = new HttpPost(
					requestParams.getString(FlockP2PManager.REQUEST_URL));

			// Request parameters and other properties.
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			for (int i = 0; i < requestParams.getJSONArray(
					FlockP2PManager.REQUEST_PARAMS).length(); i++) {
				JSONObject param = requestParams.getJSONArray(
						FlockP2PManager.REQUEST_PARAMS).getJSONObject(i);
				params.add(new BasicNameValuePair(param
						.getString(FlockP2PManager.REQUEST_KEY), param
						.getString(FlockP2PManager.REQUEST_VALUE)));
			}
			httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

			// Execute
			httpclient.execute(httppost);
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		// Monitor for Wifi connection status
		if (action.equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)) {
			flockManager.prevConnectedToWiFi = flockManager.connectedToWiFi;
			if (intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED,
					false)) {
				flockManager.connectedToWiFi = true;
			} else {
				flockManager.connectedToWiFi = false;
			}
		} else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
			// 2) Discovered peers. Need to actually request
			Log.d("requesting peers", "yay");

			p2pManager.requestPeers(p2pChannel, peerListListener);
		} else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
				.equals(action)) {
			/**
			 * 4) We requested to connect to someone and here we are! Get
			 * connection information
			 */

			Log.d("time to get connection info", "yay");

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
				Log.d("RECEIVING MESSAGE", "receiving messages");

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
				Log.d("RECEIVED MESSAGE", "received message:"
						+ responseStrBuilder.toString());

				JSONObject incomingMessage = new JSONObject(
						responseStrBuilder.toString());

				parseIncomingMessage(incomingMessage);

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
	 * Helper method that parses incoming message and adds to corresponding
	 * queue
	 * 
	 * @param message
	 */
	private static void parseIncomingMessage(JSONObject message) {
		try {
			String groupName = message.getString(FlockP2PManager.PEER_GROUP_ID);
			// check if in our peer groups
			if (FlockP2PManager.peerGroupMap.containsKey(groupName)) {
				// attempt decryption
				PeerGroup group = FlockP2PManager.peerGroupMap.get(groupName);
				JSONObject actualMessage = new JSONObject(
						SecurityHelper.decryptMessage(
								message.getString(FlockP2PManager.MESSAGE_JSON),
								group.key));
				String flockMsgType = actualMessage
						.getString(FlockP2PManager.FLOCK_MESSAGE_TYPE);
				int hopCount = actualMessage.getInt(FlockP2PManager.REQUEST);
				if (flockMsgType.equals(FlockMessageType.FLOOD.toString())) {
					// Should continue sending
					if (group.receiveFlood(otherDeviceAddress, hopCount)) {
						flockManager.floodForward();
					}
				} else if (flockMsgType.equals(FlockMessageType.INCREMENTAL)) {
					// add to message queue
					group.enqueueMessageOfType(actualMessage
							.getString(FlockP2PManager.MESSAGE_TYPE),
							actualMessage);
				}
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Client-side method that sends message to server
	 * 
	 * @param message
	 */
	public static void sendMessageThroughSocket(JSONObject message) {
		Socket socket = new Socket();
		Log.d("SENDING MESSAGE", "trying to send message");

		try {
			/**
			 * Create a client socket with the host, port, and timeout
			 * information.
			 */
			socket.bind(null);
			socket.connect((new InetSocketAddress(groupOwnerAddress,
					CLIENT_PORT)), 500);

			/**
			 * Create a byte stream from message and pipe it to the output
			 * stream of the socket. This data will be retrieved by the server
			 * device.
			 */
			OutputStream outputStream = socket.getOutputStream();
			outputStream.write(message.toString().getBytes());
			Log.d("SENDING MESSAGE", "sent message: " + message.toString());
			p2pManager.removeGroup(p2pChannel, new ActionListener() {

				@Override
				public void onSuccess() {
					synchronized (mutex) {
						alreadyConnected = false;
					}
				}

				@Override
				public void onFailure(int reason) {
				}
			});
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
	 * 
	 * @param info
	 *            WiFi connection information
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
