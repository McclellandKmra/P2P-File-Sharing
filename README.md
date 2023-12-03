## Group Number
25

## Team Members
Landon White 57113331
Kyle McClelland 14493484
James Archibald 36302346

## Overview
In this project we made a peer to peer file sharing protocol similar to bittorrent using java socket programming. Peers connected using TCP connections and exchanged pieces of the file while keeping track of preferred neighbors and updating based on download speed. The peers also kept track of which pieces they had and which pieces they needed to request from other peers. Once a peer had all the pieces of the file, they would save the file and exit the program.

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
* Connecting to and testing on CISE machines
* Updating README.md

###  James Archibald:
* Bitfield initialization
* Midpoint submission formatting
* Evaluating unchoking interval based on download speed
* Choking non perferred neighbors
* Updating README.md

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
DONE

## Not Achieved / TODO
DONE

## Playbook

### Instructions for local compilation:
* Extract the zip file
* Navigate to CNT4007_Project/project
* Run javac PeerProcess.java
* Run java PeerProcess 1001. Repeat for 1002 and 1003 in separate terminals.
* To add more peers, edit PeerInfo.cfg

### Instructions for compilation on CISE machines:
* Tar source files with `tar cvf tarname.tar srcfile.java`
* Connect to CISE machine (storm, rain, thunder, etc) with `ssh uf_username@cise_machine_name.cise.ufl.edu`
* Update PeerInfo.cfg with CISE machine numbers/number of peers
* Update Common.cfg with file name, file size, piece size
* Upload tar file to CISE machines via FileZilla or other software
* Upload other files (PeerInfo.cfg, Common.cfg, etc) to CISE machines via FileZilla or other software
* Extract tar file with tar -xvf tarname.tar
* Compile source files with javac PeerProcess.java
* Connect to CISE machines via SSH (lin114-00.cise.ufl.edu, lin114-01.cise.ufl.edu, lin114-02.cise.ufl.edu, etc) according to PeerInfo.cfg
* Run java PeerProcess 1001. Repeat for 1002, 1003, etc, in separate terminals/machines