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
* Choking and unchoking logic
* Sending and handling nterested/not interested messages
* Concurrency and thread safety

###  Kyle McClelland:
* Git repo setup
* Logging class/calling logging methods
* Pair programming for: Choking and unchoking logic, preferred peer evalutaion, peer initilizatoin and connection, and messages

###  James Archibald:
* Bitfield initialization
* Midpoint submission formatting
* Evaluating unchoking interval based on download speed
* Choking non perferred neighbors

## Achieved
* Reading data from files
* Listening for connections
* Connecting to previous peers
* Handshake messages
* Bitfield messages after handshake 
* Calculating preferred neighbors through regular and optimistic unchoking
* Choke & unchoke messages
* Interested & not interested messages
* Request messages
* Piece messages
* Have messages
* Peers constantly requesting files from neighbors that have unchoked them
* Saving to the file once a peer has all the contents
* Github requirements such as gitignore file
* Accounted for multiple threads trying to access shared resources

## In Progress


## Not Achieved / TODO
* Testing on CISE machines
* Remove intentional delay & print debugging for final submission
* Update playbook for remote instructions

## Playbook
### Instructions for local compilation:
* Extract the zip file
* Navigate to CNT4007_Project/project
* Run javac PeerProcess.java
* Run java PeerProcess 1001. Repeat for 1002 and 1003 in separate terminals.
* To add more peers, edit PeerInfo.cfg
