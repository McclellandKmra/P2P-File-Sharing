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
    private int PeerID;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java PeerProcess <peerID>");
            return;
        }
        
        int PeerID = Integer.parseInt(args[0]);
        PeerProcess peerProcess = new PeerProcess(PeerID);
    }

    public PeerProcess(int PeerID) {
        this.PeerID = PeerID;
        try {
            readCommon();
            //TODO: readPeerInfo();
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
}

