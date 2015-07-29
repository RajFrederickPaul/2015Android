package com.example.monopolymoney;

/******************************************
 * Copyright 2015 Raj Frederick Paul

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

/*
 * Author: Raj Frederick Paul
 * 
 * Main Activity:
 *  
 * This Activity is the first screen the user sees. The User can set his player name which sets the bluetooth device name which is made discoverable to other devices
 * 
 *Server: The user can choose to host a game i.e Be Banker i.e Be the server. On clicking so, they become the server and the app ServerSockets begin accepting, one for each of the players selected by server
 *Client: If the user chooses, join a game, they opt to be a client, and proceed to query all the discovered devices with its bluetoothSocket attempting to connect, if successful, it tells the user the servers it found for him or her to confirm.
 * 
 *Server: If the server's sockets for all of the devices it was attempting to connect to, is not null, it intitiates a connection to each of the players. 
 *Client: The clients do the same to confirmed server. 
 *
 *Server: At this point only the server knows who all are playing, the players are only aware of the server, so the server sends each of them a player list and begins its game activity.
 *Client: The players on receiving the player lists, begin their game activities.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;


@SuppressLint("ShowToast") public class MainActivity extends Activity {

	public BluetoothAdapter mBluetoothAdapter;
	public UUID uuid ;
	int discoverableFlag = 0; // 0- not on, 1 - discoverable 2 - connectable
	public static boolean serverActivity = false;
	public static boolean possibleServers = false;
	public static DialogFragment progDiscovery; //Both
	public static DialogFragment progPlayers;//Server

	//Selection of Players
	String[] dNameArray;
	ArrayList<String> deviceNames = new ArrayList<String>();//name only
	ArrayList<String> deviceAddresses = new ArrayList<String>();//address only

	//server needs
	public static ArrayList<String> playerList = new ArrayList<String>(); //selected by banker from deviceNaAdArray
	public static ArrayList<BluetoothServer> bServers = new ArrayList<BluetoothServer>(); //multiple servers created for each client,wrong approach

	public static ArrayList<BluetoothSocket> sSockets = new ArrayList<BluetoothSocket>();//socket connections for the servers
	public static ArrayList<ConnectedThread> sConToC = new ArrayList<ConnectedThread>(); //the connections for the servers

	//client needs
	public static ArrayList<BluetoothDevice> deviceList = new ArrayList<BluetoothDevice>();//devices
	public static ArrayList<BluetoothClient> tempClients = new ArrayList<BluetoothClient>();// temporary client objects gotten from deviceList,
	public static ArrayList<BluetoothDevice> posServerDevices = new ArrayList<BluetoothDevice>();//devices that are serving,
	public static ArrayList<BluetoothSocket> posServerSockets = new ArrayList<BluetoothSocket>();//socket connections for the clients

	//public static BluetoothDevice sDevice;//Main server device
	public static BluetoothSocket cSocket;//the socket connection
	public static ConnectedThread clientConnection;

	String MonopolyUUID="30fbf820-12da-11e5-b60b-1697f925ec7b";
	public static String playerName ="Testphrase";
	public static String phrase="";
	EditText et1;
	TextView tv2;
	public Button b2;
	public Button b3;
	public RadioButton rb1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		rb1 = (RadioButton)findViewById(R.id.radioButton1);
		rb1.setClickable(false);
		rb1.setChecked(false);

		tv2 = (TextView)findViewById(R.id.textView2);
		et1=(EditText)findViewById(R.id.editText1);
		tv2.setText("Known Devices: ");
		tv2.setVisibility(View.INVISIBLE);

		uuid= UUID.fromString(MonopolyUUID);

		registerReceiver(ScanModeChangedReceiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));

		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		registerReceiver(mReceiver, filter);

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
			Toast.makeText(getApplicationContext(), "Cannot Play, No bluetooth on this device!", 5000).show();
		}

		et1.setText(mBluetoothAdapter.getName());
		b2 = (Button) findViewById(R.id.button2);
		b3 = (Button) findViewById(R.id.button3);//the enter
		//b3.setClickable(false);

		Log.i("LOG",playerName+ " 1localdevicename : "+mBluetoothAdapter.getName());

		et1.setOnKeyListener(new OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
						(keyCode == KeyEvent.KEYCODE_ENTER)) {
					hideSoftKeyboard(MainActivity.this);

					playerName = et1.getText().toString();
					mBluetoothAdapter.setName(playerName);
					b3.setClickable(true);
					return true;
				}
				//b3.setClickable(false);
				return false;
			}
		});
	}

	public void hideSoftKeyboard(Activity activity) {
		InputMethodManager inputMethodManager = (InputMethodManager)  activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
		inputMethodManager.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), 0);
	}

	public void makeDiscoverable(int duration){//make discoverable
		Intent discoverableIntent = new
				Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, duration);
		startActivityForResult(discoverableIntent, 1);
	}

	public void startGameServer(){
		Intent i = new Intent();
		i.setComponent(new ComponentName(getApplicationContext(),GameActivity.class));
		i.putExtra("playerName", playerName);
		i.putExtra("serverOrClient", false);//server

		startActivity(i);
	}
	public static ConnectedThread getConnection(){
		return clientConnection;
	}

	public static ArrayList<ConnectedThread> getConnections(){
		return sConToC;
	}

	public void setupBluetooth(View v){
		hideSoftKeyboard(MainActivity.this);
		serverActivity = false;
		tv2.setVisibility(View.VISIBLE);
		phrase ="Looking for devices:";
		playerName = et1.getText().toString();
		mBluetoothAdapter.setName(playerName);//adaptor name should be changed by now
		beginDiscovery();
	}

	public void beginDiscovery(){
		Log.i("LOG",playerName+ " 3localdevicename : "+mBluetoothAdapter.getName());
		mBluetoothAdapter.startDiscovery();
		progDiscovery = new ProgressDialogFragment();
		progDiscovery.show(getFragmentManager(), "progDiscovery");
	}

	public void server(View v){
		phrase ="Waiting for players";
		if(!mBluetoothAdapter.isDiscovering()){
			if(!serverActivity ){
				serverActivity = true;
				choosePlayers();
			}
			else {	
				Toast.makeText(getApplicationContext(),"Server is busy", 500).show();
			}
		}else{//still discovering
			Toast.makeText(getApplicationContext(),"Busy searching...", 500).show();
		}

	}

	public void choosePlayers() {
		dNameArray = new String[deviceNames.size()];
		dNameArray=deviceNames.toArray(dNameArray);
		playerList.clear();
		DialogFragment newPlayers = new playerListDialogFragment();
		newPlayers.show(getFragmentManager(), "newPlayers");
	}
	
	public void closeOldServerSockets() {
		for(int i = 0; i < sSockets.size(); i++){
			try {
				sSockets.get(i).close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void serverConnection(){
		closeOldServerSockets();
		Toast.makeText(getApplicationContext(),"Im the Server "+mBluetoothAdapter.getName(), 5000).show();
		progPlayers = new ProgressDialogFragment();
		progPlayers.show(getFragmentManager(), "progPlayers");
		bServers.clear();
		sSockets.clear();
		sConToC.clear();

		for(int i = 0; i < playerList.size(); i++){// definitely should be multiple for FinalProto
			bServers.add(new BluetoothServer(mBluetoothAdapter,uuid,this));
			bServers.get(i).start();
		}	
	}

	public void getSocketList(BluetoothSocket socket){
		sSockets.add(socket);
		Log.i("LOG","About to try sending players");
		if(sSockets.size() == playerList.size()){
			Log.i("LOG","Found all players, sending!");
			progPlayers.dismiss();
		}
	}

	public void sendPlayerList(){
		playerList.add(mBluetoothAdapter.getName());// adding the server itself to player list
		//ConnectedThread.gotPlayers = true; //the server itself has the playerList so it sgouldnt receive what its sending
		for(int i = 0; i<sSockets.size();i++){ 
			String name =sSockets.get(i).getRemoteDevice().getName();
			sConToC.add(new ConnectedThread(sSockets.get(i),name,this));
			sConToC.get(i).gotPlayers = true;
			Log.i("LOG"," Creating connection to : "+name+ " and got players: "+sConToC.get(i).gotPlayers);
			sConToC.get(i).start();
		}
		for(int i = 0; i<sSockets.size();i++){
			sConToC.get(i).write(playerList);
		}
		phrase ="";//serverActivity = false;//change it back to false before GameActivity begins
		startGameServer();
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////

	//Client Code
	public void client(View v){
		phrase ="Looking for games:";
		if(serverActivity == false){
			if(!mBluetoothAdapter.isDiscovering()){
				searchServers();
			}
		}else {	
			Toast.makeText(getApplicationContext(),"Server is busy", 500).show();
		}
	}
	
	public void closeOldClientSockets() {
		for(int i = 0; i < posServerSockets.size(); i++){
			try {
				posServerSockets.get(i).close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void getServerList(BluetoothDevice device,BluetoothSocket mmSocket) {
		//unique server device found?
			posServerDevices.add(device);
			posServerSockets.add(mmSocket);
			Log.i("LOG"," Successful Server : "+device.getName());
			possibleServers = true;
	}

	public void searchServers(){
		closeOldClientSockets();
		posServerDevices.clear();
		tempClients.clear();
		posServerSockets.clear();

		for(int i = 0; i < deviceList.size();i++){
			Log.i("LOG","Client looking for server no "+(i+1));
			tempClients.add(new BluetoothClient(deviceList.get(i),uuid, playerName,this));
			tempClients.get(i).run();
		}
		
		Log.i("LOG","About check if it found a server ");
		if(possibleServers == false){//didnt find any servers
			Toast.makeText(getApplicationContext(), "No bankers found, try again?", 1200).show();
		}else{
			possibleServers = false;
			DialogFragment newServers = new ServerSelectDialogFragment();
			newServers.show(getFragmentManager(), "newServers");
		}
	}

	public void clientListenForServer(){
		String ServerName =cSocket.getRemoteDevice().getName();
		clientConnection = new ConnectedThread(cSocket,ServerName, this);
		clientConnection.gotPlayers = false;
		Toast.makeText(getApplicationContext(), "Starting game with Banker: " +ServerName, 500).show();
		clientConnection.start();
	}
	public void startGameClient(){
		Intent i = new Intent();
		i.setComponent(new ComponentName(getApplicationContext(),GameActivity.class));
		i.putExtra("playerName", playerName);
		i.putExtra("serverOrClient", true);//client;
	
		startActivity(i);
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////

	public static Handler handle = new Handler();
	Runnable r =new Runnable() {
		@Override
		public void run() {//For Server
			if(phrase.equals("Waiting for players")){
				Toast.makeText(getApplicationContext(),"All the players did not join. Stopping server", 400).show();
				serverActivity= false;phrase="";//must be done before dismissal otherwise sendPlayerList will be called in the dismiss
				progPlayers.dismiss();
			}
		}
	};


	//Discovery Receiver
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			// When discovery finds a device
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				// Get the BluetoothDevice object from the Intent
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				// Add the name and address to an array adapter to show in a ListView
				String deviceName=device.getName();
				String deviceAdd=device.getAddress();
				if(!deviceAddresses.contains(deviceAdd) ){//enters for new device only
					deviceAddresses.add(deviceAdd);
					deviceList.add(device);
					deviceNames.add(deviceName);
					Toast.makeText(getApplicationContext(),"Found Device: " +deviceName, 400).show();
					tv2.setText("Known Devices: "+deviceNames.toString());
					Log.i("LOG","No of devices: "+deviceNames.size());

				}else if(deviceAddresses.contains(deviceAdd) && !deviceNames.contains(deviceName)){
					int position = deviceAddresses.indexOf(deviceAdd); //remove existing copy
					deviceAddresses.remove(position);
					deviceList.remove(position);
					deviceNames.remove(position);

					Toast.makeText(getApplicationContext(),"Refound Device with new name: " +deviceName, 400).show();
					deviceAddresses.add(deviceAdd);// add the new copy hence getting the new name of device
					deviceList.add(device);
					deviceNames.add(deviceName);
					tv2.setText("Known Devices: "+deviceNames.toString());
				}
			}
			// When discovery finds a device already seen
			if (BluetoothDevice.ACTION_NAME_CHANGED.equals(action)) {
				// Get the BluetoothDevice object from the Intent
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				// Add the name and address to an array adapter to show in a ListView
				String deviceName = intent.getParcelableExtra(BluetoothDevice.EXTRA_NAME);
				String deviceAdd = device.getAddress();

				int position = deviceAddresses.indexOf(deviceAdd); //remove existing copy
				deviceAddresses.remove(position);
				deviceList.remove(position);
				deviceNames.remove(position);

				Toast.makeText(getApplicationContext(),"Device has new name: " +deviceName, 400).show();
				deviceAddresses.add(deviceAdd);// add the new copy hence getting the new name of device
				deviceList.add(device);
				deviceNames.add(deviceName);
				tv2.setText("Known Devices: "+deviceNames.toString());

			}
			if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				//mBluetoothAdapter.cancelDiscovery(); does it anyway in the dismiss
				progDiscovery.dismiss();
			}

		}
	};

	public class playerListDialogFragment extends DialogFragment {
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			//mSelectedItems = new ArrayList();  // Where we track the selected items
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

			// Set the dialog title
			builder.setTitle("Available Players:")
			// Specify the list array, the items to be selected by default (null for none),
			// and the listener through which to receive callbacks when items are selected
			.setMultiChoiceItems(dNameArray, null,
					new DialogInterface.OnMultiChoiceClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which,
						boolean isChecked) {
					if (isChecked) {
						// If the user checked the item, add it to the selected items
						playerList.add(deviceNames.get(which));
					} else if (playerList.contains(which)) {
						// Else, if the item is already in the array, remove it 
						playerList.remove(deviceNames.get(which));
					}
				}
			})
			// Set the action buttons
			.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					if(playerList.size()>0){
						serverConnection();
						handle.postDelayed(r, 35000);
					}
					else {
						serverActivity = false;
						phrase="";
					}
				}

			})
			.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					serverActivity = false;//Now server is allowed to relaunch
					phrase="";
				}
			});

			return builder.create();
		}
	}

	public class ServerSelectDialogFragment extends DialogFragment {
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			String[] array = new String[posServerDevices.size()];
			for(int i =0; i< posServerDevices.size();i++){
				array[i]=posServerDevices.get(i).getName();
			}
			builder.setTitle("Confirm your Banker from the following hosts!")
			.setItems(array, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					cSocket= posServerSockets.get(which);
					clientListenForServer();

				}
			});
			return builder.create();
		}
	}

	//Discoverable status receiver
	private final BroadcastReceiver ScanModeChangedReceiver = new BroadcastReceiver(){

		@Override
		public void onReceive(Context arg0, Intent intent) {
			String action = intent.getAction();
			if(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {

				int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, 
						BluetoothAdapter.ERROR);

				switch(mode){
				case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
					discoverableFlag = 1;
					rb1.setChecked(true);
					break;
				case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
					discoverableFlag = 2;
					rb1.setChecked(false);
					break;
				case BluetoothAdapter.SCAN_MODE_NONE:
					discoverableFlag = 0;
					rb1.setChecked(false);
					break;
				}		
			}
		}
	};

	public class ProgressDialogFragment extends DialogFragment {
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			// Get the layout inflater
			LayoutInflater inflater = getActivity().getLayoutInflater();

			// Inflate and set the layout for the dialog
			// Pass null as the parent view because its going in the dialog layout
			builder.setTitle(phrase)
			.setView(inflater.inflate(R.layout.dialog_progress, null)); 
			if(phrase.equals("Waiting for players")){
				setCancelable(false);
			}
			return builder.create();
		}

		@Override
		public void onDismiss(DialogInterface dialog){
			if(phrase.equals("Waiting for players")){
				if(serverActivity == true)
					sendPlayerList();
			}
			if(phrase.equals("Looking for devices:")){
				mBluetoothAdapter.cancelDiscovery();
			}

		}
	}

	public class ChoiceDialogFragment extends DialogFragment {
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			// Use the Builder class for convenient dialog construction
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setMessage("Close the app?")
			.setPositiveButton("Yup", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					mBluetoothAdapter.disable();
					unregisterReceiver(mReceiver);
					unregisterReceiver(ScanModeChangedReceiver);
					finish();
				}
			})
			.setNegativeButton("No, don't!", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					// User cancelled the dialog
				}
			});
			// Create the AlertDialog object and return it
			return builder.create();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onDestroy()
	{
		mBluetoothAdapter.disable();
		super.onDestroy();

	}

	@Override
	public void onResume()
	{
		super.onResume();
		Log.i("LOG","onResume discoverableFlag currently: "+discoverableFlag);
		if(discoverableFlag != 1 || !mBluetoothAdapter.isEnabled()){
			makeDiscoverable(0);
		}
	}

	@Override
	public void onBackPressed() {
		if(serverActivity){
			Toast.makeText(getApplicationContext(),"Stopping Server", 400).show();
			serverActivity= false;
		}
		else{
			DialogFragment endIt = new ChoiceDialogFragment();
			endIt.show(getFragmentManager(), "endIt");
		}
	}


}


