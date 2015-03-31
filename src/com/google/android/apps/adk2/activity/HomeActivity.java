/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.android.apps.adk2.activity;

import java.io.BufferedReader;
// import java.io.ByteArrayInputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
// import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context; /* new */
import android.content.Intent;
import android.content.IntentFilter;
//import android.content.Intent;
//import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.Handler.Callback;
import android.os.Message;
// import android.preference.PreferenceManager;
import android.util.Log;
//import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
//import android.widget.Button;
//import android.widget.EditText;
//import android.widget.ImageView;
//import android.widget.TextView;

// import com.android.future.usb.UsbAccessory;
import android.hardware.usb.UsbAccessory;
// import com.android.future.usb.UsbManager;
import android.hardware.usb.UsbManager;

import com.google.android.apps.adk2.ADK;
import com.google.android.apps.adk2.R;
import com.google.android.apps.adk2.Utilities;

public class HomeActivity extends Activity implements OnClickListener,
		Callback, Runnable {

	private Handler mDeviceHandler;
	private Handler mSettingsPollingHandler;
	private UsbManager mUSBManager;
	private UsbAccessory mAccessory;

	private FileInputStream mInputStream;
	private FileOutputStream mOutputStream;
	private ParcelFileDescriptor mFileDescriptor;

	private boolean mPollSettings = false;

	private static final String ACTION_USB_PERMISSION = "com.google.android.DemoKit.action.USB_PERMISSION";

	private static final boolean gLogPackets = true;

	static final int DIALOG_NO_PRESETS_ID = 0;

	private static HomeActivity sHomeActivity = null;

	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
				// UsbAccessory accessory = UsbManager.getAccessory(intent);
				UsbAccessory accessory = (UsbAccessory) intent
						.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
				if (accessory != null && accessory.equals(mAccessory)) {
					Log.i(ADK.TAG, "Accessory Detached");
					closeAccessory();
				}
			}
		}
	};

	public static HomeActivity get() {
		return sHomeActivity;
	}

	public boolean startPollingSettings() {
		boolean wasPolling = mPollSettings;
		mPollSettings = true;
		if (!wasPolling) {
			pollSettings();
		}
		return wasPolling;
	}

	public void stopPollingSettings() {
		mPollSettings = false;
	}

	@Override
	protected void onNewIntent(Intent intent) {

		Log.i(ADK.TAG, "onNewIntent, new intent received");
		
		String action = intent.getAction();
		if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
			UsbAccessory accessory = (UsbAccessory) intent
					.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
			if (accessory != null) {
				openAccessory(accessory);
			}
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Log.i(ADK.TAG, "HomeActivity OnCreate");

		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mUsbReceiver, filter);

		setContentView(R.layout.home);
		// setContentView(R.layout.connect);
		// mBluetoothButton = (Button)
		// findViewById(R.id.connect_bluetooth_button);
		// mBluetoothButton.setOnClickListener(this);

		mDeviceHandler = new Handler(this);
		mSettingsPollingHandler = new Handler(this);

		mUSBManager = (UsbManager) getSystemService(Context.USB_SERVICE);

		sHomeActivity = this;
		
		connectToAccessory();
		
		startPollingSettings();
	}

	// private void disconnect() {
	// finish();
	// }

	private void pollSettings() {
		if (mPollSettings) {
			// sendCommand(CMD_SETTINGS, CMD_SETTINGS);
			// sendCommand(CMD_DISPLAY_MODE, CMD_DISPLAY_MODE);
			// sendCommand(CMD_LOCK, CMD_LOCK);
			Message msg = mSettingsPollingHandler.obtainMessage(99);
			if (!mSettingsPollingHandler.sendMessageDelayed(msg, 500)) {
				Log.e(ADK.TAG, "faled to queue settings message");
			}
		}
	}

	@Override
	public void onPause() {
		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
		pollSettings();
	}

	@Override
	public void onDestroy() {
		sHomeActivity = null;
		closeAccessory();
		super.onDestroy();
	}

	public void onClick(View v) {
		// switch (v.getId()) {
	}

	public void connectToAccessory() {
		// bail out if we're already connected
		if (mAccessory != null) {
			Log.i(ADK.TAG, "connectToAccessory, mAccessory not null, already connected.");
			return;
		}

		UsbAccessory[] accessories = mUSBManager.getAccessoryList();
		UsbAccessory accessory = (accessories == null ? null : accessories[0]);
		if (accessory != null) {
			if (mUSBManager.hasPermission(accessory)) {
				openAccessory(accessory);
			} else {
				// synchronized (mUsbReceiver) {
				// if (!mPermissionRequestPending) {
				// mUsbManager.requestPermission(accessory,
				// mPermissionIntent);
				// mPermissionRequestPending = true;
				// }
				// }
			}
		} else {
			Log.i(ADK.TAG, "accessory is null");
		}
	}

	public void disconnectFromAccessory() {
		closeAccessory();
	}

	private void openAccessory(UsbAccessory accessory) {

		mFileDescriptor = mUSBManager.openAccessory(accessory);

		if (mFileDescriptor != null) {
			mAccessory = accessory;

			FileDescriptor fd = mFileDescriptor.getFileDescriptor();
			mInputStream = new FileInputStream(fd);
			mOutputStream = new FileOutputStream(fd);

			mAccessory = accessory;
			
			performPostConnectionTasks();
		}
	}

	private void closeAccessory() {

		if (mInputStream != null) {
			try {
				mInputStream.close();
			} catch (IOException e) {
			} finally {
				mInputStream = null;
			}
			
		}

		if (mOutputStream != null) {
			try {
				mOutputStream.close();
			} catch (IOException e) {
			} finally {
				mOutputStream = null;
			}
		}

		if (mFileDescriptor != null) {
			try {
				mFileDescriptor.close();
			} catch (IOException e) {
			} finally {
				mFileDescriptor = null;
			}
		}
	}

	private void performPostConnectionTasks() {

		Thread thread = new Thread(null, this, "ADK_2012");
		thread.start();
		String str = new String("hello\n");
		
		StreamWriter writer = new StreamWriter(mOutputStream, str);
		Thread write_th = new Thread(null, writer, "Writer");
		write_th.start();
		
        synchronized(write_th){
            try{
            	write_th.wait(500);
            } catch (InterruptedException e){
                e.printStackTrace();
            }
        }
	}

	public void run() {
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(mInputStream));
		
		while (true) {
			try {
				String string = reader.readLine();
				Message msg = mDeviceHandler.obtainMessage(0, string);
				mDeviceHandler.sendMessage(msg);
			}
			catch (IOException e)
			{
				break;
			}
		};
	}

	public byte[] sendCommand(int command, int sequence, byte[] payload,
			byte[] buffer) {
		int bufferLength = payload.length + 4;
		if (buffer == null || buffer.length < bufferLength) {
			Log.i(ADK.TAG, "allocating new command buffer of length "
					+ bufferLength);
			buffer = new byte[bufferLength];
		}

		buffer[0] = (byte) command;
		buffer[1] = (byte) sequence;
		buffer[2] = (byte) (payload.length & 0xff);
		buffer[3] = (byte) ((payload.length & 0xff00) >> 8);
		if (payload.length > 0) {
			System.arraycopy(payload, 0, buffer, 4, payload.length);
		}
		if (mOutputStream != null && buffer[1] != -1) {
			try {
				if (gLogPackets) {
					Log.i(ADK.TAG,
							"sendCommand: "
									+ Utilities
											.dumpBytes(buffer, buffer.length));
				}
				mOutputStream.write(buffer);
			} catch (IOException e) {
				Log.e(ADK.TAG, "accessory write failed", e);
			}
		}
		return buffer;
	}

	public void sendCommand(int command, int sequence, byte[] payload) {
		sendCommand(command, sequence, payload, null);
	}

	public boolean handleMessage(Message msg) {
		
		if (msg.getTarget() == mDeviceHandler) {
			// return handleDeviceMethod(msg);
			return true;
		} else {
			pollSettings();
			return true;
		}
	}

	public Object getAccessory() {
		return mAccessory;
	}
	
	class StreamWriter implements Runnable {
		
		FileOutputStream mOutputStream;
		byte[] mb;
		boolean mDone;
		
		public StreamWriter(FileOutputStream o, String s) {
			
			mOutputStream = o;
			mb = s.getBytes();
		}
		
		@Override
		public void run(){
			synchronized(this) {
				
				mDone = false;
				
				try {
					mOutputStream.write(mb);
					mDone = true;
				}
				catch (IOException e) {
					
				}
				notify();
			}
		}
		
		public synchronized boolean done(){
			return mDone;
		}
	}
}
