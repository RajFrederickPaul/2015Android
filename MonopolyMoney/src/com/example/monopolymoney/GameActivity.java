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
/******************************************
 * 
 * Author: Raj Frederick Paul
 * 
 * Game Activity:
 * It is the monopoly game, which is launched when the bluetooth connection is setup between server and each of the clients.
 * 
 * The player who is also Banker is the Server while the others are clients. 
 * For example, 3 players A,B,C where B is Banker(server) So the possible money transfers would be:
 * A->B
 * B->A
 * C->B
 * B->C
 * A->C = A->B->C
 * c->A = C->B->A
 * The server will the middle man since clients don't have a connection each other, only the server. A Star Network.
 * 
 * The Player selects an money amount and Recipient. If Player is Banker, he/she has the option to send money as Banker as well.
 * Also, the player who is also Banker can send money to himself(the player himself) without a bluetooth message since this transaction occurs in the same device.
 * 
 * This Activity also has the Handler used in Connected Thread since it needs to send and receive transactions.
 */
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class GameActivity extends Activity {

	protected static final int SUCCESS_CONNECT = 0;
	protected static final int MESSAGE_READ = 1;
	public static int MyMoney = 0;///Left empty before game begins
	public static String moneyWords;
	public static int currentAmount = 0;
	public static String cAmountWords= Integer.valueOf(currentAmount).toString();
	String payLoad;
	public static String Receiver= "Bank";
	public static String Sender= "Bank";
	public static String pName;
	public static int receiverPosition;
	public static TextView moneyView;
	TextView sendView;
	CheckBox cBox;
	Spinner rSpinner;
	public static SeekBar sBar;
	public static ArrayAdapter<String> adapter;
	public static Boolean isClient = true;
	public static String moneyStoB ="";
	public static Context myContext;

	public static ArrayList<String> theArray = new ArrayList<String>(Arrays.asList("Babloo"));
	public static ArrayList<String> playerList; 

	//client stuff
	public static ConnectedThread clientConnection;//the connected client itself
	//server stuff
	public static ArrayList<ConnectedThread> bConnections; //connections containing the connected sockets

	public static MediaPlayer mPlayer;
	public static Vibrator vib;
	public static TextView tView7;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_game);
		myContext = getApplicationContext();

		vib = (Vibrator) myContext.getSystemService(Context.VIBRATOR_SERVICE);
		pName=getIntent().getStringExtra("playerName");
		TextView playerName = (TextView) findViewById(R.id.textView5);
		playerName.setText(pName);
		
		tView7 = (TextView) findViewById(R.id.textView7);
		//initialization
		MyMoney = 1500;
		moneyWords= Integer.valueOf(MyMoney).toString();
		currentAmount = 0;
		cAmountWords= new Integer(currentAmount).toString();
		
		cBox = (CheckBox) findViewById(R.id.checkBox1);

		isClient=getIntent().getBooleanExtra("serverOrClient",true);
		if(isClient){//client
			clientConnection = MainActivity.getConnection();
			theArray.remove(pName);
			cBox.setVisibility(View.GONE);
		}
		else{//server
			theArray.clear();
			bConnections = MainActivity.getConnections();
			for (int i = 0; i<bConnections.size();i++){
				Log.i("LOG",i +" Client device name is "+bConnections.get(i).getConnectionName() );
				theArray.add(bConnections.get(i).getConnectionName());
			}
			theArray.add("Bank");
		}
		cBox.setChecked(false);

		moneyView = (TextView) findViewById(R.id.textView2);
		moneyView.setText(moneyWords +" $");

		sendView = (TextView) findViewById(R.id.textView6);
		sendView.setText(cAmountWords +" $");

		sBar = (SeekBar) findViewById(R.id.seekBar1);
		sBar.setMax(MyMoney);

		rSpinner = (Spinner) findViewById(R.id.spinner1);
		adapter =new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, theArray);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		rSpinner.setAdapter(adapter);

		mPlayer = MediaPlayer.create(getApplicationContext(), R.raw.chaching);

		sBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener(){

			@Override
			public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
				// TODO Auto-generated method stub
				currentAmount = arg1;
				cAmountWords= new Integer(currentAmount).toString();
				sendView.setText(cAmountWords +" $");
			}

			@Override
			public void onStartTrackingTouch(SeekBar arg0) {
				// TODO Auto-generated method stub

			}

			@Override
			public void onStopTrackingTouch(SeekBar arg0) {
				// TODO Auto-generated method stub

			}
		});


		rSpinner.setOnItemSelectedListener(new OnItemSelectedListener(){

			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1,
					int arg2, long arg3) {
				receiverPosition =(int) arg3;
				Receiver = (String) arg0.getItemAtPosition(receiverPosition);
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				Receiver = (String) arg0.getItemAtPosition(0);
			}

		});

		cBox.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if(cBox.isChecked()){//Becoming Bank
					sBar.setMax(1000);
					sBar.setProgress(200);
					if(theArray.contains("Bank")){
						theArray.remove("Bank");
					}
					if(!theArray.contains(pName)){
						theArray.add(pName);
					}
				}else{
					sBar.setMax(MyMoney);
					sBar.setProgress(0);
					if(!theArray.contains("Bank")){
						theArray.add("Bank");
					}
					if(!isClient){//it's server and shouldn't have itself when not bank
						theArray.remove(pName);
					}
				}
				adapter.notifyDataSetChanged();
				rSpinner.setSelection(0);
			}
		});
	}


	public void plus(View v){
		if(currentAmount<MyMoney){
			currentAmount++;
			sBar.setProgress(currentAmount);
		}
	}

	public void minus(View v){
		if(currentAmount>0){
			currentAmount--;
			sBar.setProgress(currentAmount);
		}
	}

	public void sendMoney(View v){ 
		if(currentAmount == 0){
			return;
		}
		if(cBox.isChecked()){
			Sender ="The Bank";
			payLoad = cAmountWords;
			moneySend();
			Toast.makeText(getApplicationContext(), "The Bank sends "+Receiver+" "+currentAmount+"$", 2000).show();
			vib.vibrate(400);
			sBar.setMax(1000);
			sBar.setProgress(200);
		}else{
			Sender =pName;
			payLoad = cAmountWords;
			Toast.makeText(getApplicationContext(), "Sending "+Receiver+" "+currentAmount+"$", 1000).show();
			vib.vibrate(400);
			MyMoney = MyMoney - currentAmount;
			moneyWords= Integer.valueOf(MyMoney).toString();
			moneyView.setText(moneyWords +" $");

			moneySend();
			sBar.setMax(MyMoney);
			sBar.setProgress(0);
		}
	}

	public void moneySend(){
		moneyStoB = payLoad;
		if(cBox.isChecked() && Receiver == pName){//Bank sending
			moneyNotification(pName,payLoad,Sender);
			return;
		}
		payLoad = payLoad+"@@"+Receiver+"@@"+Sender+"@@";
		byte[] byteArray = payLoad.getBytes();
		if(isClient){
			clientConnection.write(byteArray);
		}else{//server option
			if(Receiver == "Bank"){
				Toast.makeText(myContext, Sender+" sent The Bank "+moneyStoB+"$", 2000).show();
				mPlayer.start();
			}
			else{
				bConnections.get(theArray.indexOf(Receiver)).write(byteArray);
			}
		}
	}

	public static void moneyChannel(byte[] readBuf, String recipient){
		bConnections.get(theArray.indexOf(recipient)).write(readBuf);
	}

	public static void moneyNotification(String recipient,String money,String giver){
		Toast.makeText(myContext, giver +" sent you "+money+"$", 2000).show();
		vib.vibrate(400);
		mPlayer.start();
		MyMoney = MyMoney + Integer.parseInt(money);
		moneyWords= Integer.valueOf(MyMoney).toString();
		moneyView.setText(moneyWords +" $");
		sBar.setMax(MyMoney);
		tView7.setText("Last income: "+ giver +" sent you "+money+"$");
	}

	public static Handler mHandler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			//Log.i(tag, "in handler");
			super.handleMessage(msg);
			switch (msg.what) {
			case SUCCESS_CONNECT:
				Log.i("LOG"," Got the SUCCESS_CONNECT with the players");
				if(isClient){
					try {
						playerList = clientConnection.deserialize((byte[]) msg.obj);
					} catch (ClassNotFoundException e) {
						clientConnection.gotPlayers = false;
						e.printStackTrace();
					} catch (IOException e) {
						clientConnection.gotPlayers = false;
						e.printStackTrace();
					}
					//clientConnection.gotPlayers = true;
					Log.i("LOG"," Got the playerList");
					playerList.add("Bank");
					theArray = playerList;
				}
				break;
			case MESSAGE_READ:
				byte[] readBuf = (byte[]) msg.obj;
				String temp = new String(readBuf, 0, msg.arg1);
				String[] split = temp.split("@@");
				String moneygot = split[0];
				String recipient = split[1];
				String giver = split[2];
				Log.i("LOG"," Got "+ temp);
				Log.i("LOG"," Got "+ moneygot+" for "+recipient +" from "+giver);
				if(pName.equals(recipient)){
					moneyNotification(recipient,moneygot,giver);
				}
				else if(!isClient  && recipient.equals("Bank")){//if Server i.e Bank. The giver sent money to the bank
					Toast.makeText(myContext, giver +" sent The Bank "+moneygot+"$", 2000).show();
					mPlayer.start();
				}
				else {//should only happen to the server
					String intermediate = moneygot+"@@"+recipient+"@@"+giver+"@@";
					byte[] byteArray = intermediate.getBytes();
					moneyChannel(byteArray,recipient);
				}
				break;
			}
		}

	};

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

	public class ChoiceDialogFragment extends DialogFragment {
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			// Use the Builder class for convenient dialog construction
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setMessage("Leave the game?")
			.setPositiveButton("Yup", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					//moveTaskToBack(true);homescreen
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
	public void onBackPressed() {
		DialogFragment endIt = new ChoiceDialogFragment();
		endIt.show(getFragmentManager(), "endIt");
	}
}
