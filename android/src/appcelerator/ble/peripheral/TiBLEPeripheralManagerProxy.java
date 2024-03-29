/*
 * Appcelerator Titanium Mobile - Bluetooth Low Energy (BLE) Module
 * Copyright (c) 2020 by Axway, Inc. All Rights Reserved.
 * Proprietary and Confidential - This source code is not for redistribution
 */
package appcelerator.ble.peripheral;

import static android.content.Context.BIND_AUTO_CREATE;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.RequiresApi;
import appcelerator.ble.KeysConstants;
import appcelerator.ble.TiBLECharacteristicProxy;
import appcelerator.ble.TiBLEServiceProxy;
import appcelerator.ble.receivers.StateBroadcastReceiver;
import java.util.HashMap;
import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import ti.modules.titanium.BufferProxy;

@Kroll.proxy
public class TiBLEPeripheralManagerProxy extends KrollProxy
{
	private static final String LCAT = "TiBLEPeripheralManager";
	private final BluetoothAdapter btAdapter;
	private final StateBroadcastReceiver stateReceiver;
	private TiBLEManagePeripheralService bleService;
	private BluetoothGattService gattServiceToAddInServer;
	private Intent serviceIntent = new Intent(TiApplication.getInstance(), TiBLEManagePeripheralService.class);

	public TiBLEPeripheralManagerProxy()
	{
		btAdapter = BluetoothAdapter.getDefaultAdapter();

		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		stateReceiver = new StateBroadcastReceiver(this);
		getActivity().registerReceiver(stateReceiver, intentFilter);
	}

	@SuppressLint("MissingPermission")
	@Kroll.getProperty(name = "peripheralManagerState")
	public int peripheralManagerState()
	{
		return btAdapter.getState();
	}

