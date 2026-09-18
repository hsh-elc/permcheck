package de.hsh.permcheck.internal;

import java.net.NetworkInterface;

public class DenyContextGetNetworkInformationCheck extends AbstractDenyCheck {

    public DenyContextGetNetworkInformationCheck() {
        super("contextGetNetworkInformation", "deny.contextGetNetworkInformation");
    }

    @Override
    protected void registerImpl(Registry registry) throws Exception {
        registry.put(
            NetworkInterface.class.getDeclaredMethod("getInetAddresses"), deny());
        registry.put(
            NetworkInterface.class.getDeclaredMethod("inetAddresses"), deny());
        registry.put(
            NetworkInterface.class.getDeclaredMethod("getHardwareAddress"), deny());

    }


}
