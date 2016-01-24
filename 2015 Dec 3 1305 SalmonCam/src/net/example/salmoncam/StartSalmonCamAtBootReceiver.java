package net.example.salmoncam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/*********************************************************************************************
 * StartSalmonCamAtBootReceiver- Triggered from AndroidManifest.xml. This does nothing unless
 * bProgramAutoStartFlag is set to TRUE, then application (SalmonCam.class) is started.
 ********************************************************************************************/ 
public class StartSalmonCamAtBootReceiver extends BroadcastReceiver {public static final String PREFERENCES_FILE_NAME = "SalmonCamAppPreferences";
@Override
public void onReceive(Context context, Intent intent) {    	
	SharedPreferences settingsfile= context.getSharedPreferences(PREFERENCES_FILE_NAME,0);
	boolean bProgramAutoStartFlag = settingsfile.getBoolean("key_bProgramAutoStartFlag", false); 		
	//Start App On Boot Start Up
	if (bProgramAutoStartFlag == true){//if true autostart after boot is enabled
		Intent App = new Intent(context, SalmonCam.class);
		App.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(App);
	}else{
		//do nothing
	}
}
}//***********************END STARTSalmonCamATBOOTRECEIVER**************************************
