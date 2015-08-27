package com.yoo_hoo;
/*************
 * Author: Raj Frederick Paul
 * 
 *OneService
 *This service is used to record every halfsecond and check audio data buffer 
 *using a Fast fourier transform to check if the waveform matches that of a whistle in the frequency range 
 *specified by the user. Or the amplitude greater than the ambient sound.
 *
 *
 *
 */
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

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
import android.hardware.display.DisplayManager;
import android.media.AudioRecord.OnRecordPositionUpdateListener;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Display;
import android.media.AudioFormat;
import android.media.AudioRecord;

public class OneService extends Service{

	static MediaPlayer mPlayer = null;// For mp3 file
	long[] pattern = {0, 300, 1000};
	public int poss;
	public static Vibrator vib;
	static AudioRecord recorder; //For whistle and claps
	int bufferSize = 10752;
	FFT mFFT;

	final int mNumberOfFFTPoints =1024;//2048;
	final int mNeededNumOFFFTPointsW= 65;
	final int mNeededNumOFFFTPointsC= 130;
	public static Number[] XValW=null,XValC= null;
	public ArrayList<Double> peaks= new ArrayList<Double>();
	public ArrayList<Double> maxPeaks= new ArrayList<Double>();
	public ArrayList<Double> minAfterMaxPeaks= new ArrayList<Double>();

	public ArrayList<Integer> posOfPeaks= new ArrayList<Integer>();

	private static boolean isRecording = false;
	private static boolean isPrinting;
	private final int RECORDER_SAMPLERATE = 22050;//44100;//11025;
	private final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
	private final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

	double[] max=new double[]{0,0,0,0,0};
	int[] pos=new int[]{0,0,0,0,0};
	public static int minF=23, maxF=63;
	public long quietCount;
	public static String songPath;
	static SharedPreferences spf;
	static SharedPreferences.Editor spe;

	int checksPerSecond =0;
	public static boolean appOpen = false;
	public static boolean serviceCreated = false;
	public static boolean isPlaying= false;

	static Notification notification ;
	static PendingIntent pIntent; 
	static NotificationManager notifier;
	static OneService one;
	ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
	static LocalBroadcastManager broadcaster;
	static boolean songEnded=false;
	static Intent openIntent;
	int targetCounter = -1;
	public static double ampAvgCur,ampAvgPrev,ampAvgOld=0,ampAvgTarget=500,avgToMaxRatio=0.15;
	long s0,s1,s2,s3;
	byte data[];
	static boolean WTOrCF;
	static boolean Mode;
	public boolean screenOn;
	static boolean mActMusicPlaying=false;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		spf=getSharedPreferences("myprfs", Context.MODE_PRIVATE);
		spe=spf.edit();
		WTOrCF = spf.getBoolean("WTOrCF", true);
		Mode = spf.getBoolean("Mode", false);
		setSong();
		screenOn=spf.getBoolean("WTOrCF", true);

