package nodomain.freeyourgadget.gadgetbridge.service.devices.macosx;

import android.net.Uri;

import java.util.ArrayList;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.service.serial.AbstractSerialDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.serial.GBDeviceIoThread;
import nodomain.freeyourgadget.gadgetbridge.service.serial.GBDeviceProtocol;

public class MacOSXDeviceSupport extends AbstractSerialDeviceSupport {
    @Override
    protected GBDeviceProtocol createDeviceProtocol() {
        return null;
    }

    @Override
    protected GBDeviceIoThread createDeviceIOThread() {
        return null;
    }

    @Override
    public boolean connect() {
        return false;
    }

    @Override
    public boolean useAutoConnect() {
        return false;
    }

    @Override
    public void onSetAlarms(ArrayList<? extends Alarm> alarms) {

    }

    @Override
    public void onInstallApp(Uri uri) {

    }

    @Override
    public void onAppConfiguration(UUID appUuid, String config, Integer id) {

    }

    @Override
    public void onHeartRateTest() {

    }

    @Override
    public void onSetConstantVibration(int integer) {

    }

    @Override
    public void onSetHeartRateMeasurementInterval(int seconds) {

    }
}
