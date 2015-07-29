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
import java.io.IOException;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

public class BluetoothClient extends Thread {
	private final BluetoothSocket mmSocket;
	private final BluetoothDevice mmDevice;
	public Boolean sDiscovery;
	String pName;
	ConnectedThread CT ;
	MainActivity mActivity;

	public BluetoothClient(BluetoothDevice device,UUID uuid,String playerName,MainActivity activity) {
		// Use a temporary object that is later assigned to mmSocket,
		// because mmSocket is final
		BluetoothSocket tmp = null;
		mActivity=activity;
		mmDevice = device;
		pName = playerName;

		// Get a BluetoothSocket to connect with the given BluetoothDevice
		try {
			// MY_UUID is the app's UUID string, also used by the server code
			tmp = mmDevice.createRfcommSocketToServiceRecord(uuid);
		} catch (IOException e) { }
		mmSocket = tmp;
	}

	public void run() {
		// Cancel discovery because it will slow down the connection
		//mBluetoothAdapter.cancelDiscovery();
		try {
			// Connect the device through the socket. This will block until it succeeds or throws an exception
			if(!mActivity.posServerDevices.contains(mmDevice)){
				Log.i("LOG","Client trying device: "+mmDevice.getName());
				mmSocket.connect();
				mActivity.getServerList(mmDevice,mmSocket);
			}

		} catch (IOException connectException) {
			// Unable to connect; close the socket and get out
			try {
				Log.i("LOG",mmDevice.getName()+" client failed! ");
				mmSocket.close();
			} catch (IOException closeException) { }
			return;
		}
		// Do work to manage the connection (in a separate thread)
		// manageConnectedSocket(mmSocket);
	}

	/** Will cancel an in-progress connection, and close the socket */
	public void cancel() {
		try {
			mmSocket.close();
		} catch (IOException e) { }
	}
}