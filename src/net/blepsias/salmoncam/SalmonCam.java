package net.blepsias.salmoncam;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
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
import android.widget.Spinner;
import android.widget.TextView;

/*
 * todo- 
 * 1) change name to salmonCam
 * 2) insert settings (FPS, Jpeg qual, camcorder profile, n-minutes in header...is this possible???
 * 3) move onto GitHub
 * 4) create in Google Play
 * 
 * 
 * 	5) start SalmonCam on device startup,
 *  6) start recording on program startup at specific settings, (JPEG qual, FPS, Camcorder profile)
 *  Use shared preferences for this, http://stackoverflow.com/questions/24414863
 *  7) in the event of an exception, restart application
 *  8) method of turning off the screen when application is running to save power.
 *  9) button colors...all gray except STOP is red, till start pressed, then start button is green, STOP is gray, press stop and is grey again, press update settings and button goes sky blue momentarily.
 *  
 *  Questions for Shawn:
 *  1) What are viewer options for MJPEG?
 *  2) What are the ints (100) in, "surfaceChanged(holder,100,100,100);"
 *  
 */

public class SalmonCam extends Activity implements OnClickListener,OnItemSelectedListener, SurfaceHolder.Callback, Camera.PreviewCallback {	
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
	
	Handler handler;
	Runnable runnableRepeatTimer;
	
	Spinner spinnerCamcorderProfile;
	public TextView tvFramesPerSecond, tvJpegQuality, tvRepeatInterval;
	private CheckBox ckbxRepeat;
	
	boolean bRecording = false;
	boolean bPreviewRunning = false;
	boolean bRepeatFlag = false;
	
	int iFramesPerSecond=30000; //this is 30fps as default (...mult by 1,000)
	int iFPS; //above divided by 1000 to get fps
	int iJpegQuality=50; //must be above 20 or files don't play
	int iRepeatInterval=600000;//1,000 = 1 sec...600,000 = 10 mins. This is a default setting
	
	byte[] previewCallbackBuffer;
	
	String szDateTime;
	
	File mjpegFile;
	FileOutputStream fos;
	BufferedOutputStream bos;
	Button btnStartRecord, btnStopRecord, btnExit, btnChange;
	
	Camera.Parameters p;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		handler = new Handler();

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		setContentView(R.layout.main);
		addListenerCkbxRepeat();
		
		tvFramesPerSecond = (TextView) this.findViewById(R.id.textboxframespersecondxml);
		iFPS = iFramesPerSecond/1000;
		String szFPS = Integer.toString(iFPS);
		tvFramesPerSecond.setText(szFPS);
		
		tvJpegQuality = (TextView) this.findViewById(R.id.textboxJpegQualityxml);
		String szJpegQuality = Integer.toString(iJpegQuality);
		tvJpegQuality.setText(szJpegQuality);
					
		tvRepeatInterval = (TextView) this.findViewById(R.id.textboxRepeatIntervalxml);
		int iRepeatIntervalMinutes=(iRepeatInterval/1000)/60;
		String szRepeatInterval = Integer.toString(iRepeatIntervalMinutes);//value needs to be in seconds x 1000
		tvRepeatInterval.setText(szRepeatInterval);
		
		btnStartRecord = (Button) this.findViewById(R.id.StartRecordButton);
		btnStartRecord.setOnClickListener(this);
		
		btnStopRecord = (Button) this.findViewById(R.id.StopRecordButton);
		btnStopRecord.setOnClickListener(this);
		
		btnExit = (Button) this.findViewById(R.id.ExitButton);
		btnExit.setOnClickListener(this);
		
		btnChange = (Button) this.findViewById(R.id.ChangeButton);
		btnChange.setOnClickListener(this);

		camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_480P);//changed from quality_HIGH
		SurfaceView cameraView = (SurfaceView) findViewById(R.id.CameraView);
		holder = cameraView.getHolder();
		holder.addCallback(this);
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		cameraView.setClickable(true);
		cameraView.setOnClickListener(this);
		
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
	        spinnerCamcorderProfile.setSelection(9);
	        spinnerCamcorderProfile.setOnItemSelectedListener(this);
	              
	        makeNewTimestamps();
	}
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
			case R.id.StartRecordButton:
				startRepeatTimer();
				break;
			case R.id.StopRecordButton:	
				stopRepeatTimer();
				break;		
			case R.id.ChangeButton:	
				//FPS- needs to be in FPS x 1000 format
				String szFPS=tvFramesPerSecond.getText().toString();
				iFPS = Integer.parseInt(szFPS);
				iFramesPerSecond = iFPS *1000;
				
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
				
				//Updates shown image with one using new parameters
				surfaceChanged(holder,100,100,100);//Okay, what ints? "100, 100, 100" works, but why???
			
				hideKeyboard();
				
				//message popup indicating successful completion of above
				//Toast.makeText(this, "Change button pressed.", Toast.LENGTH_SHORT).show();
				
				break;
				
			case R.id.ExitButton:
				stopRecording();
				stopRepeatTimer();
				System.exit(0);
				break;
			}
		}
	/*
	 * This creates the checkbox that determines if the program starts an additional file after the current one completes.
	 */
	  public void addListenerCkbxRepeat() {
			ckbxRepeat = (CheckBox) findViewById(R.id.ckbxRepeatxml);
			ckbxRepeat.setOnClickListener(new OnClickListener() {
			  @Override
			  public void onClick(View v) {
				if (((CheckBox) v).isChecked()) {
					bRepeatFlag = true;
				}
			  }
			});
		  }

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		Log.v(LOGTAG, "surfaceCreated");
		camera = Camera.open();
	}
	/*
	 * This creates an mjpeg file with timestamp in name
	 */
	public void createMjpegFile(){
		
		makeNewTimestamps();
        String szFileName = "salmonCam-"+szDateTime;
        
        //creates /SalmonCam directory on device if not created yet...
        File appDir = new File(Environment.getExternalStorageDirectory(), "SalmonCam");
        appDir.mkdir();
        
        //creates directory with current date as name, (/20151004)
        File exportDir = new File(Environment.getExternalStorageDirectory(), "SalmonCam/" + szDate);
        exportDir.mkdir();
        
        //creates a new mjpeg data file, putting it in the above folder
		try {					
			mjpegFile = new File(Environment.getExternalStorageDirectory() + "/SalmonCam/" + szDate, szFileName + ".mjpeg");
			mjpegFile.createNewFile();	
		} catch (Exception e) {
			Log.v(LOGTAG,e.getMessage());
			finish();
		}
	}//end createMjpegFile
	
	public void makeNewTimestamps(){
		Date T = new Date();
		//for data file names...
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        szDateTime = sdf.format(T);
        //for daily folder names
        SimpleDateFormat date = new SimpleDateFormat("yyyyMMdd");
        szDate = date.format(T);
	}
