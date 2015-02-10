package com.jonas.digitalvoices.modem;

/**
 * Copyright 2102 by the authors. All rights reserved.
 *
 * Author: Jonas Michel
 * 
 * This is the application's main service. This service maintains
 * the app's state machine and interacts with the playing and listening
 * threads accordingly.
 * 
 */

import java.io.ByteArrayOutputStream;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class ModemService extends Service {
	public static final String TAG = ModemService.class.getSimpleName();

	private final IBinder mBinder = new ModemBinder();

	private MicrophoneListener microphoneListener = null;
	private StreamDecoder sDecoder = null;
	private ByteArrayOutputStream decodedStream = new ByteArrayOutputStream();

	private int modemTimeout = -1;
	private int timeoutCounter = -1;

	public class ModemBinder extends Binder {
		public ModemService getService() {
			return ModemService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		stopListening();
	}

	public String getBacklogStatus() {
		if (sDecoder == null)
			return "";
		else
			return sDecoder.getStatusString();
	}

	public String getReceivedText() {
		byte[] receivedBytes = sDecoder.getReceivedBytes();
		return receivedBytes == null ? "" : new String(receivedBytes);
	}

	public boolean isListening() {
		return microphoneListener != null;
	}

	public void listen() {
		stopListening();

		decodedStream.reset();

		// the StreamDecoder uses the Decoder to decode samples put in its
		// AudioBuffer
		// StreamDecoder starts a thread
		sDecoder = new StreamDecoder(decodedStream);

		// the MicrophoneListener feeds the microphone samples into the
		// AudioBuffer
		// MicrophoneListener starts a thread
		microphoneListener = new MicrophoneListener(sDecoder.getAudioBuffer());

		Log.d(TAG, "Listening...");
	}

	public void stopListening() {
		if (microphoneListener != null)
			microphoneListener.quit();

		microphoneListener = null;

		if (sDecoder != null)
			sDecoder.quit();

		sDecoder = null;
	}

	public long playData(String input, boolean compress, boolean fec) {
		stopListening();

		long millisPlayTime = -1;
		try {
			// try to play the file
			Log.d(TAG, "Playing: " + input);
			AudioUtils.performString(input, compress, fec);

			/**
			 * length of play time (ms) = nDurations * samples/duration * 1/fs *
			 * 1000
			 */
			millisPlayTime = (long) ((Constants.kPlayJitter
					+ Constants.kDurationsPerHail + Constants.kBytesPerDuration
					* input.length() + Constants.kDurationsPerCRC)
					* Constants.kSamplesPerDuration
					/ Constants.kSamplingFrequency * 1000);

		}

		catch (Exception e) {
			System.out
					.println("Could not encode " + input + " because of " + e);
		}

		return millisPlayTime;
	}

}
