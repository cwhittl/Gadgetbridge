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
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
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
    private final UUID smsSyncCharacteristic = UUID.fromString("13333333-3333-3333-3333-800000000001");
    private final UUID mmsSyncCharacteristic = UUID.fromString("13333333-3333-3333-3333-800000000002");
    private final UUID conversationSyncCharacteristic = UUID.fromString("13333333-3333-3333-3333-800000000003");
    private final UUID notificationCharacteristic = UUID.fromString("13333333-3333-3333-3333-800000000004");

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
        BluetoothGattCharacteristic conversationSync = getCharacteristic(conversationSyncCharacteristic);
        builder.notify(conversationSync, true);
        BluetoothGattCharacteristic notificationInfo = getCharacteristic(notificationCharacteristic);
        builder.notify(notificationInfo, true);
        BluetoothGattCharacteristic mmsMessageSync = getCharacteristic(mmsSyncCharacteristic);
        builder.notify(mmsMessageSync, true);
        BluetoothGattCharacteristic smsMessageSync = getCharacteristic(smsSyncCharacteristic);
        builder.notify(smsMessageSync, true);
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
            String value = new String(characteristic.getValue(), "UTF-8");
            long daysBack = 10L;
            long date = new Date(System.currentTimeMillis() - daysBack * 24 * 3600 * 1000).getTime();
            LOG.info(value);
            if (smsSyncCharacteristic.equals(characteristicUUID)) {
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                Map<String, String> response = new Gson().fromJson(value, type);
                getAllSms(getContext(), builder, date);
            } else if (mmsSyncCharacteristic.equals(characteristicUUID)) {
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                Map<String, String> response = new Gson().fromJson(value, type);
                getAllMms(getContext(), builder, date);
            } else if (conversationSyncCharacteristic.equals(characteristicUUID)) {
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                Map<String, String> response = new Gson().fromJson(value, type);
                getConversations(getContext(), builder, date);
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
    }


    @Override
    public boolean onCharacteristicWrite(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
        return super.onCharacteristicWrite(gatt, characteristic, status);
        //TODO: Implement (if necessary)
    }

    public void getConversations(final Context context, final TransactionBuilder builder, long pastDatetime) {
        // Integer lastID = Integer.parseInt(strLastID);
        ContentResolver cr = context.getContentResolver();
        Cursor convoCur = cr.query(Uri.parse("content://mms-sms/conversations?simple=true"), null, "date>" + pastDatetime, null, "date DESC");
        if (convoCur != null && convoCur.getCount() > 0) {
            while (convoCur.moveToNext()) {
                Integer count = convoCur.getInt(convoCur.getColumnIndexOrThrow("message_count"));
                if (count > 0) {
                    try {
                        JSONObject conversationObj = new JSONObject();
                        String[] recipients = convoCur.getString(convoCur.getColumnIndexOrThrow("recipient_ids")).split(" ");
                        Boolean isGroup = recipients.length > 1;
                        LOG.info(isGroup + "-" + recipients.length + "--" +convoCur.getString(convoCur.getColumnIndexOrThrow("recipient_ids")));
                        String snippet = convoCur.getString(convoCur.getColumnIndexOrThrow("snippet"));
                        if (!isGroup) {
                            Cursor recipientCur = cr.query(Uri.parse("content://mms-sms/canonical-address/" + recipients[0]), null, null, null, null);
                            if(recipientCur.moveToNext())
                            {
                                String phoneNumber = (recipientCur.getString(recipientCur.getColumnIndexOrThrow("address")));
                                conversationObj = getContactInfo(context, phoneNumber);
                            }
                            recipientCur.close();
                        }
                        final String threadId = convoCur.getString(convoCur.getColumnIndexOrThrow("_id"));
                        conversationObj.put("id", threadId);
                        conversationObj.put("snippet", snippet);
                        conversationObj.put("isGroup", isGroup);

                        conversationObj.put("count", count);
                        conversationObj.put("date", convoCur.getLong(convoCur.getColumnIndexOrThrow("date")));
                        byte[] msg = new Gson().toJson(conversationObj.toString()).getBytes("utf-8");
                        builder.write(getCharacteristic(conversationSyncCharacteristic), msg);
                    } catch (IOException e) {
                        LOG.warn("showNotification failed: " + e.getMessage());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        builder.queue(getQueue());
        convoCur.close();
    }

//    public void getConversationsTEST(final Context context, final TransactionBuilder builder, String strLastID) {
//        // Integer lastID = Integer.parseInt(strLastID);
//        long daysBack = 7L;
//        long date = new Date(System.currentTimeMillis() - daysBack * 24 * 3600 * 1000).getTime();
//        ContentResolver cr = context.getContentResolver();
//        Cursor convoCur = cr.query(Uri.parse("content://mms-sms/conversations"), null, "date>" + date, null, "date DESC");
//        if (convoCur != null && convoCur.getCount() > 0) {
//            while (convoCur.moveToNext()) {
////                Integer count = convoCur.getInt(convoCur.getColumnIndexOrThrow("message_count"));
////                if (count > 0) {
////                    try {
//
//                        final String address = PhoneNumberUtils.normalizeNumber(convoCur.getString(convoCur.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))).replace("+1", "");
//                        final String body = convoCur.getString(convoCur.getColumnIndexOrThrow("body"));
////                        final JSONObject conversationObj = new JSONObject();
//                        final String threadId = convoCur.getString(convoCur.getColumnIndexOrThrow("_id"));
//                LOG.info(address + '-' + body);
////                        String[] recipients = convoCur.getString(convoCur.getColumnIndexOrThrow("recipient_ids")).split(" ");
////                        conversationObj.put("id", threadId);
////                        Boolean isGroup = recipients.length == 0;
////                        conversationObj.put("snippet", convoCur.getString(convoCur.getColumnIndexOrThrow("snippet")));
////                        conversationObj.put("isGroup", isGroup);
////                        conversationObj.put("count", count);
////                        conversationObj.put("date", convoCur.getLong(convoCur.getColumnIndexOrThrow("date")));
////                        byte[] msg = new Gson().toJson(conversationObj.toString()).getBytes("utf-8");
////                        builder.write(getCharacteristic(conversationSyncCharacteristic), msg);
////                    } catch (IOException e) {
////                        LOG.warn("showNotification failed: " + e.getMessage());
////                    } catch (JSONException e) {
////                        e.printStackTrace();
////                    }
////                }
//            }
//        }
//        // builder.queue(getQueue());
//        convoCur.close();
//    }


    public void getAllSms(final Context context, final TransactionBuilder builder, long pastDatetime) {
//        Integer lastID = Integer.parseInt(strLastID);


        ContentResolver cr = context.getContentResolver();
        Cursor smsCur = cr.query(Uri.parse("content://sms/"), null, "date>" + pastDatetime, null, "date DESC");
        while (smsCur.moveToNext()) {
            try {
                JSONObject sms = getSMSInfo(smsCur, context);
                byte[] msg = new Gson().toJson(sms.toString()).getBytes("utf-8");
                builder.write(getCharacteristic(smsSyncCharacteristic), msg);
            } catch (IOException e) {
                LOG.warn("showNotification failed: " + e.getMessage());
            }
        }
        smsCur.close();
        builder.queue(getQueue());
    }

    public void getAllMms(final Context context, final TransactionBuilder builder, long pastDatetime) {
        Date pastDate = new Date(pastDatetime);
//        long pastDate = new Date(System.currentTimeMillis() - daysBack * 24 * 3600 * 1000).getTime();


        ContentResolver cr = context.getContentResolver();
        Cursor mmsCur = cr.query(Uri.parse("content://mms/"), null, null, null, "date DESC");
        LOG.info(mmsCur.getCount() +"-Count");
        while (mmsCur.moveToNext()) {
            try {
                Date smsDate = new Date(mmsCur.getLong(mmsCur.getColumnIndexOrThrow(Telephony.Mms.DATE))* 1000);
//                LOG.info(smsDate + " vs " + pastDate);
                if(smsDate.compareTo(pastDate) > 0) {
                // if(smsDate > pastDate) {
                    JSONObject mms = getMMSInfo(mmsCur, context);
                    byte[] msg = new Gson().toJson(mms.toString()).getBytes("utf-8");
                    builder.write(getCharacteristic(mmsSyncCharacteristic), msg);
                }
            } catch (IOException e) {
                LOG.warn("showNotification failed: " + e.getMessage());
            }
        }
        mmsCur.close();
        builder.queue(getQueue());
    }

//    public void getAllMesssages(final Context context, final TransactionBuilder builder, String strLastID) {
////        Integer lastID = Integer.parseInt(strLastID);
//        long daysBack = 2L;
//        long date = new Date(System.currentTimeMillis() - daysBack * 24 * 3600 * 1000).getTime();
//
//        LOG.info(strLastID);
//        ContentResolver cr = context.getContentResolver();
//        final String[] projection = new String[]{"_id", "ct_t", Telephony.Sms.THREAD_ID,
//                Telephony.Sms.ADDRESS,
//                Telephony.Sms.DATE,
//                Telephony.Sms.TYPE,
//                Telephony.Sms.BODY};
//        Cursor smsCur = cr.query(Uri.parse("content://mms-sms/complete-conversations/"), projection, "date>" + date, null, "date DESC");
//        while (smsCur.moveToNext()) {
//            try {
//                String string = smsCur.getString(smsCur.getColumnIndex("ct_t"));
//                JSONObject obj = new JSONObject();
//                if ("application/vnd.wap.multipart.related".equals(string)) {
//                    obj = getMMSInfo(smsCur, context);
//                } else {
//                    obj = getSMSInfo(smsCur, context);
//                }
//
//                byte[] msg = new Gson().toJson(obj.toString()).getBytes("utf-8");
//                builder.write(getCharacteristic(smsSyncCharacteristic), msg);
//            } catch (IOException e) {
//                LOG.warn("showNotification failed: " + e.getMessage());
//            }
//        }
//        smsCur.close();
//        builder.queue(getQueue());
//    }

//    public void getAllMessages(final Context context, final TransactionBuilder builder, String strLastID) {
//        Integer lastID = Integer.parseInt(strLastID);
//        long daysBack = 2L;
//        final long date = new Date(System.currentTimeMillis() - daysBack * 24 * 3600 * 1000).getTime();
//
//        LOG.info(strLastID);
//        ContentResolver cr = context.getContentResolver();
//        try {
//            // int totalSMS = lastID;
//            String selection = null;
//            if (strLastID != "" && strLastID != "0") {
//                selection = "_ID > " + strLastID;
//            }
//            Uri uri = Uri.parse("content://mms-sms/conversations?simple=true");
//            String[] reqCols = new String[]{"_id", "recipient_ids", "message_count", "snippet", "date", "read"};
//            Cursor cursor = context.getApplicationContext().getContentResolver().query(uri, reqCols, null, null, "date DESC");
//            if (cursor != null && cursor.getCount() > 0) {
//                while (cursor.moveToNext()) {
//                    Integer count = cursor.getInt(cursor.getColumnIndex(reqCols[2]));
//                    if (count > 0) {
//                        final JSONObject conversationObj = new JSONObject();
//                        final String threadId = cursor.getString(cursor.getColumnIndex(reqCols[0]));
//                        String[] recipients = cursor.getString(cursor.getColumnIndex(reqCols[1])).split(" ");
//                        conversationObj.put("id", threadId);
//                        Boolean isGroup = recipients.length == 0;
//                        conversationObj.put("snippet", cursor.getString(cursor.getColumnIndex(reqCols[3])));
//                        conversationObj.put("isGroup", isGroup);
//                        conversationObj.put("count", count);
//                        conversationObj.put("date", cursor.getLong(cursor.getColumnIndex(reqCols[4])));
////                        new Thread(new Runnable() {
////                            @Override
////                            public void run() {
//                        Cursor smsCur = context.getApplicationContext().getContentResolver().query(Uri.parse("content://sms/"), null, "thread_id=" + threadId + " AND date" + ">" + date, null, "date DESC");
//                        while (smsCur.moveToNext()) {
//                            try {
//                                JSONObject sms = DesktopDeviceSupport.this.getSMSInfo(smsCur, context, conversationObj);
//                                byte[] msg = new Gson().toJson(sms.toString()).getBytes("utf-8");
//                                builder.write(DesktopDeviceSupport.this.getCharacteristic(smsSyncCharacteristic), msg);
//                            } catch (IOException e) {
//                                LOG.warn("showNotification failed: " + e.getMessage());
//                            }
//                        }
//                        smsCur.close();
////                            }
////                        }).start();
//
////                        new Thread(new Runnable() {
////                            @Override
////                            public void run() {
//                        Cursor mmsCur = context.getApplicationContext().getContentResolver().query(Uri.parse("content://mms/"), null, "thread_id=" + threadId + " AND date" + ">" + date, null, "date DESC");
//                        while (mmsCur.moveToNext()) {
//                            try {
//                                JSONObject mms = DesktopDeviceSupport.this.getMMSInfo(mmsCur, context, conversationObj);
//                                byte[] msg = new Gson().toJson(mms.toString()).getBytes("utf-8");
//                                builder.write(DesktopDeviceSupport.this.getCharacteristic(smsSyncCharacteristic), msg);
//                            } catch (IOException e) {
//                                LOG.warn("showNotification failed: " + e.getMessage());
//                            }
//                        }
//                        mmsCur.close();
////                            }
////                        }).start();
//                    }
//                }
//            }
//            builder.queue(getQueue());
//            cursor.close();
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
//    }

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

    public JSONObject getContactInfo(Context context, final String phoneNumber)
    {
        Uri uri=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(phoneNumber));

        String[] projection = new String[]{ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts._ID};

        String contactName="";
        String contactEmail="";
        Cursor cursor=context.getContentResolver().query(uri,projection,null,null,null);

        if (cursor != null) {
            if(cursor.moveToFirst()) {
                contactName=cursor.getString(0);
                String id = cursor.getString(1);
                Cursor ce = context.getContentResolver().query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, null,
                        ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?", new String[]{id}, null);
                if (ce != null && ce.moveToFirst()) {
                    contactEmail = ce.getString(ce.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA));
                    ce.close();
                }
            }
            cursor.close();
        }


        if(StringUtils.isEmpty(contactName)) {
            contactName = "Unknown";
        }

        JSONObject contactInfo = new JSONObject();
        try {
            String image = "https://www.gravatar.com/avatar/" + md5(contactEmail) + "?s=55";
            contactInfo.put("image", image);
            contactInfo.put("name", contactName);
            contactInfo.put("email", contactEmail);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return contactInfo;
    }

    private JSONObject getSMSInfo(Cursor smsCur, Context context) {
        JSONObject smsObj = null;
        try{
            smsObj = new JSONObject();
            Integer messageId = smsCur.getInt(smsCur.getColumnIndexOrThrow(Telephony.Sms._ID));
            Integer threadId = smsCur.getInt(smsCur.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID));
            String number = PhoneNumberUtils.normalizeNumber(smsCur.getString(smsCur.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))).replace("+1", "");
            Long smsDate = smsCur.getLong(smsCur.getColumnIndexOrThrow(Telephony.Sms.DATE));
            String direction = "";
            String content = smsCur.getString(smsCur.getColumnIndexOrThrow(Telephony.Sms.BODY));
            switch (Integer.parseInt(smsCur.getString(smsCur.getColumnIndexOrThrow(Telephony.Sms.TYPE)))) {
                case Telephony.Sms.MESSAGE_TYPE_INBOX:
                    direction = "inbox";
                    break;
                case Telephony.Sms.MESSAGE_TYPE_SENT:
                    direction = "sent";
                    break;
                case Telephony.Sms.MESSAGE_TYPE_OUTBOX:
                    direction = "outbox";
                    break;
                default:
                    break;
            }
            smsObj = getContactInfo(context, number);
            smsObj.put("conversationId", threadId);
            smsObj.put("messageId", "sms_" +messageId);
            smsObj.put("date", smsDate);
            smsObj.put("number", number);
            smsObj.put("direction", direction);
            smsObj.put("content", content);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return smsObj;
    }

    private JSONObject getMMSInfo(Cursor mmsCur, Context context) {
        JSONObject mmsObj = null;
        try{
            mmsObj = new JSONObject();
            Integer messageId = mmsCur.getInt(mmsCur.getColumnIndexOrThrow(Telephony.Mms._ID));
            String mtype = mmsCur.getString(mmsCur.getColumnIndex(Telephony.Mms.MESSAGE_TYPE));

            String selectionAdd = new String("msg_id=" + messageId);
            String uriStr = "content://mms/"+messageId+"/addr";
            Uri uriAddress = Uri.parse(uriStr);
            Cursor cAdd = context.getContentResolver().query(uriAddress, null,
                    selectionAdd, null, null);
            String dirtyNumber = null;
            String address = "";
            if (cAdd.moveToFirst()) {
                address = cAdd.getString(cAdd.getColumnIndex("address"));
                if (address != null) {
                    try {
                        Long.parseLong(address.replace("-", ""));
                        dirtyNumber = address;
                    } catch (NumberFormatException nfe) {
                        if (dirtyNumber == null) {
                            dirtyNumber = address;
                        }
                    }
                }
            }
            if (cAdd != null) {
                cAdd.close();
            }

            String direction = "sent";
            if(Integer.parseInt(mtype) == 132) {
                direction = "inbox";
            }

            String number = PhoneNumberUtils.normalizeNumber(dirtyNumber).replace("+1", "");
            long smsDate = mmsCur.getLong(mmsCur.getColumnIndexOrThrow(Telephony.Mms.DATE)) * 1000;
            Integer threadId = mmsCur.getInt(mmsCur.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID));
            String content = getMMSContent(context, messageId);
            mmsObj = getContactInfo(context, number);
            mmsObj.put("messageId", "mms_" +messageId);
            mmsObj.put("conversationId", threadId);
            mmsObj.put("date", smsDate);
            mmsObj.put("number", number);
            mmsObj.put("direction", direction);
            if (content == "") {
                mmsObj.put("content", " [Multimedia Item] ");
            } else {
                mmsObj.put("content", content);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return mmsObj;
    }

    private String getMMSContent(Context context, Integer id) {
        String body = "";
        String selectionPart = new String ("mid = '" + id.toString() + "'");
        Cursor curPart = context.getContentResolver (). query (Uri.parse ("content://mms/part"), null, selectionPart, null, null);
        while(curPart.moveToNext())
        {
            if(curPart.getString(3).equals("image/jpeg")
                    || curPart.getString(3).equals("image/png")
                    || curPart.getString(3).equals("image/gif"))
            {
                body = body + " [Image] ";
            }
            else if ("text/plain".equals(curPart.getString(3))) {
                String data = curPart.getString(curPart.getColumnIndex("_data"));
                if (data != null) {
                    body = body + getMmsText(context, curPart.getString(0));
                } else {
                    body = body + curPart.getString(curPart.getColumnIndex("text"));
                }
            }
        }
        curPart.close();
        return body;
    }
    private String getMmsText(Context context, String id) {
        Uri partURI = Uri.parse("content://mms/part/" + id);
        InputStream is = null;
        StringBuilder sb = new StringBuilder();
        try {
            is = context.getContentResolver().openInputStream(partURI);
            if (is != null) {
                InputStreamReader isr = new InputStreamReader(is, "UTF-8");
                BufferedReader reader = new BufferedReader(isr);
                String temp = reader.readLine();
                while (temp != null) {
                    sb.append(temp);
                    temp = reader.readLine();
                }
            }
        } catch (IOException e) {}
        finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {}
            }
        }
        return sb.toString();
    }

    private String getANumber(Context context, int id) {
        String add = "";
        String type = "";
        final String[] projection = new String[] {"address","contact_id","charset","type"};
        final String selection = "type=137 or type=151"; // PduHeaders
        Uri.Builder builder = Uri.parse("content://mms").buildUpon();
        builder.appendPath(String.valueOf(id)).appendPath("addr");

        Cursor cursor = context.getContentResolver().query(
                builder.build(),
                projection,
                selection,
                null, null);

        if (cursor.moveToFirst()) {
            do {
                add = cursor.getString(cursor.getColumnIndex("address"));
                type = cursor.getString(cursor.getColumnIndex("type"));
            } while(cursor.moveToNext());
        }
        // Outbound messages address type=137 and the value will be 'insert-address-token'
        // Outbound messages address type=151 and the value will be the address
        // Additional checking can be done here to return the correct address.
        return add;
    }

    public String md5(String s) {
        try {
            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            // Create Hex String
            StringBuffer hexString = new StringBuffer();
            for (int i=0; i<messageDigest.length; i++)
                hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }
}