/*
 * Starts recording...
 */
	public void startRecording(){
		if (bRecording == false){
			try {
				fos = new FileOutputStream(mjpegFile);//oooh, this is where the specific file name is called...
				bos = new BufferedOutputStream(fos);
				bRecording = true;
				Log.v(LOGTAG, "Recording Started");
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			//Toast.makeText(this, "Recording started."+bRepeatFlag, Toast.LENGTH_SHORT).show();
			}
			else{
			}
	}
	/*
	 * Stops recording...
	 */
	public void stopRecording(){
		if (bRecording == true){
			try {
				bos.flush();
				bos.close();
				bRecording = false;
			} catch (IOException e) {
				e.printStackTrace();
			}
			Log.v(LOGTAG, "Recording Stopped");
			//Toast.makeText(this, "Recording stopped.", Toast.LENGTH_SHORT).show();
			}else{
			}
	}
	
	@Override
	@SuppressLint("NewApi")
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Log.v(LOGTAG, "surfaceChanged");
		if (!bRecording) {
			if (bPreviewRunning){
				camera.stopPreview();
			} try {	
				p = camera.getParameters();
				p.set("camera-id", 2);
				p.setPreviewSize(camcorderProfile.videoFrameWidth, camcorderProfile.videoFrameHeight);
				p.setPreviewFpsRange(iFramesPerSecond, iFramesPerSecond);//note: This is fps x 1000 (!)
				camera.setParameters(p);
				camera.setPreviewDisplay(holder);				
				camera.setPreviewCallback(this);
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
		if (bRecording) {
			bRecording = false;
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
     /**********************************************************************************************
     * hideKeyboard()- hides onscreen data entry keyboard
     * **********************************************************************************************/
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm.isAcceptingText()) { // verify if the soft keyboard is open
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }/********************************end hideKeyboard()**********************************************/
     /**************************************************************************************
     * startRepeatTimer et al- starts new recording session immediately following current one
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
       public void run() {
    	   //Toast.makeText(getApplicationContext(), "Runnable started.", Toast.LENGTH_SHORT).show();
          /* do what you need to do */
    	                      if(bRecording == true){//if already recording
    	                      	stopRecording();
    	                      	createMjpegFile();
    	                      	startRecording();
    	                      }else{//is NOT currently running
    	                      	createMjpegFile();
    	                      	startRecording();
    	                      }
          /* and here comes the "trick" */
    	  if (bRepeatFlag == true){                 
          handler.postDelayed(this, iRepeatInterval);//60000 = 1 minute
    	  }else{
    	  }
       }
    };
    runnableRepeatTimer = task;
    return task;
   }//end startRepeatTimer et al

	@Override
	public void onPreviewFrame(byte[] b, Camera c) {
		if (bRecording) {
			// Assuming ImageFormat.NV21
			if (p.getPreviewFormat() == ImageFormat.NV21) {
				Log.v(LOGTAG,"Started Writing Frame");
				try {
					makeNewTimestamps();
					YuvImage im = new YuvImage(b, ImageFormat.NV21, p.getPreviewSize().width, p.getPreviewSize().height, null);
					Rect r = new Rect(0,0,p.getPreviewSize().width,p.getPreviewSize().height);
					
					ByteArrayOutputStream jpegByteArrayOutputStream = new ByteArrayOutputStream();
					im.compressToJpeg(r, iJpegQuality, jpegByteArrayOutputStream);//note: qual = 20 or less doesn't work.
					byte[] jpegByteArray = jpegByteArrayOutputStream.toByteArray();
					byte[] boundaryBytes = (szBoundaryStart + jpegByteArray.length + " bytes" + szAbsoluteTime + szDateTime + szJpegQuality + iJpegQuality + "%" + szFramesPerSecond + iFPS +szCamcorderFrame + camcorderProfile.videoFrameHeight +" x "+ camcorderProfile.videoFrameWidth + szCamcorderBitrate + camcorderProfile.videoBitRate + szBoundaryEnd).getBytes();
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