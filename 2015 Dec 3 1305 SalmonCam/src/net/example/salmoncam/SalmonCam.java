package net.example.salmoncam;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.CamcorderProfile;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/************************************************************************
 * TODO
 * 
 * 0) Why is application crashing after 12 hours or so??? Is this only the Defy+ devices?
 * 
 * 0.5) How about inserting a WAIT- 2 secs between file or frame generation 
 * to allow processor to catch up???
 * 
 * 1) Create caveat emptor screen if no auto startup. This application will 
 * 	  crash on some settings...
 * 2) <DONE> Power save...dim screen after 60 seconds.
 * 3) Add a timeout to the popup keypad
 * 4) Need to make an "Is Recording?" boolean and if so, device restarts in
 *    a recording state.
 * 5) Make auto-start, loop, and running boolean load into settings file 
 *    outside of Update button. Tweaking bRecordingFlag propagated to other 
 *    issues in application...maybe an additional boolean for this purpose,
 *    bRestartSessionIfCrashed = true
 * 6) In the MJPEG frame insert battery level...anything else? Do focus, 
 *    exposure, camcorder profile, image dimensions.
 * 7) Clean up LogFiles....make consistent, are they stored someplace???
 * 
    The Log.e() method is used to log errors.
    The Log.w() method is used to log warnings.
    The Log.i() method is used to log informational messages.
    The Log.d() method is used to log debug messages.
    The Log.v() method is used to log verbose messages.
    The Log.wtf() method is used to log terrible failures that should never happen. ("WTF" stands for "What a Terrible Failure!" of course.)

http://code.tutsplus.com/tutorials/android-essentials-application-logging--mobile-4578
http://stackoverflow.com/questions/1756296/android-writing-logs-to-text-file

 ************************************************************************
 *    Solution for power supply? How long till solar panel will initiate 
 *    restart with video recording on dead battery?
 *    
 *    If we're not writing data to the SDCard, how much power is being used?
 *    Is it possible to stop writing to the card when light is below a threshold, 
 *    check light levels every 10 minutes, then start recording again when light 
 *    levels are above that threshold? Will this keep the battery from dying overnight?
 *    
 *    Test this by running the program on battery, no record and see how long 
 *    till dead battery....
 ************************************************************************/

public class SalmonCam extends Activity implements OnClickListener,OnItemSelectedListener, SurfaceHolder.Callback, Camera.PreviewCallback, SensorEventListener {	
	public static final String PREFERENCES_FILE_NAME = "SalmonCamAppPreferences";
	public static final String LOGTAG = "VIDEOCAPTURE";
	String szBoundaryStart = "\r\n\r\n--myboundary\r\nContent Type: image/jpeg\r\nImage Size: ";
	String szAbsoluteTime = "\r\nAbsTime: ";
	String szJpegQuality = "\r\nJpegQual: ";
	String szFramesPerSecond = "\r\nFPS: ";
	String szCamcorderFrame = "\r\nCamcorderFrame: ";
	String szCamcorderBitrate = "\r\nCamcorderBitrate: ";
	String szBoundaryEnd = "\r\n\r\n";
	String szDate;

	private SurfaceHolder holder;
	private Camera camera;	
	private CamcorderProfile camcorderProfile;

	PowerManager pm;
	WakeLock wakeLock;
	
	private SensorManager senSensorManager;
	SensorManager sensorManager; //...whaaaat?
	private Sensor senLight;
	float fLightLevel = 0;

	//private PowerManager mPowerManager;
	//private PowerManager.WakeLock wakeLock;

	Handler handler;
	Runnable runnableRepeatTimer;

	boolean bRecordingFlag = false;
	boolean bPreviewRunning = false;
	boolean bScreenDimFlag = false;
	boolean bViewRotatedFlag = false;
	boolean bProgramAutoStartFlag, bRepeatFlag;
	boolean bTooDarkFlag = false;

	
	
	
	SeekBar seekbarZoom, seekbarExposure;
	Spinner spinnerCamcorderProfile;
	CheckBox ckbxRepeat, ckbxProgramAutoStart;

	TextView tvFramesPerSecond, tvJpegQuality, tvRepeatInterval, tvZoomDigit, tvExposureDigit, tvRecordingNotrecordingIndicator; 

	int iZoom, iExposure, iMaxZoom, iMaxExposure;
	int iFramesPerSecondX1k, iFps, iJpegQuality, iRepeatInterval, iCamcorderProfileSpinnerPosition; //this is 30fps as default (...mult by 1,000)

	byte[] previewCallbackBuffer;

	String szDateTime, szFps;

	File mjpegFile;
	FileOutputStream fos;
	BufferedOutputStream bos;
	Button btnRotateView, btnStartRecord, btnStopRecord, btnExit, btnChange, btnScreenOff;

	Camera.Parameters parameters;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		//setContentView(R.layout.)
		setContentView(R.layout.main);

		handler = new Handler();
		
		
		senSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
	    senLight = senSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
	    senSensorManager.registerListener(this, senLight , SensorManager.SENSOR_DELAY_FASTEST);
		

		//adds archival values to iFramesPerSecond, iJpegQuality, iRepeatInterval, and 
		//bProgramAutoStartFlag, bRepeatFlag still need to save, grab and load camcorder settings...
		loadsSettingsFromArchivalSettingsFile();

		//Note: Partial wakelock allows screen + keyboard backlight to go off, cpu keeps running.
		pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MyWakelock");

