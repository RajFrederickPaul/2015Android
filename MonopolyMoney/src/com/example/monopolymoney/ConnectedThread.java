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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;

public class ConnectedThread extends Thread {
	private static final int SUCCESS_CONNECT = 0;
	private static final int MESSAGE_READ = 1;
	private final BluetoothSocket mmSocket;
	private final InputStream mmInStream;
	private final OutputStream mmOutStream;
	Handler mHandler;
	String Name ="Unnamed Connection";
	public static Boolean gotPlayers = false;
	MainActivity mActivity;

	public ConnectedThread(BluetoothSocket socket, String pName, MainActivity mainActivity) {
		mmSocket = socket;
		InputStream tmpIn = null;
		OutputStream tmpOut = null;
		Name = pName;

		mActivity =mainActivity;
		mHandler= GameActivity.mHandler;
		// Get the input and output streams, using temp objects because
		// member streams are final
		try {
			tmpIn = socket.getInputStream();
			tmpOut = socket.getOutputStream();
		} catch (IOException e) { }

		mmInStream = tmpIn;
		mmOutStream = tmpOut;
	}

	public void run() {
		byte[] buffer = new byte[1024];  // buffer store for the stream
		int bytes; // bytes returned from read()

		// Keep listening to the InputStream until an exception occurs
		while (true) {
			try {
				// Read from the InputStream
				bytes = mmInStream.read(buffer);
				if(gotPlayers){
					// Send the obtained bytes to the UI activity
					mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
				}
				if(!gotPlayers){// when client hasnt got the players yet
					mHandler.obtainMessage(SUCCESS_CONNECT, bytes, -1, buffer).sendToTarget();
					gotPlayers = true;
					mActivity.startGameClient();
				}
			} catch (IOException e) {
				break;
			}
		}
	}

	/* Call this from the main activity to send data to the remote device */
	public void write(byte[] bytes) {
		try {
			mmOutStream.write(bytes);
		} catch (IOException e) { }
	}

	public void write(ArrayList<String> playerList) {
		try {
			mmOutStream.write(serialize(playerList));
		} catch (IOException e) {
		}
	}

	public byte[] serialize(ArrayList<String> theObject) throws IOException {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		ObjectOutputStream o = new ObjectOutputStream(b);
		o.writeObject(theObject);
		return b.toByteArray();
	}
	
	public static ArrayList<String> deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
		ByteArrayInputStream b = new ByteArrayInputStream(bytes);
		ObjectInputStream o = new ObjectInputStream(b);
		return (ArrayList<String>) o.readObject();
	}

	public String getConnectionName(){
		return Name;
	}

	/* Call this from the main activity to shutdown the connection */
	public void cancel() {
		try {
			mmSocket.close();
		} catch (IOException e) { }
	}
}