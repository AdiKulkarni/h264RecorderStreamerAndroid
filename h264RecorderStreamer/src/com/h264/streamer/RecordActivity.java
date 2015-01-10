package com.h264.streamer;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnErrorListener;
import android.media.MediaRecorder.OnInfoListener;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Menu;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

public class RecordActivity extends Activity implements SurfaceHolder.Callback,
		OnInfoListener, OnErrorListener {

	// Declarations
	private static final String TAG = "RecordVideo";
	private MediaRecorder mRecorder = null;
	private VideoView mVideoView = null;
	private SurfaceHolder mHolder = null;
	private Button mInitBtn = null;
	private Button mStartBtn = null;
	private Button mStopBtn = null;

	private Camera mCamera = null;
	private TextView mRecordingMsg = null;
	boolean uploadDone = false;

	// networking variables
	private int DATAGRAM_PORT = 4003;
	private static final int MAX_UDP_DATAGRAM_LEN = 1024;
	DatagramPacket dp;
	DatagramSocket ds;
	InputStream dataStream;
	private ParcelFileDescriptor pfd;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.v(TAG, "in onCreate");
		setContentView(R.layout.record);
		mInitBtn = (Button) findViewById(R.id.initBtn);
		mStartBtn = (Button) findViewById(R.id.beginBtn);
		mStopBtn = (Button) findViewById(R.id.stopBtn);

		mRecordingMsg = (TextView) findViewById(R.id.recording);
		mVideoView = (VideoView) this.findViewById(R.id.videoView);

		// Set Buttons
		mInitBtn.setEnabled(true);
		mStartBtn.setEnabled(false);
		mStopBtn.setEnabled(false);

	}

	@Override
	protected void onResume() {
		Log.v(TAG, "in onResume");
		super.onResume();

		setContentView(R.layout.record);
		mInitBtn = (Button) findViewById(R.id.initBtn);
		mStartBtn = (Button) findViewById(R.id.beginBtn);
		mStopBtn = (Button) findViewById(R.id.stopBtn);

		mRecordingMsg = (TextView) findViewById(R.id.recording);
		mVideoView = (VideoView) this.findViewById(R.id.videoView);
		// Toast.makeText(this,"onResume",Toast.LENGTH_SHORT).show();

		mInitBtn.setEnabled(true);
		mStartBtn.setEnabled(false);
		mStopBtn.setEnabled(false);

		if (!initCamera())
			finish();
	}

	@Override
	protected void onPause() {
		Log.v(TAG, "in onPause");
		super.onPause();
		releaseRecorder();
		releaseCamera();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	// @Override
	public void onError(MediaRecorder mr, int what, int extra) {
		Log.e(TAG, "got a recording error");
		stopRecording();
		Toast.makeText(this,
				"Recording error has occurred. Stopping the recording",
				Toast.LENGTH_SHORT).show();
	}

	// @Override
	public void onInfo(MediaRecorder mr, int what, int extra) {
		Log.i(TAG, "got a recording event");
		if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
			Log.i(TAG, "...max duration reached");
			stopRecording();
			Toast.makeText(this,
					"Recording limit has been reached. Stopping the recording",
					Toast.LENGTH_SHORT).show();
		}
	}

	@SuppressWarnings("deprecation")
	private boolean initCamera() {
		try {
			mCamera = Camera.open();
			mCamera.lock();
			mHolder = mVideoView.getHolder();
			mHolder.addCallback(this);
			mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		} catch (RuntimeException re) {
			Log.v(TAG, "Could not initialize the Camera");
			re.printStackTrace();
			return false;
		}
		return true;
	}

	// @Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		Log.v(TAG, "surfaceChanged: Width x Height = " + width + "x" + height);
	}

	// @Override
	public void surfaceCreated(SurfaceHolder mHolder) {
		Log.v(TAG, "in surfaceCreated");
		try {
			if (mCamera != null) {
				mCamera.setPreviewDisplay(mHolder);
				mCamera.startPreview();
			}
		} catch (IOException e) {
			Log.v(TAG, "Could not start the preview");
			e.printStackTrace();
		}
		mInitBtn.setEnabled(true);

	}

	// @Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.v(TAG, "in surfaceDestroyed");
	}

	private void releaseRecorder() {
		if (mRecorder != null) {
			mRecorder.release();
			mRecorder = null;
		}
	}

	private void releaseCamera() {
		if (mCamera != null) {
			try {
				mCamera.reconnect();
			} catch (IOException e) {
				e.printStackTrace();
			}
			mCamera.stopPreview();
			mCamera.setPreviewCallback(null);
			mCamera.release();
			mCamera = null;
		}
	}

	public void doClick(View view) throws IOException {
		switch (view.getId()) {
		case R.id.initBtn:
			initRecorder();
			break;
		case R.id.beginBtn:
			beginRecording();
			break;
		case R.id.stopBtn:
			stopRecording();
			break;

		}
	}

	@SuppressLint("NewApi")
	private void initRecorder() {

		try {

			mInitBtn.setEnabled(false);
			byte[] lMsg = new byte[MAX_UDP_DATAGRAM_LEN];
			ds = new DatagramSocket(DATAGRAM_PORT);
			dp = new DatagramPacket(lMsg, lMsg.length);
			mRecordingMsg.setText("MediaRecorder initialized");
			ds.receive(dp);
			mRecordingMsg.setText("Request from: " + dp.getAddress() + ":"
					+ dp.getAddress());
			ds.connect(dp.getAddress(), dp.getPort());

			pfd = ParcelFileDescriptor.fromDatagramSocket(ds);

			// if (mRecorder != null)
			// return;
			initCamera();

			// mCamera.stopPreview();
			mCamera.unlock();
			mRecorder = new MediaRecorder();
			mRecordingMsg.setText("camera");
			mRecorder.setCamera(mCamera);
			mRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
			mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
			mRecorder.setAudioChannels(2);
			mRecorder.setAudioEncodingBitRate(128000);
			mRecordingMsg.setText("video");
			mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
			mRecorder.setVideoSize(640, 480);
			mRecordingMsg.setText("size");
			mRecorder.setVideoFrameRate(15);
			mRecordingMsg.setText("fps");
			mRecorder.setVideoEncodingBitRate(3000000);
			mRecordingMsg.setText("bps");
			mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
			mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
			mRecorder.setPreviewDisplay(mHolder.getSurface());
			mRecorder.setOutputFile(pfd.getFileDescriptor());
			mRecordingMsg.setText("prepare");
			mRecorder.prepare();
			mRecordingMsg.setText("Connected to: " + dp.getAddress() + ":"
					+ dp.getPort());
			// mRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
			Log.v(TAG, "MediaRecorder initialized");
			// Set Buttons

			mStartBtn.setEnabled(true);
			mStopBtn.setEnabled(false);

		} catch (Exception e) {
			// mRecordingMsg.setText("MediaRecorder failed to initialize");
			Log.v(TAG, "MediaRecorder failed to initialize");
			// mRecordingMsg.setText(e.toString());
			e.printStackTrace();
		}
	}

	private void beginRecording() throws IOException {
		mRecorder.setOnInfoListener(this);
		mRecorder.setOnErrorListener(this);
		mRecorder.start();
		mRecordingMsg.setText("Streaming h264 video to " + dp.getAddress()
				+ ":" + dp.getPort());
		// Set Buttons
		mInitBtn.setEnabled(false);
		mStartBtn.setEnabled(false);
		mStopBtn.setEnabled(true);
	}

	private void stopRecording() {
		if (mRecorder != null) {
			mRecorder.setOnErrorListener(null);
			mRecorder.setOnInfoListener(null);
			try {
				mRecorder.stop();
			} catch (IllegalStateException e) {
				Log.e(TAG, "Got IllegalStateException in stopRecording");
			}
			releaseRecorder();
			mRecordingMsg
					.setText("Stopped session. Press initialize to listen for next connection");
			releaseCamera();
			ds.disconnect();
			ds.close();

			// Set Buttons
			mInitBtn.setEnabled(true);
			mStartBtn.setEnabled(false);
			mStopBtn.setEnabled(false);

		}
	}

}