	private final ServiceConnection bleServiceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			Log.d(LCAT, "onServiceConnected(): Service is Binded");
			bleService = ((TiBLEManagePeripheralService.LocalBinder) service).getService();
			bleService.initialisePeripheralAndOpenGattServer(TiBLEPeripheralManagerProxy.this);
			bleService.addService(gattServiceToAddInServer);
			gattServiceToAddInServer = null;
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			bleService = null;
			Log.d(LCAT, "onServiceDisconnected(): Service is unBinded");
		}
	};

	private void startAndBindService()
	{
		TiApplication.getInstance().startService(serviceIntent);
		Log.d(LCAT, "startAndBindService(): starting the service");
		TiApplication.getInstance().bindService(serviceIntent, bleServiceConnection, BIND_AUTO_CREATE);
		Log.d(LCAT, "startAndBindService(): binding the service ");
	}

	private void stopAndUnbindService()
	{
		if (bleService != null) {
			TiApplication.getInstance().unbindService(bleServiceConnection);
			Log.d(LCAT, "stopAndUnbindService(): service unbinding initiated");
			TiApplication.getInstance().stopService(serviceIntent);
			Log.d(LCAT, "stopAndUnbindService(): service stopping..");
			bleService = null;
		}
	}

	@Kroll.method
	public TiBLEServiceProxy addService(KrollDict dict)
	{
		TiBLEServiceProxy serviceProxy = TiBLEServiceProxy.createServiceProxy(dict);
		if (serviceProxy == null) {
			Log.e(LCAT, "addService(): Unable to add service");
			return null;
		}
		if (bleService == null) {
			startAndBindService();
			gattServiceToAddInServer = serviceProxy.getService();
			return serviceProxy;
		}
		bleService.addService(serviceProxy.getService());
		return serviceProxy;
	}

	@Kroll.method
	public void removeService(KrollDict dict)
	{
		if (dict == null || !dict.containsKeyAndNotNull(KeysConstants.service.name())) {
			Log.e(LCAT, "removeService(): Cannot remove service, required parameter not provided");
			return;
		}
		if (bleService == null) {
			Log.e(LCAT, "removeService(): Cannot remove service, GATT server not opened");
			return;
		}
		TiBLEServiceProxy serviceProxy = (TiBLEServiceProxy) dict.get(KeysConstants.service.name());
		bleService.removeServiceFromServer(serviceProxy.getService());
	}

	@Kroll.method
	public void removeAllServices()
	{
		if (bleService == null) {
			Log.e(LCAT, "removeAllServices(): Cannot remove all service, GATT server not opened");
			return;
		}
		bleService.removeAllServicesFromServer();
	}

	@Kroll.method
	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	public void startAdvertising(KrollDict dict)
	{
		if (dict == null || !dict.containsKey(KeysConstants.serviceUUIDs.name())
			|| !dict.containsKey(KeysConstants.localName.name())) {
			Log.e(LCAT, "startAdvertising(): Cannot start Advertising, required parameters not provided");
			return;
		}
		if (bleService == null) {
			Log.e(LCAT, "startAdvertising(): Cannot start Advertising, GATT server not opened");
			return;
		}
		Object[] serviceUUIDObjects = (Object[]) dict.get(KeysConstants.serviceUUIDs.name());
		boolean name = (boolean) dict.get(KeysConstants.localName.name());
		if (serviceUUIDObjects != null) {
			String[] uuids = new String[serviceUUIDObjects.length];
			for (int i = 0; i < serviceUUIDObjects.length; i++) {
				uuids[i] = (String) serviceUUIDObjects[i];
			}
			bleService.startAdvertising(uuids, name);
		}
	}

	@Kroll.method
	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	public void stopAdvertising()
	{
		if (bleService == null) {
			Log.e(LCAT, "stopAdvertising(): Cannot stop Advertising, GATT server not opened");
			return;
		}
		bleService.stopAdvertising();
	}

	@Kroll.getProperty(name = "isAdvertising")
	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	public boolean isAdvertising()
	{
		if (bleService == null) {
			Log.e(LCAT, "isAdvertising(): Advertising is not going on, GATT server not opened");
			return false;
		}
		return bleService.isLEAdvertising();
	}

	@Kroll.method(name = "updateValue")
	public boolean updateCharacteristicAndNotifySubscribers(KrollDict dict)
	{
		if (dict == null || !dict.containsKey(KeysConstants.characteristic.name())
			|| !dict.containsKey(KeysConstants.data.name())) {
			Log.e(LCAT,
				  "updateCharacteristicAndNotifySubscribers(): Cannot update value, required parameters not provided");
			return false;
		}
		if (bleService == null) {
			Log.e(LCAT, "updateCharacteristicAndNotifySubscribers(): Cannot update value, GATT server not opened");
			return false;
		}
		TiBLECharacteristicProxy characteristicProxy =
			(TiBLECharacteristicProxy) dict.get(KeysConstants.characteristic.name());
		BluetoothGattCharacteristic characteristic = characteristicProxy.getCharacteristic();
		BufferProxy value = (BufferProxy) dict.get(KeysConstants.data.name());
		if (dict.containsKey(KeysConstants.centrals.name())) {
			Object[] centralsObjects = (Object[]) dict.get(KeysConstants.centrals.name());
			if (centralsObjects != null) {
				TiBLECentralProxy[] centralProxies = new TiBLECentralProxy[centralsObjects.length];
				for (int i = 0; i < centralsObjects.length; i++) {
					centralProxies[i] = (TiBLECentralProxy) centralsObjects[i];
				}
				return bleService.updateCharacteristicAndNotifySubscribers(characteristic, value, centralProxies);
			}

		} else {
			return bleService.updateCharacteristicAndNotifySubscribers(characteristic, value, null);
		}
		return false;
	}

	@Kroll.method(name = "respondToRequest")
	public void respondToCharacteristicRequest(KrollDict dict)
	{
		if (dict == null || !dict.containsKey(KeysConstants.request.name())
			|| !dict.containsKey(KeysConstants.result.name())) {
			Log.e(
				LCAT,
				"respondToCharacteristicRequest(): Cannot respond to characteristic request, required parameters not provided");
			return;
		}
		if (bleService == null) {
			Log.e(LCAT,
				  "respondToCharacteristicRequest(): Cannot respond to characteristic request, GATT server not opened");
			return;
		}
		TiBLECharacteristicRequestProxy requestProxy =
			(TiBLECharacteristicRequestProxy) dict.get(KeysConstants.request.name());
		int result = (int) dict.get(KeysConstants.result.name());
		bleService.sendResponseToCharacteristic(requestProxy, result);
	}

	@Kroll.method
	public void respondToDescriptorRequest(KrollDict dict)
	{
		if (dict == null || !dict.containsKey(KeysConstants.descriptorRequest.name())
			|| !dict.containsKey(KeysConstants.result.name())) {
			Log.e(
				LCAT,
				"respondToDescriptorRequest(): Cannot respond to descriptor request, required parameters not provided");
			return;
		}
		if (bleService == null) {
			Log.e(LCAT, "respondToDescriptorRequest(): Cannot respond to descriptor request, GATT server not opened");
			return;
		}
		TiBLEDescriptorRequestProxy descriptorRequestProxy =
			(TiBLEDescriptorRequestProxy) dict.get(KeysConstants.descriptorRequest.name());
		int result = (int) dict.get(KeysConstants.result.name());
		bleService.sendResponseToDescriptor(descriptorRequestProxy, result);
	}

	@Kroll.method
	public void publishL2CAPChannel(KrollDict dict)
	{
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
			Log.e(
				LCAT,
				"publishL2CAPChannel(): unable to publish. This feature is supported on Android OS Version 'Q' and above.");
			return;
		}
		if (dict == null || !dict.containsKey(KeysConstants.encryptionRequired.name())) {
			Log.e(LCAT, "publishL2CAPChannel(): cannot publish l2cap channel as required params not provided.");
			return;
		}
		if (bleService == null) {
			Log.e(LCAT, "publishL2CAPChannel(): Cannot publish l2cap channel, GATT server not opened.");
			return;
		}

		boolean encryptionRequired = dict.getBoolean(KeysConstants.encryptionRequired.name());
		bleService.publishL2CAPChannel(encryptionRequired, this);
	}

	@Kroll.method
	public void unpublishL2CAPChannel(KrollDict dict)
	{
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
			Log.e(
				LCAT,
				"publishL2CAPChannel(): unable to publish. This feature is supported on Android OS Version 'Q' and above.");
			return;
		}
		if (bleService == null) {
			Log.e(LCAT, "unpublishL2CAPChannel(): Cannot unpublish l2cap channel, GATT server not opened.");
			return;
		}

		bleService.unpublishL2CAPChannel(this);
	}

	public void cleanup()
	{
		try {
			getActivity().unregisterReceiver(stateReceiver);
		} catch (IllegalArgumentException e) {
			Log.e(LCAT, "cleanup(): Error during unregistering the receiver," + e.getMessage());
		}
		if (bleService != null) {
			closePeripheral();
		}
	}

	@Kroll.method
	public void closePeripheral()
	{
		if (bleService == null) {
			Log.e(LCAT, "closePeripheral(): Cannot close peripheral as GATT server already not opened.");
			return;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			if (isAdvertising()) {
				bleService.stopAdvertising();
			}
		}
		bleService.closeGattServer();
		stopAndUnbindService();
	}

	public void bluetoothStateChanged(int state)
	{
		HashMap<String, Integer> dict = new HashMap<>();
		dict.put(KeysConstants.state.name(), state);
		fireEvent(KeysConstants.didUpdateState.name(), dict);
		if (state == BluetoothAdapter.STATE_OFF && bleService != null) {
			closePeripheral();
		}
	}
}
