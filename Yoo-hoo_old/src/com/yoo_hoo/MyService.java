package com.yoo_hoo;
/*************
 * Author: Raj Frederick Paul
 * 
 *MyService
 *This service is used to record every second and check audio data buffer 
 *using a Fast fourier transform to check if the waveform matches that of a whistle in the frequency range 
 *specified by the user.
 *
 *
 *
 */
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.yoo_hoo.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.os.Vibrator;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.media.AudioFormat;
import android.media.AudioRecord;

public class MyService extends Service{

	static MediaPlayer mPlayer = null;// For mp3 file
	long[] pattern = {0, 300, 1000};
	public static Vibrator vib;
	static AudioRecord  recorder; //For whistle
	static int bufferSize = 21312;
	FFT mFFT;

	final int mNumberOfFFTPoints =2048;
	final int mNeededNumOFFFTPoints= 64;
	public static Number[] XVal=null;
	public static double[] peaks=null;
	public ArrayList<Double> peak= new ArrayList<Double>();
	public ArrayList<Double> peak2= new ArrayList<Double>();

	private boolean isRecording = false;
	private static final int RECORDER_SAMPLERATE = 44100;
	private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
	private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

	double[] max=new double[]{0,0,0,0,0};
	int[] pos=new int[]{0,0,0,0,0};
	public static int minF, maxF;
	public static String songPath;
	static SharedPreferences spf;

	int checksPerSecond =0;
	public static boolean appOpen = false;
	public static boolean isPlaying= false;

	Notification notification ;
	NotificationManager notifier;
	ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
	ExecutorService soundListener = Executors.newSingleThreadExecutor();
	static LocalBroadcastManager broadcaster;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		spf=getSharedPreferences("myprfs", Context.MODE_PRIVATE);
		setServiceFrequencies();
		setSong();
		Log.i("LOG"," Creating Service"+minF+" "+maxF);

		bufferSize = AudioRecord.getMinBufferSize
				(RECORDER_SAMPLERATE,RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING)*3;
		initRecorder();

		Intent notificationIntent = new Intent(this, MainActivity.class);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		notificationIntent.setAction(Intent.ACTION_MAIN);
		notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		PendingIntent pIntent = PendingIntent.getActivity(this, 0,
				notificationIntent, 0);

		notification = new Notification.Builder(this)
		.setSmallIcon(R.drawable.whistle_notification)
		.setContentTitle("Yoo-hoo")
		.setOngoing(true).setContentIntent(pIntent)
		.setContentText("is Active!").build();

		notifier = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
		notifier.notify(1, notification);

		mFFT = new FFT(mNumberOfFFTPoints);
		XVal= new Number[mNeededNumOFFFTPoints];

		bufferSize = AudioRecord.getMinBufferSize
				(RECORDER_SAMPLERATE,RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING)*3;

		initRecorder();
		vib = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
		initMusicPlayer();

		broadcaster = LocalBroadcastManager.getInstance(this);

		IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
		filter.addAction(Intent.ACTION_SCREEN_ON);
		registerReceiver(ScreenReceiver, filter);
	}

	public static void setServiceFrequencies(){
		minF=spf.getInt("sRange", 40);
		maxF=minF+10;
	}

	private final BroadcastReceiver ScreenReceiver = new BroadcastReceiver(){

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)||intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
				if(mPlayer.isPlaying()){
					sendMusic("0");
				}
			}
		}};

		public static void setSong() {
			songPath=spf.getString("filePath", null);
		}

		public static void initMusicPlayer(){
			vib.cancel();
			if(mPlayer!= null){
				mPlayer.stop();
			}
			try{
				mPlayer=new MediaPlayer();
				mPlayer.setDataSource(songPath);
				mPlayer.prepare();
				mPlayer.setOnCompletionListener(new OnCompletionListener(){
					@Override
					public void onCompletion(MediaPlayer mp) {
						Log.i("LOG"," Song ended");
						sendMusic("0");//Notifies that musicActive = true because song finished user could stop it
					}
				});

			}catch (Exception e) {
			}
		}

		public static void initRecorder(){
			if(recorder!=null){
				if(recorder.getState()==AudioRecord.STATE_INITIALIZED){
					recorder.stop();
				}
			}
			recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
					RECORDER_SAMPLERATE, RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING, bufferSize);
		}

		Runnable endData = new Runnable() {
			public void run() {
				stopRecording();
			}
		};

		Runnable getData = new Runnable() {
			public void run() {
				Log.i("LOG"," In SavingData  ");
				checksPerSecond=0;
				savingData();
			}
		};

		public void startRecording(){
			recorder.startRecording();
			isRecording = true;

			//worker.schedule(endData, 300, TimeUnit.MILLISECONDS);
			try {
				soundListener.submit(getData).get( 1000, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			} catch (TimeoutException e) {
				//e.printStackTrace();
			}
			worker.execute(endData);
		}

		public void stopRecording(){
			isRecording = false;
			recorder.stop();
			//Log.i("LOG",minF+" "+maxF+" "+checksPerSecond+" Max Array: "+Arrays.toString(max)+" , pos Array: "+Arrays.toString(pos));//Fmax = mPeakPos * RECORDER_SAMPLERATE / mNumberOfFFTPoints;
			if(appOpen)
				MainActivity.xValUpdate(XVal);
			whistleCheck();
		}
		
		public void whistleCheck(){
			if(pos[3] >= minF && pos[3] <= maxF && max[3]>=2){
				double MaxPeak1=peak.get(peak.size()-2),MaxPeak2=peak2.get(peak2.size()-1);
				int minDiff=pos[3]-5;
				Log.i("LOG",minF+" "+maxF+" "+checksPerSecond+" Max Array: "+Arrays.toString(max)+" , pos Array: "+Arrays.toString(pos)+" Peak1 "+MaxPeak1+" Peak2 "+MaxPeak2);//Fmax = mPeakPos * RECORDER_SAMPLERATE / mNumberOfFFTPoints;
				if(pos[0]>=minDiff && MaxPeak1<0.3*max[3] && MaxPeak2<0.3*max[3])
					playMusic();
				else
					startRecording();
			}else
				startRecording();
		}

		public void playMusic(){// function to play music and notify MainActivity whether app is closed or in background or open
			isPlaying=true;
			mPlayer.start();
			vib.vibrate(pattern,0);//Log.i("LOG"," appOPen = "+appOpen);

			if(appOpen)
				sendMusic("1");//Notifies that musicActive is now true when app is already open
			else 
			{
				Intent dialogIntent = new Intent(this, MainActivity.class);
				dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				dialogIntent.setAction(Intent.ACTION_MAIN);
				dialogIntent.addCategory(Intent.CATEGORY_LAUNCHER);
				startActivity(dialogIntent);
			}
			MainActivity.xValUpdate(XVal);
		}

		public void savingData(){
			byte data[] = new byte[bufferSize];

			int read = 0;                
			while(isRecording){
				read = recorder.read(data, 0, bufferSize);
				if(read > 0){//if(checksPerSecond <1){//if(read > 0){//
					checksPerSecond++;
					calculateFFT(data); 
				}
			}
		}

		public double[] calculateFFT(byte[] signal)
		{           
			double temp;
			Complex[] y;
			Complex[] complexSignal = new Complex[mNumberOfFFTPoints];
			double[] absSignal = new double[64];

			for(int i = 0; i < mNumberOfFFTPoints; i++){
				temp = (double)((signal[2*i] & 0xFF) | (signal[2*i+1] << 8)) / 32768.0F;
				complexSignal[i] = new Complex(temp,0.0);
			}

			y = mFFT.fft(complexSignal); // Using FFT class

			for(int i=0;i<5;i++){
				max[i]=0;pos[i]=0;
			}
			peak.clear();
			peak2.clear();
			boolean spotted = false;
			for(int i = 0; i <(mNeededNumOFFFTPoints); i++)
			{
				absSignal[i] = Math.sqrt(Math.pow(y[i].re(), 2) + Math.pow(y[i].im(), 2));
				XVal[i]= (Number)absSignal[i];
				if(absSignal[i] >= max[3])
				{ 
					spotted=true;
					max[0] = max[1];pos[0]=pos[1];
					max[1] = max[2];pos[1]=pos[2];
					max[2] = max[3];pos[2]=pos[3];
					max[3] = absSignal[i];pos[3]=i;
				}else if(spotted){
					peak.add(max[3]);
					spotted=false;
				}
			}
			spotted=false;
			if(pos[3] >= minF && pos[3] <= maxF){
				for(int j= mNeededNumOFFFTPoints-1;j>pos[3]; j--){
					if(absSignal[j] >= max[4])
					{
						spotted=true;
						max[4] = absSignal[j];pos[4]=j;
					} else if(spotted){
						peak2.add(max[4]);
						spotted=false;
					}
				}
			}
			return absSignal;
		}

		public static void sendMusic(String message) {
			Intent intent = new Intent("whistle");
			if(message != null)
				intent.putExtra("blah", message);
			broadcaster.sendBroadcast(intent);
		}

		public void clearBuffer(){//used to empty off the buffer that may have a secondary activation signal
			initRecorder();
			byte[] data = new byte[bufferSize];
			recorder.startRecording();
			int r=0,bClearCount=0;
			while(bClearCount <1){
				r = recorder.read(data,0,bufferSize);
				if(r>0)bClearCount++;
			}
			initRecorder();
		}

		@Override
		public int onStartCommand(Intent intent, int flags, int startId) {
			Log.i("LOG","Starting Service "+minF+" "+maxF);
			for(int i=0;i<5;i++){
				max[i]=0;pos[i]=0;
			}
			clearBuffer();
			startRecording();
			return super.onStartCommand(intent, flags, startId);
		}


		@Override
		public void onDestroy() {
			Log.i("LOG"," Stopping service  ");
			notifier.cancel(1);

			isRecording = false;
			recorder.stop();
			recorder.release();
			if(appOpen)
				MainActivity.xValUpdate(MainActivity.XValue);
			
			unregisterReceiver(ScreenReceiver);
			initMusicPlayer();
			super.onDestroy();
		}


}

