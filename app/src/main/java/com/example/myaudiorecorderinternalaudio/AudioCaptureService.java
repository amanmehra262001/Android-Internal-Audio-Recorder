package com.example.myaudiorecorderinternalaudio;

import static com.example.myaudiorecorderinternalaudio.App.CHANNEL_ID;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
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
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.microsoft.cognitiveservices.speech.CancellationDetails;
import com.microsoft.cognitiveservices.speech.CancellationReason;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionResult;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AudioCaptureService extends Service {


    class AudioCaptureServiceThread extends Thread{
        public boolean exit;
        public AudioCaptureServiceThread(){
            exit = false;
        }
        @Override
        public void run() {
            super.run();
            outputFile = new File(getOutputFilePath());
            Log.d(TAG, "run: Created file for capture target: "+outputFile.getPath());
            try{
            writeAudioTofile(outputFile);
            }catch (Exception e){
                Log.e(TAG, "run: Unknown error from audiothread class",e );
                e.printStackTrace();
            }
        }
    }

//    🔧`TODO: See if this NetworkRequestThread can be accommodated in AudioCaptureServiceThread.
    class NetworkRequestThread extends Thread{
        public boolean exit;
        public NetworkRequestThread(){
            exit = false;
        }
        @Override
        public void run() {
            Looper.prepare();
            super.run();
            Log.d(TAG, "run: Running Network request thread");
            try{
                doMultiPartRequest();
            }catch (Exception e){
                Log.e(TAG, "run: Error in doMultiPartRequest",e );
                e.printStackTrace();
            }
        }
    }

    private final int NUM_SAMPLES_PER_READ = 1024;
    private final int BYTES_PER_SAMPLE = 2; // 2 bytes since we hardcoded the PCM 16-bit format
    private final int BUFFER_SIZE_IN_BYTES = NUM_SAMPLES_PER_READ * BYTES_PER_SAMPLE;
    private static final int RECORDER_SAMPLE_RATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final String speechKey = "10654f470a1d499e93b3dc2d897101b1";
    private static final String speechRegion = "eastus";

    MediaProjectionManager mediaProjectionManager;
    MediaProjection mediaProjection;
    AudioRecord audioRecord;
    AudioPlaybackCaptureConfiguration audioPlaybackCaptureConfiguration;
    FileOutputStream fileOutputStream;

    public int mResultCode;
    public Intent mResultData;
    public File outputFile;
    private File waveFile;
    public boolean isContinuous;
    private boolean isInternalAudio;
    private boolean isChannelRecording;
    private String channelId;

    private String TAG = "AudioCaptureService";
    private int seconds;
    private boolean isStopped;


    AudioCaptureServiceThread audioCaptureServiceThread;
    NetworkRequestThread networkRequestThread;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate: Creating notificationIntent");
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MyAudioRecorderInternalAudio")
                .setContentText("Recording internal audio")
                .setSmallIcon(R.drawable.ic_android)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);
        Log.i(TAG, "onCreate: Created notificationIntent");
        mediaProjectionManager = (MediaProjectionManager) getApplicationContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        outputFile = new File(getOutputFilePath());
        waveFile = new File(getWaveFilePath());
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        seconds = 0;
        isStopped = false;
        isContinuous = intent.getBooleanExtra("isContinuous",false);
        isInternalAudio = intent.getBooleanExtra("isInternalAudio",true);
        isChannelRecording = intent.getBooleanExtra("isChannelRecording",false);
        channelId = intent.getStringExtra("channelId");
        mResultData = intent.getParcelableExtra("extraData");
        Log.d(TAG, "onStartCommand: Channel name: "+channelId+isChannelRecording);
        createAudioPlaybackCaptureConfiguration();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isStopped = true;
        try {
            fileOutputStream.close();
            audioCaptureServiceThread.interrupt();
            audioCaptureServiceThread.join();
            rawToWave(outputFile, waveFile);
            if(isContinuous){
            networkRequestThread = new NetworkRequestThread();
            networkRequestThread.start();
            System.out.println(networkRequestThread.isAlive());
            }else{
                if(networkRequestThread != null){
                    networkRequestThread.interrupt();
                    networkRequestThread.join();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "onDestroy: IOException");
            e.printStackTrace();
        } catch (InterruptedException e) {
            Log.e(TAG, "onDestroy: InterruptedException",e );
            e.printStackTrace();
        }
        Log.d(TAG, "writeAudioTofile: Audio capture finished for: "+outputFile.getPath());
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void createAudioPlaybackCaptureConfiguration() {
        Log.i(TAG, "createAudioPlaybackCaptureConfiguration: Creating audioPlaybackCaptureConfiguration");
        setupMediaProjection();
        try {
            AudioPlaybackCaptureConfiguration.Builder audioPlaybackCaptureConfigurationBuilder = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection).addMatchingUsage(AudioAttributes.USAGE_MEDIA);
            audioPlaybackCaptureConfiguration =
                    audioPlaybackCaptureConfigurationBuilder.build();
            Log.i(TAG, "createAudioPlaybackCaptureConfiguration: Built audioPlaybackCaptureConfiguration");
            recordInternalAudio();
        } catch (Exception e) {
            Log.e(TAG, "createAudioPlaybackCaptureConfiguration: Error:", e);
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void recordInternalAudio() {
        Log.d(TAG, "recordInternalAudio: Starting audio recording");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getApplicationContext(), "Permission not granted", Toast.LENGTH_SHORT).show();
            return;
        }
        if(!isInternalAudio){
            audioRecord = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(RECORDER_AUDIO_ENCODING)
                            .setSampleRate(RECORDER_SAMPLE_RATE)
                            .setChannelMask(RECORDER_CHANNELS)
                            .build())
                    .setBufferSizeInBytes(BUFFER_SIZE_IN_BYTES)
                    .build();
        }else {
            audioRecord = new AudioRecord.Builder()
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(RECORDER_AUDIO_ENCODING)
                            .setSampleRate(RECORDER_SAMPLE_RATE)
                            .setChannelMask(RECORDER_CHANNELS)
                            .build()).setBufferSizeInBytes(BUFFER_SIZE_IN_BYTES)
                    .setAudioPlaybackCaptureConfig(audioPlaybackCaptureConfiguration)
                    .build();
        }
        Log.i(TAG, "recordInternalAudio: Starting audio recording");
        audioRecord.startRecording();
        Log.i(TAG, "recordInternalAudio: Audio recording started");
        if(isContinuous) {
            audioCaptureServiceThread = new AudioCaptureServiceThread();
            audioCaptureServiceThread.start();
        }else {
        start();
        }
    }

    private String getOutputFilePath() {
        ContextWrapper contextWrapper = new ContextWrapper(getApplicationContext());
        File music = contextWrapper.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        File file = new File(music, "testFile"+".pcm");
        return file.getPath();
    }

    private String getWaveFilePath() {
        ContextWrapper contextWrapper = new ContextWrapper(getApplicationContext());
        File music = contextWrapper.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        File file = new File(music, "testFile"+".wav");
        return file.getPath();
    }

    private void writeAudioTofile(File outputFile){
        try {
            fileOutputStream = new FileOutputStream(outputFile);
            byte[] capturedAudioSamples = new byte[NUM_SAMPLES_PER_READ];
            int i = 0;
            Log.d(TAG, "writeAudioTofile: Starting while loop");
            while (!audioCaptureServiceThread.isInterrupted()){
//                Log.d(TAG, "writeAudioTofile: "+i);
                audioRecord.read(capturedAudioSamples,0,NUM_SAMPLES_PER_READ);
//                Log.d(TAG, "writeAudioTofile: Read from audiorecord");
                fileOutputStream.write(capturedAudioSamples,0,NUM_SAMPLES_PER_READ);
//                Log.d(TAG, "writeAudioTofile: Written by fileoutputstream");
                i++;
            }
            fileOutputStream.close();
            Log.d(TAG, "writeAudioTofile: Audio capture finished for: "+outputFile.getPath());

        } catch (FileNotFoundException e) {
            Log.e(TAG, "writeAudioTofile: Error in writting to the file",e );
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(TAG, "writeAudioTofile: Error in writing to a file",e );
            e.printStackTrace();
        }
    }


    private void setupMediaProjection() {
        try {
            mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK,mResultData);
        }catch (Exception e){
            Log.e(TAG, "createAudioPlaybackCaptureConfiguration: Error in creating mediaProjection",e );
        }
    }

    private void rawToWave(final File rawFile, final File waveFile) throws IOException {

        byte[] rawData = new byte[(int) rawFile.length()];
        DataInputStream dataInputStream = null;
        try {
            dataInputStream = new DataInputStream(new FileInputStream(rawFile));
            dataInputStream.read(rawData);
        } finally {
            if (dataInputStream != null) {
                dataInputStream.close();
            }
        }

        DataOutputStream dataOutputStream = null;
        try {
            dataOutputStream = new DataOutputStream(new FileOutputStream(waveFile));
            // WAVE header
            // see http://ccrma.stanford.edu/courses/422/projects/WaveFormat/
            writeString(dataOutputStream, "RIFF"); // chunk id
            writeInt(dataOutputStream, 36 + rawData.length); // chunk size
            writeString(dataOutputStream, "WAVE"); // format
            writeString(dataOutputStream, "fmt "); // subchunk 1 id
            writeInt(dataOutputStream, 16); // subchunk 1 size
            writeShort(dataOutputStream, (short) 1); // audio format (1 = PCM)
            writeShort(dataOutputStream, (short) 1); // number of channels
            writeInt(dataOutputStream, RECORDER_SAMPLE_RATE); // sample rate
            writeInt(dataOutputStream, NUM_SAMPLES_PER_READ * 2); // byte rate
            writeShort(dataOutputStream, (short) 2); // block align
            writeShort(dataOutputStream, (short) 16); // bits per sample
            writeString(dataOutputStream, "data"); // subchunk 2 id
            writeInt(dataOutputStream, rawData.length); // subchunk 2 size
            // Audio data (conversion big endian -> little endian)
            short[] shorts = new short[rawData.length / 2];
            ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
            ByteBuffer bytes = ByteBuffer.allocate(shorts.length * 2);
            for (short s : shorts) {
                bytes.putShort(s);
            }

            dataOutputStream.write(fullyReadFileToBytes(rawFile));
        } finally {
            if (dataOutputStream != null) {
                dataOutputStream.close();
            }
        }
    }
    byte[] fullyReadFileToBytes(File f) throws IOException {
        int size = (int) f.length();
        byte bytes[] = new byte[size];
        byte tmpBuff[] = new byte[size];
        FileInputStream fileInputStream= new FileInputStream(f);
        try {

            int read = fileInputStream.read(bytes, 0, size);
            if (read < size) {
                int remain = size - read;
                while (remain > 0) {
                    read = fileInputStream.read(tmpBuff, 0, remain);
                    System.arraycopy(tmpBuff, 0, bytes, size - remain, read);
                    remain -= read;
                }
            }
        }  catch (IOException e){
            throw e;
        } finally {
            fileInputStream.close();
        }

        return bytes;
    }
    private void writeInt(final DataOutputStream dataOutputStream, final int value) throws IOException {
        dataOutputStream.write(value >> 0);
        dataOutputStream.write(value >> 8);
        dataOutputStream.write(value >> 16);
        dataOutputStream.write(value >> 24);
    }

    private void writeShort(final DataOutputStream dataOutputStream, final short value) throws IOException {
        dataOutputStream.write(value >> 0);
        dataOutputStream.write(value >> 8);
    }

    private void writeString(final DataOutputStream output, final String value) throws IOException {
        for (int i = 0; i < value.length(); i++) {
            output.write(value.charAt(i));
        }
    }

    public void start(){
        if(!isStopped){
        seconds++;
        audioCaptureServiceThread = new AudioCaptureServiceThread();
        audioCaptureServiceThread.start();
        System.out.println("Audio captured:"+seconds);
        refresh(10000);
        }else{
            Log.d(TAG, "start: isStopped: "+isStopped);
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;

            mediaProjection.stop();
        }
    }

    private  void refresh(int milliseconds){
        final Handler handler = new Handler();
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                audioCaptureServiceThread.interrupt();
                try {
                    audioCaptureServiceThread.join();
                    audioCaptureServiceThread = null;
                    Log.d(TAG, "run: Nullified audioThread");
                    rawToWave(outputFile, waveFile);
                    Log.d(TAG, "run: Converted to .wav file");
                    Log.d(TAG, "run: Running speech recognition");
//                    try {
//                        startSpeechRecognition();
//                    } catch (ExecutionException e) {
//                        Log.e(TAG, "writeAudioTofile: ExecutionException occured",e );
//                        e.printStackTrace();
//                    }catch (InterruptedException e) {
//                        Log.e(TAG, "writeAudioTofile: InterruptedException occured",e );
//                        e.printStackTrace();
//                    }
//                    Call the api
                    Log.d(TAG, "run: Calling the api");
//                    doMultiPartRequest();
                    Log.d(TAG, "run: Starting the speech recognition");
                    networkRequestThread = new NetworkRequestThread();
                    networkRequestThread.start();
                }catch (InterruptedException e) {
                    Log.e(TAG, "run: InterruptedException in audio thread",e );
                    e.printStackTrace();
                } catch (IOException e) {
                    Log.e(TAG, "run: IOException in audio thread",e );
                    e.printStackTrace();
                }
                start();
            }
        };
        handler.postDelayed(runnable, milliseconds);
    }

    private void doMultiPartRequest() {
        String path = getWaveFilePath();
        Log.d(TAG, "doMultiPartRequest: file path is :"+ path);
        File audioFile = new File(path);
        if(audioFile.isFile()){
            Log.d(TAG, "doMultiPartRequest: Given file is a file.");
            doActualRequest(path);
        }
    }

    private void doActualRequest(String path) {
        OkHttpClient client = new OkHttpClient().newBuilder()
                .readTimeout(1,TimeUnit.MINUTES)
                .connectTimeout(1,TimeUnit.MINUTES)
                .build();
        Log.d(TAG, "doActualRequest: Got client instance");
        MediaType mediaType = MediaType.parse("text/plain");

        RequestBody body;
        Request request;
        if(isChannelRecording){
            Log.d(TAG, "doActualRequest: Recording Channel");
            body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("voice",path,
                            RequestBody.create(MediaType.parse("application/octet-stream"),
                                    new File(path)))
                    .addFormDataPart("channel",channelId)
                    .build();
            Log.d(TAG, "doActualRequest: Created request body");
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.readTimeout(5, TimeUnit.MINUTES);
            request = new Request.Builder()
                    .url("http://chrome-android-prod.eba-aq5ejnit.ap-south-1.elasticbeanstalk.com/voices/api/tv-recording/")
                    .method("POST", body)
                    .addHeader("Cookie", "csrftoken=QqqQk4DnIilXyxTtD7p2Hc1JL8jG9MCUdHjOjxdIlCWdrFykztRyDPuQ9z1jFCDG")
                    .build();
        }else{
            Log.d(TAG, "doActualRequest: Recording ad");
            body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("voice",path,
                            RequestBody.create(MediaType.parse("application/octet-stream"),
                                    new File(path)))
                    .addFormDataPart("viewer","1")
                    .build();
            Log.d(TAG, "doActualRequest: Created request body");
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.readTimeout(5, TimeUnit.MINUTES);
            request = new Request.Builder()
                    .url("http://chrome-android-prod.eba-aq5ejnit.ap-south-1.elasticbeanstalk.com/voices/api/audio-recording/")
                    .method("POST", body)
                    .addHeader("Cookie", "csrftoken=QqqQk4DnIilXyxTtD7p2Hc1JL8jG9MCUdHjOjxdIlCWdrFykztRyDPuQ9z1jFCDG")
                    .build();
        }
        Log.d(TAG, "doActualRequest: Created actual request");
        try {
            Log.d(TAG, "doActualRequest: Sending response");
            Response response = client.newCall(request).execute();
            Log.d(TAG, "doActualRequest: RESPONSE:"+response.body().string());
            Toast.makeText(getApplicationContext(), "Audio sent successfully", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "doActualRequest: ERROR:"+e.toString());
            Toast.makeText(getApplicationContext(), "Some error occurred in sending audio", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

//    --------------------SPEECH RECOGNITION-----------------------------

    public void startSpeechRecognition() throws InterruptedException, ExecutionException{
        Log.d(TAG, "startSpeechRecognition: Starting speech recognition");
        SpeechConfig speechConfig = SpeechConfig.fromSubscription(speechKey, speechRegion);
        speechConfig.setSpeechRecognitionLanguage("en-US");
        Log.d(TAG, "startSpeechRecognition: Built speechconfig configuration");
        recognizeSpeech(speechConfig);
    }

    private void recognizeSpeech(SpeechConfig speechConfig) {
        Log.d(TAG, "recognizeSpeech: Starting recognizeSpeech");
        String waveFilePath = getWaveFilePath();
        AudioConfig audioConfig = AudioConfig.fromWavFileInput(waveFilePath);
        SpeechRecognizer speechRecognizer = new SpeechRecognizer(speechConfig,audioConfig);
        Log.d(TAG, "recognizeSpeech: Built speechrecognizer instance");

        Log.d(TAG, "recognizeSpeech: Starting speech recognition");
        Future<SpeechRecognitionResult> task = speechRecognizer.recognizeOnceAsync();
        SpeechRecognitionResult speechRecognitionResult = null;
        Log.d(TAG, "recognizeSpeech: Entering try/catch block");
        try {
            speechRecognitionResult = task.get();
        } catch (ExecutionException e) {
            Log.e(TAG, "recognizeSpeech: ExecutionException occured:",e );
            e.printStackTrace();
        } catch (InterruptedException e) {
            Log.e(TAG, "recognizeSpeech: InterruptedException occured",e );
            e.printStackTrace();
        }
        Log.d(TAG, "recognizeSpeech: Exited try/catch block");

        if (speechRecognitionResult.getReason() == ResultReason.RecognizedSpeech) {
            System.out.println("RECOGNIZED: Text=" + speechRecognitionResult.getText());
        }
        else if (speechRecognitionResult.getReason() == ResultReason.NoMatch) {
            System.out.println("NOMATCH: Speech could not be recognized.");
        }
        else if (speechRecognitionResult.getReason() == ResultReason.Canceled) {
            CancellationDetails cancellation = CancellationDetails.fromResult(speechRecognitionResult);
            System.out.println("CANCELED: Reason=" + cancellation.getReason());

            if (cancellation.getReason() == CancellationReason.Error) {
                System.out.println("CANCELED: ErrorCode=" + cancellation.getErrorCode());
                System.out.println("CANCELED: ErrorDetails=" + cancellation.getErrorDetails());
                System.out.println("CANCELED: Did you set the speech resource key and region values?");
            }
        }

        System.exit(0);
    }


}

// STOP THREAD IN JAVA: https://www.java67.com/2015/07/how-to-stop-thread-in-java-example.html