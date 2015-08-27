package com.yoo_hoo;

import java.io.File;
import java.util.Arrays;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.Toast;

import com.androidplot.ui.AnchorPosition;
import com.androidplot.ui.XLayoutStyle;
import com.androidplot.ui.YLayoutStyle;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XValueMarker;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.YValueMarker;
import com.ipaulpro.afilechooser.utils.FileUtils;
import com.yoo_hoo.R;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;

/*************
 * Author: Raj Frederick Paul
 * 
 *A Simple Whistle/Clap detection app. 
 * 
 * Main Activity
 *
 *The activity gives the user the option to pick an audio file to play on whistle detection.
 *And  a switch to choose between claps or whistles to detect.And another switch to choose modes/
 *It also has a graph depicting the waveform every half second which is updated by the service while active.
 *Also buttons to start and stop the listening service.
 *
 *
 */

public class MainActivity extends Activity {
	static MainActivity mActivity;
	Intent intent;
	Button Start;
	public static Button Stop;
	static Button SetFile;
	static Button playFile;
	public Switch switch1,switch2;
	public RelativeLayout buttonsLayout;
	static AdView mAdView;
	boolean adLoaded=false;
	AdRequest adRequest;
	InterstitialAd mInterstitialAd;

	static boolean appActive =true;
	static boolean whistled =false; // Flag added to assist the Advertising Interstitials
	static int startTutorial=0;//to prevent calling it twice or more by accident due to delay
	static boolean tutorialOpen =false;
	static boolean terminating;
	static MediaPlayer mPlayer;
	Vibrator vib;

	String stopMusic="Stop Music";
	String stopListening="Stop Listening";
	int[] location1=new int[4],location2=new int[4],location3=new int[4],location4=new int[4],location5=new int[4];

	BroadcastReceiver receiver;
	SharedPreferences spf;
	static SharedPreferences.Editor spe;
	public String[] types= new String[]{"mp3","flac","wav","aac","m4a","ogg"};
	public static String filePath = new String("");

	public static Number[] WValue=new Number[65],CValue=new Number[130],tempNumber;
	public static XYPlot mySimpleXYPlot;
	public static boolean WTOrCF;
	public static boolean Mode;
	static XValueMarker Marker1;
	static XValueMarker Marker2; 
	static YValueMarker Marker3;
	static YValueMarker Marker4; 
	static Context context;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		setContentView(R.layout.activity_main);
		Log.i("LOG","Creating Actvity");
		mActivity=this;
		Start = (Button) findViewById(R.id.button1);
		Stop = (Button) findViewById(R.id.button2); 
		buttonsLayout = (RelativeLayout) findViewById(R.id.buttonsLayout);

		SetFile = (Button) findViewById(R.id.button3);
		playFile = (Button) findViewById(R.id.button4);

		switch1 = (Switch) findViewById(R.id.switch1); 
		switch2 = (Switch) findViewById(R.id.switch2); 

		mAdView = (AdView) findViewById(R.id.adView);

		mySimpleXYPlot = (XYPlot) findViewById(R.id.mySimpleXYPlot);
		mySimpleXYPlot.getGraphWidget().position(
				0, XLayoutStyle.ABSOLUTE_FROM_LEFT,
				20, YLayoutStyle.ABSOLUTE_FROM_TOP,
				AnchorPosition.LEFT_TOP);

		mPlayer=new MediaPlayer();
		spf=getSharedPreferences("myprfs", Context.MODE_PRIVATE);
		spe=spf.edit();

		intent=new Intent(getApplicationContext(), OneService.class);
		vib = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

		filePath=spf.getString("filePath", "");Log.i("LOG","File Path "+filePath);
		WTOrCF = spf.getBoolean("WTOrCF", true);
		switch1.setChecked(WTOrCF); 
		Mode = spf.getBoolean("Mode", false);
		switch2.setChecked(Mode); 

		adRequest = new AdRequest.Builder().build();//adRequest =AdRequest.Builder.addTestDevice("").build();
		mAdView.loadAd(adRequest);

		mAdView.setAdListener(new AdListener() {
			@Override
			public void onAdOpened() {
			}

			public void onAdFailedToLoad(int errorCode){
				adLoaded=false;
				mAdView.setVisibility(View.GONE);
			}
			public void onAdLoaded(){
				adLoaded=true;
			}
		});

		mInterstitialAd = new InterstitialAd(this);
		mInterstitialAd.setAdUnitId(getString(R.string.stoplistening_interstitial));

		mInterstitialAd.setAdListener(new AdListener() {// TODO
			@Override
			public void onAdClosed() {
				requestNewInterstitial();
				if(Mode){//if Idle mode, should close itself
					terminating =true;
					ExitActivity.exitApplication(context);
				}
			}
		});
		requestNewInterstitial();

