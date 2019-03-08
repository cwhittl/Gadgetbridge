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
                    getAllSms(getContext(), builder, strLastId);
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
    }


    @Override
    public boolean onCharacteristicWrite(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
        return super.onCharacteristicWrite(gatt, characteristic, status);
        //TODO: Implement (if necessary)
    }

    public void getAllSms(Context context, TransactionBuilder builder, String strLastID) {
        Integer lastID = Integer.parseInt(strLastID);
        long daysBack = 2L;
        long date = new Date(System.currentTimeMillis() - daysBack * 24 * 3600 * 1000).getTime();

        LOG.info(strLastID);
        ContentResolver cr = context.getContentResolver();
        try{
            // int totalSMS = lastID;
            String selection = null;
            if (strLastID != "" && strLastID != "0") {
                selection = "_ID > " + strLastID;
            }
//            Cursor c = context.getApplicationContext().getContentResolver().query(Telephony.Threads.CONTENT_URI, null, null, null, "date DESC");
//            if (c != null && c.getCount() > 0) {
//                while (c.moveToNext()) {
//                    JSONObject obj = new JSONObject();
//                    String number = PhoneNumberUtils.normalizeNumber(c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))).replace("+1", "");
//                    if(number != "") {
//                        String threadId = c.getString(c.getColumnIndexOrThrow(Telephony.Sms._ID));
//                        //String smsDate = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.DATE));
//                        obj.put("number", number);
//                        Cursor smsCur = cr.query(Uri.parse("content://sms/"), null,  "thread_id=" + threadId, null, "date DESC");
//                        while (smsCur.moveToNext()) {
//                            String messageId = c.getString(c.getColumnIndexOrThrow(Telephony.Mms._ID));
//                            LOG.info("SMS_ID" + messageId);
//                        }
//                        Cursor mmsCur = cr.query(Uri.parse("content://mms/"), null,  "thread_id=" + threadId, null, "date DESC");
//                        while (mmsCur.moveToNext()) {
//                            String messageId = c.getString(c.getColumnIndexOrThrow(Telephony.Mms._ID));
//                            LOG.info("MMS_ID" + messageId);
//                        }
                        //obj.put("body", c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY)));
                        //obj.put("dateFormat", smsDate);
                        // String type = "";
//                        switch (Integer.parseInt(c.getString(c.getColumnIndexOrThrow(Telephony.Sms.TYPE)))) {
//                            case Telephony.Sms.MESSAGE_TYPE_INBOX:
//                                type = "inbox";
//                                break;
//                            case Telephony.Sms.MESSAGE_TYPE_SENT:
//                                type = "sent";
//                                break;
//                            case Telephony.Sms.MESSAGE_TYPE_OUTBOX:
//                                type = "outbox";
//                                break;
//                            default:
//                                break;
//                        }
//                        obj.put("type", type);
//                        LOG.info(obj.toString());
//                    }
//                }
//            }
//            builder.queue(getQueue());
//            c.close();
            Uri uri = Uri.parse("content://mms-sms/conversations?simple=true");
            String[] reqCols = new String[] { "_id", "recipient_ids", "message_count", "snippet", "date", "read" };
            Cursor cursor = context.getApplicationContext().getContentResolver().query(uri, reqCols, null, null, "date DESC");
            if (cursor != null && cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    Integer count = cursor.getInt(cursor.getColumnIndex(reqCols[2]));
                    if(count > 0) {
                        JSONObject obj = new JSONObject();
                        String threadId = cursor.getString(cursor.getColumnIndex(reqCols[0]));
                        String [] recipients = cursor.getString(cursor.getColumnIndex(reqCols[1])).split(" ");
                        obj.put("id", threadId);
                        Boolean isGroup = recipients.length == 0;
                        obj.put("snippet", cursor.getString(cursor.getColumnIndex(reqCols[3])));
                        obj.put("isGroup", isGroup);
                        obj.put("count", count);
                        obj.put("date", cursor.getLong(cursor.getColumnIndex(reqCols[4])));
                        List<JSONObject> messages = new ArrayList<JSONObject>();
                        Cursor smsCur = context.getApplicationContext().getContentResolver().query(Uri.parse("content://sms/"), null, "thread_id=" + threadId + " AND date" + ">" + date, null, "date DESC");
                        while (smsCur.moveToNext()) {
//                            Integer messageId = smsCur.getInt(smsCur.getColumnIndexOrThrow(Telephony.Sms._ID));
//                            String number = PhoneNumberUtils.normalizeNumber(smsCur.getString(smsCur.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))).replace("+1", "");
//                            String smsDate = smsCur.getString(smsCur.getColumnIndexOrThrow(Telephony.Sms.DATE));
//                            String direction = "";
//                            String content = smsCur.getString(smsCur.getColumnIndexOrThrow(Telephony.Sms.BODY));
//                            switch (Integer.parseInt(smsCur.getString(smsCur.getColumnIndexOrThrow(Telephony.Sms.TYPE)))) {
//                                case Telephony.Sms.MESSAGE_TYPE_INBOX:
//                                    direction = "inbox";
//                                    break;
//                                case Telephony.Sms.MESSAGE_TYPE_SENT:
//                                    direction = "sent";
//                                    break;
//                                case Telephony.Sms.MESSAGE_TYPE_OUTBOX:
//                                    direction = "outbox";
//                                    break;
//                                default:
//                                    break;
//                            }
//                            obj.put("direction", direction);
//                            JSONObject smsObj = getContactInfo(context, number);
//                            smsObj.put("messageId", messageId);
//                            smsObj.put("date", smsDate);
//                            smsObj.put("number", number);
//                            smsObj.put("direction", direction);
//                            smsObj.put("content", content);
                            messages.add(getSMSInfo(smsCur, context));
                        }
                        smsCur.close();
                        Cursor mmsCur = context.getApplicationContext().getContentResolver().query(Uri.parse("content://mms/"), null, "thread_id=" + threadId + " AND date" + ">" + date, null, "date DESC");
                        while (mmsCur.moveToNext()) {
//                            Integer messageId = mmsCur.getInt(mmsCur.getColumnIndexOrThrow(Telephony.Mms._ID));
//                            String number = PhoneNumberUtils.normalizeNumber(getAddressNumber(context, messageId)).replace("+1", "");
//                            String smsDate = mmsCur.getString(mmsCur.getColumnIndexOrThrow(Telephony.Mms.DATE));
//                            String content = getMMSContent(context, messageId);
//                            JSONObject mmsObj = getContactInfo(context, number);
//                            mmsObj.put("messageId", messageId);
//                            mmsObj.put("date", smsDate);
//                            mmsObj.put("number", number);
////                            mmsObj.put("direction", direction);
//                            if (content == "") {
//                                mmsObj.put("content", "[Multimedia Item]");
//                            } else {
//                                mmsObj.put("content", content);
//                            }
                            messages.add(getMMSInfo(mmsCur, context));
                        }
                        mmsCur.close();
                        JSONArray sortedMessages = new JSONArray();
                        Collections.sort(messages, new Comparator<JSONObject>() {
                            //You can change "Name" with "ID" if you want to sort by ID
                            private static final String KEY_NAME = "date";

                            @Override
                            public int compare(JSONObject a, JSONObject b) {
                                String valA = new String();
                                String valB = new String();

                                try {
                                    valA = (String) a.get(KEY_NAME);
                                    valB = (String) b.get(KEY_NAME);
                                } catch (JSONException e) {
                                    //do something
                                }

                                return -valA.compareTo(valB);
                                //if you want to change the sort order, simply use the following:
                                //return -valA.compareTo(valB);
                            }
                        });

                        for (int i = 0; i < messages.size(); i++) {
                            sortedMessages.put(messages.get(i));
                        }
                        obj.put("messages", sortedMessages);
                        LOG.info(obj.toString());
                        byte[] msg = new Gson().toJson(obj.toString()).getBytes("utf-8");
                        builder.write(getCharacteristic(messageSyncCharacteristic),msg);
                    }
                }
            }
            builder.queue(getQueue());
            cursor.close();


//            Cursor c = cr.query(Telephony.Sms.CONTENT_URI, null, selection, null, Telephony.Sms.Inbox.DEFAULT_SORT_ORDER);
//            if (c != null) {
//                int totalSMS = c.getCount();
//                if (c.moveToFirst()) {
//                    for (int j = 0; j < totalSMS; j++) {
//                        String number = PhoneNumberUtils.normalizeNumber(c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))).replace("+1", "");
//                        JSONObject obj = getContactInfo(number, context);
//                        obj.put("id", c.getString(c.getColumnIndexOrThrow(Telephony.Sms._ID)));
//                        String smsDate = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.DATE));
//                        obj.put("number",number);
//                        obj.put("body", c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY)));
//                        obj.put("dateFormat", smsDate);
//                        String type = "";
//                        switch (Integer.parseInt(c.getString(c.getColumnIndexOrThrow(Telephony.Sms.TYPE)))) {
//                            case Telephony.Sms.MESSAGE_TYPE_INBOX:
//                                type = "inbox";
//                                break;
//                            case Telephony.Sms.MESSAGE_TYPE_SENT:
//                                type = "sent";
//                                break;
//                            case Telephony.Sms.MESSAGE_TYPE_OUTBOX:
//                                type = "outbox";
//                                break;
//                            default:
//                                break;
//                        }
//                        obj.put("type", type);
//                        byte[] msg = new Gson().toJson(obj.toString()).getBytes("utf-8");
//                        builder.write(getCharacteristic(messageSyncCharacteristic),msg);
//                        c.moveToNext();
//                    }
//                }
//                builder.queue(getQueue());
//                c.close();
//
//            } else {
//                LOG.info("No message to show!");
//            }
        } catch (IOException e) {
            LOG.warn("showNotification failed: " + e.getMessage());
        } catch (JSONException e) {
            e.printStackTrace();
        }
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

    private JSONObject getSMSInfo(Cursor smsCur, Context context) {
        JSONObject smsObj = new JSONObject();
        try{
            Integer messageId = smsCur.getInt(smsCur.getColumnIndexOrThrow(Telephony.Sms._ID));
            String number = PhoneNumberUtils.normalizeNumber(smsCur.getString(smsCur.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))).replace("+1", "");
            String smsDate = smsCur.getString(smsCur.getColumnIndexOrThrow(Telephony.Sms.DATE));
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
            smsObj.put("messageId", messageId);
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
        JSONObject mmsObj = new JSONObject();
        try {
            Integer messageId = mmsCur.getInt(mmsCur.getColumnIndexOrThrow(Telephony.Mms._ID));
            String selectionAdd = new String("msg_id=" + messageId);
            String uriStr = "content://mms/"+messageId+"/addr";
            Uri uriAddress = Uri.parse(uriStr);
            Cursor cAdd = context.getContentResolver().query(uriAddress, null,
                    selectionAdd, null, null);
            String dirtyNumber = null;
            String type = "";
            String address = "";
            if (cAdd.moveToLast()) {
                do {
                    address = cAdd.getString(cAdd.getColumnIndex("address"));
                    type = cAdd.getString(cAdd.getColumnIndex("type"));
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
                } while (cAdd.moveToPrevious());
            }
            if (cAdd != null) {
                cAdd.close();
            }

            String direction = "sent";
            if(Integer.parseInt(type) == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                direction = "inbox";
            }

            String number = PhoneNumberUtils.normalizeNumber(dirtyNumber).replace("+1", "");
            String smsDate = mmsCur.getString(mmsCur.getColumnIndexOrThrow(Telephony.Mms.DATE));
            String content = getMMSContent(context, messageId);
            mmsObj = getContactInfo(context, number);
            mmsObj.put("messageId", messageId);
            mmsObj.put("date", smsDate);
            mmsObj.put("number", number);
            mmsObj.put("direction", direction);
            if (content == "") {
                mmsObj.put("content", "[Multimedia Item]");
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
                body = body + "[Image]";
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
