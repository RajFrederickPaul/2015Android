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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

public class BluetoothServer extends Thread {
	private final BluetoothServerSocket mmServerSocket;
	BluetoothAdapter BA;
	ConnectedThread CT ;
	MainActivity mActivity;
	
	public BluetoothServer(BluetoothAdapter mBluetoothAdapter,UUID uuid,MainActivity activity) {
		// Use a temporary object that is later assigned to mmServerSocket,
		// because mmServerSocket is final
		mActivity=activity;
		BluetoothServerSocket tmp = null;
		BA= mBluetoothAdapter;
		try {
			// MY_UUID is the app's UUID string, also used by the client code
			tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("Monopoly Money", uuid);
		} catch (IOException e) { }
		mmServerSocket = tmp;
	}

	public void run() {
		BluetoothSocket socket = null;
		// Keep listening until exception occurs or a socket is returned
		Log.i("LOG"," Starting "+BA.getName()+" server: ");
		while (true) {
			try {
				socket = mmServerSocket.accept();
			} catch (IOException e) {
				break;
			}
			// If a connection was accepted
			if (socket != null) {
				
				mActivity.getSocketList(socket);
				Log.i("LOG",BA.getName()+" server connected: ");//manageConnectedSocket(socket);
				
				try {
					mmServerSocket.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
			}
		}
	}

//	public void manageConnectedSocket(BluetoothSocket socket) {
//		CT =new ConnectedThread(socket);
//	}

	/** Will cancel the listening socket, and cause the thread to finish */
	public void cancel() {
		try {
			mmServerSocket.close();
		} catch (IOException e) { }
	}
}