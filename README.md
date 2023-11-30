## Group Number

## Team Members

## Contributions

###  Landon White:
* Peer initialization and connection
* Threading for server, client, and messages
* Sending and handling handshake messages
* Saving peer bitfields and formatting bitfield messages
* Sending and handling request messages
* Unchoking and preferred neighbors for peers with the complete file
* Sending and handling request messages
* Sending and handling piece messages
* Keeping track of and saving file contents
* Sending and recieving have messages

###  Kyle McClelland:
* Git repo setup
* Logging system
* Pair programming for:

###  James Archibald:
* Bitfield initialization
* Midpoint submission formatting
* Pair programming for:

## Achieved
* Reading data from files
* Listening for connections
* Connecting to previous peers
* Handshake messages
* Bitfield messages after handshake 
* Request messages
* Piece messages
* Saving to the file once a peer has all the contents
* Have messages

## In Progress
* Interested/not interested messages
* Choking and unchoking 
* Change of preferred neighbors
* Logging

## Not Achieved / TODO
* Testing on CISE machines
* Fix gitignore file

## Playbook
### Instructions for local compilation:
* Extract the zip file
* Navigate to CNT4007_Project/project
* Run javac PeerProcess.java
* Run java PeerProcess 1001. Repeat for 1002 and 1003 in separate terminals.
* To add more peers, edit PeerInfo.cfg