		if(Mode){
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH){
				DisplayManager dm = (DisplayManager) this.getSystemService(Context.DISPLAY_SERVICE);
				screenOn=true;
				for (Display display : dm.getDisplays()) {
					if (display.getState() == Display.STATE_OFF) {
						screenOn = false;
					}
				}
			}else{
				PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
				if (powerManager.isScreenOn())
				{ screenOn=true; }
				else{ screenOn=false;}
			}
		}
		spe.putBoolean("screenOn", screenOn);
		spe.commit();Log.i("LOG","Screen now "+screenOn);
		Log.i("LOG"," Creating Service");

		Intent notificationIntent = new Intent(this, MainActivity.class);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		notificationIntent.setAction(Intent.ACTION_MAIN);
		notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		pIntent = PendingIntent.getActivity(this, 0,
				notificationIntent, 0);

		one=this;
		setNotification(one);

		notifier = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
		notifier.cancel(1);

		mFFT = new FFT(mNumberOfFFTPoints);
		XValC= new Number[mNeededNumOFFFTPointsC];
		XValW= new Number[mNeededNumOFFFTPointsW];

		bufferSize = AudioRecord.getMinBufferSize
				(RECORDER_SAMPLERATE,RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING)*3;
		data = new byte[bufferSize];

		initRecorder();

		vib = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
		mPlayer=new MediaPlayer();
		initMusicPlayer();

		broadcaster = LocalBroadcastManager.getInstance(this);

		IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
		filter.addAction(Intent.ACTION_SCREEN_ON);
		registerReceiver(ScreenReceiver, filter);

		openIntent = new Intent(this, MainActivity.class);
		openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		openIntent.setAction(Intent.ACTION_MAIN);
		openIntent.addCategory(Intent.CATEGORY_LAUNCHER);

		mPlayer.setOnCompletionListener(new OnCompletionListener(){
			@Override
			public void onCompletion(MediaPlayer mp) {
				Log.i("LOG"," Song ended");
				if(!appOpen){
					songEnded=true;
					startActivity(openIntent);
				}
				else 
					sendMusic("0");//Notifies that musicActive = true because song finished user could stop it
			}
		});
		serviceCreated=true;

		if(Mode && !appOpen){
			Log.i("LOG","Manually Starting StartCommand");
			startService(new Intent(getApplicationContext(), OneService.class));
		}
	}

	private final BroadcastReceiver ScreenReceiver = new BroadcastReceiver(){
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_ON) || intent.getAction().equals(Intent.ACTION_ANSWER)) {
				screenOn=true;
				Log.i("LOG","Screen now "+screenOn);
				spe.putBoolean("screenOn", screenOn);
				spe.commit();
				if(mPlayer.isPlaying()){
					sendMusic("0");
				}
				if(Mode){
					isRecording=false;
					recorder.stop();
					recorder.release();//must be released so it doesn't interfere with other apps
				}
			}

			if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
				screenOn=false;Log.i("LOG","Screen now "+screenOn);
				spe.putBoolean("screenOn", screenOn);
				spe.commit();
				if(Mode){
					if(mActMusicPlaying)
						MainActivity.initMusicPlayer();
					notifier.notify(1, notification);quietCount=4;
					Log.i("LOG","Flag Screen Off Starting Recording");
					initRecorder();
					recorder.startRecording();
					newStartRecording();
				}
			}
		}};

		public void initRecorder(){
			recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
					RECORDER_SAMPLERATE, RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING, bufferSize);

			recorder.setPositionNotificationPeriod((int) (RECORDER_SAMPLERATE*0.45));
			recorder.setRecordPositionUpdateListener(new OnRecordPositionUpdateListener()
			{
				@Override
				public void onMarkerReached(AudioRecord arg0) {
				}
				@Override
				public void onPeriodicNotification(AudioRecord recorder) {// Log.i("LOG1"," Triggered");
					isRecording = false;
					s1=System.currentTimeMillis();
					if((s1-s0)>10){
						if(WTOrCF){
							calculateFFTWhistle(data);
							checkRecForWhistle();
						}
						else{
							calculateFFTClap(data);
							checkRecForClap();
						}
					}
					else
						newStartRecording();
				}
			});
		}

		public static void setSong() {
			Log.i("LOG",songPath +" SongPath + spf "+spf);
			songPath=spf.getString("filePath", "");
		}

		public static int notificationImg(){
			if(WTOrCF)
				return R.drawable.whistle_notification;
			else
				return R.drawable.claps_not;
		}

		public static String notificationString(){
			if(Mode)
				if(WTOrCF)
					return("will listen for whistles when in sleep mode.");
				else
					return("will listen for claps when in sleep mode.");
			else
				if(WTOrCF)
					return("is Active now and listening for Whistles!");
				else
					return("is Active now and listening for Claps!");

		}

		public static void initMusicPlayer(){
			vib.cancel();
			if(mPlayer.isPlaying())
				mPlayer.stop();
			mPlayer.reset();
			try {
				mPlayer.setDataSource(songPath);
				mPlayer.prepare();
			} catch (Exception e) {
				Log.i("LOG"," I died");
				e.printStackTrace();
			}
		}

		public static void songChange(){
			setSong();
			initMusicPlayer();
		}

		Runnable startRecord = new Runnable() {
			public void run() {
				while(isRecording){
					recorder.read(data, 0, bufferSize);
				}
			}
		};

		public void newStartRecording(){
			ampAvgPrev=ampAvgCur;
			isRecording = true;
			s0=System.currentTimeMillis();
			worker.execute(startRecord);
		}


		public void checkRecForClap(){
			s2=System.currentTimeMillis();

			if(appOpen && poss<1 && isPrinting)
				MainActivity.xValUpdate(XValC,ampAvgCur,ampAvgTarget);

			if(poss>=1){
				if((ampAvgCur <(ampAvgOld*(1.7)))){//first checks if it was a burst and if so checks for clap parameters (ampAvgCur/ampAvgPrev)<0.12&& 
					if((poss == 1 && clapCheck(1)>0) || poss==2){
						isPrinting=false;
						recorder.stop();
						playMusic();
						return;
					}
					poss=0;
				}else if(poss==1 && clapCheck(2)>0 ){//mercy check ampAvgCur>ampAvgTarget  && ampAvgCur>(ampAvgOld*(1.7))
					poss=2;
				}else
					poss=0;
			}

			if(poss<1){
				if(ampAvgCur>ampAvgTarget && (ampAvgCur/max[3])>=avgToMaxRatio  && quietCount>3 && (pos[3]>15||(pos[3]==3))){
					Log.i("LOG","Cur "+new DecimalFormat("##.####").format(ampAvgCur)+" Target "+new DecimalFormat("##.####").format(ampAvgTarget)+" Ratio "+new DecimalFormat("##.##").format(ampAvgCur/max[3])+" poss"+poss+" "+pos[3]+" max "+new DecimalFormat("##.##").format(max[3])+ " "+quietCount);
					poss=1;quietCount=0;
				}else {
					targetCounter=(++targetCounter)%5;
					if(targetCounter==0){
						if(ampAvgTarget>(ampAvgCur*5))// if the target is much higher than the new cur,we take the average to smoothen the sudden drop in cur
							ampAvgOld=ampAvgCur; 
						else
							ampAvgOld=(ampAvgOld+ampAvgCur)/2; 	
					}else if(ampAvgCur>ampAvgOld){
						ampAvgOld=ampAvgCur;
					}
					ampAvgTarget=ampAvgOld+(ampAvgOld*2.5);
					poss=0;if(quietCount<4)quietCount++;//quietCount==4 means 2 sec of no claps atleast
				}
			}
			s3=System.currentTimeMillis();
			//Log.i("LOG","Trecording "+(s1-s0)+" TFFT "+ (s2-s1)+" Tcalculaton "+ (s3-s2));
			newStartRecording(); 
		}

		public int clapCheck(int j){//Replace with your conditions for clap TODO
			if(j>1)	return 1;
			else 
				return 0;
		}

		public void checkRecForWhistle(){
			if(appOpen && isPrinting)
				MainActivity.xValUpdate(XValW);
			//Log.i("LOG"," Pos array "+Arrays.toString(pos));
			if(pos[3] >= minF && pos[3] <= maxF && max[3]>=2 && ((max[3]<15 && ampAvgPrev<0.2)||max[3]>15 )&& ampAvgCur>ampAvgPrev && checkWhistle()){//in Frequency Range ,cant be super soft, was quiet earlier
				isPrinting=false;
				recorder.stop();
				playMusic();
				return;
			}
			else
				newStartRecording();
		}

		public boolean checkWhistle(){//Replace with your conditions for Whistle TODO
			return false;
		}

		public void playMusic(){// function to play music and notify MainActivity whether app is closed or in background or open
			Log.i("LOG","playing music & "+appOpen );
			isPlaying=true;poss=0;
			mPlayer.start();
			vib.vibrate(pattern,0);//Log.i("LOG"," appOPen = "+appOpen);

			if(appOpen)
				sendMusic("1");//Notifies that musicActive is now true when app is already open
			else{
				startActivity(openIntent);
			}
		}

		public double[] calculateFFTClap(byte[] signal)
		{           
			double temp;
			Complex[] y;
			Complex[] complexSignal = new Complex[mNumberOfFFTPoints];
			double[] absSignal = new double[mNeededNumOFFFTPointsC];

			for(int i = 0; i < mNumberOfFFTPoints; i++){
				temp = (double)((signal[2*i] & 0xFF) | (signal[2*i+1] << 8)) / 32768.0F;
				complexSignal[i] = new Complex(temp,0.0);
			}

			y = mFFT.fft(complexSignal); // Using FFT class

			if(poss<1){
				peaks.clear();
				posOfPeaks.clear();
				for(int i=0;i<5;i++){
					max[i]=0;pos[i]=0;
				}
			}
			boolean spotted = false;
			double sum=0,pMax=0;
			int peakPosition =0;
			for(int i = 0; i <(mNeededNumOFFFTPointsC); i++)
			{
				absSignal[i] = Math.sqrt(Math.pow(y[i].re(), 2) + Math.pow(y[i].im(), 2));
				sum+=absSignal[i];
				if(poss<1){
					XValC[i]= (Number)absSignal[i];
					if(absSignal[i] >= max[3])
					{ 
						max[0] = max[1];pos[0]=pos[1];
						max[1] = max[2];pos[1]=pos[2];
						max[2] = max[3];pos[2]=pos[3];
						max[3] = absSignal[i];pos[3]=i;
					}
					if(absSignal[i] >= pMax)
					{ 
						spotted=true;
						pMax = absSignal[i];peakPosition=i;
					}else if(spotted){
						peaks.add(pMax);posOfPeaks.add(peakPosition);
						spotted=false;pMax=0;
					}
				}
			}
			ampAvgCur=sum/mNeededNumOFFFTPointsC;
			return absSignal;
		}

		public double[] calculateFFTWhistle(byte[] signal)
		{           
			double temp;
			Complex[] y;
			Complex[] complexSignal = new Complex[mNumberOfFFTPoints];
			double[] absSignal = new double[mNeededNumOFFFTPointsW];

			for(int i = 0; i < mNumberOfFFTPoints; i++){
				temp = (double)((signal[2*i] & 0xFF) | (signal[2*i+1] << 8)) / 32768.0F;
				complexSignal[i] = new Complex(temp,0.0);
			}

			y = mFFT.fft(complexSignal); // Using FFT class

			peaks.clear();
			posOfPeaks.clear();
			maxPeaks.clear();
			minAfterMaxPeaks.clear();
			for(int i=0;i<5;i++){
				max[i]=0;pos[i]=0;
			}

			boolean spotted = false;
			double sum=0,pMax=0,Max=0;
			int peakPosition =0;
			for(int i = 0; i <(mNeededNumOFFFTPointsW); i++)
			{
				absSignal[i] = Math.sqrt(Math.pow(y[i].re(), 2) + Math.pow(y[i].im(), 2));
				sum+=absSignal[i];
				XValW[i]= (Number)absSignal[i];
				if(absSignal[i] >= max[3])
				{ 
					max[0] = max[1];pos[0]=pos[1];
					max[1] = max[2];pos[1]=pos[2];
					max[2] = max[3];pos[2]=pos[3];
					max[3] = absSignal[i];pos[3]=i;
				}
				//				if(i>7)
				if(absSignal[i] >= pMax)
				{ 
					spotted=true;
					pMax = absSignal[i];peakPosition=i;
				}else if(spotted){
					peaks.add(pMax);posOfPeaks.add(peakPosition);

					if(pMax>=Max){
						Max=pMax;
						maxPeaks.add(pMax);
					}
					spotted=false;pMax=0;
				}
			}
			ampAvgCur=sum/mNeededNumOFFFTPointsW;
			return absSignal;
		}

		//		public double[] calculateFFTWhistle(byte[] signal)
		//		{           
		//			double temp;
		//			Complex[] y;
		//			Complex[] complexSignal = new Complex[mNumberOfFFTPoints];
		//			double[] absSignal = new double[mNeededNumOFFFTPointsW];
		//
		//			for(int i = 0; i < mNumberOfFFTPoints; i++){
		//				temp = (double)((signal[2*i] & 0xFF) | (signal[2*i+1] << 8)) / 32768.0F;
		//				complexSignal[i] = new Complex(temp,0.0);
		//			}
		//
		//			y = mFFT.fft(complexSignal); // Using FFT class
		//
		//			for(int i=0;i<5;i++){
		//				max[i]=0;pos[i]=0;
		//			}
		//			maxPeaks.clear();
		//			minAfterMaxPeaks.clear();
		//			peaks.clear();
		//			peakAmpR.clear();
		//			boolean spotted = false,spotted1 = false;
		//			double sum=0,pMax=0;
		//			for(int i = 0; i <(mNeededNumOFFFTPointsW); i++)
		//			{
		//				absSignal[i] = Math.sqrt(Math.pow(y[i].re(), 2) + Math.pow(y[i].im(), 2));
		//				XValW[i]= (Number)absSignal[i];
		//				sum+=absSignal[i];
		//				if(absSignal[i] >= max[3])
		//				{ 
		//					spotted=true;
		//					max[0] = max[1];pos[0]=pos[1];
		//					max[1] = max[2];pos[1]=pos[2];
		//					max[2] = max[3];pos[2]=pos[3];
		//					max[3] = absSignal[i];pos[3]=i;
		//				}else if(spotted){
		//					maxPeaks.add(max[3]);
		//					spotted=false;
		//				}
		//				if(i<=maxF+1)
		//					if(absSignal[i] >= pMax)
		//					{ 
		//						spotted1=true;
		//						pMax = absSignal[i];
		//					}else if(spotted1){
		//						peaks.add(pMax);
		//						spotted1=false;pMax=0;
		//					}
		//			}
		//			spotted1=spotted=false;pMax=0;
		//			if(pos[3] >= minF && pos[3] <= maxF){
		//				for(int j= mNeededNumOFFFTPointsW-1;j>pos[3]; j--){
		//					if(absSignal[j] >= max[4])
		//					{
		//						spotted=true;
		//						max[4] = absSignal[j];pos[4]=j;
		//					} else if(spotted){
		//						minAfterMaxPeaks.add(max[4]);
		//						spotted=false;
		//					}
		//
		//					if(absSignal[j] >= pMax)
		//					{ 
		//						spotted1=true;
		//						pMax = absSignal[j];
		//					}else if(spotted1){
		//						peakAmpR.add(pMax);
		//						spotted1=false;pMax=0;
		//					}
		//				}
		//			}
		//			ampAvgCur=sum/mNeededNumOFFFTPointsW;
		//			return absSignal;
		//		}

		public static void sendMusic(String message) {
			Intent intent = new Intent("whistle");
			if(message != null)
				intent.putExtra("blah", message);
			broadcaster.sendBroadcast(intent);
		}

		@Override
		public int onStartCommand(Intent intent, int flags, int startId) {
			for(int i=0;i<5;i++){
				max[i]=0;pos[i]=0;
			}
			isPrinting=true;quietCount=4;poss=0;
			Log.i("LOG","Starting Service,Mode:"+Mode+ " "+screenOn);
			notifier.notify(1, notification);
			if(!Mode){//Regular Mode=false
				recorder.startRecording();
				newStartRecording();
			}else if(!screenOn){//Idle Mode=true and screen is off
				Log.i("LOG","Delayed Screen Off Starting Recording");
				recorder.startRecording();
				newStartRecording();
			}
			return super.onStartCommand(intent, flags, startId);
		}

		public static boolean terminateService(){
			notifier.cancel(1);
			isPrinting=false;isRecording = false;
			recorder.release();
			initMusicPlayer();
			if(appOpen)
				MainActivity.clearUpdate();
			return true;
		}

		@Override
		public void onDestroy() {
			unregisterReceiver(ScreenReceiver);
			Log.i("LOG"," Stopping service  ");
			super.onDestroy();
		}

		public static void setNotification(OneService oneService){
			notification = new Notification.Builder(oneService)
			.setSmallIcon(notificationImg())
			.setContentTitle("Yoo-hoo!")
			.setOngoing(!Mode).setContentIntent(pIntent)
			.setContentText(notificationString()).build();
		}

		public static void changeWTOrCF(boolean wTOrCF2) {
			if(WTOrCF!=wTOrCF2){
				WTOrCF=wTOrCF2;
				notifier.cancel(1);
				setNotification(one);
				notifier.notify(1, notification);
			}

		}
}

