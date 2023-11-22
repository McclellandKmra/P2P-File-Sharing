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

<<<<<<< Updated upstream
=======
    private HashMap<Socket, Long> downloadRates = new HashMap<>();

    private BitSet bitfield;
    private HashMap<Socket, BitSet> peerBitfields = new HashMap<>();
    private List<Integer> requestedIndices = new ArrayList<>();
    private Map<Integer, byte[]> fileContents;


>>>>>>> Stashed changes
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

<<<<<<< Updated upstream
=======
        peerProcess.t2 = new Thread(()-> {
            while (true) {
                peerProcess.managePreferredNeighbors();
                try {
                    Thread.sleep(1000 * peerProcess.unchokingInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }           
        });
        peerProcess.t2.start();

        Thread t3 = new Thread(() -> {
            while (true) {
                peerProcess.manageOptimisticallyUnchokedNeighbor();
                try {
                    Thread.sleep(1000 * peerProcess.optimisticUnchokingInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        t3.start();
>>>>>>> Stashed changes

        
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
    
<<<<<<< Updated upstream
                } catch (IOException e) {
                    System.err.println("Error connecting to peer " + currentPeerId + " at " + peerInfo.hostname + ":" + peerInfo.port);
                    e.printStackTrace();
=======
        requestedIndices.add(pieceToRequest);
    
        // Construct and send the request message for the selected piece
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(pieceToRequest);
        sendMessage(socket, (byte) 6, buffer.array());
    
        System.out.println("Sent request message for piece " + pieceToRequest + " to " + socket.getRemoteSocketAddress());
    }

    private void handleRequestMessage(Socket socket, byte[] message) {
        int pieceIndex = ByteBuffer.wrap(Arrays.copyOfRange(message, 5, message.length)).getInt();
        System.out.println("Received request message for piece " + pieceIndex + " from " + socket.getRemoteSocketAddress());

        // Check if the piece is available
        if (bitfield.get(pieceIndex)) {
            // If the piece is available, read its data and send it
            byte[] pieceData = fileContents.get(pieceIndex);
            sendPieceMessage(socket, pieceIndex, pieceData);
        } else {
            // Handle the case where the requested piece is not available
            System.out.println("ERROR: Piece " + pieceIndex + " is not available.");
        }
    }

    private void sendPieceMessage(Socket socket, int pieceIndex, byte[] pieceData) {
        ByteBuffer buffer = ByteBuffer.allocate(4 + pieceData.length);
        buffer.putInt(pieceIndex); // Piece index
        buffer.put(pieceData); // Piece data
    
        // Send the buffer array
        sendMessage(socket, (byte)7, buffer.array());
        System.out.println("Sent piece " + pieceIndex + " to " + socket.getRemoteSocketAddress());
    }

    private void handlePieceMessage(Socket socket, byte[] message) {
        int pieceIndex = ByteBuffer.wrap(Arrays.copyOfRange(message, 5, 9)).getInt();
        byte[] pieceData = Arrays.copyOfRange(message, 9, message.length);
        fileContents.put(pieceIndex, pieceData);
        
        bitfield.set(pieceIndex);
        
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(pieceIndex);

        long pieceSize = message.length - 5; // Adjust this if your message has additional headers or information
        downloadRates.put(socket, downloadRates.getOrDefault(socket, 0L) + pieceSize);

        for (Socket connection : connections) {
            sendHaveMessage(connection, buffer.array());
        }

        requestedIndices.remove(Integer.valueOf(pieceIndex));

        //TODO: logic for if it has the whole file
        if (fileContents.size() == Math.ceil((double) fileSize / pieceSize)) {
            System.out.println("Saving file");
            saveCompleteFile();
        }
    
        System.out.println("Received and saved piece " + pieceIndex + " from " + socket.getRemoteSocketAddress());

        sendRequestMessage(socket);
    }

    private void sendHaveMessage(Socket socket, byte[] pieceIndex) {
        sendMessage(socket, (byte) 4, pieceIndex);
    }

    private void handleHaveMessage(Socket socket, byte[] message) {
        //TODO: Make sure this is accurate

        int pieceIndex = ByteBuffer.wrap(Arrays.copyOfRange(message, 5, message.length)).getInt();

        // Retrieve the bitfield for the corresponding peer
        BitSet peerBitfield = peerBitfields.get(socket);
        if (peerBitfield == null) {
            peerBitfield = new BitSet(); // Create a new BitSet if it does not exist
        }
    
        // Set the bit for the received piece index
        peerBitfield.set(pieceIndex);
    
        // Update the peerBitfields map
        peerBitfields.put(socket, peerBitfield);
    
        System.out.println("Peer " + peerIDs.get(socket) + " has piece " + pieceIndex);
    }


    //Choking


    public void unchokingInterval() {
        /* Every choking interval of time, recalculates unchoked neighbors and chokes other neighbors */

        while (true) { // Infinite loop to keep checking at regular intervals
            preferredNeighbors.clear();
            if (peers.get(peerID).hasFile) { //choose preferred neighbors randomly if you have the complete file
                List<Socket> interested = new ArrayList<>(interestedPeers);
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

    
    //Helper Functions


    public void initializeFileContents() {
        fileContents = new HashMap<>();
        String filePath = "./peer_" + peerID + "/" + fileName;
        File file = new File(filePath);
    
        try (FileInputStream fis = new FileInputStream(file)) {
            int pieceIndex = 0;
            byte[] buffer = new byte[pieceSize];
            int bytesRead;
    
            while ((bytesRead = fis.read(buffer)) != -1) {
                // If bytesRead is less than pieceSize, copy only the bytes read
                byte[] actualData = bytesRead < pieceSize ? Arrays.copyOf(buffer, bytesRead) : buffer;
                fileContents.put(pieceIndex, actualData);
                pieceIndex++;
    
                // Reinitialize buffer if it's the last piece and it was smaller
                if (bytesRead < pieceSize) {
                    buffer = new byte[pieceSize];
>>>>>>> Stashed changes
                }
            }
        }
    }
    
}

<<<<<<< Updated upstream
=======
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

    private void managePreferredNeighbors() {
        if (peers.get(peerID).hasFile) {
            // If peer has the complete file, select preferred neighbors randomly
            selectPreferredNeighborsRandomly();
        } else {
            // Otherwise, select based on download rates
            selectPreferredNeighborsBasedOnRate();
        }
        chokeNonPreferredNeighbors();
    }
    
    private void selectPreferredNeighborsRandomly() {
        List<Socket> interested = new ArrayList<>(interestedPeers);
        Collections.shuffle(interested);
        preferredNeighbors.clear();
        for (int i = 0; i < Math.min(numberOfPreferredNeighbors, interested.size()); i++) {
            Socket neighbor = interested.get(i);
            preferredNeighbors.add(neighbor);
            unchoke(neighbor);
        }
    }

    private void selectPreferredNeighborsBasedOnRate() {
        // Clear the current preferred neighbors list
        preferredNeighbors.clear();
    
        // Sort the peers by their download rates in descending order
        List<Map.Entry<Socket, Long>> sortedPeers = new ArrayList<>(downloadRates.entrySet());
        sortedPeers.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));
    
        // Pick the top 'k' peers
        for (int i = 0; i < Math.min(numberOfPreferredNeighbors, sortedPeers.size()); i++) {
            Socket neighbor = sortedPeers.get(i).getKey();
            preferredNeighbors.add(neighbor);
            unchoke(neighbor);
        }
    
        // Reset download rates for the next interval
        downloadRates.clear();
    }

    private void manageOptimisticallyUnchokedNeighbor() {
        List<Socket> chokedInterestedPeers = new ArrayList<>(interestedPeers);
        chokedInterestedPeers.removeAll(preferredNeighbors);
        // Ensures we don't select already unchoked peers
        if (!chokedInterestedPeers.isEmpty()) {
            Collections.shuffle(chokedInterestedPeers);
            Socket optimisticallyUnchoked = chokedInterestedPeers.get(0);
            unchoke(optimisticallyUnchoked);
        }
    }

}
>>>>>>> Stashed changes
