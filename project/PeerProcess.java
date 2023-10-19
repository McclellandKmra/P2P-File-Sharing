import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class PeerProcess {
    private int NumberOfPreferredNeighbors;
    private int UnchokingInterval;
    private int OptimisticUnchokingInterval;
    private String FileName;
    private int FileSize;
    private int PieceSize;
    private static int PeerID;

    private Map<Integer, PeerInfo> peers = new HashMap<>();

    private ServerSocket serverSocket;

    private static final String HANDSHAKE_HEADER = "P2PFILESHARINGPROJ";

    private byte[] createHandshakeMessage(int peerID) {
        byte[] header = HANDSHAKE_HEADER.getBytes();
        byte[] zeroBits = new byte[10];
        byte[] peerIDBytes = ByteBuffer.allocate(4).putInt(peerID).array();

        ByteBuffer buffer = ByteBuffer.allocate(32);
        buffer.put(header);
        buffer.put(zeroBits);
        buffer.put(peerIDBytes);

        return buffer.array();
    }

    private boolean isValidHandshake(byte[] handshakeMsg) {
        byte[] header = new byte[18];
        byte[] peerIDBytes = new byte[4];

        System.arraycopy(handshakeMsg, 0, header, 0, 18);
        System.arraycopy(handshakeMsg, 28, peerIDBytes, 0, 4);

        return HANDSHAKE_HEADER.equals(new String(header));
    }

    public class PeerInfo {
        int peerID;
        String hostname;
        int port;
        boolean hasFile;
        
        public PeerInfo(int peerID, String hostname, int port, boolean hasFile) {
            this.peerID = peerID;
            this.hostname = hostname;
            this.port = port;
            this.hasFile = hasFile;
        }
    }

    public PeerProcess() {
        try {
            readCommon();
            readPeerInfo();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error reading configuration files.");
        }
    }

    private void startPeerProcess(int peerID) {
        PeerInfo currentPeer = peers.get(peerID);
        if (currentPeer == null) {
            System.err.println("Invalid peer ID provided.");
            return;
        }

        // Starting listener on its own port
        try {
            serverSocket = new ServerSocket(currentPeer.port);
            // Start a new thread to handle incoming connections
            new Thread(this::handleIncomingConnections).start();
        } catch (IOException e) {
            System.err.println("Error starting server socket on port " + currentPeer.port);
            return;
        }
        
        // Connect to peers that started before the current peer
        for (Map.Entry<Integer, PeerInfo> entry : peers.entrySet()) {
            if (entry.getKey() < peerID) {
                // Connect to entry.getValue()
                try {
                    Socket socket = new Socket(entry.getValue().hostname, entry.getValue().port);
                    System.out.println("[" + getCurrentTimestamp() + "]: Peer [" + peerID + "] makes a connection to Peer [" + entry.getKey() + "].");
                    new Thread(() -> handleConnection(socket)).start();
                } catch (IOException e) {
                    System.err.println("Error connecting to " + entry.getValue().hostname + " on port " + entry.getValue().port);
                }
            }
        }
    }

    private void handleIncomingConnections() {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                System.out.println("[" + getCurrentTimestamp() + "]: Peer [??] is connected from Peer [??].");
                new Thread(() -> handleConnection(socket)).start();
            } catch (IOException e) {
                System.err.println("Error while accepting connection.");
                // Optionally handle the error further or decide when to break the loop.
            }
        }
    }

    // This is a placeholder method to handle the connection
    private void handleConnection(Socket socket) {
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // Sending handshake message
            out.write(createHandshakeMessage(PeerID));

            // Reading and validating handshake message from other peer
            byte[] receivedHandshake = new byte[32];
            in.readFully(receivedHandshake);

            if (!isValidHandshake(receivedHandshake)) {
                System.out.println("Invalid handshake received. Closing connection.");
                socket.close();
                return;
            }

            System.out.println("Valid handshake received from Peer [" + ByteBuffer.wrap(receivedHandshake, 28, 4).getInt() + "]");

            // Continue with further message handling after handshake

        } catch (IOException e) {
            System.err.println("Error during handshake with " + socket.getRemoteSocketAddress());
            // Handle error further if necessary
        }
    }


    public static void main(String[] args) {
    if (args.length != 1) {
        System.err.println("Usage: java PeerProcess <peerID>");
        return;
    }
    
    PeerID = Integer.parseInt(args[0]);
    
    PeerProcess peer = new PeerProcess();
    peer.startPeerProcess(PeerID);
    }




    // file reading

    //reads and stores data from Common.cfg
    private void readCommon() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader("Common.cfg"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length != 2) {
                    // Malformed line; you can handle this error as you see fit
                    continue;
                }
                String key = parts[0];
                String value = parts[1];

                switch (key) {
                    case "NumberOfPreferredNeighbors":
                        NumberOfPreferredNeighbors = Integer.parseInt(value);
                        break;
                    case "UnchokingInterval":
                        UnchokingInterval = Integer.parseInt(value);
                        break;
                    case "OptimisticUnchokingInterval":
                        OptimisticUnchokingInterval = Integer.parseInt(value);
                        break;
                    case "FileName":
                        FileName = value;
                        break;
                    case "FileSize":
                        FileSize = Integer.parseInt(value);
                        break;
                    case "PieceSize":
                        PieceSize = Integer.parseInt(value);
                        break;
                }
            }
        }
    }

    //reads and stores data from PeerInfo.cfg
    private void readPeerInfo() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader("PeerInfo.cfg"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                int peerID = Integer.parseInt(parts[0]);
                String hostname = parts[1];
                int port = Integer.parseInt(parts[2]);
                boolean hasFile = parts[3].equals("1");
                peers.put(peerID, new PeerInfo(peerID, hostname, port, hasFile));
            }
        }
    }



    // helper functions
    private static String getCurrentTimestamp() {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    return sdf.format(new Date());
    }
}

