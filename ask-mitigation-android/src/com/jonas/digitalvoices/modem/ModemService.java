package com.jonas.digitalvoices.modem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import net.fec.openrq.ArrayDataDecoder;
import net.fec.openrq.ArrayDataEncoder;
import net.fec.openrq.EncodingPacket;
import net.fec.openrq.OpenRQ;
import net.fec.openrq.Parsed;
import net.fec.openrq.encoder.SourceBlockEncoder;
import net.fec.openrq.parameters.FECParameters;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.github.icedrake.jsmaz.Smaz;
import com.jonas.reedsolomon.CRCGen;

public class ModemService extends Service {
	public static final String TAG = ModemService.class.getSimpleName();

	private final IBinder mBinder = new ModemBinder();

	private MicrophoneListener mMicrophoneListener = null;
	private StreamDecoder mStreamDecoder = null;
	private ByteArrayOutputStream mDecodedStream = new ByteArrayOutputStream();

	private boolean mUseCompression = false;
	private boolean mUseChecksum = false;

	private String mReceivedText = "";

	/**
	 * Handler target for the StreamDecoder to signal when reception of a
	 * complete OTA message is detected.
	 */
	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			byte[] bytes = msg.getData().getByteArray(
					StreamDecoder.MSG_KEY_RECEIVED_BYTES);

			receivedBytes(bytes);
		}
	};

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

	public void setUseCompression(boolean useCompression) {
		this.mUseCompression = useCompression;
	}

	public void setUseChecksum(boolean useChecksum) {
		this.mUseChecksum = useChecksum;
	}

	public String getBacklogStatus() {
		if (mStreamDecoder == null)
			return "";
		else
			return mStreamDecoder.getStatusString();
	}

	public String getReceivedText() {
		synchronized (mReceivedText) {
			return mReceivedText == null ? "" : mReceivedText;
		}
	}

	public boolean isListening() {
		return mMicrophoneListener != null;
	}

	public void listen() {
		stopListening();

		mDecodedStream.reset();

		// the StreamDecoder uses the Decoder to decode samples put in its
		// AudioBuffer
		// StreamDecoder starts a thread
		mStreamDecoder = new StreamDecoder(mDecodedStream, mHandler);

		// the MicrophoneListener feeds the microphone samples into the
		// AudioBuffer
		// MicrophoneListener starts a thread
		mMicrophoneListener = new MicrophoneListener(
				mStreamDecoder.getAudioBuffer());

		Log.d(TAG, "Listening...");
	}

	public void stopListening() {
		if (mMicrophoneListener != null)
			mMicrophoneListener.quit();

		mMicrophoneListener = null;

		if (mStreamDecoder != null)
			mStreamDecoder.quit();

		mStreamDecoder = null;

		synchronized (mReceivedText) {
			// clear the received text
			mReceivedText = "";
		}
	}

	public void playData(String input) {
		stopListening();

		// play the text
		Log.d(TAG, "Playing: " + input);
		new SendDataTask().execute(ArrayUtils.box(input.getBytes()));
	}

	private void receivedBytes(byte[] bytes) {
		new ReceiveDataTask().execute(ArrayUtils.box(bytes));
	}

	/**
	 * An AsyncTask to send text as sound. If configured to do so, the text will
	 * be compressed and/or sent with a checksum.
	 * 
	 * A transmission sequence consists of: flag byte (compression and checksum
	 * options), payload (optionally compressed), checksum (optional).
	 */
	private class SendDataTask extends AsyncTask<Byte, Void, String> {
		private boolean showToast = false;

		@Override
		protected String doInBackground(Byte... bytes) {
			byte[] data = ArrayUtils.unbox(bytes);
			String toastText = null;
			byte flags = (byte) 0xFF;

			// compress input if necessary
			if (mUseCompression) {
				flags &= ~(1 << Constants.COMPRESSION_FLAG_BIT);

				int uncompressedLength = data.length;
				data = new Smaz().compress(new String(data));

				int compressionRatio = (int) ((float) data.length
						/ (float) uncompressedLength * 100);

				toastText = "Compressed text by "
						+ Integer.toString(compressionRatio) + "%";
				showToast = true;
			}

			// apply a checksum if necessary
			if (mUseChecksum) {
				flags &= ~(1 << Constants.CHECKSUM_FLAG_BIT);

				byte crc = CRCGen.crc_8_ccitt(data, data.length);
				data = ArrayUtils.concatenate(data, new byte[] { crc });
			}

			// attach flags
			data = ArrayUtils.concatenate(new byte[] { flags }, data);

			try {
				// play the input
				AudioUtils.performArray(data);

			} catch (IOException e) {
				Log.d(TAG, "Could not encode data because of " + e);

			}

			return toastText;
		}

		@Override
		protected void onPostExecute(String result) {
			if (!showToast)
				return;

			Toast.makeText(getApplication(), result, Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * An AsyncTask to process received sound data converting it back into text.
	 * If indicated by the received flag byte, the text will be compressed
	 * and/or sent with a checksum.
	 * 
	 * A transmission sequence consists of: flag byte (compression and checksum
	 * options), payload (optionally compressed), checksum (optional).
	 */
	private class ReceiveDataTask extends AsyncTask<Byte, Void, String> {
		private boolean showToast = false;

		@Override
		protected String doInBackground(Byte... bytes) {
			byte[] data = ArrayUtils.unbox(bytes);
			String toastText = null;

			// check flags
			byte flags = data[0];
			boolean useChecksum = (~(flags >> Constants.CHECKSUM_FLAG_BIT) & 1) == 1;
			boolean useCompression = (~(flags >> Constants.COMPRESSION_FLAG_BIT) & 1) == 1;

			// remove flag byte
			data = ArrayUtils.subarray(data, 1, data.length - 1);

			if (useChecksum) {
				byte generatedCRC = CRCGen.crc_8_ccitt(
						ArrayUtils.subarray(data, 0, data.length - 1),
						data.length - 1);
				byte receivedCRC = data[data.length - 1];

				if (generatedCRC != receivedCRC) {
					toastText = "Received corrupted data";
					showToast = true;

					return toastText;
				}

				// remove checksum byte
				data = ArrayUtils.subarray(data, 0, data.length - 1);
			}

			String text = null;
			if (useCompression) {
				// decompress data
				text = new Smaz().decompress(data);

				int decompressionRatio = (int) ((float) text.length()
						/ (float) data.length * 100);

				toastText = "Decompressed text by "
						+ Integer.toString(decompressionRatio) + "%";
				showToast = true;

			} else {
				text = new String(data);
			}

			synchronized (mReceivedText) {
				StringBuilder sb = new StringBuilder(mReceivedText);

				if (!mReceivedText.equals(""))
					sb.append("\n");

				sb.append(text);

				mReceivedText = sb.toString();
			}

			return toastText;
		}

		@Override
		protected void onPostExecute(String result) {
			if (!showToast)
				return;

			Toast.makeText(getApplication(), result, Toast.LENGTH_SHORT).show();
		}

	}
}
