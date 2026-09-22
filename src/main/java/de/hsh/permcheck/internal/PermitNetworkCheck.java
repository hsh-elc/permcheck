package de.hsh.permcheck.internal;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public class PermitNetworkCheck extends AbstractPermitCheck {

    public PermitNetworkCheck() {
        super("network", "deny.networkExceptSpecifiedPermissions", "permit.network", PermitNetworkCheck::checkAndMapName);
    }

    @Override
    protected void registerImpl(Registry registry) throws Exception {
        registry.put(Socket.class.getDeclaredMethod("connect", SocketAddress.class, int.class), firstArgSocketAddress());
        registry.put(ServerSocket.class.getDeclaredMethod("accept"), returnValSocketExit());
        registry.put(ServerSocket.class.getDeclaredMethod("bind", SocketAddress.class), firstArgSocketAddress());
        registry.put(ServerSocket.class.getDeclaredMethod("bind", SocketAddress.class, int.class), firstArgSocketAddress());

        registry.put(DatagramSocket.class.getDeclaredMethod("send", DatagramPacket.class), firstArgDatagramPacketEnter());
        registry.put(DatagramSocket.class.getDeclaredMethod("receive", DatagramPacket.class), firstArgDatagramPacketExit());

        // This is intentionally incomplete. There are lots of classes in the JavaSE library that build network 
        // connections. We do not try to intercept all of them. Instead we assume that the program is running
        // inside a docker container with "--network none" enabled.
    }
    

    /**
     * Check name and compute a canonical string, where portrange is always a range - possible with lower and upper bound equal.
     * @param name
     * @return
     */
    public static String checkAndMapName(String name) {
        if (name == null) throw new RuntimeException("Illegal permit.network name 'null'");
        name = name.trim();
        int i = name.indexOf(':');
        if (i < 0) {
            throw new RuntimeException("Illegal permit.network name '"+name+"': There is no <portrange> specified.");
        }
        String hostMask = name.substring(0, i);
        if (name.indexOf(':', i+1) >= 0) {
            throw new RuntimeException("Illegal permit.network name '"+name+"': Double colon not allowed.");
        }
        String portAsString = name.substring(hostMask.length()+1).trim();
        hostMask = hostMask.trim();

        i = hostMask.indexOf('*');
        if (i >= 0) {
            if (i > 0) {
                throw new RuntimeException("Illegal permit.network name '"+name+"': The wildcard is allowed only as the leftmost character of the <host> part.");
            }
            if (hostMask.substring(1).indexOf('*') >= 0) {
                throw new RuntimeException("Illegal permit.network name '"+name+"': Two wildcard characters '*' are not allowed in the <host> part.");
            }
        }

        i = portAsString.indexOf('-');
        if (i < 0) {
            int port;
            try {
                port = Integer.parseInt(portAsString);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Illegal permit.network name '"+name+"': Illegal <portrange>.", e);
            }
            if (port < 0 || port > 65535) {
                throw new RuntimeException("Illegal permit.network name '"+name+"': Illegal port number, allowed range is 0-65535.");
            }
            portAsString = port + "-" + port;
        } else {
            String[] parts = portAsString.split("\\-");
            if (parts.length != 2) {
                throw new RuntimeException("Illegal permit.network name '"+name+"': Only one '-' characters is allowed in the <portrange> part.");
            }
            String fromPortAsString = parts[0].trim();
            String toPortAsString = parts[1].trim();
            int fromPort = 0, toPort = 65535;
            if (!fromPortAsString.isEmpty()) {
                try {
                    fromPort = Integer.parseInt(fromPortAsString);
                } catch (NumberFormatException e) {
                    throw new RuntimeException("Illegal permit.network name '"+name+"': Illegal <portrange> lower bound.", e);
                }
                if (fromPort < 0 || fromPort > 65535) {
                    throw new RuntimeException("Illegal permit.network name '"+name+"': Illegal port number, allowed range is 0-65535.");
                }
            }
            if (!toPortAsString.isEmpty()) {
                try {
                    toPort = Integer.parseInt(toPortAsString);
                } catch (NumberFormatException e) {
                    throw new RuntimeException("Illegal permit.network name '"+name+"': Illegal <portrange> upper bound.", e);
                }
                if (toPort < 0 || toPort > 65535) {
                    throw new RuntimeException("Illegal permit.network name '"+name+"': Illegal port number, allowed range is 0-65535.");
                }
                if (fromPort > toPort) {
                    throw new RuntimeException("Illegal permit.network name '"+name+"': Illegal <portrange>, upper bound is less than lower bound.");
                }
            }
            portAsString = fromPort + "-" + toPort;
        }
        return hostMask + ":" + portAsString;
    }
    


    @Override
    protected boolean matches(String permission, String query) {
        String[] parts = permission.split(":");
        String permissionHostMask = parts[0];
        String permissionPortRange = parts[1];
        parts = query.split(":");
        if (parts.length != 2) {
            throw new RuntimeException("Unexpected query '"+query+"' in PermitNetworkCheck.matches");
        }
        String queryHost = parts[0];
        String queryPort = parts[1];
        
        return matchesHost(permissionHostMask, queryHost) && matchesPort(permissionPortRange, queryPort);
    }

    private boolean matchesHost(String permissionMask, String query) {
        InetAddress q;
        try {
            q = InetAddress.getByName(query);
        } catch (UnknownHostException e) {
            return false;
        }
        // canoncial name of query host:
        String sq = q.getHostName().toLowerCase();

        if (permissionMask.equals(query)) return true;
        if (permissionMask.equals(sq)) return true;
        if (permissionMask.charAt(0) == '*') {
            return sq.endsWith(permissionMask.substring(1));
        }

        // compute canonical name of permission:        
        InetAddress[] addr;
        try {
            addr = InetAddress.getAllByName(permissionMask);
        } catch (UnknownHostException e) {
            try {
                addr = new InetAddress[] { InetAddress.getByName(permissionMask) };
            } catch (UnknownHostException e1) {
                // No match, of we cannot resolve the permitted hosts
                return false;
            }
        }
        String[] saddr = Arrays.stream(addr).map(a -> a.getHostName().toLowerCase()).toArray(String[]::new);

        return Arrays.stream(saddr).anyMatch(sq::equals);
    }

    private boolean matchesPort(String permissionPortRange, String queryPort) {
        String[] arr = permissionPortRange.split("\\-");
        int lower = Integer.parseInt(arr[0]);
        int upper = Integer.parseInt(arr[1]);
        int query = Integer.parseInt(queryPort);
        return lower <= query && query <= upper;
    }
    

    



    private class FirstArgSocketAddressInsert extends ActionInsert {
        protected FirstArgSocketAddressInsert() {
            super(PermitNetworkCheck.this, Action.ACCESS);
        }
        @Override
        protected String getName(Hook hook) {
            SocketAddress endpoint = getFirstArg(hook, SocketAddress.class);
            return getHostnameFromSocketAddress(endpoint);
        }
    }

    private FirstArgSocketAddressInsert firstArgSocketAddress() {
        return new FirstArgSocketAddressInsert();
    }

    private class ReturnValSocketExitInsert extends ExitInsert {
        @Override public void onExitImpl(Hook hook, Object result, Throwable thrown) {
            if (thrown != null) throw new RuntimeException("Unexpected exception received", thrown);
            if (result == null) throw new IllegalArgumentException("Unexpected return value null received");
            Socket returnVal = (Socket)result;
            String hostname = getHostnameFromSocketAddress(returnVal.getLocalSocketAddress());
            checkAction(hostname, Action.ACCESS, hook);
        }
    }

    private ReturnValSocketExitInsert returnValSocketExit() {
        return new ReturnValSocketExitInsert();
    }



    private class FirstArgDatagramPacketEnterInsert extends ActionInsert {
        protected FirstArgDatagramPacketEnterInsert() {
            super(PermitNetworkCheck.this, Action.ACCESS);
        }
        @Override protected String getName(Hook hook) {
            DatagramPacket packet = getFirstArg(hook, DatagramPacket.class);
            return getHostnameFromSocketAddress(packet.getSocketAddress());
        }
    }

    private FirstArgDatagramPacketEnterInsert firstArgDatagramPacketEnter() {
        return new FirstArgDatagramPacketEnterInsert();
    }

    private class FirstArgDatagramPacketExitInsert extends ExitInsert {
        @Override public void onExitImpl(Hook hook, Object result, Throwable thrown) {
            if (thrown != null) throw new RuntimeException("Unexpected exception received", thrown);
            DatagramPacket packet = getFirstArg(hook, DatagramPacket.class);
            String hostname = getHostnameFromSocketAddress(packet.getSocketAddress());
            checkAction(hostname, Action.ACCESS, hook);
        }
    }

    private FirstArgDatagramPacketExitInsert firstArgDatagramPacketExit() {
        return new FirstArgDatagramPacketExitInsert();
    }


    private static String getHostnameFromSocketAddress(SocketAddress endpoint) {
        if (!(endpoint instanceof InetSocketAddress)) {
            throw new IllegalArgumentException("Unsupported address type");
        }
        InetSocketAddress epoint = (InetSocketAddress) endpoint;
        InetAddress addr = epoint.getAddress ();
        if (addr != null) {
            if (!(addr instanceof Inet4Address || addr instanceof Inet6Address)) {
                throw new IllegalArgumentException("invalid address type");
            }
        }
        int port = epoint.getPort();

        String stringEndpoint;
        stringEndpoint = epoint.getHostName();
        if (stringEndpoint.equals("127.0.0.1")) stringEndpoint = "localhost";

        return stringEndpoint + ":" + port;

    }
}
