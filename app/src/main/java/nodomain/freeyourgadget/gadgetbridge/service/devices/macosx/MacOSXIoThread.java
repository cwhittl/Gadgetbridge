package nodomain.freeyourgadget.gadgetbridge.service.devices.macosx;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import java.io.IOException;
import java.io.InputStream;

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.btclassic.BtClassicIoThread;
import nodomain.freeyourgadget.gadgetbridge.service.serial.AbstractSerialDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.serial.GBDeviceProtocol;

public class MacOSXIoThread extends BtClassicIoThread {
    public MacOSXIoThread(GBDevice gbDevice, Context context, GBDeviceProtocol deviceProtocol, AbstractSerialDeviceSupport deviceSupport, BluetoothAdapter btAdapter) {
        super(gbDevice, context, deviceProtocol, deviceSupport, btAdapter);
    }

    @Override
    protected byte[] parseIncoming(InputStream inStream) throws IOException {
        return new byte[0];
    }
}
