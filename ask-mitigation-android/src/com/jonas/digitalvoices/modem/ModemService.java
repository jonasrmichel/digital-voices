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

public class ModemService extends Service {
	public static final String TAG = ModemService.class.getSimpleName();

	private final IBinder mBinder = new ModemBinder();

	private MicrophoneListener mMicrophoneListener = null;
	private StreamDecoder mStreamDecoder = null;
	private ByteArrayOutputStream mDecodedStream = new ByteArrayOutputStream();

	private boolean mUseCompression = false;
	private boolean mUseFEC = false;

	private String mReceivedText = null;

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

	public void setUseFEC(boolean useFEC) {
		this.mUseFEC = useFEC;
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
	}

	public long playData(String input) {
		stopListening();

		// try to play the file
		Log.d(TAG, "Playing: " + input);
		new PlayStringTask().execute(input);

		/**
		 * length of play time (ms) = nDurations * samples/duration * 1/fs *
		 * 1000
		 */
		long millisPlayTime = (long) ((Constants.kPlayJitter
				+ Constants.kDurationsPerHail + Constants.kBytesPerDuration
				* input.length() + Constants.kDurationsPerCRC)
				* Constants.kSamplesPerDuration / Constants.kSamplingFrequency * 1000);

		return millisPlayTime;
	}

	private void receivedBytes(byte[] bytes) {
		String receivedText = null;

	}

	private class PlayStringTask extends AsyncTask<String, Void, Integer> {
		public static final String FLAG_USE_COMPRESSION = "compression";
		public static final String FLAG_USE_FEC = "fec";

		@Override
		protected Integer doInBackground(String... strings) {
			String input = strings[0];
			if (input == null)
				return -1;

			byte[] array = null;

			// compress input if necessary
			int compressionRatio = -1;
			if (mUseCompression) {
				array = new Smaz().compress(input);

				compressionRatio = (int) ((float) array.length
						/ (float) input.length() * 100);
			}

			if (array == null)
				array = input.getBytes();

			// apply error correction if necessary
			if (mUseFEC)
				array = applyFECEncoding(array);

			try {
				// play the input
				AudioUtils.performArray(array);

			} catch (IOException e) {
				Log.d(TAG, "Could not encode " + input + " because of " + e);
			}

			return compressionRatio;
		}

		@Override
		protected void onPostExecute(Integer result) {
			if (result < 0)
				return;

			Toast.makeText(getApplication(),
					"Compression ratio of " + result + "%", Toast.LENGTH_SHORT)
					.show();
		}

		/**
		 * Applies forward error correction encoding to an array of bytes.
		 * 
		 * @param bytes
		 *            an array of bytes to encode with FEC.
		 * @return an array list of chunks containing the data's FEC symbols as
		 *         payloads.
		 */
		private byte[] applyFECEncoding(byte[] bytes) {
			Log.d(TAG, "applyFECEncoding(): " + Arrays.toString(bytes));

			// the total length in bytes of the data to be encoded
			int dataLength = bytes.length;

			// apply forward error correction encoding
			FECParameters fecParams = FECParameters.deriveParameters(
					dataLength, Constants.FEC_SYMBOL_SIZE,
					Constants.FEC_MAX_DECODING_BLOCK_BYTES);
			ArrayDataEncoder fecDataEncoder = OpenRQ.newEncoder(bytes,
					fecParams);
			ByteArrayOutputStream baos = new ByteArrayOutputStream(dataLength);
			for (SourceBlockEncoder sourceBlockEncoder : fecDataEncoder
					.sourceBlockIterable()) {
				// encode the fec source block source packets
				for (EncodingPacket packet : sourceBlockEncoder
						.sourcePacketsIterable()) {
					Log.d(TAG,
							"source packet: "
									+ Arrays.toString(packet.asArray()));

					baos.write(packet.asArray(), packet.asArray().length
							- Constants.FEC_SYMBOL_SIZE,
							Constants.FEC_SYMBOL_SIZE);
				}

				// number of repair symbols
				// (e.g. the number may depend on a channel loss rate)
				int numRepairSymbols = (int) Math.ceil(sourceBlockEncoder
						.numberOfSourceSymbols() * Constants.FEC_DEGREE_REPAIR);

				// encode the fec source block repair packets
				for (EncodingPacket packet : sourceBlockEncoder
						.repairPacketsIterable(numRepairSymbols)) {
					Log.d(TAG,
							"repair packet: "
									+ Arrays.toString(packet.asArray()));

					baos.write(packet.asArray(), packet.asArray().length
							- Constants.FEC_SYMBOL_SIZE,
							Constants.FEC_SYMBOL_SIZE);
				}
			}

			// encode data length as first byte
			byte[] lengthPrefixed = ArrayUtils.concatenate(
					new byte[] { (byte) dataLength }, baos.toByteArray());

			return lengthPrefixed;
		}
	}

	private class DecodeBytesTask extends AsyncTask<Byte, Void, Void> {

		@Override
		protected Void doInBackground(Byte... bytes) {
			// unbox the byte values
			byte[] data = new byte[bytes.length];
			for (int i = 0; i < bytes.length; i++)
				data[i] = bytes[i].byteValue();

			// extract the text content
			String text = null;
			if (mUseFEC)
				data = applyFECDecoding(data);

			if (mUseCompression)
				text = new Smaz().decompress(data);

			if (text == null)
				text = new String(data);

			synchronized (mReceivedText) {
				StringBuilder sb = new StringBuilder(mReceivedText);
				sb.append("\n");
				sb.append(text);

				mReceivedText = sb.toString();
			}

			return null;
		}

		private byte[] applyFECDecoding(byte[] bytes) {
			// data length is encoded as the first bye
			int dataLength = (int) bytes[0];

			FECParameters fecParams = FECParameters.deriveParameters(
					dataLength, Constants.FEC_SYMBOL_SIZE,
					Constants.FEC_MAX_DECODING_BLOCK_BYTES);
			ArrayDataDecoder fecDataDecoder = OpenRQ.newDecoder(fecParams,
					Constants.FEC_EXTRA_SYMBOLS);

			Parsed<EncodingPacket> parsed;
			byte[] packet;
			for (int i = 1; i < bytes.length;) { // begin at the first byte
				// construct a "faked" FEC encoded packet
				packet = new byte[Constants.FEC_PACKET_SIZE];
				packet[Constants.FEC_PACKET_NUMBER_INDEX] = (byte) i;
				packet[packet.length - 1 - Constants.FEC_SYMBOL_SIZE] = (byte) Constants.FEC_SYMBOL_SIZE;
				for (int j = Constants.FEC_SYMBOL_SIZE; j > 0; j--) {
					packet[packet.length - j] = bytes[i++];
				}

				parsed = fecDataDecoder.parsePacket(packet, true);
				if (!parsed.isValid())
					continue;

				fecDataDecoder.sourceBlock(parsed.value().sourceBlockNumber())
						.putEncodingPacket(parsed.value());

				if (fecDataDecoder.isDataDecoded())
					return fecDataDecoder.dataArray();
			}

			return null;
		}
	}
}
