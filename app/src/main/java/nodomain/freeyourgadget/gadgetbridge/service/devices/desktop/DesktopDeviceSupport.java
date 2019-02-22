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
package nodomain.freeyourgadget.gadgetbridge.service.devices.desktop;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
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
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.SetDeviceStateAction;
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

public class DesktopDeviceSupport extends AbstractBTLEDeviceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(DesktopDeviceSupport.class);
    private final UUID deviceService = UUID.fromString("13333333-3333-3333-3333-800000000000");
    private final UUID messageSyncCharacteristic = UUID.fromString("13333333-3333-3333-3333-800000000001");
    private final UUID notificationCharacteristic = UUID.fromString("13333333-3333-3333-3333-800000000002");

    private boolean isMusicAppStarted = false;
    private MusicSpec bufferMusicSpec = null;
    private MusicStateSpec bufferMusicStateSpec = null;

    public DesktopDeviceSupport() {
        super(LOG);
        addSupportedService(deviceService);
        addSupportedService(GattService.UUID_SERVICE_IMMEDIATE_ALERT);
    }

    @Override
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZING, getContext()));
        builder.setGattCallback(this);
        enableNotifications(builder, true)
                .setInitialized(builder);

        return builder;
    }

    public DesktopDeviceSupport enableNotifications(TransactionBuilder builder, boolean enable) {
        BluetoothGattCharacteristic notificationInfo = getCharacteristic(notificationCharacteristic);
        builder.notify(notificationInfo, true);
        BluetoothGattCharacteristic messageSync = getCharacteristic(messageSyncCharacteristic);
        builder.notify(messageSync, true);
        return this;
    }

    private void setInitialized(TransactionBuilder builder) {
        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZED, getContext()));
    }


    @Override
    public boolean useAutoConnect() {
        return true;
    }

    @Override
    public void onNotification(NotificationSpec notificationSpec) {
        LOG.debug("NOTIFICATION!");
        String title = StringUtils.getFirstOf(notificationSpec.sender, notificationSpec.title);
        String message = notificationSpec.body;
        try {
            TransactionBuilder builder = performInitialized("showNotification");
            byte[] msg = new Gson().toJson(notificationSpec).getBytes("utf-8");

            builder.write(getCharacteristic(notificationCharacteristic), msg);
            LOG.info("Showing notification, title: " + title + " message: " + message);
            builder.queue(getQueue());
        } catch (IOException e) {
            LOG.warn("showNotification failed: " + e.getMessage());
        }
    }

    @Override
    public void onSetCallState(CallSpec callSpec) {
        LOG.debug("CALL!");
        String title = callSpec.name;
        String message = callSpec.number;
        try {
            TransactionBuilder builder = performInitialized("setCallState");
            byte[] msg = new Gson().toJson(callSpec).getBytes("utf-8");
            builder.write(getCharacteristic(notificationCharacteristic), msg);
            LOG.info("Showing notification, title: " + title + " message: " + message);
            builder.queue(getQueue());
        } catch (IOException e) {
            LOG.warn("showNotification failed: " + e.getMessage());
        }
    }


    @Override
    public void onSetMusicState(MusicStateSpec stateSpec) {
        DeviceCoordinator coordinator = DeviceHelper.getInstance().getCoordinator(gbDevice);
        if (!coordinator.supportsMusicInfo()) {
            return;
        }

        if (bufferMusicStateSpec != stateSpec) {
            bufferMusicStateSpec = stateSpec;
            sendMusicStateToDevice();
        }

    }

    @Override
    public void onSetMusicInfo(MusicSpec musicSpec) {
        DeviceCoordinator coordinator = DeviceHelper.getInstance().getCoordinator(gbDevice);
        if (!coordinator.supportsMusicInfo()) {
            return;
        }

        if (bufferMusicSpec != musicSpec) {
            bufferMusicSpec = musicSpec;
            sendMusicStateToDevice();
        }

    }


    private void sendMusicStateToDevice() {
        LOG.debug("MUSIC!");

        String title = bufferMusicSpec.artist;
        String message = bufferMusicSpec.track;
        try {
            TransactionBuilder builder = performInitialized("sendMusic");
            byte[] msg = new Gson().toJson(bufferMusicSpec).getBytes("utf-8");
            builder.write(getCharacteristic(notificationCharacteristic), msg);
            LOG.info("Showing notification, title: " + title + " message: " + message);
            builder.queue(getQueue());
        } catch (IOException e) {
            LOG.warn("showNotification failed: " + e.getMessage());
        }
    }

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
        try {
            TransactionBuilder builder = performInitialized("messageSync");
            LOG.info("Characteristic Change " + characteristic.getUuid());
            UUID characteristicUUID = characteristic.getUuid();
            if (messageSyncCharacteristic.equals(characteristicUUID)) {
                String value = new String(characteristic.getValue(), "UTF-8");
                LOG.info(value);
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                Map<String, String> response = new Gson().fromJson(value, type);
                String strLastId = response.get("sync");
                if(strLastId != null) {
                    // getAllSms(getContext(), builder, strLastID);
                }

            } else {
                LOG.info("Unhandled characteristic changed: " + characteristicUUID);
                logMessageContent(characteristic.getValue());
            }
        } catch (IOException e) {
            LOG.warn("showNotification failed: " + e.getMessage());
        }
        return false;

    }

    @Override
    public boolean onCharacteristicRead(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic, int status) {
        return super.onCharacteristicRead(gatt, characteristic, status);
        //TODO: Implement (if necessary)
    }

    @Override
    public boolean onCharacteristicWrite(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
        return super.onCharacteristicWrite(gatt, characteristic, status);
        //TODO: Implement (if necessary)
    }

    public void getAllSms(Context context, TransactionBuilder builder, String lastID) {
        LOG.info(lastID);
        ContentResolver cr = context.getContentResolver();
        try{
            int totalSMS = 0;
            String selection = null;
            if (lastID != "") {
                selection = "_ID > " + lastID;
            }
            Cursor c = cr.query(Telephony.Sms.CONTENT_URI, null, selection, null, Telephony.Sms.Inbox.DEFAULT_SORT_ORDER);
            if (c != null) {
                totalSMS = c.getCount();
                if (c.moveToFirst()) {
                    for (int j = 0; j < totalSMS; j++) {
                        JSONObject obj = new JSONObject();
                        obj.put("id", c.getString(c.getColumnIndexOrThrow(Telephony.Sms._ID)));
                        String smsDate = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.DATE));
                        obj.put("number",c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)));
                        obj.put("body", c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY)));
                        obj.put("dateFormat", smsDate);
                        String type = "";
                        switch (Integer.parseInt(c.getString(c.getColumnIndexOrThrow(Telephony.Sms.TYPE)))) {
                            case Telephony.Sms.MESSAGE_TYPE_INBOX:
                                type = "inbox";
                                break;
                            case Telephony.Sms.MESSAGE_TYPE_SENT:
                                type = "sent";
                                break;
                            case Telephony.Sms.MESSAGE_TYPE_OUTBOX:
                                type = "outbox";
                                break;
                            default:
                                break;
                        }
                        obj.put("type", type);
                        byte[] msg = new Gson().toJson(obj.toString()).getBytes("utf-8");
                        builder.write(getCharacteristic(messageSyncCharacteristic),msg);
                        c.moveToNext();
                    }
                }
                builder.queue(getQueue());
                c.close();

            } else {
                LOG.info("No message to show!");
            }
        } catch (IOException e) {
            LOG.warn("showNotification failed: " + e.getMessage());
        } catch (JSONException e) {
            e.printStackTrace();
        }
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
    public void onSetCannedMessages(CannedMessagesSpec cannedMessagesSpec) {

    }

    @Override
    public void onSendWeather(WeatherSpec weatherSpec) {

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
}
