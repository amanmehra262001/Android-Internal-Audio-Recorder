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
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;

public class ChannelRecording extends AppCompatActivity {
    private static final int REQUEST_AUDIO_PERMISSION_CODE = 101;
    private static final int REQUEST_INTERNET_PERMISSION_CODE = 1000;
    public static final String TAG = "ChannelRecording";

    MediaProjectionManager mediaProjectionManager;
    Button startBtn;
    Button stopBtn;
    TextInputEditText inputEditText;
    RadioGroup radioGroup;
    RadioButton radioButton;

    String channelName="";
    Boolean isContinuous;
    String channelId;
    public int mResultCode;
    public Intent mResultData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel_recording);

        startBtn = findViewById(R.id.start_tvrecord);
        stopBtn = findViewById(R.id.stop_tvrecord);
        radioGroup = findViewById(R.id.radio_group);
        radioButton = findViewById(R.id.radio_one);

        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: Clicked startBtnContinuous");
                if(checkRecordingPermission()){
                    Log.d(TAG, "onCreate: Permission Granted");
                    isContinuous = true;
                    channelId = getChannelId(radioButton.getText().toString());
                    Log.d(TAG, "onClick: Channel Id is: "+channelId);
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
                Intent serviceIntent = new Intent(ChannelRecording.this, AudioCaptureService.class);
                stopService(serviceIntent);
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
                Toast.makeText(ChannelRecording.this, "User cancelled the request!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (ChannelRecording.this == null) {
                return;
            }
            Log.i(TAG, "onActivityResult: Starting screen capture");
            mResultCode = resultCode;
            mResultData = data;
            Log.i(TAG, "onActivityResult: Resultcode and resultdata created.");
            Log.i(TAG, "onCreate: Starting service");
            Log.d(TAG, "onActivityResult: Audio Capturing service");
            Intent serviceIntent = new Intent(ChannelRecording.this,AudioCaptureService.class);
            serviceIntent.putExtra("extraData",data);
            serviceIntent.putExtra("isContinuous", isContinuous);
            serviceIntent.putExtra("isChannelRecording", true);
            serviceIntent.putExtra("channelId", channelId);
            startForegroundService(serviceIntent);

            Log.i(TAG, "onActivityResult: Service Started");
        }
    }


    private void requestRecordingPermission(){
        ActivityCompat.requestPermissions(ChannelRecording.this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION_CODE);
    }

    private void requestInternetPermission(){
        ActivityCompat.requestPermissions(ChannelRecording.this, new String[]{Manifest.permission.INTERNET}, REQUEST_INTERNET_PERMISSION_CODE);
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

    public void checkButton(View view){
        int radioId = radioGroup.getCheckedRadioButtonId();

        radioButton = findViewById(radioId);

        Toast.makeText(this, "Selected channel: "+radioButton.getText(), Toast.LENGTH_SHORT).show();
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

    private String getChannelId(String channelName){
        switch (channelName){
            case "ABP":
                return "1";
            case "Aaj Tak":
                return "2";
            case "NDTV":
                return "3";
        }
        return "0";
    }

}

// Intent serviceIntent = new Intent(ChannelRecording.this, AudioCaptureService.class);
//                serviceIntent.putExtra("isContinuous", true);
//                serviceIntent.putExtra("isChannelRecording", true);
//                serviceIntent.putExtra("channelName", radioButton.getText().toString());
//                startService(serviceIntent);