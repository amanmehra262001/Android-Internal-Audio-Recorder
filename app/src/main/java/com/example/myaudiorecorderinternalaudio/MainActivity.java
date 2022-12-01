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
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_AUDIO_PERMISSION_CODE = 101;
    private static final int REQUEST_INTERNET_PERMISSION_CODE = 1000;

    public int mResultCode;
    public Intent mResultData;

    MediaProjectionManager mediaProjectionManager;
    Button startBtn;
    Button stopBtn;
    String TAG = "MainActivity";


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startBtn = findViewById(R.id.start_button);
        stopBtn = findViewById(R.id.stop_button);
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        startBtn.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.Q)
            @Override
            public void onClick(View view) {
                if(checkRecordingPermission()){
                    Log.d(TAG, "onCreate: Permissoin Granted");
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
                Log.i(TAG, "onCreate: Stoping service");
                Intent serviceIntent = new Intent(MainActivity.this,AudioCaptureService.class);
                stopService(serviceIntent);
                Log.i(TAG, "onCreate: Service stopped");
                stopBtn.setText("Stop");
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
            Log.i(TAG, "onActivityResult: Resultcode and resltdata created.");
            Log.i(TAG, "onCreate: Starting service");
            Intent serviceIntent = new Intent(MainActivity.this,AudioCaptureService.class);
            serviceIntent.putExtra("extraData",data);
            startForegroundService(serviceIntent);
            Log.i(TAG, "onCreate: Service started");
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