//Arrays.sort(pos);
//if(pos[0]==pos[3]-3 && pos[1]==pos[3]-2 && pos[2]==pos[3]-1){
//	playMusic();
//}
//else
//	startRecording();
//if(mPeakPos >= 17){
//Dlist.add(mMaxFFTSample);
//Dlist.add((double) mPeakPos);
//}

//	ArrayList<Double> Dlist= new ArrayList<Double>();
//Log.i("LOG"," result "+ Dlist.toString());
//
//double mag_dB = 10 * Math.log10(Math.abs(mMaxFFTSample));


//hasFocus=MainActivity.inFocus;
//if(!hasFocus || isActivityDead()){
//	MainActivity.musicActive=true;
//	Intent dialogIntent = new Intent(this, MainActivity.class);
//	dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//	dialogIntent.setAction(Intent.ACTION_MAIN);
//	dialogIntent.addCategory(Intent.CATEGORY_LAUNCHER);
//	startActivity(dialogIntent);
//	sendMusic("1");
//	MainActivity.musicActive=true;
//}else
//	sendMusic("1");//Notifies that musicActive is now true when app is already open


//public boolean isActivityDead() {
//	ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
//	List<ActivityManager.RunningTaskInfo> runningTaskInfo = manager.getRunningTasks(1); 
//	ComponentName componentInfo = runningTaskInfo.get(0).topActivity;
//	return !componentInfo.getPackageName().equals("com.yoo_hoo");
//
//}

//public boolean isActivityAlive() {
//ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE); 
//for (RunningTaskInfo task : manager.getRunningTasks(Integer.MAX_VALUE)) {
//	if ("com.yoo_hoo".equals(task.topActivity.getPackageName())) {
//		return true;
//	}
//}
//return false;
//}

//max[0]=max[1]=max[2]=max[3]=max[4]=max[5]=max[6]=0;
//pos[0]=pos[1]=pos[2]=pos[3]=pos[4]=pos[5]=pos[6]=0;

//public double[] calculateFFT(byte[] signal)
//{           
//	double temp;
//	Complex[] y;
//	Complex[] complexSignal = new Complex[mNumberOfFFTPoints];
//	double[] absSignal = new double[68];
//
//	for(int i = 0; i < mNumberOfFFTPoints; i++){
//		temp = (double)((signal[2*i] & 0xFF) | (signal[2*i+1] << 8)) / 32768.0F;
//		complexSignal[i] = new Complex(temp,0.0);
//	}
//
//	y = mFFT.fft(complexSignal); // Using FFT class
//
//	for(int i=0;i<7;i++){
//		max[i]=0;pos[i]=0;
//	}
//	for(int i = 0; i <(mNeededNumOFFFTPoints); i++)
//	{
//		absSignal[i] = Math.sqrt(Math.pow(y[i].re(), 2) + Math.pow(y[i].im(), 2));
//		XVal[i]= (Number)absSignal[i];
//		if(absSignal[i] > max[3])
//		{
//			max[0] = max[1];pos[0]=pos[1];
//			max[1] = max[2];pos[1]=pos[2];
//			max[2] = max[3];pos[2]=pos[3];
//			max[3] = absSignal[i];pos[3]=i;
//		} 
//	}
//	for(int i = mNeededNumOFFFTPoints; i <68; i++)
//	{
//		absSignal[i] = Math.sqrt(Math.pow(y[i].re(), 2) + Math.pow(y[i].im(), 2));
//		XVal[i]= (Number)absSignal[i];
//	}
//	for(int j= pos[3]+4;j>pos[3]; j--){
//		if(absSignal[j] > max[4])
//		{
//			max[6] = max[5];pos[6]=pos[5];
//			max[5] = max[4];pos[5]=pos[4];
//			max[4] = absSignal[j];pos[4]=j;
//		} 
//	}
//	return absSignal;
//}

//public void whistleCheckold(){
//if(pos[3] >= minF && pos[3] <= maxF){
//	Log.i("LOG",minF+" "+maxF+" "+checksPerSecond+" Max Array: "+Arrays.toString(max)+" , pos Array: "+Arrays.toString(pos));//Fmax = mPeakPos * RECORDER_SAMPLERATE / mNumberOfFFTPoints;
//	int minDiff=pos[3]-4, maxDiff=pos[3]+4;
//	if(pos[0]>=minDiff && pos[6] <= maxDiff){
//		playMusic();
//	}
//	else
//		startRecording();
//}else
//	startRecording();
//}

//if(peak2.isEmpty()){
//	Log.i("LOG",minF+" "+maxF+" "+checksPerSecond+" Max Array: "+Arrays.toString(max)+" , pos Array: "+Arrays.toString(pos)+" Peak1 "+MaxPeak1);//Fmax = mPeakPos * RECORDER_SAMPLERATE / mNumberOfFFTPoints;
//	if(pos[0]>=minDiff && MaxPeak1<0.3*max[3])
//		playMusic();
//	else
//		startRecording();
//
//}else{
//	MaxPeak2=peak2.get(peak2.size()-1);