import java.nio.channels.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.net.*;
import java.io.*;
import java.nio.*;

public class MessageLogger {
    
    public void TCPLogMessage(int peerID1, int peerID2) {
        String filepath = "log_peer_" + peerID2 + ".log";
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = formatter.format(date);
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(filepath, true))) {
            writer.write(dateString + ": Peer " + peerID1 + " makes a connection to Peer " + peerID2 + ".");
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Whenever a peer changes its preferred neighbors, it generates the following log message, taking in the peer and its preferred neighbors
    public void changeOfNeighborsLogMessage(int peerID, List<Integer> preferredNeighbors) {
        String filepath = "log_peer_" + peerID + ".log";
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = formatter.format(date);
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(filepath, true))) {
            if (preferredNeighbors.size() > 0) {
                writer.write(dateString + ": Peer " + peerID + " has the preferred neighbors ");
            for (int i = 0; i < preferredNeighbors.size(); i++) {
                if (i == preferredNeighbors.size() - 1) {
                    writer.write(preferredNeighbors.get(i) + ".");
                    break;
                }
                writer.write(preferredNeighbors.get(i) + ", ");
            }
            writer.newLine();
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void changeOfOptNeighborsLogMessage(int peerID, int optNeighbor) {
        String filepath = "log_peer_" + peerID + ".log";
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = formatter.format(date);
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(filepath, true))) {
            writer.write(dateString + ": Peer " + peerID + " has the optimistically unchoked neighbor " + optNeighbor + ".");
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void unchokeLogMessage(int peerID1, int peerID2) {
        String filepath = "log_peer_" + peerID1 + ".log";
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = formatter.format(date);
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(filepath, true))) {
            writer.write(dateString + ": Peer " + peerID1 + " is unchoked by " + peerID2 + ".");
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void chokeLogMessage(int peerID1, int peerID2) {
        String filepath = "log_peer_" + peerID1 + ".log";
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = formatter.format(date);
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(filepath, true))) {
            writer.write(dateString + ": Peer " + peerID1 + " is choked by " + peerID2 + ".");
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void haveLogMessage(int peerID1, int peerID2, int pieceIndex) {
        String filepath = "log_peer_" + peerID1 + ".log";
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = formatter.format(date);
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(filepath, true))) {
            writer.write(dateString + ": Peer " + peerID1 + " received the 'have' message from " + peerID2 + " for the piece " + pieceIndex + ".");
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void interestedLogMessage(int peerID1, int peerID2) {
        String filepath = "log_peer_" + peerID1 + ".log";
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = formatter.format(date);
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(filepath, true))) {
            writer.write(dateString + ": Peer " + peerID1 + " received the 'interested' message from " + peerID2 + ".");
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }  

    public void notInterestedLogMessage(int peerID1, int peerID2) {
        String filepath = "log_peer_" + peerID1 + ".log";
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = formatter.format(date);
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(filepath, true))) {
            writer.write(dateString + ": Peer " + peerID1 + " received the 'not interested' message from " + peerID2 + ".");
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void pieceDownloadedLogMessage(int peerID1, int peerID2, int pieceIndex, int numPieces) {
        String filepath = "log_peer_" + peerID1 + ".log";
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = formatter.format(date);
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(filepath, true))) {
            writer.write(dateString + ": Peer " + peerID1 + " has downloaded the piece " + pieceIndex + " from " + peerID2 + ". Now the number of pieces it has is " + numPieces + ".");
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void fileDownloadedLogMessage(int peerID) {
        String filepath = "log_peer_" + peerID + ".log";
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = formatter.format(date);
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(filepath, true))) {
            writer.write(dateString + ": Peer " + peerID + " has downloaded the complete file.");
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
