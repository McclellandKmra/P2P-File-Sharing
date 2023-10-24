import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class PeerProcess {
    public Thread t1, t2;
    private int numberOfPreferredNeighbors;
    private int unchokingInterval;
    private int optimisticUnchokingInterval;
    private String fileName;
    private int fileSize;
    private int pieceSize;
    private int peerID;

    private BitSet bitfield;
    private HashMap<Socket, BitSet> peerBitfields = new HashMap<>();

    private Map<Integer, PeerInfo> peers = new HashMap<>();
    private Map<Socket, Integer> peerIDs = new HashMap<>();

    private ServerSocket serverSocket;
    private HashMap<Socket, ObjectOutputStream> objectOutputStreams = new HashMap<>();
    private HashMap<Socket, ObjectInputStream> objectInputStreams = new HashMap<>();
    private List<Socket> connections = new ArrayList<>();
    private List<Socket> preferredNeighbors = new ArrayList<>();
    private Set<Socket> interestedPeers = new HashSet<>();

    private List<Thread> listenerThreads = new ArrayList<>();

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

        peerProcess.t2 = new Thread(()-> {
            peerProcess.unchokingInterval();
        });
        peerProcess.t2.start();


        
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
        PeerInfo currentPeer = peers.get(peerID);
        int totalPieces = (int) Math.ceil((double) fileSize / pieceSize);
        bitfield = new BitSet(totalPieces);

        if (currentPeer.hasFile) {
            bitfield.set(0, totalPieces);// Set all bits to 1 if the peer has the complete file
        } else {
            bitfield.clear();  // Clear all bits (set to 0) if the peer does not have the complete file
        }
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
                connections.add(socket);
                sendHandshake(socket);
                receiveHandshake(socket);
                Thread listenerThread = new Thread(() -> listenForMessages(socket));
                listenerThread.start();
                listenerThreads.add(listenerThread);
                if (currentPeer.hasFile) {
                    sendBitfieldMessage(socket);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Messages

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
                    connections.add(socket);
    
                    // Send a handshake message to the connected peer
                    sendHandshake(socket);
                    receiveHandshake(socket);
                    Thread listenerThread = new Thread(() -> listenForMessages(socket));
                    listenerThread.start();
                    listenerThreads.add(listenerThread);
                    if (peers.get(peerID).hasFile) {
                        sendBitfieldMessage(socket);
                    }
    
                } catch (IOException e) {
                    System.err.println("Error connecting to peer " + currentPeerId + " at " + peerInfo.hostname + ":" + peerInfo.port);
                    e.printStackTrace();
                }
            }
        }
    }
    
    public void listenForMessages(Socket socket) {
        try {
            ObjectInputStream in = objectInputStreams.get(socket);
            
            while (true) {
                byte[] message = (byte[]) in.readObject();
                handleMessage(socket, message);
            }
        } catch (EOFException e) {
            // The other end has probably closed the connection.
            System.out.println("Connection closed by " + socket.getRemoteSocketAddress());
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void handleMessage(Socket socket, byte[] message) {
        int messageType = Byte.toUnsignedInt(message[4]);
        switch(messageType) {
            case 0: handleChokeMessage(socket, message); break;
            case 1: handleUnchokeMessage(socket, message); break;
            case 3: handleNotInterestedMessage(socket, message); break;
            case 2: handleInterestedMessage(socket, message); break;
            case 5: handleBitfieldMessage(socket, message); break;
        }
    }

    public void handleBitfieldMessage(Socket socket, byte[] message) {
        // Extract the bitfield part of the message, skipping the message length and type bytes.
        byte[] bitfieldBytes = Arrays.copyOfRange(message, 5, message.length);
        
        BitSet receivedBitfield = byteArrayToBitset(bitfieldBytes);
        peerBitfields.put(socket, receivedBitfield);
    
        // Convert the received bitfield to a string of ones and zeros
        StringBuilder bitfieldString = new StringBuilder(receivedBitfield.length());
        for (int i = 0; i < receivedBitfield.length(); i++) {
            if (receivedBitfield.get(i)) {
                bitfieldString.append("1");
            } else {
                bitfieldString.append("0");
            }
        }
    
        // Print the received bitfield
        System.out.println("Received bitfield from " + socket.getRemoteSocketAddress() + ": " + bitfieldString.toString());
        
        // Check if the received bitfield has pieces that this peer doesn't have
        BitSet missingPieces = (BitSet) receivedBitfield.clone();
        missingPieces.andNot(this.bitfield);
        
        if (missingPieces.isEmpty()) {
            sendNotInterestedMessage(socket);
        } else {
            sendInterestedMessage(socket);
        }
    }
      
    public void sendMessage(Socket socket, byte messageType, byte[] payload) {
        try {
            ObjectOutputStream out = objectOutputStreams.get(socket);
    
            // Determine message length
            int messageLength = (payload != null) ? payload.length + 1 : 1;
    
            // Create message
            ByteBuffer messageBuffer = ByteBuffer.allocate(4 + messageLength);
            messageBuffer.putInt(messageLength);
            messageBuffer.put(messageType);
            if (payload != null) {
                messageBuffer.put(payload);
            }
    
            // Send message
            out.writeObject(messageBuffer.array());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendBitfieldMessage(Socket socket) {
        //TODO: use send message function
        try {
            ObjectOutputStream out = objectOutputStreams.get(socket);
            
            byte[] bitfieldBytes = bitsetToByteArray(bitfield);

            // Calculate the total message length: 4 bytes for the length itself, 1 byte for the type, and the size of the bitfield
            int messageLength = 4 + 1 + bitfieldBytes.length;

            ByteBuffer buffer = ByteBuffer.allocate(messageLength);
            buffer.putInt(messageLength); // Message length
            buffer.put((byte) 5); // Bitfield message type
            buffer.put(bitfieldBytes);

            out.writeObject(buffer.array());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private byte[] bitsetToByteArray(BitSet bitset) {
        int byteCount = (bitset.length() + 7) / 8; // This ensures rounding up if not a multiple of 8
        byte[] bytes = new byte[byteCount];

        for (int i = 0; i < bitset.length(); i++) {
            if (bitset.get(i)) {
                bytes[i / 8] |= 1 << (7 - i % 8); // Set the specific bit in the byte
            }
        }
        return bytes;
    }

    private BitSet byteArrayToBitset(byte[] bytes) {
        BitSet bitset = new BitSet(bytes.length * 8);
        
        for (int i = 0; i < bytes.length * 8; i++) {
            if ((bytes[i / 8] & (1 << (7 - i % 8))) != 0) {
                bitset.set(i);
            }
        }
        return bitset;
    }

    private void sendNotInterestedMessage(Socket socket) {
        sendMessage(socket, (byte) 3, null);
    }

    private void handleNotInterestedMessage(Socket socket, byte[] message) {
        interestedPeers.remove(socket);
        //TODO: handle not interested message and log
        System.out.println("received not interested message from " + socket.getRemoteSocketAddress());
    }

    private void sendInterestedMessage(Socket socket) {
        sendMessage(socket, (byte) 2, null);
    }

    private void handleInterestedMessage(Socket socket, byte[] message) {
        interestedPeers.add(socket);
        //TODO: handle interested messaage and log
        System.out.println("received interested message from " + socket.getRemoteSocketAddress());
    }

    private void sendUnchokeMessage(Socket socket) {
        sendMessage(socket, (byte) 1, null);
        System.out.println("Sent unchoke message to " + socket.getRemoteSocketAddress());
    }

    //TODO:
    private void handleUnchokeMessage(Socket socket, byte[] message) {
        System.out.println("getting choked by " + socket.getRemoteSocketAddress());
    }

    private void sendChokeMessage(Socket socket) {
        sendMessage(socket, (byte) 0, null);
        System.out.println("Sent choke message to " + socket.getRemoteSocketAddress());
    }

    //TODO:
    private void handleChokeMessage(Socket socket, byte[] message) {
        System.out.println("getting unchoked by " + socket.getRemoteSocketAddress());
    }

    //Choking

    public void unchokingInterval() {
        while (true) { // Infinite loop to keep checking at regular intervals
            preferredNeighbors.clear();
            if (peers.get(peerID).hasFile) { //choose preferred neighbors randomly
                List<Socket> interested = new ArrayList<>(interestedPeers); // Assuming you have this list from before
                Collections.shuffle(interested);
                
                preferredNeighbors.clear();
                for (int i = 0; i < Math.min(numberOfPreferredNeighbors, interested.size()); i++) {
                    Socket neighbor = interested.get(i);
                    preferredNeighbors.add(neighbor);
                    unchoke(neighbor);
                }
                chokeNonPreferredNeighbors();
            }
            //TODO: implement else when peer does not have complete file
    
            try {
                Thread.sleep(1000 * unchokingInterval);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void unchoke(Socket socket) {
        //TODO: logic for making sure it wasnt previously choked
        sendUnchokeMessage(socket);
    }

    public void chokeNonPreferredNeighbors() {
        for (Socket socket : connections) { //TODO: optimistically unchoked neighbor logic
            if (!preferredNeighbors.contains(socket)) {
                sendChokeMessage(socket);
            }
        }
    }



}