//for(int i=0;i<mNeededNumOFFFTPointsC;i++){
//				if(XValC[i].doubleValue()>ampAvgPrev)
//					cPointsOAvg++;
//			}

//public void clearBuffer(){//used to empty off the buffer that may have a secondary activation signal
//	initRecorder();
//	byte[] data = new byte[bufferSize];
//	recorder.startRecording();
//	int r=0,bClearCount=0;
//	while(bClearCount <1){
//		r = recorder.read(data,0,bufferSize);
//		if(r>0)bClearCount++;
//	}
//	initRecorder();ampAvgPrev=0;
//}


//			mAfter=peaks.get(numPeaks-1);
//			for(int i =numPeaks-2;i>indexOfMax;i--){
//				temp=peaks.get(i);
//				if(temp>mAfter){
//					mAfter=temp;numMaxPeaksR++;
//				}
//			}

//		public void whistleCheck(){
//			//if(pos[3] >= minF && pos[3] <= maxF && max[3]>=2){//Amplitude should higher than 2,attempt at false positives
//			if(ampAvgCur>ampAvgPrev){
//				double MaxPeak1=maxPeaks.get(maxPeaks.size()-2),MaxPeak2=minAfterMaxPeaks.get(minAfterMaxPeaks.size()-1);
//				int minDiff=pos[3]-5;
//				Log.i("LOG",ampAvgCur+" "+minF+" "+checksPerSecond+" Max Array: "+Arrays.toString(max)+" , pos Array: "+Arrays.toString(pos)+" Peak1 "+MaxPeak1+" Peak2 "+MaxPeak2);//Fmax = mPeakPos * RECORDER_SAMPLERATE / mNumberOfFFTPoints;
//				if(pos[0]>=minDiff && MaxPeak1<0.3*max[3] && MaxPeak2<0.3*max[3]){
//					recorder.stop();
//					playMusic();
//					return;
//				}
//			}
//			newStartRecording();
//		}


