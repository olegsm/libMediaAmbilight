package com.media.ambilight.ble;

import com.media.ambilight.AmbilightSettings;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;

public class BluetouthGattAttributes {
    private static HashMap<String, String> mAttributes = new HashMap<String, String>();
    private static ArrayList<String> mMacs = new ArrayList<String>();
    public static String HEART_RATE_MEASUREMENT = "0000FEE9-0000-1000-8000-00805F9B34FB";
    //public static String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";

    public static String CLIENT_CHARACTERISTIC_CONFIG = "D44BC439-ABFD-45A2-B575-925416129600";
    //public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    static {
        mAttributes.put(HEART_RATE_MEASUREMENT, "LED");
        mAttributes.put(CLIENT_CHARACTERISTIC_CONFIG, "LEDINPUT");

        mMacs.addAll(AmbilightSettings.AMBILIGHT_MACS);
    }

    public static String lookup(String uuid, String defaultName) {
        String name = mAttributes.get(uuid);
        return name == null ? defaultName : name;
    }

    public static boolean allowedMac(String mac) {
        return mMacs.contains(mac);
    }

    public static int indexMac(String mac) {
        for (int i = 0; i < mMacs.size(); ++i) {
            if (TextUtils.equals(mac, mMacs.get(i))) {
                return i;
            }
        }
        return 0;
    }

    public static int allowedMacSize() {
        return mMacs.size();
    }
}
