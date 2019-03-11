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
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
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
    private final UUID conversationSyncCharacteristic = UUID.fromString("13333333-3333-3333-3333-800000000002");
    private final UUID notificationCharacteristic = UUID.fromString("13333333-3333-3333-3333-800000000003");

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
        BluetoothGattCharacteristic mmsMessageSync = getCharacteristic(messageSyncCharacteristic);
        builder.notify(mmsMessageSync, true);
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

            LOG.info("Characteristic Change " + characteristic.getUuid());
            UUID characteristicUUID = characteristic.getUuid();
//            String value = new String(characteristic.getValue(), "UTF-8");
//            JSONObject commands = new JSONObject(value);
            long daysBack = 10L;
            long date = new Date(System.currentTimeMillis() - daysBack * 24 * 3600 * 1000).getTime();
//            LOG.info(value);
            if (messageSyncCharacteristic.equals(characteristicUUID)) {
                // if(commands.get("s").toString() != null) {
                    TransactionBuilder builder = performInitialized("messageSync");
                    getAllMessages(getContext().getContentResolver(), builder, date, "ALL");
                //}
            } else if (notificationCharacteristic.equals(characteristicUUID)) {
                //TBD
            } else if (conversationSyncCharacteristic.equals(characteristicUUID)) {
                TransactionBuilder builder = performInitialized("convo");
                getConversations(getContext(), builder, date);
            } else {
                LOG.info("Unhandled characteristic changed: " + characteristicUUID);
                logMessageContent(characteristic.getValue());
            }
        } catch (IOException e) {
            LOG.warn("showNotification failed: " + e.getMessage());
//        } catch (JSONException e) {
//            LOG.warn("showNotification failed: " + e.getMessage());
        }
        return true;

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
                        if (!isGroup) {
                            Cursor recipientCur = cr.query(Uri.parse("content://mms-sms/canonical-address/" + recipients[0]), null, null, null, null);
                            if(recipientCur.moveToNext())
                            {
                                conversationObj.put("number", recipientCur.getString(recipientCur.getColumnIndexOrThrow("address")));
                                conversationObj = addContactInfo(cr, conversationObj);
                            }
                            recipientCur.close();
                        }
                        final String threadId = convoCur.getString(convoCur.getColumnIndexOrThrow("_id"));
                        String snippet = convoCur.getString(convoCur.getColumnIndexOrThrow("snippet"));
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


    private List<JSONObject> getAllSms(final ContentResolver cr, long pastDatetime, List<JSONObject> allMessages) {
        Cursor smsCur = cr.query(Uri.parse("content://sms/"), null, "date>" + pastDatetime, null, "date DESC");
        while (smsCur.moveToNext()) {
            allMessages.add(getSMSInfo(smsCur, cr));
        }
        smsCur.close();
        return allMessages;
    }

    private List<JSONObject> getAllMms(final ContentResolver cr, long pastDatetime, List<JSONObject> allMessages) {
        Date pastDate = new Date(pastDatetime);
        Cursor mmsCur = cr.query(Uri.parse("content://mms/"), null, null, null, "date DESC");
        while (mmsCur.moveToNext()) {
            Date smsDate = new Date(mmsCur.getLong(mmsCur.getColumnIndexOrThrow(Telephony.Mms.DATE))* 1000);
            if(smsDate.compareTo(pastDate) > 0) {
                allMessages.add(getMMSInfo(mmsCur, cr));
            }
        }
        mmsCur.close();
        return allMessages;
    }

    private void getAllMessages(final ContentResolver cr, TransactionBuilder builder, long pastDatetime, final String type) {
        List<JSONObject> allMessages = new ArrayList<JSONObject>();
        if(type == "SMS" || type == "ALL") {
            allMessages = getAllSms(cr, pastDatetime, allMessages);
        }

        if(type == "MMS" || type == "ALL") {
            allMessages = getAllMms(cr, pastDatetime, allMessages);
        }

        Collections.sort( allMessages, sortByDateDesc());

        for (int i = 0; i < allMessages.size(); i++) {
            try {
                byte[] msg = new Gson().toJson(allMessages.get(i).toString()).getBytes("utf-8");
                builder.write(getCharacteristic(messageSyncCharacteristic), msg);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        builder.queue(getQueue());

    }

    private Comparator<JSONObject> sortByDateDesc() {
        return new Comparator<JSONObject>() {
            private static final String KEY_NAME = "date";

            @Override
            public int compare(JSONObject a, JSONObject b) {

                try {
                    return -Long.compare((long) a.get(KEY_NAME), (long) b.get(KEY_NAME));
                }
                catch (JSONException e) {
                    //do something
                }
                return 0;
            }
        };
    }

    public JSONObject addContactInfo(ContentResolver cr, final JSONObject messageObject)
    {
        try {
            String number = PhoneNumberUtils.normalizeNumber(messageObject.get("number").toString().replace("+1", ""));
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            messageObject.put("number", number);
            String[] projection = new String[]{ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts._ID};

            String contactName = "";
            String contactEmail = "";
            Cursor cursor = cr.query(uri, projection, null, null, null);

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    contactName = cursor.getString(0);
                    String id = cursor.getString(1);
                    Cursor ce = cr.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, null,
                            ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?", new String[]{id}, null);
                    if (ce != null && ce.moveToFirst()) {
                        contactEmail = ce.getString(ce.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA));
                        ce.close();
                    }
                }
                cursor.close();
            }


            if (StringUtils.isEmpty(contactName)) {
                contactName = "Unknown";
            }

            try {
                String image = "https://www.gravatar.com/avatar/" + md5(contactEmail) + "?s=55";
                messageObject.put("image", image);
                messageObject.put("name", contactName);
                messageObject.put("email", contactEmail);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } catch (JSONException e) {
            //do something
        }
        return messageObject;
    }

    private JSONObject getSharedInfo(Cursor cursor, String type) {
        JSONObject messageObj = null;
        Boolean isMMS = type == "MMS";
        try{
            Integer threadId = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID));
            Integer messageId = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms._ID));
            Long date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));
            messageObj = new JSONObject();
            messageObj.put("messageType", type);
            messageObj.put("messageId", type.toLowerCase() + "_" +messageId);
            messageObj.put("conversationId", threadId);
            messageObj.put("date", isMMS ? date * 1000 : date);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return messageObj;
    }

    private JSONObject getSMSInfo(Cursor smsCur, ContentResolver cr) {
        JSONObject smsObj = getSharedInfo( smsCur, "SMS");
        try{
            String content = smsCur.getString(smsCur.getColumnIndexOrThrow(Telephony.Sms.BODY));
            String direction = "sent";
            if(smsCur.getInt(smsCur.getColumnIndexOrThrow(Telephony.Sms.TYPE)) == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                direction = "inbox";
            }
            smsObj.put("number", smsCur.getString(smsCur.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)));
            smsObj = addContactInfo(cr, smsObj);
            smsObj.put("direction", direction);
            smsObj.put("content", content);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return smsObj;
    }

    private JSONObject getMMSInfo(Cursor mmsCur, ContentResolver cr) {
        LOG.info("MMMSSSS!!!");
        JSONObject mmsObj = getSharedInfo( mmsCur, "MMS");
        try{
            Integer messageId = mmsCur.getInt(mmsCur.getColumnIndexOrThrow(Telephony.Mms._ID));
            String mtype = mmsCur.getString(mmsCur.getColumnIndex(Telephony.Mms.MESSAGE_TYPE));

            String selectionAdd = new String("msg_id=" + messageId);
            String uriStr = "content://mms/"+messageId+"/addr";
            Uri uriAddress = Uri.parse(uriStr);
            Cursor cAdd = cr.query(uriAddress, null,
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
            String content = getMMSContent(cr, messageId);
            if (content == "") {
                mmsObj.put("content", " [Multimedia Item] ");
            } else {
                mmsObj.put("content", content);
            }

            String direction = "sent";
            if(Integer.parseInt(mtype) == 132) {
                direction = "inbox";
            }
            mmsObj.put("direction", direction);
            mmsObj.put("number", dirtyNumber);
            mmsObj = addContactInfo(cr, mmsObj);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        LOG.info("MMS -" + mmsObj.toString());
        return mmsObj;
    }

    private String getMMSContent(ContentResolver cr, Integer id) {
        String body = "";
        String selectionPart = new String ("mid = '" + id.toString() + "'");
        Cursor curPart = cr.query (Uri.parse ("content://mms/part"), null, selectionPart, null, null);
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
                    body = body + getMmsText(cr, curPart.getString(0));
                } else {
                    body = body + curPart.getString(curPart.getColumnIndex("text"));
                }
            }
        }
        curPart.close();
        return body;
    }
    private String getMmsText(ContentResolver cr, String id) {
        Uri partURI = Uri.parse("content://mms/part/" + id);
        InputStream is = null;
        StringBuilder sb = new StringBuilder();
        try {
            is = cr.openInputStream(partURI);
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