		//**************START SCREEN FIELDS, TEXT, BUTTONS, etc
		tvRecordingNotrecordingIndicator = (TextView) this.findViewById(R.id.textviewIndicatorxml);
		tvRecordingNotrecordingIndicator.setText("STOPPED");
		tvRecordingNotrecordingIndicator.setPadding(10, 10, 10, 10);
		tvRecordingNotrecordingIndicator.setBackgroundColor(0xFFFF0000);
		tvRecordingNotrecordingIndicator.setGravity(Gravity.LEFT);

		btnRotateView = (Button) this.findViewById(R.id.buttonrotateviewxml);
		btnRotateView.setOnClickListener(this);

		btnStartRecord = (Button) this.findViewById(R.id.StartRecordButton);
		btnStartRecord.setOnClickListener(this);

		btnStopRecord = (Button) this.findViewById(R.id.StopRecordButton);
		btnStopRecord.setOnClickListener(this);

		btnChange = (Button) this.findViewById(R.id.UpdateSettingsButton);
		btnChange.setOnClickListener(this);

		tvFramesPerSecond = (TextView) this.findViewById(R.id.textboxframespersecondxml);
		iFps = iFramesPerSecondX1k/1000;
		szFps = Integer.toString(iFps);
		tvFramesPerSecond.setText(szFps);
		tvFramesPerSecond.setClickable(true);		
		tvFramesPerSecond.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				getSupportedPreviewFpsRange();
			}
		});

		tvJpegQuality = (TextView) this.findViewById(R.id.textboxJpegQualityxml);
		String szJpegQuality = Integer.toString(iJpegQuality);
		tvJpegQuality.setText(szJpegQuality);

		camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_480P);//changed from quality_HIGH

		//Camcorder Profile spinner
		spinnerCamcorderProfile = (Spinner) this.findViewById(R.id.spinnerCamcorderProfilexml);
		List<String> list = new ArrayList<String>();
		list.add("QUALITY_1080P");
		list.add("QUALITY_480P");
		list.add("QUALITY_720P");
		list.add("QUALITY_CIF");
		list.add("QUALITY_HIGH");
		list.add("QUALITY_LOW");
		list.add("QUALITY_QCIF");
		list.add("QUALITY_QVGA");
		list.add("QUALITY_TIME_LAPSE_1080P");
		list.add("QUALITY_TIME_LAPSE_480P");
		list.add("QUALITY_TIME_LAPSE_720P");
		list.add("QUALITY_TIME_LAPSE_CIF");
		list.add("QUALITY_TIME_LAPSE_HIGH");
		list.add("QUALITY_TIME_LAPSE_LOW");
		list.add("QUALITY_TIME_LAPSE_QCIF");
		list.add("QUALITY_TIME_LAPSE_QVGA");

		ArrayAdapter<String> dataAdapter = new ArrayAdapter<String> (this, android.R.layout.simple_spinner_item,list);            
		dataAdapter.setDropDownViewResource (android.R.layout.simple_spinner_dropdown_item);          
		spinnerCamcorderProfile.setAdapter(dataAdapter);
		spinnerCamcorderProfile.setSelection(iCamcorderProfileSpinnerPosition);
		spinnerCamcorderProfile.setOnItemSelectedListener(this);



		seekbarZoom = (SeekBar) this.findViewById(R.id.seekbarZoomxml);
		tvZoomDigit = (TextView) this.findViewById(R.id.textboxzoomdigitxml);	
		seekbarZoom.setProgress(iZoom);//sets seekbar to current value
		seekbarZoom.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekbarZoom) {
				// TODO Auto-generated method stub
			}
			@Override
			public void onStartTrackingTouch(SeekBar seekbarZoom) {
				// TODO Auto-generated method stub
			}
			@Override
			public void onProgressChanged(SeekBar seekbarZoom, int progress, boolean fromUser) {
				iZoom = seekbarZoom.getProgress();
				String szZoom = String.valueOf(iZoom);
				tvZoomDigit.setText(szZoom);							
			}
		});

		seekbarExposure = (SeekBar) this.findViewById(R.id.seekbarExposurexml);
		tvExposureDigit = (TextView) this.findViewById(R.id.textboxexposuredigitxml);
		seekbarExposure.setProgress(iExposure);
		seekbarExposure.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekbarExposure) {
				// TODO Auto-generated method stub
			}
			@Override
			public void onStartTrackingTouch(SeekBar seekbarExposure) {
				// TODO Auto-generated method stub
			}
			@Override
			public void onProgressChanged(SeekBar seekbarExposure, int progress, boolean fromUser) {
				iExposure = seekbarExposure.getProgress();
				String szExposure = String.valueOf(iExposure);
				tvExposureDigit.setText(szExposure);							
			}
		});

		tvRepeatInterval = (TextView) this.findViewById(R.id.textboxRepeatIntervalxml);
		int iRepeatIntervalMinutes=(iRepeatInterval/1000)/60;
		String szRepeatInterval = Integer.toString(iRepeatIntervalMinutes);//value needs to be in seconds x 1000
		tvRepeatInterval.setText(szRepeatInterval);

		btnScreenOff = (Button) this.findViewById(R.id.DefaultSettingsButton);
		btnScreenOff.setOnClickListener(this);

		//Checks the ckbx when boolean is true, creates listener for program auto start checkbox
		ckbxProgramAutoStart = (CheckBox)findViewById(R.id.ckbxProgramAutoStartxml);
		ckbxProgramAutoStart.setChecked(bProgramAutoStartFlag);//needs boolean value here!
		addListenerCkbxProgramAutoStart();

		//Checks the ckbx when boolean is true, creates listener for repeat checkbox. Causes the recording session to repeat ad infinitum...
		ckbxRepeat = (CheckBox)findViewById(R.id.ckbxRepeatxml);
		ckbxRepeat.setChecked(bRepeatFlag);//needs boolean value here!
		addListenerCkbxRepeat();

		btnExit = (Button) this.findViewById(R.id.ExitButton);
		btnExit.setOnClickListener(this);
		//**************END SCREEN FIELDS, TEXT, BUTTONS, etc.

		SurfaceView cameraView = (SurfaceView) findViewById(R.id.CameraView);

		holder = cameraView.getHolder();
		holder.addCallback(this);
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		cameraView.setClickable(true);
		cameraView.setOnClickListener(this);

		makeNewTimestamps();
		/************************************************************************************
		 * This is a good place for notification that previous session was interrupted and to
		 * restart recording if recording was previously ongoing. 
		 ************************************************************************************/
		
		isJactivelteAndSdcard(); //checks to see if this is a Samsung 4 w/removeable sdcard.
		
		/*	if(bRecordingFlag == true){
			//startRecording();
			new Handler().postDelayed(new Runnable(){
			    @Override
			    public void run(){
			    	//startRepeatTimer();
			    }
			}, 2000);//waits 1 second as application finishes tasks	

			startRepeatTimer(); //this starts the recording cycle + interval timer
		}*/

	}//end onCreate();
	
	
	

	
	
	
	@Override
	public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
		switch(position){
		case 0: camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
		break;
		case 1: camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
		break;
		case 2: camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
		break;
		case 3: camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_CIF);
		break;
		case 4: camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
		break;
		case 5: camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
		break;
		case 6: camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_QCIF);
		break;
		case 7: camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_QVGA);
		break;
		case 8: camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_1080P);
		break;
		case 9: camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_480P);
		break;
		case 10:camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_720P);
		break;
		case 11:camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_CIF);
		break;
		case 12:camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_HIGH);
		break;
		case 13:camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_LOW);
		break;
		case 14:camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_QCIF);
		break;
		case 15:camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_QVGA);
		break;
		}
	}
	@Override
	public void onNothingSelected(AdapterView<?> parent) {
	}
	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.buttonrotateviewxml:
			Toast.makeText(this, "Light sensor changed: "+fLightLevel, Toast.LENGTH_SHORT).show();
		
			if(bViewRotatedFlag == false){
				camera.setDisplayOrientation(90);
				bViewRotatedFlag = true;
			}else{
				camera.setDisplayOrientation(0);
				bViewRotatedFlag = false;
			}
			vibrate(100);
			break;
		case R.id.StartRecordButton:
			//Confirms that if this is an I9295 running CyanogenMod, then removeable SDCard must be mounted
			boolean bSdCardWritable = checkExtSdCardWritable();
			if (android.os.Build.DEVICE.contains("jactivelte")&&(bSdCardWritable == false)){
				Toast.makeText(this,"SdCard not accessable.", Toast.LENGTH_SHORT).show();
				break;
			}
			
			if (bScreenDimFlag == true){
				turnOnScreen();}
			wakeLock.acquire();//no sleep, no standby while recording...
			startRepeatTimer();
			vibrate(100);
			break;
		case R.id.StopRecordButton:	
			if (bScreenDimFlag == true){
				turnOnScreen();}
			wakeLock.release();//can sleep when not recording...
			stopRepeatTimer();
			vibrate(100);
			break;		
		case R.id.UpdateSettingsButton:
			if (bScreenDimFlag == true){
				turnOnScreen();}
			vibrate(100);	
			updateSettings();//grabs entered values, converts to appropriate formats, loads variables						
			releaseCamera(); //does camera.stopPreview
			setCamera(camera); //
			camera.startPreview();
			hideKeyboard();
			break;

		case R.id.DefaultSettingsButton:
			//how's about making a separate function for below 4 lines???
			SharedPreferences settingsfile= getSharedPreferences(PREFERENCES_FILE_NAME,0);
			Editor myEditor = settingsfile.edit();//Maybe only do a complete reset at "Update Settings"???
			myEditor.clear();
			myEditor.commit();

			loadsSettingsFromArchivalSettingsFile();
			reloadDisplay();

			Toast.makeText(this,"settingsfile cleared...", Toast.LENGTH_SHORT).show();
			break;

		case R.id.ExitButton:
			vibrate(200);
			stopRecording();
			stopRepeatTimer();
			camera.stopPreview();
			populateSettingsFile();
			new Handler().postDelayed(new Runnable(){
				@Override
				public void run(){
					finish();
				}
			}, 1000);//waits 1 second as application finishes tasks		
			break;
		}
	}

	
	
	
	
	//end light sensor specific code
	protected void onPause() {
	    super.onPause();
	    senSensorManager.unregisterListener(this);
	}
	
	
	protected void onResume() {
	    super.onResume();
	    senSensorManager.registerListener(this, senLight, SensorManager.SENSOR_DELAY_FASTEST);
	}
	
	
	@Override
	public void onSensorChanged(SensorEvent event) {
	//	float x=0;//,y=0,z = 0;
	//	 Sensor mySensor = event.sensor;
		 
	//	    if (mySensor.getType() == Sensor.TYPE_LIGHT) {
	//	        x = event.values[0];
		      //  y = event.values[1];
		       // z = event.values[2];
		if(event.sensor.getType() == Sensor.TYPE_LIGHT){
			
			fLightLevel = event.values[0];
			if (fLightLevel > 55){//light above certain level: able to record
				bTooDarkFlag = false;
				//Toast.makeText(this, "It's light! "+fLightLevel, Toast.LENGTH_SHORT).show();
				 
			}else{
				bTooDarkFlag = true;
				//Toast.makeText(this, "Too dark! "+fLightLevel, Toast.LENGTH_SHORT).show(); 	
			}
			//Toast.makeText(this, "Light sensor changed."+event.values[0], Toast.LENGTH_SHORT).show();
		    }	
	}
	
	public void checkLightLevel(SensorEvent event){//this can go all away
		
		float fLight = event.values[0];
		
		
		
	/*	float x=0;//,y=0,z = 0;
			 Sensor mySensor = event.sensor;
			 
			    if (mySensor.getType() == Sensor.TYPE_LIGHT) {
			        x = event.values[0];
			      //  y = event.values[1];
			       // z = event.values[2];
		
			    }
		*/
		Toast.makeText(this, "Level from global float is is "+fLightLevel, Toast.LENGTH_SHORT).show();
		Toast.makeText(this, "Immediate level is "+fLight, Toast.LENGTH_SHORT).show();
	}
	
	 
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	 
	}//end light sensor specific code
	
	
	
	
	
	public void reloadDisplay(){//loads display fields with current ints and strings
		iFps = iFramesPerSecondX1k/1000;
		szFps = Integer.toString(iFps);
		tvFramesPerSecond.setText(szFps);

		String szJpegQuality = Integer.toString(iJpegQuality);
		tvJpegQuality.setText(szJpegQuality);

		spinnerCamcorderProfile.setSelection(iCamcorderProfileSpinnerPosition);

		seekbarZoom.setProgress(iZoom);

		seekbarExposure.setProgress(iExposure);

		int iRepeatIntervalMinutes=(iRepeatInterval/1000)/60;
		String szRepeatInterval = Integer.toString(iRepeatIntervalMinutes);//value needs to be in seconds x 1000
		tvRepeatInterval.setText(szRepeatInterval);

		ckbxProgramAutoStart.setChecked(bProgramAutoStartFlag);

		ckbxRepeat.setChecked(bRepeatFlag);//needs boolean value here!	
	}

	public void releaseCamera()
	{
		camera.stopPreview();
		// camera.release();  //...cause crash
		//camera = null;
	}
	/***************************************************************
	 * Sets the camera to the parameters chosen by the user.
	 ***************************************************************/
	@SuppressWarnings("deprecation")
	public void setCamera(Camera camera){
		Camera.Parameters parameters=camera.getParameters();
		parameters.setPreviewFpsRange(iFramesPerSecondX1k, iFramesPerSecondX1k);//note: This is fps x 1000 (!)
		parameters.setPreviewSize(camcorderProfile.videoFrameWidth, camcorderProfile.videoFrameHeight);
		parameters.setZoom(iZoom);
		parameters.setExposureCompensation(iExposure);	
		camera.setParameters(parameters);
	}
	/********************************************************************************************
	 * REPEAT RECORD SESSION CHECKBOX LISTENER- This creates the checkbox that determines if the
	 * program starts an additional file after the current one completes.
	 ********************************************************************************************/
	public void addListenerCkbxRepeat() {
		ckbxRepeat = (CheckBox) findViewById(R.id.ckbxRepeatxml);
		ckbxRepeat.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (((CheckBox) v).isChecked()) {
					bRepeatFlag = true;//sets boolean flag to true if box checked
					//vibrate(100);
					//sets bRepeatFlag boolean value in settingsfile to true
					SharedPreferences settingsfile= getSharedPreferences(PREFERENCES_FILE_NAME,0);
					Editor myEditor = settingsfile.edit();
					myEditor.putBoolean("key_bRepeatFlag", true);
					myEditor.commit();
				}
			}
		});
	}//********************END REPEAT RECORD SESSION CHECKBOX LISTENER******
	/********************************************************************************************
	 * CHECK BOX LISTENER PROGRAM AUTO START- This causes the application to open immediately
	 * on device boot up. Note: this value is written to settingsfile at THIS event, other setting 
	 * changes are written when [Update settings] button is pressed.
	 ********************************************************************************************/
	public void addListenerCkbxProgramAutoStart() {
		ckbxProgramAutoStart = (CheckBox) findViewById(R.id.ckbxProgramAutoStartxml);
		ckbxProgramAutoStart.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (((CheckBox) v).isChecked()) {  
					bProgramAutoStartFlag = true; //sets boolean flag to true if box checked
					//sets programAutoStart boolean value in settingsfile to true
					SharedPreferences settingsfile= getSharedPreferences(PREFERENCES_FILE_NAME,0);
					Editor myEditor = settingsfile.edit();
					myEditor.putBoolean("key_bProgramAutoStartFlag", true);
					myEditor.commit();
				}
			}
		});
	}//********************END CHECK BOX LISTENER PROGRAM AUTO START******
	/**************************************************************************************
	 * Surface created
	 *************************************************************************************/
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		Log.v(LOGTAG, "surfaceCreated");
		camera = Camera.open();
	}//*********************End Surface created*******************************************

	/**************************************************************************************
	 * Update settings- does all of the "stuff" when you press [Update settings] button.
	 * Loads user entered values in appropriate scaling (eg 30fps to 30000)
	 *************************************************************************************/
	public void updateSettings(){
		//FramesPerSecond, 
		szFps = tvFramesPerSecond.getText().toString();	
		int iFps = Integer.parseInt(szFps);
		iFramesPerSecondX1k = iFps * 1000;

		//Jpeg quality- cant be <20 or >100, checks this and populates field with entered or corrected value.
		String szJpegQuality=tvJpegQuality.getText().toString();
		int iJpegQualityTemp = Integer.parseInt(szJpegQuality);
		if (iJpegQualityTemp < 21){//...can't be less than 21. Sets value to 21 if user enters less than that amt
			iJpegQuality = 21;
		}else if(iJpegQualityTemp > 100){//can't be greater than 100
			iJpegQuality = 100;
		}else{ //quality is between 21 and 100...
			iJpegQuality = iJpegQualityTemp;
		}
		szJpegQuality = Integer.toString(iJpegQuality);
		tvJpegQuality.setText(szJpegQuality);

		//Recording duration.
		String szRepeatInterval=tvRepeatInterval.getText().toString();
		int iRepeatIntervalMinutes = Integer.parseInt(szRepeatInterval);
		iRepeatInterval = iRepeatIntervalMinutes * 1000 *60;	

		iCamcorderProfileSpinnerPosition = spinnerCamcorderProfile.getSelectedItemPosition();

		iZoom = seekbarZoom.getProgress();
		iExposure = seekbarExposure.getProgress();
		return;
	}//**********************END UPDATE SETTINGS******************************************
	/************************************************************************************
	 * Populate settings ints and strings from settings file-
	 * boolean bProgramAutoStartFlag - triggers automatic restart of SalmonCam on device boot.
	 * boolean bRepeatFlag - loops video recording sessions
	 * integer iJpegQuality - 
	 * integer iFramesPerSecond, (or is that iFPS) - 
	 * integer iRepeatInterval - 
	 * ...still need to add Camcorder profile, how?
	 ************************************************************************************/
	public void populateSettingsFile(){
		SharedPreferences settingsfile= getSharedPreferences(PREFERENCES_FILE_NAME,0);
		Editor myEditor = settingsfile.edit();
		myEditor.putBoolean("key_bProgramAutoStartFlag", bProgramAutoStartFlag);
		myEditor.putBoolean("key_bRepeatFlag", bRepeatFlag);
		//myEditor.putBoolean("key_bRecordingFlag", bRecordingFlag);
		myEditor.putInt("key_iJpegQuality", iJpegQuality);
		myEditor.putInt("key_iFramesPerSecond", iFramesPerSecondX1k);
		myEditor.putInt("key_iRepeatInterval", iRepeatInterval);
		myEditor.putInt("key_iCamcorderProfileSpinnerPosition", iCamcorderProfileSpinnerPosition);
		myEditor.putInt("key_iZoom", iZoom);
		myEditor.putInt("key_iExposure", iExposure);
		myEditor.commit();
	}
	//***************************End Populate settings************************************
	/************************************************************************************
	 * Load Settings From Archival Settings File- This retrieves archival settings on start and loads into variables.
	 * boolean bProgramAutoStartFlag - triggers automatic restart of SalmonCam on device boot.
	 * boolean bRepeatFlag - loops video recording sessions
	 * integer iJpegQuality - 20%-99%
	 * integer iFramesPerSecond, (or is that iFPS) - 
	 * integer iRepeatInterval - Sets length of recording interval (1-99 mins).
	 * ...still need to add Camcorder profile, how?
	 ************************************************************************************/
	public void loadsSettingsFromArchivalSettingsFile(){
		SharedPreferences settingsfile = getApplicationContext().getSharedPreferences(PREFERENCES_FILE_NAME,0);
		bProgramAutoStartFlag = settingsfile.getBoolean("key_bProgramAutoStartFlag", false);
		bRepeatFlag = settingsfile.getBoolean("key_bRepeatFlag", false);
		//bRecordingFlag = settingsfile.getBoolean("key_bRecordingFlag", false);
		iJpegQuality = settingsfile.getInt("key_iJpegQuality", 50);
		iFramesPerSecondX1k = settingsfile.getInt("key_iFramesPerSecond", 30000);
		iRepeatInterval = settingsfile.getInt("key_iRepeatInterval", 600000);
		iCamcorderProfileSpinnerPosition = settingsfile.getInt("key_iCamcorderProfileSpinnerPosition", 9);
		iZoom = settingsfile.getInt("key_iZoom", 0);
		iExposure = settingsfile.getInt("key_iExposure", 0);
	}
	//***************************End Get settings************************************
	/********************************************************************************************
	 * TURN OFF SCREEN/TURN ON SCREEN- This is intended to be a power saving feature that will be 
	 * timer actuated. Note: Some devices (Panasonic Toughpad) on "params.screenBrightness = 0;" 
	 * turn the screen completely off, thereby locking the device, 
	 ********************************************************************************************/
	public void turnOnScreen(){
		// turn on screen
		WindowManager.LayoutParams params = getWindow().getAttributes();
		params.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
		params.screenBrightness = 1;//-1, 0, -1f, 0.1f..."0" is the darkest
		getWindow().setAttributes(params);
		bScreenDimFlag = false;
		vibrate(100);
		/*     Log.v("ProximityActivity", "ON!");
	   //  mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "tag");
	     wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "tag");    
	     wakeLock.acquire();
		 */	}

	// @TargetApi(21) //Suppress lint error for PROXIMITY_SCREEN_OFF_WAKE_LOCK
	public void turnOffScreen(){
		WindowManager.LayoutParams params = getWindow().getAttributes();
		params.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
		params.screenBrightness = 0.1f;//-1, 0, -1f, 0.1f..."0" is the darkest, this turns OFF screen on Toughpad
		getWindow().setAttributes(params);
		bScreenDimFlag = true;
		vibrate(100);
		// turn off screen
		/*	     Log.v("ProximityActivity", "OFF!");
	    // mWakeLock = mPowerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "tag");
	     wakeLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "tag");
	     wakeLock.acquire();
		 */	}//*********************END TURN ON / TURN OFF SCREEN FUNCTION********************************

	/*****************************************************************************************
	 * checkExtSdCardWritable()- This tests to see what is going on with the removeable sdcard. Is 
	 * it mounted or not? Create a temporary file to see whether a volume is really writeable. 
	 * This is hardcoded to the location of the removeable card in a Samsung S4 running CyanogenMod 
	 * Jactivelte.
	 *****************************************************************************************/
	private static boolean checkExtSdCardWritable() {
		String directoryName = "/storage/sdcard1" + "/temp";
		File directory = new File(directoryName);
		if (!directory.isDirectory()) {
			if (!directory.mkdirs()) {
				return false;
			}
		}
		return directory.canWrite();
	}//end checkExtSdCardWritable()
	/*****************************************************************************************
	 * isJactivelteAndSdcard()- Warn if custom ROM is CyanogenMod "Jactivelte" and if no 
	 * SDCard is mounted to OS.
	 *****************************************************************************************/
	public void isJactivelteAndSdcard(){		
		if (android.os.Build.DEVICE.contains("jactivelte")){
			boolean bSdCardWritable = checkExtSdCardWritable();
			if(bSdCardWritable == false){
				Toast.makeText(this,"The removeable sdcard is not available. Disconnect USB, or insert a card.", Toast.LENGTH_LONG).show();
			}else{ //do nothing, sdcard is writable
			}
		}else{ //do nothing, this is not a Samsung S4 running CyanogenMod Jactivelte custom ROM
		}	
	}//end no SDCard warning
	/*********************************************************************************************
	 * Causes device to buzz for defined number of milliseconds
	 *********************************************************************************************/
	public void vibrate(int iDuration){
		Vibrator vibs = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		vibs.vibrate(iDuration);
	}
	/****************************************************************************************
	 * CREATE TARGET FILE STRUCTURE WITH TIMESTAMPS- This creates an mjpeg file with timestamp 
	 * in name, as well as /SalmonCam/ directory with date stamp directory enclosed. 
	 * Like this, "/SalmonCam/20150823/salmonCam20150823133056.mjpeg"
	 * 
	 * Note: also inserted boolean that defines if it is too dark to take pictures or not. 
	 * Having it here keeps the timer running unimpeded, however it does not start data
	 * data gathering until the start of the next timer session...
	 ****************************************************************************************/
	public void createMjpegFile(){
		makeNewTimestamps();
		String szFilePath;
		String szFileName = "salmonCam-"+szDateTime;

		//if jactivite then
		if (android.os.Build.DEVICE.contains("jactivelte")){
			//  this is the location of the removeable sdcard in a Samsung S4  
			//  running a CyanogenMod "Jactivelte" custom ROM	 
			szFilePath = "/storage/sdcard1";
		}else{//if not Samsung S4 running CyanogenMod "jactivelte" custom ROM... likely default to internal sdcard
			szFilePath = Environment.getExternalStorageDirectory().getAbsolutePath();
		}

		//creates a directory named /SalmonCam at the root of the sdcard


		try {


			File appDir = new File(szFilePath, "SalmonCam");
			appDir.mkdir();  
			// } catch (FileNotFoundException e) {
			// } catch (Exception e) {
		} catch (Throwable e) { 
			//Toast.makeText(this, "Ouch! SdCard not available!", Toast.LENGTH_LONG).show();
			//Log.v(LOGTAG,e.getMessage());
			//finish(); 
		}







		//creates a /yyyymmdd named directory, putting it in the above folder
		File exportDir = new File(szFilePath, "SalmonCam/" + szDate);
		exportDir.mkdir();

		//creates a new mjpeg data file, putting it in the above folder
		try {					
			mjpegFile = new File(szFilePath + "/SalmonCam/" + szDate, szFileName + ".mjpeg");
			mjpegFile.createNewFile();	
		} catch (Exception e) {
			Log.v(LOGTAG,e.getMessage());
			finish();
		}
	}//*****************END CREATE TARGET FILE STRUCTURE WITH TIMESTAMPS*************************

	/* good method here...
	  String url = Environment.getExternalStorageDirectory();
	  if(android.os.Build.DEVICE.contains("Samsung") || android.os.Build.MANUFACTURER.contains("Samsung")){
	            url = url + "/external_sd/";
	  }
	 */





	public File getSDPath() {
		String filepath = Environment.getExternalStorageDirectory()
				.getAbsolutePath();

		if (android.os.Build.DEVICE.contains("samsung")
				|| android.os.Build.MANUFACTURER.contains("samsung")) {//start big if...
			File f = new File(Environment.getExternalStorageDirectory()
					.getParent() + "/extSdCard");
			if (f.exists() && f.isDirectory()) {//already created?
				try {
					File file = new File(f, "test");
					FileOutputStream fos = new FileOutputStream(file);
					filepath = Environment.getExternalStorageDirectory()
							.getParent() + "/extSdCard";
				} catch (FileNotFoundException e) {
				}

			} else {//not created yet...
				f = new File(Environment.getExternalStorageDirectory()
						.getAbsolutePath() + "/external_sd");
				if (f.exists() && f.isDirectory()) {
					try {
						File file = new File(f, "test");
						FileOutputStream fos = new FileOutputStream(file);
						filepath = Environment.getExternalStorageDirectory()
								.getAbsolutePath() + "/external_sd";
					} catch (FileNotFoundException e) {
					}

				}
			}
		}//end big if statement...

		return new File(filepath);
	}











	/****************************************************************************************
	 * Makes Timestamps used in above function
	 ****************************************************************************************/
	public void makeNewTimestamps(){
		Date T = new Date();
		//for data file names...
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
		szDateTime = sdf.format(T);
		//for daily folder names
		SimpleDateFormat date = new SimpleDateFormat("yyyyMMdd");
		szDate = date.format(T);
	}//******************END makeNewTimestamps***************************************************

	/*********************************************************************************************
	 * Start recording...sets boolean flag, initiates output streams.
	 ********************************************************************************************/
	public void startRecording(){
		if (bRecordingFlag  == false){
			try {
				fos = new FileOutputStream(mjpegFile);//oooh, this is where the specific file name is called...
				bos = new BufferedOutputStream(fos);

				bRecordingFlag  = true;
				Log.v(LOGTAG, "Function startRecording() initiated.");
				//changes indicator to green + "RECORDING"
				tvRecordingNotrecordingIndicator.setText("RECORDING");
				tvRecordingNotrecordingIndicator.setPadding(10, 10, 10, 10);
				tvRecordingNotrecordingIndicator.setBackgroundColor(0xFF00CC00);//green
				//updates settingsfile 
				SharedPreferences settingsfile= getSharedPreferences(PREFERENCES_FILE_NAME,0);
				Editor myEditor = settingsfile.edit();
				//myEditor.putBoolean("key_bRecordingFlag", true);
				myEditor.commit();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			//Toast.makeText(this, "Recording started."+bRepeatFlag, Toast.LENGTH_SHORT).show();
		}
		else{
		}
	}//***********************END START RECORDING FUNCTION*****************************************
	/*********************************************************************************************
	 * Stops recording...resets boolean flag, clears buffered output stream.
	 ********************************************************************************************/
	public void stopRecording(){
		if (bRecordingFlag  == true){
			try {
				bos.flush();//StartCrash HERE!
				bos.close();

				bRecordingFlag  = false;
				//myEditor.putBoolean("key_bRecordingFlag", false);
				Log.v(LOGTAG, "Function stopRecording() initiated.");
				//changes indicator to red + "STOPPED"
				tvRecordingNotrecordingIndicator.setText("STOPPED");
				tvRecordingNotrecordingIndicator.setPadding(10, 10, 10, 10);
				tvRecordingNotrecordingIndicator.setBackgroundColor(0xFFFF0000);//red
				//updates settingsfile 
				SharedPreferences settingsfile= getSharedPreferences(PREFERENCES_FILE_NAME,0);
				Editor myEditor = settingsfile.edit();
				myEditor.commit();

			} catch (IOException e) {
				e.printStackTrace();
			}
			//Toast.makeText(this, "Recording stopped.", Toast.LENGTH_SHORT).show();
		}else{
		}
	}//***********************END STOP RECORDING FUNCTION*****************************************

	@SuppressWarnings("deprecation")
	@Override
	@SuppressLint("NewApi")
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Log.v(LOGTAG, "surfaceChanged");
		if (!bRecordingFlag ) {
			if (bPreviewRunning){
				camera.stopPreview();
			} try {	//how about grabbing setCamera() from above?


				parameters = camera.getParameters();
				parameters.set("camera-id", 2);
				parameters.setPreviewSize(camcorderProfile.videoFrameWidth, camcorderProfile.videoFrameHeight);
				parameters.setPreviewFpsRange(iFramesPerSecondX1k, iFramesPerSecondX1k);//note: This is fps x 1000 (!)
				iMaxExposure = parameters.getMaxExposureCompensation();
				iMaxZoom = parameters.getMaxZoom();
				seekbarZoom.setMax(iMaxZoom);
				seekbarExposure.setMax(iMaxExposure);
				parameters.setRotation(0);
				//parameters.setZoom(1);
				parameters.setZoom(iZoom);
				parameters.setExposureCompensation(iExposure);

				//p.setPreviewFrameRate(iFramesPerSecondX1k);			
				camera.setParameters(parameters);
				camera.setPreviewDisplay(holder);				
				camera.setPreviewCallback(this);

				//				setCamera(camera);

				Log.v(LOGTAG,"startPreview");
				camera.startPreview();
				bPreviewRunning = true;
			}
			catch (IOException e) {
				Log.e(LOGTAG,e.getMessage());
				e.printStackTrace();
			}	
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.v(LOGTAG, "surfaceDestroyed");
		if (bRecordingFlag ) {
			bRecordingFlag  = false;
			try {
				bos.flush();
				bos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		bPreviewRunning = false;
		camera.release();
		finish();
	}	
	/****************************************************************
	 * getSupportedPreviewFpsRange()- Returns specified frame rate 
	 * (.getSupportedPreviewFpsRange()) to log file and also displays 
	 * as toast message.
	 * Todo: 
	 * 1.) report FPS not multiplied by 1000, (eg 30 fps, not 30000)
	 * 2.) get device brand and model and incorporate in message,
	 * "Recommended FPS settings for the *Brand* *model* are:
	 ****************************************************************/		        
	public void getSupportedPreviewFpsRange(){    
		Camera.Parameters camParameter = camera.getParameters();
		List<int[]> frame = camParameter.getSupportedPreviewFpsRange();
		Iterator<int[]> supportedPreviewFpsIterator = frame.iterator();
		while (supportedPreviewFpsIterator.hasNext()) {
			int[] tmpRate = supportedPreviewFpsIterator.next();
			StringBuffer sb = new StringBuffer();
			sb.append("SupportedPreviewRate: ");
			for (int i = tmpRate.length, j = 0; j < i; j++) {
				sb.append(tmpRate[j]/1000 + ", ");
			}
			Log.d(LOGTAG, "FPS6: " + sb.toString());
			Toast.makeText(this, "FPS = "+sb.toString(), Toast.LENGTH_SHORT).show();
		}	        		        		        	
	}//*****************end getSupportedPreviewFpsRange()**********************	
	/**********************************************************************************************
	 * hideKeyboard()- hides onscreen data entry keyboard
	 * **********************************************************************************************/
	private void hideKeyboard() {
		InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
		View view = this.getCurrentFocus();
		if (imm.isAcceptingText()) { // verify if the soft keyboard is open
			imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
			//imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
		}
	}/********************************end hideKeyboard()**********************************************/
	/**************************************************************************************
	 * startRepeatTimer et al- starts new recording session immediately following current one.
	 **************************************************************************************/
	synchronized private void startRepeatTimer() {
		handler.postDelayed(getCountdownTask(), 1000); //number is delay to start, 1000 = 1 second
		// Toast.makeText(getApplicationContext(), "Repeat timer started.", Toast.LENGTH_SHORT).show(); 
	}
	synchronized private void stopRepeatTimer() {
		handler.removeCallbacks(runnableRepeatTimer);
		runnableRepeatTimer = null;//runnableRepeatDismissCountdown
		stopRecording();
		// Toast.makeText(getApplicationContext(), "Repeat timer stopped.", Toast.LENGTH_SHORT).show();
	}    
	private Runnable getCountdownTask() { 
		Runnable task = new Runnable() {
			@Override
			public void run() {/* do what you need to do */
				if(bTooDarkFlag == false){// start not too dark
				
				if(bRecordingFlag  == true){//if already recording
					stopRecording();//Startup CRASH HERE!!!
					createMjpegFile();
					startRecording();
				}else{//is NOT currently running
					createMjpegFile();
					startRecording();
				}
				}//end not too dark
				else{ //if it is too dark to record
					if(bRecordingFlag  == true){
						stopRecording();//Startup CRASH HERE!!!
						createMjpegFile();
						bRecordingFlag = false; //reset flag here because recording stops until its light again
					//above flag reset cause red/green indicator to change
					}
				}
				/* and here comes the "trick" */
				if (bRepeatFlag == true){                 
					handler.postDelayed(this, iRepeatInterval);//60000 = 1 minute
				}else{
				}
			}//end run()
		};
		runnableRepeatTimer = task;
		return task;
	}//*****************************end startRepeatTimer et al***************************************//*****************************end startRepeatTimer et al***************************************
	/*****************************************************************************
	 *            This is really a key function: needs good description.
	 *****************************************************************************/
	@Override
	public void onPreviewFrame(byte[] b, Camera c) {
		if (bRecordingFlag ) {
			// Assuming ImageFormat.NV21
			if (parameters.getPreviewFormat() == ImageFormat.NV21) {
				Log.v(LOGTAG,"Started Writing Frame");
				try {
					makeNewTimestamps();
					YuvImage im = new YuvImage(b, ImageFormat.NV21, parameters.getPreviewSize().width, parameters.getPreviewSize().height, null);
					Rect r = new Rect(0,0,parameters.getPreviewSize().width,parameters.getPreviewSize().height);

					ByteArrayOutputStream jpegByteArrayOutputStream = new ByteArrayOutputStream();
					im.compressToJpeg(r, iJpegQuality, jpegByteArrayOutputStream);//note: qual = 20 or less doesn't work.
					byte[] jpegByteArray = jpegByteArrayOutputStream.toByteArray();
					byte[] boundaryBytes = (szBoundaryStart + jpegByteArray.length + " bytes" + szAbsoluteTime + szDateTime + szJpegQuality + iJpegQuality + "%" + szFramesPerSecond + iFramesPerSecondX1k +szCamcorderFrame + camcorderProfile.videoFrameHeight +" x "+ camcorderProfile.videoFrameWidth + szCamcorderBitrate + camcorderProfile.videoBitRate + szBoundaryEnd).getBytes();
					bos.write(boundaryBytes);										
					bos.write(jpegByteArray);
					bos.flush();
					//bos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				Log.v(LOGTAG,"Finished Writing Frame");
			} else {
				Log.v(LOGTAG,"NOT THE RIGHT FORMAT");
			}
		}
	}
}
