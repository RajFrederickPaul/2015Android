package com.yoo_hoo;
/*************
 * Author: Raj Frederick Paul
 * 
 *Demo Activity
 *The tutorial activity. This activity is transparent giving the user information in depth of the view underneath,
 * its function and description.
 *
 *
 */
import com.yoo_hoo.R;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.Display;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;

public class DemoActivity extends Activity {

	ImageView setFile;
	TextView explanation;
	LayoutParams params;
	int currentlyRed;
	int[] location1=new int[4],location2=new int[4],location3=new int[4],location4=new int[4],location5=new int[4];
	int[] curLocation;
	Display display;
	Point size;
	int sHeight;
	public Vibrator vib;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i("LOG","Creating Tutorial ");
		setContentView(R.layout.activity_demo);
		currentlyRed=R.id.imageView1;
		getActionBar().setDisplayHomeAsUpEnabled(true);
		vib = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

		display = getWindowManager().getDefaultDisplay();
		size = new Point();
		display.getSize(size);
		sHeight = size.y;

		explanation=(TextView) findViewById(R.id.textView1);

		location1= setImagePosition(R.id.imageView1,1);
		location2= setImagePosition(R.id.imageView2,2);
		location3= setImagePosition(R.id.imageView3,3);
		location4= setImagePosition(R.id.imageView4,4);
		location5= setImagePosition(R.id.imageView5,5);

		curLocation=location1;
		//Setting  Text view Position
		params = (LayoutParams) explanation.getLayoutParams();
		params.topMargin=location3[0]+location3[2]-20;
		params.leftMargin=location3[1];
		params.width = location5[3];
		params.height = location5[0]-location3[0]-location3[2]+10;
		explanation.setLayoutParams(params);

		RelativeLayout b4=(RelativeLayout) findViewById(R.id.blank4);
		RelativeLayout b5=(RelativeLayout) findViewById(R.id.blank5);
		RelativeLayout b7=(RelativeLayout) findViewById(R.id.blank7);
		//RelativeLayout b8=(RelativeLayout) findViewById(R.id.blank8);

		params = (LayoutParams) b5.getLayoutParams();
		params.topMargin=location1[0]+location1[2]-20;
		params.leftMargin=location1[1];
		params.width = location5[3];
		params.height = location4[0]-location1[0]-location1[2]+10;
		b5.setLayoutParams(params);

		params = (LayoutParams) b7.getLayoutParams();
		params.topMargin=location4[0]-10;
		params.leftMargin=location3[1]+location3[3];
		params.width = location4[1]-location3[3]-location3[1];
		params.height = location3[2]+location3[0]-location4[0]-10;
		b7.setLayoutParams(params);

		params = (LayoutParams) b4.getLayoutParams();
		params.topMargin=location5[2]+location5[0]-20;
		params.leftMargin=location5[1];
		params.width = location5[3];
		params.height = sHeight-(location5[2]+location5[0]-20);
		b4.setLayoutParams(params);
	}

	public int[] setImagePosition(int id,int i){
		setFile=(ImageView) findViewById(id);
		int[] location= getIntent().getIntArrayExtra(""+i+"");
		scaleImage(location[3],location[2]-10, setFile);
		LayoutParams params = (LayoutParams) setFile.getLayoutParams();
		params.topMargin=location[0]-10;
		params.leftMargin=location[1];
		params.width = location[3];
		params.height = location[2]-10;
		setFile.setLayoutParams(params);
		return location;
	}


	public void scaleImage(int width,int height, ImageView view){
		Drawable drawable = view.getDrawable();
		Bitmap b = ((BitmapDrawable) drawable).getBitmap();
		Bitmap bitmap = b.copy(Bitmap.Config.ARGB_8888, true);
		Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
		Drawable result = new BitmapDrawable(getResources(),scaledBitmap);
		view.setImageDrawable(result);
	}

	public void scaleImageRed(int width,int height, ImageView view){
		Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.circle);
		Bitmap bitmap = b.copy(Bitmap.Config.ARGB_8888, true);
		Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
		Drawable result = new BitmapDrawable(getResources(),scaledBitmap);
		view.setImageDrawable(result);
	}

	public void scaleImageBlue(int width,int height, ImageView view){
		Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.circle1);
		Bitmap bitmap = b.copy(Bitmap.Config.ARGB_8888, true);
		Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
		Drawable result = new BitmapDrawable(getResources(),scaledBitmap);
		view.setImageDrawable(result);
	}

	public void changeInfo(View v){
		vib.vibrate(50);
		switch(v.getId()){
		case R.id.imageView1:
			scaleImageBlue(curLocation[3],curLocation[2]-10, (ImageView) findViewById(currentlyRed));
			scaleImageRed(location1[3],location1[2]-10, (ImageView) v);
			currentlyRed=R.id.imageView1;curLocation=location1;
			explanation.setText(R.string.sAudioFile);
			break;
		case R.id.imageView2:
			scaleImageBlue(curLocation[3],curLocation[2]-10, (ImageView) findViewById(currentlyRed));
			scaleImageRed(location2[3],location2[2]-10, (ImageView) v);
			currentlyRed=R.id.imageView2;curLocation=location2;
			explanation.setText(R.string.playFile);
			break;
		case R.id.imageView3:
			scaleImageBlue(curLocation[3],curLocation[2]-10, (ImageView) findViewById(currentlyRed));
			scaleImageRed(location3[3],location3[2]-10, (ImageView) v);
			currentlyRed=R.id.imageView3;curLocation=location3;
			explanation.setText(R.string.sample);
			break;
		case R.id.imageView4:
			scaleImageBlue(curLocation[3],curLocation[2]-10, (ImageView) findViewById(currentlyRed));
			scaleImageRed(location4[3],location4[2]-10, (ImageView) v);
			currentlyRed=R.id.imageView4;curLocation=location4;
			explanation.setText(R.string.sBar);
			break;
		case R.id.imageView5:
			scaleImageBlue(curLocation[3],curLocation[2]-10, (ImageView) findViewById(currentlyRed));
			scaleImageRed(location5[3],location5[2]-10, (ImageView) v);
			currentlyRed=R.id.imageView5;curLocation=location5;
			explanation.setText(R.string.stop);
			break;
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			MainActivity.tutorialOpen=false;
			onBackPressed();//NavUtils.navigateUpFromSameTask(this);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
}
