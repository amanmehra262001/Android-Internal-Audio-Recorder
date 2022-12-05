package com.example.myaudiorecorderinternalaudio;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_AUDIO_PERMISSION_CODE = 101;
    private static final int REQUEST_INTERNET_PERMISSION_CODE = 1000;
    public static final String MSG = "com.example.myaudiorecorderinternalaudio.MSG";

    public int mResultCode;
    public Intent mResultData;
    public boolean isContinuous;
    public boolean isInternalAudio;

    MediaProjectionManager mediaProjectionManager;
    Switch internalAudioSwitch;
    Button startBtnContinuous;
    Button startBtnLoop;
    Button stopBtn;
    Button recordChannelBtn;
    String TAG = "MainActivity";


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        internalAudioSwitch = findViewById(R.id.internal_audio_switch);
        startBtnContinuous = findViewById(R.id.start_button_continuous);
        startBtnLoop = findViewById(R.id.start_button_loop);
        stopBtn = findViewById(R.id.stop_button);
        recordChannelBtn = findViewById(R.id.tv_record);
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

//        startBtnFromMic.setOnClickListener(new View.OnClickListener() {
//            @RequiresApi(api = Build.VERSION_CODES.O)
//            @Override
//            public void onClick(View view) {
//                if(checkRecordingPermission()){
//                    Log.d(TAG, "onClick: Permission Granted");
//                    isContinuous = true;
////                    startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(),REQUEST_AUDIO_PERMISSION_CODE);
//                    Log.d(TAG, "onClick: MIC Audio Capturing service");
//                    Intent serviceIntent = new Intent(MainActivity.this, MicAudioCaptureService.class);
//                    Log.d(TAG, "onClick: Service Intent created");
//                    startService(serviceIntent);
//                    Log.d(TAG, "onClick: MicAudioCaptureService started");
//                }else{
//                    requestRecordingPermission();
//                    requestInternetPermission();;
//                }
//            }
//        });

        startBtnLoop.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.Q)
            @Override
            public void onClick(View view) {
                if(checkRecordingPermission()){
                    Log.d(TAG, "onCreate: Permissoin Granted");
                    isContinuous = false;
                    isInternalAudio = internalAudioSwitch.isChecked();
                    startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(),REQUEST_AUDIO_PERMISSION_CODE);
                }else{
                    requestRecordingPermission();
                    requestInternetPermission();;
                }
            }
        });

        startBtnContinuous.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.Q)
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: Clicked startBtnContinuous");
                if(checkRecordingPermission()){
                    Log.d(TAG, "onCreate: Permission Granted");
                    isContinuous = true;
                    isInternalAudio = internalAudioSwitch.isChecked();
                    startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(),REQUEST_AUDIO_PERMISSION_CODE);
                }else{
                    requestRecordingPermission();
                    requestInternetPermission();;
                }
            }
        });

        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopBtn.setText("Please Wait...");
                Log.i(TAG, "onCreate: Stopping service");
                Intent serviceIntent = null;
                serviceIntent = new Intent(MainActivity.this, AudioCaptureService.class);
                stopService(serviceIntent);
                Log.i(TAG, "onCreate: Service stopped");
                stopBtn.setText("Stop");
            }
        });

        recordChannelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent activityIntent = new Intent(MainActivity.this, ChannelRecording.class);
                startActivity(activityIntent);
            }
        });

    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_AUDIO_PERMISSION_CODE) {
            if (resultCode != Activity.RESULT_OK) {
                Log.i(TAG, "onActivityResult: User Cancelled");
                Toast.makeText(MainActivity.this, "User cancelled the request!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (MainActivity.this == null) {
                return;
            }
            Log.i(TAG, "onActivityResult: Starting screen capture");
            mResultCode = resultCode;
            mResultData = data;
            Log.i(TAG, "onActivityResult: Resultcode and resultdata created.");
            Log.i(TAG, "onCreate: Starting service");
            Log.d(TAG, "onActivityResult: Audio Capturing service");
            Intent serviceIntent = new Intent(MainActivity.this,AudioCaptureService.class);
            serviceIntent.putExtra("extraData",data);
            serviceIntent.putExtra("isContinuous", isContinuous);
            serviceIntent.putExtra("isInternalAudio", isInternalAudio);
            startForegroundService(serviceIntent);

            Log.i(TAG, "onActivityResult: Service Started");
        }
    }

    private void requestRecordingPermission(){
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION_CODE);
    }

    private void requestInternetPermission(){
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.INTERNET}, REQUEST_INTERNET_PERMISSION_CODE);
    }

    public boolean checkRecordingPermission() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_DENIED ){
            requestRecordingPermission();
            requestInternetPermission();;
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_AUDIO_PERMISSION_CODE || requestCode == REQUEST_INTERNET_PERMISSION_CODE){
            if(grantResults.length>0){
                boolean permissionToRecord = grantResults[0] ==PackageManager.PERMISSION_GRANTED;
                boolean permissionToInternet =  grantResults[1] ==PackageManager.PERMISSION_GRANTED;
                if(permissionToRecord && permissionToInternet){
                    Toast.makeText(getApplicationContext(), "Permission Given", Toast.LENGTH_SHORT).show();
                }
                else{
                    Toast.makeText(getApplicationContext(), "Permission Denied", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}

//https://medium.com/@juliozynger/media-projection-and-audio-capture-1ca72e271e9c

// TODO: create toggle button to set and unset isInternalAudio value