import java.net.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class PeerProcess {
    public Thread t1, t2; // t1 = listener thread for incoming connections, t2 = thread for calculating unchoking interval
    private int numberOfPreferredNeighbors;
    private int unchokingInterval;
    private int optimisticUnchokingInterval;
    private String fileName;
    private int fileSize;
    private int pieceSize;
    private int peerID;

    private HashMap<Socket, Long> downloadRates = new HashMap<>();

    private BitSet bitfield;
    private ConcurrentHashMap<Socket, BitSet> peerBitfields = new ConcurrentHashMap<>();
    private CopyOnWriteArrayList<Integer> requestedIndices = new CopyOnWriteArrayList<>();
    private HashMap<Integer, byte[]> fileContents = new HashMap<>();

    private ConcurrentHashMap<Socket, Boolean> isUnchoked = new ConcurrentHashMap<>();  //whether not not this peer is unchoked by the socket peer
    private CopyOnWriteArrayList<Socket> unchokedNeighbors = new CopyOnWriteArrayList<>(); //peers that are unchoked by this peer; ie. peers that this peer is sending data to

    private ConcurrentHashMap<Integer, PeerInfo> peers = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Socket, Integer> peerIDs = new ConcurrentHashMap<>();

    private ServerSocket serverSocket;
    private ConcurrentHashMap<Socket, ObjectOutputStream> objectOutputStreams = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Socket, ObjectInputStream> objectInputStreams = new ConcurrentHashMap<>();
    private CopyOnWriteArrayList<Socket> connections = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<Socket> preferredNeighbors = new CopyOnWriteArrayList<>();
    private Set<Socket> interestedPeers = new HashSet<>(); //Peers that are interested in this peers data
    private Set<Socket> interestingPeers = new HashSet<>(); //Peers that this peer is interested in
    private Socket optimisticallyUnchokedNeighbor;

    private List<Thread> listenerThreads = new ArrayList<>();

    MessageLogger log = new MessageLogger();

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

        peerProcess.initialize();
        
        peerProcess.t1 = new Thread(()->{
            peerProcess.startServer();
        });
        peerProcess.t1.start();

        peerProcess.connectToServers();

        peerProcess.t2 = new Thread(()-> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            peerProcess.unchokingInterval();
        });
        peerProcess.t2.start();

        Thread t3 = new Thread(() -> {
            peerProcess.optimisticUnchokingInterval();
        });
        t3.start();

        
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

    public void readCommon() throws IOException {
        /* reads and stores data from Common.cfg */

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
        /* Reads and stores info from peerInfo.cfg */

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
    
    public void initialize() {
        //Initializes the bitfield and fileContents

        PeerInfo currentPeer = peers.get(peerID);
        int totalPieces = (int) Math.ceil((double) fileSize / pieceSize);
        bitfield = new BitSet(totalPieces);
        fileContents = new HashMap<>();
    
        if (currentPeer.hasFile) {
            bitfield.set(0, totalPieces); // Set all bits to 1 as the peer has the complete file
    
            // Initialize file contents since this peer has the complete file
            String filePath = "./" + peerID + "/" + fileName; // Adjust the file path as needed
            File file = new File(filePath);
    
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[pieceSize];
                int bytesRead, pieceIndex = 0;
    
                while ((bytesRead = fis.read(buffer)) != -1) {
                    byte[] actualData = bytesRead < pieceSize ? Arrays.copyOf(buffer, bytesRead) : buffer.clone();
                    fileContents.put(pieceIndex++, actualData);
    
                    if (bytesRead < pieceSize) {
                        buffer = new byte[pieceSize]; // Reset buffer for next read
                    }
                }
            } catch (FileNotFoundException e) {
                System.err.println("File not found: " + e.getMessage());
            } catch (IOException e) {
                System.err.println("IO Error while initializing file contents: " + e.getMessage());
            }
        } else {
            bitfield.clear(); // Clear all bits (set to 0) as the peer does not have the complete file
        }
    }
    
    public void startServer() {
        /* Starts a server on this peer's listening port to accept incoming connections */

        //Get the PeerInfo object for this peer
        PeerInfo currentPeer = peers.get(peerID);

        //Starting listener on its own port
        try {
            serverSocket = new ServerSocket(currentPeer.port);
        } 
        catch (IOException e) {
            System.err.println("Error starting server socket on port " + currentPeer.port);
            return;
        }

        //Infinite loop to accept incoming connections
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
                sendBitfieldMessage(socket);
            }
        } 
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void connectToServers() {
        /* Sends connection requests to each peer initalized before it. This is where the initial exchange of messages starts */

        // Iterate over each peer from the peers map
        for (Map.Entry<Integer, PeerInfo> entry : peers.entrySet()) {
            int currentPeerId = entry.getKey();
            PeerInfo peerInfo = entry.getValue();
    
            //Only attempt to connect if the currentPeerId is less than this peer's ID
            //Ensures that the peer only connects to peers with a lower ID, ie ones that have already started
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

                    log.TCPLogMessage(this.peerID, currentPeerId);
    
                    // Send a handshake message to the connected peer
                    sendHandshake(socket);
                    receiveHandshake(socket);
                    Thread listenerThread = new Thread(() -> listenForMessages(socket));
                    listenerThread.start();
                    listenerThreads.add(listenerThread);

                    //peers without the file may skip the bitfield, but all peers exchange bitfields for consistency. 
                    sendBitfieldMessage(socket);
                    
                    
                } catch (IOException e) {
                    System.err.println("Error connecting to peer " + currentPeerId + " at " + peerInfo.hostname + ":" + peerInfo.port);
                    e.printStackTrace();
                }
            }
        }
    }
    
    public void listenForMessages(Socket socket) {
        /* Listens for incoming messages from a socket and calls the handler when one is recieved. Each peer has a thread listening for messages from each other
         * peer, so it can accept messages and handle them at any time.
         */

        try {
            ObjectInputStream in = objectInputStreams.get(socket);
            
            while (true) {
                byte[] message = (byte[]) in.readObject();
                handleMessage(socket, message);
            }
        } catch (EOFException e) {
            // The other end has probably closed the connection.
            System.out.println("Connection closed by " + socket.getRemoteSocketAddress());
        } catch (SocketException e) {
            System.err.println("SocketException: Connection reset. Attempting to reconnect...");
            // Implement reconnection logic here if appropriate
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


    // Messages


    public void handleMessage(Socket socket, byte[] message) {
        /* Calls the appropriate handler for a message type */

        int messageType = Byte.toUnsignedInt(message[4]);
        switch(messageType) {
            case 0: handleChokeMessage(socket, message); break;
            case 1: handleUnchokeMessage(socket, message); break;
            case 2: handleInterestedMessage(socket, message); break;
            case 3: handleNotInterestedMessage(socket, message); break;
            case 4: handleHaveMessage(socket, message); break;
            case 5: handleBitfieldMessage(socket, message); break;
            case 6: handleRequestMessage(socket, message); break;
            case 7: handlePieceMessage(socket, message); break;
        }
    }

    public void sendMessage(Socket socket, byte messageType, byte[] payload) {
        /* Formats and sends the message to the given socket */

        synchronized (objectOutputStreams.get(socket)) { // Synchronize on the specific ObjectOutputStream for the socket
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
                out.flush(); // Ensure data is sent immediately
                out.reset(); // Reset the stream to handle subsequent objects correctly
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendHandshake(Socket socket) {
        /* Creates the handshake message and sends it over the socket */

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
        } 
        catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void receiveHandshake(Socket socket) {

        try {
            ObjectInputStream in = objectInputStreams.get(socket);
    
            //Read the handshake message bytes
            byte[] handshakeBytes = (byte[]) in.readObject();
    
            //Parse the handshake
            String header = new String(handshakeBytes, 0, 18);
            //Validates the header
            if (!header.equals("P2PFILESHARINGPROJ")) {
                throw new IllegalArgumentException("Invalid handshake header");
            }
    
            // Extract peer ID from the last 4 bytes
            int receivedPeerID = ByteBuffer.wrap(handshakeBytes, 28, 4).getInt();
            peerIDs.put(socket, receivedPeerID);
            //log the message
            System.out.println("received handshake from " + receivedPeerID);

        } catch (Exception e) {
            e.printStackTrace();
            return;
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
        System.out.println("Received bitfield from " + peerIDs.get(socket));
        
        // Check if the received bitfield has pieces that this peer doesn't have
        BitSet missingPieces = (BitSet) receivedBitfield.clone();
        missingPieces.andNot(this.bitfield);


        
        if (missingPieces.isEmpty()) {
            sendNotInterestedMessage(socket);
        } else {
            sendInterestedMessage(socket);
        }
    }
      
    public void sendBitfieldMessage(Socket socket) {
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

    private void sendNotInterestedMessage(Socket socket) {
        sendMessage(socket, (byte) 3, null);
        interestingPeers.remove(socket);
        System.out.println("Sending not interested message to " + peerIDs.get(socket));
    }

    private void handleNotInterestedMessage(Socket socket, byte[] message) {
        interestedPeers.remove(socket);
        System.out.println("received not interested message from " + peerIDs.get(socket));
        log.notInterestedLogMessage(peerID, peerIDs.get(socket));
    }

    private void sendInterestedMessage(Socket socket) {
        sendMessage(socket, (byte) 2, null);
        interestingPeers.add(socket);
        System.out.println("Sending interested message to " + peerIDs.get(socket));
    }

    private void handleInterestedMessage(Socket socket, byte[] message) {
        interestedPeers.add(socket);
        System.out.println("received interested message from " + peerIDs.get(socket));
        log.interestedLogMessage(peerID, peerIDs.get(socket));
    }

    private void checkAndSendNotInterestedMessages(int pieceIndex) {
        //called whenever a peer receives a piece completely. If piece causes the peer to no longer be interested, sends a not interested message.

        for (Socket peerSocket : connections) {
            BitSet peerBitfield = peerBitfields.get(peerSocket);
            if (peerBitfield == null) {
                continue;
            }
            if (!isInterested(bitfield, peerBitfield) && peerBitfield.get(pieceIndex)) {
                sendNotInterestedMessage(peerSocket);
            }
        }
    }

    private void sendUnchokeMessage(Socket socket) {
        sendMessage(socket, (byte) 1, null);
        System.out.println("Sent unchoke message to " + peerIDs.get(socket));
    }

    private void handleUnchokeMessage(Socket socket, byte[] message) {
        isUnchoked.put(socket, true);
        System.out.println("getting unchoked by " + peerIDs.get(socket));
        log.unchokeLogMessage(peerID, peerIDs.get(socket));
        sendRequestMessage(socket);
    }

    private void sendChokeMessage(Socket socket) {
        sendMessage(socket, (byte) 0, null);
        System.out.println("Sent choke message to " + peerIDs.get(socket));
    }

    private void handleChokeMessage(Socket socket, byte[] message) {
        isUnchoked.put(socket, false);
        System.out.println("getting choked by " + peerIDs.get(socket));
        log.chokeLogMessage(peerID, peerIDs.get(socket));
    }

    private void sendRequestMessage(Socket socket) {
        /* Determines what piece to request and requests it */

        // Return if not unchoked
        if (!isUnchoked.getOrDefault(socket, false)) {
            return;
        }

        // Determine which pieces are available, needed, and not already requested
        BitSet availablePieces = peerBitfields.get(socket);
        BitSet neededPieces = (BitSet) bitfield.clone();
        neededPieces.flip(0, neededPieces.size());

        List<Integer> possibleRequests = new ArrayList<>();
        for (int i = 0; i < availablePieces.length(); i++) {
            if (availablePieces.get(i) && neededPieces.get(i) && !requestedIndices.contains(i)) {
                possibleRequests.add(i);
            }
        }
    
        // If there are no pieces to request, return
        if (possibleRequests.isEmpty()) {
            return;
        }
    
        int randomIndex = new Random().nextInt(possibleRequests.size());
        int pieceToRequest = possibleRequests.get(randomIndex);
    
        requestedIndices.add(pieceToRequest);
    
        // Construct and send the request message for the selected piece
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(pieceToRequest);
        sendMessage(socket, (byte) 6, buffer.array());
    
        System.out.println("Sent request message for piece " + pieceToRequest + " to " + peerIDs.get(socket));
    }

    private void handleRequestMessage(Socket socket, byte[] message) {
        int pieceIndex = ByteBuffer.wrap(Arrays.copyOfRange(message, 5, message.length)).getInt();
        System.out.println("Received request message for piece " + pieceIndex + " from " + peerIDs.get(socket));

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
        buffer.putInt(pieceIndex);
        buffer.put(pieceData);


        //TODO: Temporary code to allow multiple instances on same device
        try {
            Thread.sleep(25);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Send the buffer array
        sendMessage(socket, (byte)7, buffer.array());
        System.out.println("Sent piece " + pieceIndex + " to " + peerIDs.get(socket));
    }

    private void handlePieceMessage(Socket socket, byte[] message) {
        int pieceIndex = ByteBuffer.wrap(Arrays.copyOfRange(message, 5, 9)).getInt();
        byte[] pieceData = Arrays.copyOfRange(message, 9, message.length);
        fileContents.put(pieceIndex, pieceData);
        
        bitfield.set(pieceIndex);
        
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(pieceIndex);

        long pieceSize = message.length - 5;
        downloadRates.put(socket, downloadRates.getOrDefault(socket, 0L) + pieceSize);

        for (Socket connection : connections) {
            sendHaveMessage(connection, buffer.array());
        }

        requestedIndices.remove(Integer.valueOf(pieceIndex));

        System.out.println("Received and saved piece " + pieceIndex + " from " + peerIDs.get(socket));
        log.haveLogMessage(this.peerID, peerIDs.get(socket), pieceIndex);

        int numPieces = 0;
        for (int i = 0; i < bitfield.length(); i++) {
            if (bitfield.get(i)) {
                numPieces++;
            }
        }

        log.pieceDownloadedLogMessage(peerID, peerIDs.get(socket), pieceIndex, numPieces);

        if (fileContents.size() == Math.ceil((double) fileSize / pieceSize)) {
            System.out.println("Saving file");
            log.fileDownloadedLogMessage(peerID);
            saveCompleteFile();
        }

        checkAndSendNotInterestedMessages(pieceIndex);

        sendRequestMessage(socket);
    }

    private void sendHaveMessage(Socket socket, byte[] pieceIndex) {
        sendMessage(socket, (byte) 4, pieceIndex);
    }

    private void handleHaveMessage(Socket socket, byte[] message) {
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

        if (!bitfield.get(pieceIndex) && !interestingPeers.contains(socket)) {
            // If this peer does not have the piece, send 'interested' message
            sendInterestedMessage(socket);
        }

        for (int i = 0; i < Math.ceil((double) fileSize / pieceSize); i++) {
            if (!peerBitfield.get(i)) {
                return;
            }
        }

        peers.get(peerIDs.get(socket)).hasFile = true;
        System.out.println("Peer " + peerIDs.get(socket) + " has the complete file");
        checkExit();
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
                    if (!unchokedNeighbors.contains(neighbor)) {
                        sendUnchokeMessage(neighbor);
                        unchokedNeighbors.add(neighbor);
                    }
                }
            }
            else {
                selectPreferredNeighborsBasedOnRate();
            }

            chokeNonPreferredNeighbors();
            
            // Log the change of neighbor

            List<Integer> preferredNeighborIds = preferredNeighbors.stream()
                .map(peerIDs::get)  // Replace socketToPeerIdMap with your map
                .collect(Collectors.toList());
            log.changeOfNeighborsLogMessage(peerID, preferredNeighborIds);
    
            try {
                Thread.sleep(1000 * unchokingInterval);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void optimisticUnchokingInterval() {
        /*  Gets the new optimistically unchoked neighbor and chokes the old one every optimisticUnchokingInterval seconds. */

        while(true) {
            List<Socket> chokedInterestedPeers = new ArrayList<>(interestedPeers);
            chokedInterestedPeers.removeAll(preferredNeighbors);
            // Ensures we don't select already unchoked peers
            if (!chokedInterestedPeers.isEmpty()) {
                Collections.shuffle(chokedInterestedPeers);
                Socket optimisticallyUnchoked = chokedInterestedPeers.get(0);
                optimisticallyUnchoked = chokedInterestedPeers.get(0);
                System.out.println("Optimistically unchoking " + peerIDs.get(optimisticallyUnchoked));
                if (optimisticallyUnchokedNeighbor != optimisticallyUnchoked) {
                    if (optimisticallyUnchokedNeighbor != null) {
                        sendChokeMessage(optimisticallyUnchokedNeighbor);
                        unchokedNeighbors.remove(optimisticallyUnchokedNeighbor);
                    }
                    optimisticallyUnchokedNeighbor = optimisticallyUnchoked;
                    sendUnchokeMessage(optimisticallyUnchoked);
                }

                log.changeOfOptNeighborsLogMessage(peerID, peerIDs.get(optimisticallyUnchoked));
            }
            try {
                Thread.sleep(1000 * optimisticUnchokingInterval);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void chokeNonPreferredNeighbors() {
        for (Socket socket : unchokedNeighbors) {
            if (!preferredNeighbors.contains(socket) && socket != optimisticallyUnchokedNeighbor) {
                sendChokeMessage(socket);
                unchokedNeighbors.remove(socket);
            }
        }
    }
 

    //Helper Functions


    private boolean isInterested(BitSet myBitfield, BitSet peerBitfield) {
        BitSet temp = (BitSet) peerBitfield.clone();
        temp.andNot(myBitfield);
        return !temp.isEmpty();
    }
    
    public void saveCompleteFile() {
        String outputFilePath = "./" + peerID + "/" + fileName; // Construct the file path
    
        try (FileOutputStream fileOutputStream = new FileOutputStream(outputFilePath)) {
            for (int i = 0; i < fileContents.size(); i++) {
                byte[] pieceData = fileContents.get(i);
                if (pieceData != null) {
                    fileOutputStream.write(pieceData);
                } else {
                    System.err.println("Missing piece " + i + ". Cannot save file.");
                    return;
                }
            }
            System.out.println("File has been successfully saved to " + outputFilePath);
        } catch (IOException e) {
            System.err.println("IO Error while saving the file: " + e.getMessage());
        }

        checkExit();
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

    private void selectPreferredNeighborsBasedOnRate() {
        // Clear the current preferred neighbors list
        preferredNeighbors.clear();

        // Filter only interested peers
        List<Map.Entry<Socket, Long>> interestedSortedPeers = downloadRates.entrySet().stream()
                .filter(entry -> interestedPeers.contains(entry.getKey()))
                .sorted((o1, o2) -> o2.getValue().compareTo(o1.getValue()))
                .collect(Collectors.toList());

        // Pick the top 'k' interested peers based on download rates
        for (int i = 0; i < Math.min(numberOfPreferredNeighbors, interestedSortedPeers.size()); i++) {
            Socket neighbor = interestedSortedPeers.get(i).getKey();
            preferredNeighbors.add(neighbor);
            if (!unchokedNeighbors.contains(neighbor)) {
                sendUnchokeMessage(neighbor);
                unchokedNeighbors.add(neighbor);
            }
        }

        // Reset download rates for the next interval
        downloadRates.clear();
    }


    private void checkExit() {
        /* Checks if all peers have the complete file and exits if they do */

        for (PeerInfo peer : peers.values()) {
            System.out.println(peer.peerID + " " + peer.hasFile);
            if (peer.peerID == peerID) {
                continue;
            }
            if (!peer.hasFile) {
                return;
            }
        }

        if (fileContents.size() != Math.ceil((double) fileSize / pieceSize)) {
            return;
        }


        System.out.println("All peers have the complete file. Exiting...");
        System.exit(0);
    }

}