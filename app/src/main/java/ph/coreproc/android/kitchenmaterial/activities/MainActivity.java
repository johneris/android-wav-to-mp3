package ph.coreproc.android.kitchenmaterial.activities;

import android.app.ProgressDialog;
import android.content.Context;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.netcompss.loader.LoadJNI;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import butterknife.InjectView;
import butterknife.OnClick;
import ph.coreproc.android.kitchenmaterial.R;
import ph.coreproc.android.kitchenmaterial.utils.GameConstants;
import ph.coreproc.android.kitchenmaterial.utils.UiUtil;

/**
 * Created by johneris on 6/1/2015.
 */
public class MainActivity extends BaseActivity {

    boolean isRecording;

    private int bufferSize;

    AudioRecord mRecorder ;
    MediaPlayer mediaPlayer;

    String fileNameIn;
    String fileNameOut;

    @InjectView(R.id.btnRecord)
    Button btnRecord;

    @InjectView(R.id.btnPlay)
    Button btnPlay;

    @InjectView(R.id.btnConvert)
    Button btnConvert;

    @InjectView(R.id.etFilename)
    EditText etFilename;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        isRecording = false;

        bufferSize = 0;

        mRecorder = null;
        mediaPlayer = null;

        fileNameIn = Environment.getExternalStorageDirectory().getPath() + "/" + "recording.wav";
        fileNameOut = Environment.getExternalStorageDirectory().getPath() + "/" + "recording_converted.mp3";

        btnConvert.setEnabled(false);
        btnPlay.setEnabled(false);

        RecordingInitializer recordingInitializerTask = new RecordingInitializer();
        recordingInitializerTask.execute();
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_main;
    }

    @OnClick(R.id.btnRecord)
    public void record() {
        if (!isRecording) {
            btnRecord.setText("Stop");
            startRecording();
        } else {
            stopRecording();
            btnRecord.setText("Record");
        }
    }

    @OnClick(R.id.btnConvert)
    public void convert() {
        File fileOut = new File(fileNameOut);
        File fileIn = new File(fileNameIn);
        if (fileOut.exists()) {
            fileOut.delete();
        }
        ConvertBackground convertBackground = new ConvertBackground(
                mContext,
                new String[]{"-y", "-i", fileIn.getAbsolutePath(),
                        "-ar", "22050", "-ac", "1", "-ab", "64k",
                        "-f", "mp3", fileOut.getAbsolutePath()},
                Environment.getExternalStorageDirectory().getPath()
        );
        convertBackground.execute();
    }

    @OnClick(R.id.btnPlay)
    public void play() {
        if(mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        }

        if(mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            btnPlay.setText("Play");
        } else {
            File fileOut = new File(fileNameOut);
            // file does not exists
            if(!fileOut.exists()) {
                UiUtil.showToastShort(mContext, "convert a wav file first.");
            }
            try {
                mediaPlayer.setDataSource((new FileInputStream(fileOut)).getFD());
                mediaPlayer.prepare();
                mediaPlayer.start();
                btnPlay.setText("Stop");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void startRecording() {
        isRecording = true;
        mRecorder.startRecording();

        StartMusicTask startMusicTask = new StartMusicTask();
        startMusicTask.execute();
    }

    private void stopRecording() {
        if (null != mRecorder) {
            isRecording = false;
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }
        GameConstants.copyWaveFile(bufferSize, getTempFilename(), new File(fileNameOut).getAbsolutePath());
    }

    private class RecordingInitializer extends AsyncTask<Void, Void, Void> {

        ProgressDialog pd = new ProgressDialog(mContext);

        @Override
        protected void onPreExecute() {
            btnRecord.setEnabled(false);
            pd.setMessage("Preparing your studio...");
            pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            pd.setCancelable(false);
            pd.show();

            bufferSize = AudioRecord.getMinBufferSize(
                    GameConstants.RECORDER_SAMPLE_RATE,
                    GameConstants.RECORDER_CHANNELS,
                    GameConstants.RECORDER_AUDIO_ENCODING);

        }

        @Override
        protected Void doInBackground(Void... voids) {

            if (mRecorder != null) {
                mRecorder.release();
            }

            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioManager.setParameters("noise_suppression=auto");

            mRecorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    GameConstants.RECORDER_SAMPLE_RATE,
                    GameConstants.RECORDER_CHANNELS,
                    GameConstants.RECORDER_AUDIO_ENCODING, bufferSize);

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            btnRecord.setEnabled(true);
            pd.dismiss();
        }

    }

    private class StartMusicTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            short data[] = new short[bufferSize];

            String recordingFile = getTempFilename();

            DataOutputStream dos = null;
            try {
                dos = new DataOutputStream(new BufferedOutputStream(
                        new FileOutputStream(recordingFile)));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            int read = 0;

            while (isRecording) {
                read = mRecorder.read(data, 0, bufferSize);

                for (int i = 0; i < bufferSize && i < read; i++) {
                    try {
                        dos.writeShort(Short.reverseBytes((short) data[i]));
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                        Toast.makeText(getApplicationContext(),
                                "" + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }

            }

            try {
                dos.close();
                dos.flush();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NullPointerException e) {
                e.printStackTrace();
                Toast.makeText(getApplicationContext(), "" + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
            return null;
        }

    }

    private String getTempFilename() {
        String filepath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC).getPath();
        File file = new File(filepath, GameConstants.AUDIO_RECORDER_FOLDER);
        if (!file.exists()) {
            file.mkdirs();
        }
        File tempFile = new File(filepath,
                GameConstants.AUDIO_RECORDER_TEMP_FILE);
        if (tempFile.exists())
            tempFile.delete();
        return (file.getAbsolutePath() + "/" + GameConstants.AUDIO_RECORDER_TEMP_FILE);
    }

    public class ConvertBackground extends AsyncTask<String, Integer, Integer> {

        ProgressDialog progressDialog;
        Context mContext;

        String[] command;
        String folder;

        public ConvertBackground(Context context, String[] command, String folder) {
            mContext = context;
            this.command = command;
            this.folder = folder;
        }

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(mContext);
            progressDialog.setCancelable(false);
            progressDialog.setMessage("Convert " + fileNameIn + " to mp3" +
                    "\n output file: " + fileNameOut);
            progressDialog.show();
        }

        protected Integer doInBackground(String... paths) {
            Log.i("convert", "convert doInBackground started...");

            LoadJNI vk = new LoadJNI();
            try {
                vk.run(command, folder, getApplicationContext());
            } catch (Throwable e) {
                Log.e("convert", "vk run exeption.", e);
            } finally {

            }

            Log.i("convert", "doInBackground finished");
            return Integer.valueOf(0);
        }

        protected void onProgressUpdate(Integer... progress) {
        }

        @Override
        protected void onCancelled() {
            Log.i("convert", "onCancelled");
            progressDialog.dismiss();
            super.onCancelled();
        }


        @Override
        protected void onPostExecute(Integer result) {
            Log.i("convert", "onPostExecute");
            progressDialog.dismiss();
            super.onPostExecute(result);

            UiUtil.showToastShort(mContext, "finish");
        }

    }

}
