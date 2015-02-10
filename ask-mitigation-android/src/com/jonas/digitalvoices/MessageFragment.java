package com.jonas.digitalvoices;

import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.jonas.digitalvoices.modem.ModemService;

public class MessageFragment extends Fragment {

	private boolean mIsBound = false;
	private ModemService mModemService;
	private ServiceConnection mModemConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			mModemService = ((ModemService.ModemBinder) service).getService();
			mIsBound = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			mModemService = null;
			mIsBound = false;
		}
	};

	String mSentText = null;
	Uri mCreateDataUri = null;
	String mCreateDataType = null;
	String mCreateDataExtraText = null;

	private Timer mRefreshTimer = null;
	private Handler mHandler = new Handler();

	/** UI elements. */
	private EditText mEditTextToPlay;
	private CheckBox mCheckBoxUseCompression, mCheckBoxUseFEC;
	private Button mButtonPlay, mButtonListen;
	private TextView mTextViewStatus, mTextViewListen;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View rootView = (View) inflater.inflate(R.layout.fragment_main,
				container, false);

		mEditTextToPlay = (EditText) rootView.findViewById(R.id.EditTextToPlay);

		mCheckBoxUseCompression = (CheckBox) rootView
				.findViewById(R.id.CheckBoxUseCompression);
		mCheckBoxUseFEC = (CheckBox) rootView.findViewById(R.id.CheckBoxUseFEC);

		mButtonPlay = (Button) rootView.findViewById(R.id.ButtonPlay);
		mButtonPlay.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				String str = mEditTextToPlay.getText().toString().trim();

				if (str.matches("")) {
					Toast.makeText(getActivity(), R.string.empty_text_warning,
							Toast.LENGTH_SHORT).show();
					return; // nothing to play
				}

				mModemService.playData(str,
						mCheckBoxUseCompression.isChecked(),
						mCheckBoxUseFEC.isChecked());
			}
		});

		mButtonListen = (Button) rootView.findViewById(R.id.ButtonListen);
		mButtonListen.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (mModemService.isListening())
					mModemService.stopListening();

				else
					mModemService.listen();
			}
		});

		mTextViewStatus = (TextView) rootView.findViewById(R.id.TextViewStatus);
		mTextViewListen = (TextView) rootView.findViewById(R.id.TextViewListen);

		final Intent intent = getActivity().getIntent();
		final String action = intent.getAction();
		if (Intent.ACTION_SEND.equals(action)) {

			mCreateDataUri = intent.getData();
			mCreateDataType = intent.getType();

			if (mCreateDataUri == null) {
				mCreateDataUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);

			}

			mCreateDataExtraText = intent.getStringExtra(Intent.EXTRA_TEXT);

			if (mCreateDataUri == null)
				mCreateDataType = null;

			// The new entry was created, so assume all will end well and
			// set the result to be returned.
			getActivity().setResult(Activity.RESULT_OK,
					(new Intent()).setAction(null));
		}

		return rootView;
	}

	@Override
	public void onStart() {
		super.onStart();

		Intent intent = new Intent(getActivity(), ModemService.class);
		getActivity().bindService(intent, mModemConnection,
				Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onStop() {
		super.onStop();

		if (mIsBound) {
			getActivity().unbindService(mModemConnection);
			mIsBound = false;
		}
	}

	@Override
	public void onPause() {
		super.onPause();

		if (mRefreshTimer != null) {
			mRefreshTimer.cancel();
			mRefreshTimer = null;
		}

		mModemService.stopListening();
	}

	@Override
	public void onResume() {
		super.onResume();

		String sentText = null;

		if (mCreateDataExtraText != null) {
			sentText = mCreateDataExtraText;

		} else if (mCreateDataType != null
				&& mCreateDataType.startsWith("text/")) {
			// read the URI into a string

			byte[] b = readDataFromUri(this.mCreateDataUri);
			if (b != null)
				mSentText = new String(b);

		}

		if (sentText != null) {
			mEditTextToPlay.setText(sentText);
		}

		mRefreshTimer = new Timer();

		mRefreshTimer.schedule(new TimerTask() {
			@Override
			public void run() {

				mHandler.post(new Runnable() // have to do this on the UI thread
				{
					public void run() {
						updateResults();
					}
				});

			}
		}, 500, 500);

	}

	private void updateResults() {
		if (mModemService != null && mModemService.isListening()) {
			mTextViewStatus.setText(mModemService.getBacklogStatus());
			mTextViewListen.setText(mModemService.getReceivedText());

			mButtonListen.setText(R.string.button_text_stop_listening);

		} else {
			mTextViewStatus.setText("");
			mButtonListen.setText(R.string.button_text_listen);
		}
	}

	private byte[] readDataFromUri(Uri uri) {
		byte[] buffer = null;

		try {
			InputStream stream = getActivity().getContentResolver()
					.openInputStream(uri);

			int bytesAvailable = stream.available();
			// int maxBufferSize = 1024;
			int bufferSize = bytesAvailable; // Math.min(bytesAvailable,
												// maxBufferSize);
			int totalRead = 0;
			buffer = new byte[bufferSize];

			// read file and write it into form...
			int bytesRead = stream.read(buffer, 0, bufferSize);
			while (bytesRead > 0) {
				bytesRead = stream.read(buffer, totalRead, bufferSize);
				totalRead += bytesRead;
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();

		}

		return buffer;
	}
}