//		public void whistleCheck1(){
//			double mPeakLeft=peaks.get(peaks.indexOf(max[3])-1),mPeakRight=peakAmpR.get(peakAmpR.size()-1);
//			int minDiff=pos[3]-5;
//			if((max[3]<15 && ampAvgPrev<0.2) || (max[3]>=15 && ampAvgCur>ampAvgPrev) ){//whether ambient,low volume whistle or loud
//
//				Log.i("LOG",ampAvgCur+" "+minF+" "+checksPerSecond+" Max "+max[3]+" at pos Array: "+Arrays.toString(pos)+" PeakL "+mPeakLeft+" "+maxPeaks.get(maxPeaks.size()-2)+" PeakR "+mPeakRight+" "+minAfterMaxPeaks.get(minAfterMaxPeaks.size()-1));//Fmax = mPeakPos * RECORDER_SAMPLERATE / mNumberOfFFTPoints;
//				if(pos[0]>=minDiff  && mPeakLeft<0.3*max[3] && mPeakRight<0.3*max[3] && mPeakLeft==maxPeaks.get(maxPeaks.size()-2) && mPeakRight==minAfterMaxPeaks.get(minAfterMaxPeaks.size()-1)){
//					recorder.stop();
//					playMusic();
//					return;
//				}
//			}
//			newStartRecording();
//		}