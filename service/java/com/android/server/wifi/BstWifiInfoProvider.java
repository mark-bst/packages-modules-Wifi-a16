/*
 * Copyright (C) 2026 BlueStacks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wifi;

import android.net.DhcpInfo;
import android.net.InetAddresses;
import android.net.MacAddress;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiSsid;
import android.os.SystemProperties;
import android.util.Log;

import com.android.net.module.util.Inet4AddressUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/** Provides the app-visible Wi-Fi identity backed by the BlueStacks virtual network. */
final class BstWifiInfoProvider {
    private static final String TAG = "BstWifiInfoProvider";
    private static final String DEFAULT_MAC = "02:00:00:00:00:00";
    private static final File MAC_FILE = new File("/data/downloads/.tmp/.ma");
    private static String sWrittenMac;

    private BstWifiInfoProvider() {}

    static boolean isEnabled() {
        return true;
    }

    static WifiInfo createWifiInfo() {
        final String mac = validatedMac(
                SystemProperties.get("bst.wifi_mac_addr", DEFAULT_MAC));
        final String bssid = validatedMac(
                SystemProperties.get("bst.bssid_mac_addr", DEFAULT_MAC));
        persistMacForNetworkInterface(mac);

        final WifiInfo info = new WifiInfo();
        info.setSSID(WifiSsid.fromBytes("BlueStacks".getBytes(StandardCharsets.UTF_8)));
        info.setBSSID(bssid);
        info.setNetworkId(1);
        info.setRssi(-10);
        info.setLinkSpeed(72);
        info.setMacAddress(mac);
        info.setSupplicantState(SupplicantState.COMPLETED);
        info.setInetAddress(ipv4Property("bst.status.ip_guest_addr", "10.0.2.15"));
        return info;
    }

    static DhcpInfo createDhcpInfo() {
        final Inet4Address guest = ipv4Property("bst.status.ip_guest_addr", "10.0.2.15");
        final Inet4Address gateway =
                ipv4Property("bst.status.ip_gateway_addr", "10.0.2.2");
        final DhcpInfo info = new DhcpInfo();
        info.ipAddress = Inet4AddressUtils.inet4AddressToIntHTL(guest);
        info.gateway = Inet4AddressUtils.inet4AddressToIntHTL(gateway);
        info.serverAddress = info.gateway;
        info.dns1 = Inet4AddressUtils.inet4AddressToIntHTL(
                ipv4Property("bst.dns_server", "8.8.8.8"));
        info.dns2 = Inet4AddressUtils.inet4AddressToIntHTL(
                ipv4Property("bst.dns_server2", "10.0.2.3"));
        info.leaseDuration = 86400;
        return info;
    }

    private static String validatedMac(String value) {
        try {
            return MacAddress.fromString(value).toString();
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Invalid BlueStacks MAC address: " + value);
            return DEFAULT_MAC;
        }
    }

    private static Inet4Address ipv4Property(String key, String fallback) {
        final String value = SystemProperties.get(key, fallback);
        try {
            final InetAddress address = InetAddresses.parseNumericAddress(value);
            if (address instanceof Inet4Address) {
                return (Inet4Address) address;
            }
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Invalid IPv4 property " + key + ": " + value);
        }
        return (Inet4Address) InetAddresses.parseNumericAddress(fallback);
    }

    private static synchronized void persistMacForNetworkInterface(String mac) {
        if (mac.equals(sWrittenMac)) {
            return;
        }
        final File directory = MAC_FILE.getParentFile();
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        final File temporary = new File(directory, MAC_FILE.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            output.write(mac.getBytes(StandardCharsets.US_ASCII));
            output.getFD().sync();
        } catch (IOException e) {
            Log.w(TAG, "Unable to stage BlueStacks MAC address", e);
            temporary.delete();
            return;
        }
        temporary.setReadable(true, false);
        if (!temporary.renameTo(MAC_FILE)) {
            Log.w(TAG, "Unable to publish BlueStacks MAC address");
            temporary.delete();
            return;
        }
        MAC_FILE.setReadable(true, false);
        sWrittenMac = mac;
    }
}
