# CNT4007_Project

## Group Members:

Landon White, Kyle McClelland, James Archibald

## Instructions to Run:

Navigate into the /project directory

Compile: 
javac PeerProcess.java

Start each peer in a new terminal. It is currently configured for three peers: 
java PeerProcess 1001
java PeerProcess 1002
java PeerProcess 1003

This will start the first three peers and have them load the peer and config files. They will all connect in the specified order, handshake, exchange bitfields when necessary, and send interested/not interested messages based on the bitfield. Currerntly, preferred peers and choking is only calculated for peers with the complete file (1001). Some extra info is printed instead of logged for development purposes.