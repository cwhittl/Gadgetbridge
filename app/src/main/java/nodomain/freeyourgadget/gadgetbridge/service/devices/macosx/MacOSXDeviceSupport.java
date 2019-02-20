/*  Copyright (C) 2018-2019 José Rebelo

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.macosx;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;
import android.net.Uri;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.CalendarEventSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CannedMessagesSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicStateSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.SetDeviceStateAction;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.IntentListener;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfoProfile;

public class MacOSXDeviceSupport extends AbstractBTLEDeviceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(MacOSXDeviceSupport.class);
    private final DeviceInfoProfile<MacOSXDeviceSupport> deviceInfoProfile;
    private final GBDeviceEventVersionInfo versionCmd = new GBDeviceEventVersionInfo();

    private final IntentListener mListener = new IntentListener() {
        @Override
        public void notify(Intent intent) {
            String s = intent.getAction();
            if (s.equals(DeviceInfoProfile.ACTION_DEVICE_INFO)) {
                handleDeviceInfo((nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfo) intent.getParcelableExtra(DeviceInfoProfile.EXTRA_DEVICE_INFO));
            }
        }
    };

    public MacOSXDeviceSupport() {
        super(LOG);
        addSupportedService(UUID.fromString("13333333-3333-3333-3333-333333333337"));

        deviceInfoProfile = new DeviceInfoProfile<>(this);
        deviceInfoProfile.addListener(mListener);
        addSupportedProfile(deviceInfoProfile);
    }

    @Override
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZING, getContext()));

        enableNotifications(builder, true)
                .setInitialized(builder);
        return builder;
    }

    public MacOSXDeviceSupport enableNotifications(TransactionBuilder builder, boolean enable) {
        BluetoothGattCharacteristic deviceInfo = getCharacteristic(UUID.fromString( "13333333-3333-3333-3333-333333333337"));
        builder.notify(deviceInfo, true);
        return this;
    }

    private void setInitialized(TransactionBuilder builder) {
        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZED, getContext()));
    }


    @Override
    public boolean useAutoConnect() {
        return true;
    }

    private void handleDeviceInfo(nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfo info) {
        LOG.warn("Device info: " + info);
        versionCmd.hwVersion = info.getHardwareRevision();
        versionCmd.fwVersion = info.getFirmwareRevision();
        handleGBDeviceEvent(versionCmd);
    }

    @Override
    public void onNotification(NotificationSpec notificationSpec) {
        LOG.debug("notificationSpec: " + notificationSpec);
    }

    @Override
    public void onDeleteNotification(int id) {

    }

    @Override
    public void onSetTime() {

    }

    @Override
    public void onSetAlarms(ArrayList<? extends Alarm> alarms) {

    }

    @Override
    public void onSetCallState(CallSpec callSpec) {

    }

    @Override
    public void onSetCannedMessages(CannedMessagesSpec cannedMessagesSpec) {

    }

    @Override
    public void onSetMusicState(MusicStateSpec stateSpec) {

    }

    @Override
    public void onSetMusicInfo(MusicSpec musicSpec) {

    }

    @Override
    public void onEnableRealtimeSteps(boolean enable) {

    }

    @Override
    public void onInstallApp(Uri uri) {

    }

    @Override
    public void onAppInfoReq() {

    }

    @Override
    public void onAppStart(UUID uuid, boolean start) {

    }

    @Override
    public void onAppDelete(UUID uuid) {

    }

    @Override
    public void onAppConfiguration(UUID appUuid, String config, Integer id) {

    }

    @Override
    public void onAppReorder(UUID[] uuids) {

    }

    @Override
    public void onFetchRecordedData(int dataTypes) {

    }

    @Override
    public void onReset(int flags) {

    }

    @Override
    public void onHeartRateTest() {

    }

    @Override
    public void onEnableRealtimeHeartRateMeasurement(boolean enable) {

    }

    @Override
    public void onFindDevice(boolean start) {

    }

    @Override
    public void onSetConstantVibration(int integer) {

    }

    @Override
    public void onScreenshotReq() {

    }

    @Override
    public void onEnableHeartRateSleepSupport(boolean enable) {

    }

    @Override
    public void onSetHeartRateMeasurementInterval(int seconds) {

    }

    @Override
    public void onAddCalendarEvent(CalendarEventSpec calendarEventSpec) {

    }

    @Override
    public void onDeleteCalendarEvent(byte type, long id) {

    }

    @Override
    public void onSendConfiguration(String config) {

    }

    @Override
    public void onTestNewFunction() {

    }

    @Override
    public void onSendWeather(WeatherSpec weatherSpec) {

    }

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);

        UUID characteristicUUID = characteristic.getUuid();
//        if (XWatchService.UUID_NOTIFY.equals(characteristicUUID)) {
//            byte[] data = characteristic.getValue();
//            if (data[0] == XWatchService.COMMAND_ACTIVITY_TOTALS) {
//                handleSummarizedData(characteristic.getValue());
//            } else if (data[0] == XWatchService.COMMAND_ACTIVITY_DATA) {
//                handleDetailedData(characteristic.getValue());
//            } else if (data[0] == XWatchService.COMMAND_ACTION_BUTTON) {
//                handleButtonPressed(characteristic.getValue());
//            } else if (data[0] == XWatchService.COMMAND_CONNECTED) {
//                handleDeviceInfo(data, BluetoothGatt.GATT_SUCCESS);
//            } else {
//                LOG.info("Handled characteristic with unknown data: " + characteristicUUID);
//                logMessageContent(characteristic.getValue());
//            }
//        } else {
            LOG.info("Unhandled characteristic changed: " + characteristicUUID);
            logMessageContent(characteristic.getValue());
//        }
        return false;
    }

    @Override
    public boolean onCharacteristicRead(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic, int status) {
        return super.onCharacteristicChanged(gatt, characteristic);
        //TODO: Implement (if necessary)
    }

    @Override
    public boolean onCharacteristicWrite(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
        return super.onCharacteristicWrite(gatt, characteristic, status);
        //TODO: Implement (if necessary)
    }
}