		if(filePath.equals(""))
			SetFile.setText("Select Audio File");
		else{
			setSong(filePath);
		}

		for(int i= 0; i<65;i++)WValue[i]=0;
		for(int i= 0; i<130;i++)CValue[i]=0;

		if(isServiceRunning()){//Used when app restarts
			Start.setVisibility(View.GONE);
			Stop.setVisibility(View.VISIBLE);
			if(filePath.equals("")){//Case when Song was deleted
				Start.setVisibility(View.VISIBLE);
				Stop.setVisibility(View.GONE);
				if(OneService.terminateService())
					stopService(intent);
				clearUpdate();
			}
		}
		else{				//	Leaving for now	//First time app is opened 
			clearUpdate();
		}

		switch1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				vib.vibrate(50);
				setClapOrWhistle(isChecked);
			}

		});

		switch2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if(filePath ==""){
					Toast.makeText(getApplicationContext(), "You have not selected an Audio file", 500).show();
					Mode=false;switch2.setChecked(Mode);
					spe.putBoolean("Mode", Mode);
					spe.commit();
					return;
				}
				Mode=isChecked;
				spe.putBoolean("Mode", Mode);
				spe.commit();vib.vibrate(50);
				if(!Mode){
					if(isServiceRunning()){
						Stop.setVisibility(View.GONE);
						if(OneService.terminateService())
							stopService(intent);
						Start.setVisibility(View.VISIBLE);
					}
					buttonsLayout.setVisibility(View.VISIBLE);
				}else{//Idle Mode = true
					if(filePath=="")
					initMusicPlayer();
					buttonsLayout.setVisibility(View.GONE);
					startService(intent);
				}
			}

		});
		receiver = new BroadcastReceiver() {//while app is open TODO
			@Override
			public void onReceive(Context context, Intent intent) {
				String s = intent.getStringExtra("blah");
				if(s.equals("1")){//case: music just started playing 
					Stop.setText(stopMusic);
					whistled=true;
				}
				if(s.equals("0")){//case: music stops when song ends by itself OR pressing the power button screenON to stop music
					stoppingMusic();Log.i("LOG","Song ended by itself OR screenOn, I am summoned");
					whistled=false;
					if (mInterstitialAd.isLoaded()) {
						mInterstitialAd.show();
					}else{
						if(Mode){
							terminating =true;//finish();
							ExitActivity.exitApplication(context);
						}
					}
				}
			}
		};

		mPlayer.setOnCompletionListener(new OnCompletionListener(){
			@Override
			public void onCompletion(MediaPlayer mp) {
				initMusicPlayer();//calls itself to reset mPlayer
			}
		});
		context=getApplicationContext();

		ViewTreeObserver vto = buttonsLayout.getViewTreeObserver();
		vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				location5[0]=buttonsLayout.getTop();
				location5[1]=buttonsLayout.getLeft();
				location5[2]=buttonsLayout.getHeight();
				location5[3]=buttonsLayout.getWidth();
				if(startTutorial==1 && location5[0]>0){
					startTutorial=2;vib.vibrate(50);
					tutorialData();
				}
			}

		});
	}


	public void setClapOrWhistle(boolean isChecked){
		if(!isChecked){//OFF-Clap 
			WTOrCF=false;
			spe.putBoolean("WTOrCF", WTOrCF);
			spe.commit();
			xValUpdate(CValue);
			mySimpleXYPlot.removeMarkers();
			Log.i("LOG","Chose Clap");
		}
		else{//ON-Whistle
			WTOrCF=true;
			spe.putBoolean("WTOrCF", WTOrCF);
			spe.commit();
			xValUpdate(WValue);
			mySimpleXYPlot.removeMarkers();
			plotMarkerUpdate(23,63);
			Log.i("LOG","Chose Whistle");
		}
		if(Mode && isServiceRunning())
			OneService.changeWTOrCF(WTOrCF);
	}

	private void requestNewInterstitial() {
		AdRequest adRequest = new AdRequest.Builder()
		//.addTestDevice("B9D66D1C8230B5AF991ADD28F900DD1F")
		.build();

		mInterstitialAd.loadAd(adRequest);
	}


	public void sAudioFile(View v){
		initMusicPlayer();
		Intent getContentIntent = FileUtils.createGetContentIntent();
		Intent intent = Intent.createChooser(getContentIntent, "Select a file");
		if(isServiceRunning() && !Mode)
			Toast.makeText(getApplicationContext(), "Stop Listening before changing Audio File", 500).show();
		else
			startActivityForResult(intent, 12345);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case 12345:   
			if (resultCode == RESULT_OK) {

				final Uri uri = data.getData();
				String path = FileUtils.getPath(this, uri);
				//Log.i("LOG","File Path is "+path);

				if(path==null){
					Toast.makeText(getApplicationContext(), "Cannot retrieve file!", 500).show();
					break;
				}

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
		spe.putString("filePath", filePath);
		spe.commit();
		File file = new File(filePath);
		if(file.exists()){
			SetFile.setText(file.getName());
			initMusicPlayer();
			if(Mode && isServiceRunning() && OneService.serviceCreated && OneService.songPath!=filePath && !OneService.isPlaying){//To change service songpath while already running running in idle mode
				Log.i("LOG","Sending Song to Service"); OneService.songChange();
			}
			Log.i("LOG","file name"+file.getName());
		}
		else{//case when file is deleted and app opens newly and checks
			Toast.makeText(getApplicationContext(), "Previously select Audio file deleted", 500).show();
			SetFile.setText("Select Audio File");
			spe.putString("filePath", "");
			spe.commit();
			filePath="";
		}
	}

	public void playFile(View v){
		if(filePath ==""){
			Toast.makeText(getApplicationContext(), "Audio file not selected", 500).show();
			return;
		}
		if(isServiceRunning() && !Mode){
			Toast.makeText(getApplicationContext(), "Stop Listening before playing Audio File", 500).show();
		}else{
			if(playFile.getText().equals("Play"))
			{
				vib.vibrate(50);
				mPlayer.start();
				playFile.setText("Stop");if(Mode)OneService.mActMusicPlaying=true;
			}
			else{
				vib.vibrate(50);
				initMusicPlayer();
			}
		}
	}

	public static void initMusicPlayer(){
		playFile.setText("Play");
		if(mPlayer.isPlaying()){
			mPlayer.stop();
			if(Mode)OneService.mActMusicPlaying=false;
		}
		mPlayer.reset();
		try{
			mPlayer.setDataSource(filePath);
			mPlayer.prepare();
		}catch (Exception e) {
		}
	}

	public void start(View v){
		if(filePath ==""){
			Toast.makeText(getApplicationContext(), "You have not selected an Audio file", 500).show();
			return;
		}
		vib.vibrate(50);
		initMusicPlayer();
		switch1.setEnabled(false);
		switch2.setEnabled(false);
		Stop.setVisibility(View.VISIBLE);
		Start.setVisibility(View.GONE);
		startService(intent);
	}

	public void stop(View v){
		vib.vibrate(50);
		if(OneService.isPlaying){
			stoppingMusic();
		}
		else{
			Start.setVisibility(View.VISIBLE);
			Stop.setVisibility(View.GONE);
			if(OneService.terminateService())
				stopService(intent);
			switch1.setEnabled(true);
			switch2.setEnabled(true);
			if(whistled){
				whistled=false;
				if (mInterstitialAd.isLoaded()) {
					mInterstitialAd.show();
				}
			}
		}
	}

	public void stoppingMusic(){
		OneService.isPlaying=false;
		OneService.initMusicPlayer();
		Stop.setText(stopListening);
		startService(intent);
	}

	@Override
	protected void onStart() {
		super.onStart();
		LocalBroadcastManager.getInstance(this).registerReceiver((receiver), new IntentFilter("whistle"));
	}

	@Override
	protected void onDestroy() {
		mAdView.destroy();
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onStop() {
		OneService.appOpen= false;
		Log.i("LOG","Stopping Activity");
		if(Mode && !mPlayer.isPlaying())OneService.mActMusicPlaying=false;
		if(Mode && terminating){
			Log.i("LOG","Terminating bloat");
		}
		LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
		super.onStop();
	}

	@Override
	protected void onResume(){ 
		Log.i("LOG","Resuming");startTutorial=0;
		if(adLoaded && !tutorialOpen){
			mAdView.setVisibility(View.VISIBLE);}
		buttonsLayout.setVisibility(View.VISIBLE);//To fool the globalLayoutListener for Tutorial 

		terminating =false;
		OneService.appOpen = true;
		if(isServiceRunning()){
			Start.setVisibility(View.GONE);
			Stop.setVisibility(View.VISIBLE);

			switch1.setEnabled(false);
			switch2.setEnabled(false);

			if(OneService.isPlaying){//sets stopMusic when app relaunches from recents or fresh
				whistled=true;
				Stop.setText(stopMusic);
				if(WTOrCF){
					mySimpleXYPlot.removeMarkers();
					xValUpdate(OneService.XValW);
				}
				else{
					mySimpleXYPlot.removeMarkers();
					xValUpdate(OneService.XValC,OneService.ampAvgPrev,OneService.ampAvgTarget);//plotMarkerUpdate(100,128);
				}
			}else
				clearUpdate();
			if(OneService.songEnded){//unlikely event when song ends in minimized app, resets things when is opens
				stoppingMusic();
				OneService.songEnded=false;//not calling the ad here,don't want to be too annoying
			}
		}else{
			Start.setVisibility(View.VISIBLE);
			Stop.setVisibility(View.GONE);
			clearUpdate();
		}
		if(WTOrCF)
			plotMarkerUpdate(23,63);

		if(Mode){
			switch1.setEnabled(true);
			switch2.setEnabled(true);
			buttonsLayout.setVisibility(View.GONE);
			if(!isServiceRunning())
				startService(intent);
			if(OneService.isPlaying){
				mAdView.setVisibility(View.GONE);
			}
		}

		super.onResume();
	}


	private boolean isServiceRunning() {
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if ("com.yoo_hoo.OneService".equals(service.service.getClassName()) ) {
				return true;
			}
		}
		return false;
	}

	public static void clearUpdate(){
		if(WTOrCF)
			xValUpdate(WValue);
		else
			xValUpdate(CValue);
	}

	public static void xValUpdate(Number[] Update,double avg, double avgPlusThreshold){
		plotClapThresholdUpdate(avg,avgPlusThreshold);
		xValUpdate(Update);
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

	public static void plotMarkerUpdate(int min,int max){
		mySimpleXYPlot.removeMarkers();
		Marker1 = new XValueMarker(min, null);
		Marker2 = new XValueMarker(max, null);
		mySimpleXYPlot.addMarker(Marker1);
		mySimpleXYPlot.addMarker(Marker2);
		mySimpleXYPlot.redraw();
	}

	public static void plotClapThresholdUpdate(double min,double max){
		mySimpleXYPlot.removeMarkers();
		Marker3 = new YValueMarker(min, "Avg");
		Marker4 = new YValueMarker(max, "Target");
		mySimpleXYPlot.addMarker(Marker3);
		mySimpleXYPlot.addMarker(Marker4);
		mySimpleXYPlot.redraw();
	}

	public void launchTutorial(){
		if(startTutorial>0){//To avoid double taps
			return;
		}
		if(isServiceRunning() && !Mode){
			Toast.makeText(getApplicationContext(), "Need to Stop Listening first", 500).show();
			return;
		}
		startTutorial=1;
		Log.i("LOG","Starting Tutorial ");
		mAdView.setVisibility(View.GONE);
		buttonsLayout.setVisibility(View.GONE);
		buttonsLayout.setVisibility(View.VISIBLE);//causes the global listener to trigger tutorialData
	}

	public void tutorialData(){
		Intent tIntent = new Intent();tIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
		tIntent.setComponent(new ComponentName(getApplicationContext(),DemoActivity.class));
		location1[0]=SetFile.getTop();
		location1[1]=SetFile.getLeft();
		location1[2]=SetFile.getHeight();
		location1[3]=SetFile.getWidth();
		tIntent.putExtra("1", location1);
		location2[0]=playFile.getTop();
		location2[1]=playFile.getLeft();
		location2[2]=playFile.getHeight();
		location2[3]=playFile.getWidth();
		tIntent.putExtra("2", location2);
		location3[0]=switch1.getTop();
		location3[1]=switch1.getLeft();
		location3[2]=switch1.getHeight();
		location3[3]=switch1.getWidth();
		tIntent.putExtra("3", location3);
		location4[0]=switch2.getTop();
		location4[1]=switch2.getLeft();
		location4[2]=switch2.getHeight();
		location4[3]=switch2.getWidth();
		tIntent.putExtra("4", location4);
		tIntent.putExtra("5", location5);
		tutorialOpen=true;
		startActivity(tIntent);//tutorial=false;
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		final MenuItem item = menu.findItem(R.id.tutorial);
	    item.getActionView().setOnClickListener(new OnClickListener() {
	        @Override
	        public void onClick(View v) {
	            onOptionsItemSelected(item);
	        }
	    });
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
		case R.id.tutorial:
			launchTutorial();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
}

//
//public void endApplication() {
//	Intent intent = new Intent(Intent.ACTION_MAIN);
//	intent.addCategory(Intent.CATEGORY_HOME);
//	intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//	//		    startActivity(intent);
//	//	    Intent intent = new Intent(getApplicationContext(), MainActivity.class);
//	//	    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//	//	    intent.putExtra("EXIT", true);
//	//	    startActivity(intent);
//}

//if(WTOrCF){
//	MyService.isPlaying=false;
//	MyService.initMusicPlayer();
//}else{
//	ClapService.isPlaying=false;
//	ClapService.initMusicPlayer();
//}