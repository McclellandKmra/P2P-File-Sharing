import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class PeerProcess {
    public Thread t1;
    private int numberOfPreferredNeighbors;
    private int unchokingInterval;
    private int optimisticUnchokingInterval;
    private String fileName;
    private int fileSize;
    private int pieceSize;
    private int peerID;

    private Map<Integer, PeerInfo> peers = new HashMap<>();

    private ServerSocket serverSocket;
    private HashMap<Socket, ObjectOutputStream> objectOutputStreams = new HashMap<>();
    private HashMap<Socket, ObjectInputStream> objectInputStreams = new HashMap<>();
    private List<Socket> incomingConnections = new ArrayList<>();
    private List<Socket> outgoingConnections = new ArrayList<>();

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

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java PeerProcess <peerID>");
            return;
        }
        
        int PeerID = Integer.parseInt(args[0]);
        PeerProcess peerProcess = new PeerProcess(PeerID);

        peerProcess.initalizeBitfield();
        
        peerProcess.t1 = new Thread(()->{
            peerProcess.startServer();
        });
        peerProcess.t1.start();

        peerProcess.connectToServers();


        
        //connect to relevant peer processes
    }

    public PeerProcess(int peerID) {
        this.peerID = peerID;
        try {
            readCommon();
            readPeerInfo();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //reads and stores data from Common.cfg
    public void readCommon() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader("Common.cfg"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                String key = parts[0];
                String value = parts[1];

                switch (key) {
                    case "NumberOfPreferredNeighbors":
                        numberOfPreferredNeighbors = Integer.parseInt(value);
                        break;
                    case "UnchokingInterval":
                        unchokingInterval = Integer.parseInt(value);
                        break;
                    case "OptimisticUnchokingInterval":
                        optimisticUnchokingInterval = Integer.parseInt(value);
                        break;
                    case "FileName":
                        fileName = value;
                        break;
                    case "FileSize":
                        fileSize = Integer.parseInt(value);
                        break;
                    case "PieceSize":
                        pieceSize = Integer.parseInt(value);
                        break;
                }
            }
        }
    }

    public void readPeerInfo() throws IOException {
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

    public void initalizeBitfield() {
        //TODO: fill with ones if its the first peer, fill with 0s otherwise
    }

    public void startServer() {
        PeerInfo currentPeer = peers.get(peerID);

        // Starting listener on its own port
        try {
            serverSocket = new ServerSocket(currentPeer.port);
        } catch (IOException e) {
            System.err.println("Error starting server socket on port " + currentPeer.port);
            return;
        }

        try{
            while(true) {
                Socket socket = serverSocket.accept();
                objectOutputStreams.put(socket, new ObjectOutputStream(socket.getOutputStream()));
                objectInputStreams.put(socket, new ObjectInputStream(socket.getInputStream()));
                incomingConnections.add(socket);
                sendHandshake(socket);
                receiveHandshake(socket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendHandshake(Socket socket) {
        try {
            ObjectOutputStream out = objectOutputStreams.get(socket);
            
            // Format message
            String header = "P2PFILESHARINGPROJ";
            byte[] zeroBits = new byte[10];
            byte[] peerIDBytes = ByteBuffer.allocate(4).putInt(peerID).array();
    
            // Create handshake message
            ByteArrayOutputStream handshakeMsg = new ByteArrayOutputStream();
            handshakeMsg.write(header.getBytes());
            handshakeMsg.write(zeroBits);
            handshakeMsg.write(peerIDBytes);
    
            // Send handshake message
            out.writeObject(handshakeMsg.toByteArray());
    
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void receiveHandshake(Socket socket) {
        try {
            ObjectInputStream in = objectInputStreams.get(socket);
    
            // Read the handshake message bytes
            byte[] handshakeBytes = (byte[]) in.readObject();
    
            // Parse the handshake
            String header = new String(handshakeBytes, 0, 18);
            if (!header.equals("P2PFILESHARINGPROJ")) {
                throw new IllegalArgumentException("Invalid handshake header");
            }
    
            // Extract peer ID from the last 4 bytes
            int receivedPeerID = ByteBuffer.wrap(handshakeBytes, 28, 4).getInt();

            //log the message
            System.out.println("received handshake from " + receivedPeerID);

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    public void connectToServers() {
        // Iterate over each peer from the peers map
        for (Map.Entry<Integer, PeerInfo> entry : peers.entrySet()) {
            int currentPeerId = entry.getKey();
            PeerInfo peerInfo = entry.getValue();
    
            // Only attempt to connect if the currentPeerId is less than this peer's ID
            if (currentPeerId < this.peerID) {
                try {
                    Socket socket = new Socket(peerInfo.hostname, peerInfo.port);
    
                    // Create ObjectOutputStream and ObjectInputStream for the new connection
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
    
                    // Store these streams in the appropriate maps
                    objectOutputStreams.put(socket, out);
                    objectInputStreams.put(socket, in);
                    incomingConnections.add(socket);
    
                    // Send a handshake message to the connected peer
                    sendHandshake(socket);
                    receiveHandshake(socket);
    
                } catch (IOException e) {
                    System.err.println("Error connecting to peer " + currentPeerId + " at " + peerInfo.hostname + ":" + peerInfo.port);
                    e.printStackTrace();
                }
            }
        }
    }
    
}

