package com.yoo_hoo;

import java.io.File;
import java.util.Arrays;
/*************
 * Author: Raj Frederick Paul
 * 
 *A Simple Whistle detection app. 
 * 
 * Main Activity
 *
 *The activity gives the user the option to pick an audio file to play on whistle detection.
 *And the a slider to set the frequency range the service should search in.
 *It also has a graph depicting the waveform every second which is updated by the service while active.
 *Also buttons to start and stop the listening service.
 *
 *
 */
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.androidplot.ui.AnchorPosition;
import com.androidplot.ui.XLayoutStyle;
import com.androidplot.ui.YLayoutStyle;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XValueMarker;
import com.androidplot.xy.XYPlot;
import com.ipaulpro.afilechooser.utils.FileUtils;


public class MainActivity extends Activity {
	Intent i;
	Button Start;
	public static Button Stop;
	static Button SetFile;
	static Button playFile;
	public SeekBar sBar;

	static boolean musicActive =false;
	static MediaPlayer mPlayer = null;
	String stopMusic="Stop Music";
	String stopListening="Stop Listening";
	BroadcastReceiver receiver;
	SharedPreferences spf;
	static SharedPreferences.Editor spe;
	public String[] types= new String[]{"mp3","flac","wav","aac","m4a"};
	public static String filePath = new String("");
	public int minF= 28,maxProg =53-minF;

	public static Number[] XValue=new Number[64],tempNumber;
	public static XYPlot mySimpleXYPlot;
	public static int sRange;
	static XValueMarker Marker1;
	static XValueMarker Marker2; 

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		Log.i("LOG","Creating Actvity");
		Start = (Button) findViewById(R.id.button1);
		Stop = (Button) findViewById(R.id.button2); 
		SetFile = (Button) findViewById(R.id.button3);
		playFile = (Button) findViewById(R.id.button4);

		sBar = (SeekBar) findViewById(R.id.seekBar1);
		mySimpleXYPlot = (XYPlot) findViewById(R.id.mySimpleXYPlot);
		mySimpleXYPlot.getGraphWidget().position(
				0, XLayoutStyle.ABSOLUTE_FROM_LEFT,
				20, YLayoutStyle.ABSOLUTE_FROM_TOP,
				AnchorPosition.LEFT_TOP);

		mPlayer=new MediaPlayer();
		spf=getSharedPreferences("myprfs", Context.MODE_PRIVATE);
		spe=spf.edit();
		filePath=spf.getString("filePath", "");
		Log.i("LOG","File Path "+filePath);
		sRange = spf.getInt("sRange", 40);
		if(filePath.equals(""))
			SetFile.setText("Select Audio File");
		else{
			setSong(filePath);
		}

		sBar.setMax(maxProg);
		sBar.setProgress(sRange-minF);
		plotMarkerUpdate();
		for(int i= 0; i<64;i++)XValue[i]=0;

		if(isMyServiceRunning()){//Used when app restarts
			Start.setVisibility(View.GONE);
		}else{				//	Log.i("LOG","Called the first time option");	//First time app is opened ish 
			xValUpdate(XValue);
		}

		i=new Intent(getApplicationContext(), MyService.class);

		receiver = new BroadcastReceiver() {//while app is open
			@Override
			public void onReceive(Context context, Intent intent) {
				String s = intent.getStringExtra("blah");
				if(s.equals("1")){//case: music just started playing 
					if(musicActive== false){
						musicActive= true;
						Stop.setText(stopMusic);
					}
				}
				if(s.equals("0")){//case: music stops when song ends by itself OR pressing the power button off to stop music
					MyService.isPlaying = musicActive = false;
					MyService.initMusicPlayer();
					Stop.setText(stopListening);
					startService(i);
				}
			}
		};

		sBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener(){
			@Override
			public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
				sRange = arg1+minF;
				plotMarkerUpdate();
				spe.putInt("sRange", sRange);
				spe.commit();
				if(isMyServiceRunning()){
					Log.i("LOG","Setting Service Frequencies");
					MyService.setServiceFrequencies();
				}
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {	
			}
		});

		
	}

	public void sAudioFile(View v){
		initMusicPlayer();
		Intent getContentIntent = FileUtils.createGetContentIntent();
		Intent intent = Intent.createChooser(getContentIntent, "Select a file");
		if(!isMyServiceRunning())
			startActivityForResult(intent, 12345);
		else
			Toast.makeText(getApplicationContext(), "Stop Listening before changing Audio File", 500).show();
	}

	@SuppressLint("ShowToast") @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case 12345:   
			if (resultCode == RESULT_OK) {

				final Uri uri = data.getData();
				String path = FileUtils.getPath(this, uri);
				Log.i("LOG","File Path is "+path);

				String extension = "";
				int i = path.lastIndexOf('.');
				if (i != -1) {
					extension = path.substring(i+1);
				}
				if(isMusicFile(extension)){
					setSong(path);
				}else
					Toast.makeText(getApplicationContext(), "Not an audio File!", 500).show();

			}
			break;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	public boolean isMusicFile(String paramStr){
		for (String s : types) {
			if (paramStr.equalsIgnoreCase(s)) return true;
		}
		return false;
	}
	public void setSong(String filePath2) {
		filePath=filePath2;
		initMusicPlayer();
		spe.putString("filePath", filePath);
		spe.commit();
		File file = new File(filePath);
		Log.i("LOG","file name"+file.getName());
		SetFile.setText(file.getName());
	}

	public void playFile(View v){
		if(filePath !=""){
			if(!isMyServiceRunning()){
				if(playFile.getText().equals("Play"))
				{
					mPlayer.start();
					playFile.setText("Stop");
				}
				else{
					initMusicPlayer();
				}
			}else
				Toast.makeText(getApplicationContext(), "Stop Listening before playing Audio File", 500).show();
		}
		else
			Toast.makeText(getApplicationContext(), "Audio file not selected", 500).show();
	}
	public static void initMusicPlayer(){
		playFile.setText("Play");
		if(mPlayer!= null){
			mPlayer.stop();
		}
		try{
			mPlayer=new MediaPlayer();
			mPlayer.setDataSource(filePath);
			mPlayer.prepare();
		}catch (Exception e) {
		}
		mPlayer.setOnCompletionListener(new OnCompletionListener(){
			@Override
			public void onCompletion(MediaPlayer mp) {
				initMusicPlayer();//calls itself to reset mPlayer
			}
		});
	}


	public void start(View v){
		if(filePath !=""){
			initMusicPlayer();
			Start.setVisibility(View.GONE);
			startService(i);	}
		else
			Toast.makeText(getApplicationContext(), "You have not selected an Audio file", 500).show();
	}

	public void stop(View v){
		if(musicActive == true){
			musicActive = false;
			MyService.isPlaying=false;
			MyService.initMusicPlayer();
			Stop.setText(stopListening);
			startService(i);
		}
		else{
			Start.setVisibility(View.VISIBLE);
			stopService(i);
			xValUpdate(XValue);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		LocalBroadcastManager.getInstance(this).registerReceiver((receiver), new IntentFilter("whistle"));
	}

	@Override
	protected void onResume(){
		Log.i("LOG","Resuming");
		MyService.appOpen = true;
		if(isMyServiceRunning()){
			if(MyService.isPlaying == true){//sets stopMusic when app relaunches from recents or fresh
				musicActive= true;
				Stop.setText(stopMusic);
				plotMarkerUpdate();
				xValUpdate(MyService.XVal);
			}
		}
		super.onResume();
	}

	@Override
	protected void onPause() {
		MyService.appOpen= false;//redundant , just in case
		super.onPause();
	}

	@Override
	protected void onStop() {
		MyService.appOpen= false;
		Log.i("LOG","Stopping ");
		LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
		super.onStop();
	}


	private boolean isMyServiceRunning() {
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if ("com.yoo_hoo.MyService".equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	public static void xValUpdate(Number[] Update){
		tempNumber=Update;
		mySimpleXYPlot.clear();

		mySimpleXYPlot.addSeries(new SimpleXYSeries(
				Arrays.asList(tempNumber),          // SimpleXYSeries takes a List so turn our array into a List
				SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, // Y_VALS_ONLY means use the element index as the x value
				"Amplitude Vs Frequency"), new LineAndPointFormatter(
						Color.rgb(0, 0, 200),                   // line color
						Color.rgb(0, 0, 100),                   // point color
						null,                                   // fill color (none)
						null));

		mySimpleXYPlot.redraw();
	}

	public static void plotMarkerUpdate(){
		mySimpleXYPlot.removeXMarkers();
		Marker1 = new XValueMarker(sRange, null);
		Marker2 = new XValueMarker(sRange+10, null);
		mySimpleXYPlot.addMarker(Marker1);
		mySimpleXYPlot.addMarker(Marker2);
		mySimpleXYPlot.redraw();
	}



}


//@Override
//public void onConfigurationChanged(Configuration newConfig) {
//  super.onConfigurationChanged(newConfig);
//  setContentView(R.layout.myLayout);
//}
//new PointLabelFormatter(Color.WHITE)));

//mySimpleXYPlot.setDomainStep(XYStepMode.INCREMENT_BY_VAL, 1);
//mySimpleXYPlot.setTicksPerRangeLabel(3);

//public static void setMusicActivity(Boolean a){
//	musicActive= a;
//	if(musicActive== true){
//		Stop.setText("Stop Music");
//	}
//}

//@Override
//public void onWindowFocusChanged (boolean hasFocus){
//	inFocus=hasFocus;
//	//Toast.makeText(getApplicationContext(), "called"+MyService.hasFocus, 500).show();
//}\

//private String getRealPathFromURI(Uri contentUri) {
//String docId = DocumentsContract.getDocumentId(contentUri);
//String[] split = docId.split(":");
//final String[] selectionArgs = new String[] {split[1]};
//String[] proj = { MediaStore.Images.Media.DATA };
//CursorLoader loader = new CursorLoader(getApplicationContext(), contentUri, proj, "_id=?", selectionArgs, null);
//Cursor cursor = loader.loadInBackground();
//int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
//cursor.moveToFirst();
//String result = cursor.getString(column_index);
//cursor.close();
//return result;
//}
