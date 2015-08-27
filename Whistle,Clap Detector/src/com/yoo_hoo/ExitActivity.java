package com.yoo_hoo;
/*************
 * Author: Raj Frederick Paul
 * 
 *Exit Activity
 *A slightly modified activity gotten from stackoverflow.com. Used to close the app but the service is restarted which is essential.
 *
 *
 */
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;


	public class ExitActivity extends Activity
	{
	    @Override protected void onCreate(Bundle savedInstanceState)
	    {
	        super.onCreate(savedInstanceState);

	        if(android.os.Build.VERSION.SDK_INT >= 21)
	        {
	            finishAndRemoveTask();
	        }
	        else
	        {
	            finish();
	        }
	        android.os.Process.killProcess(android.os.Process.myPid());
	    }

	    public static void exitApplication(Context context)
	    {
	    	
	        Intent intent = new Intent(context, ExitActivity.class);

	        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS );

	        context.startActivity(intent);
	    }
	}

	