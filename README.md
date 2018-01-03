# Network analysis for the Bolt database protocol

This tool reads raw network dumps, and converts Bolt traffic to readable logs.
You probably don't need this, as the built-in Neo4j query logging likely solves most of your woes.

This tool does what query logging does, and adds:

- Queries are logged before they execute, so queries that crash the database are seen
- Logging is on network traffic level, so bolt-level protocol issues can be debugged
- Output is JSON, so log can be machine parsed

## Install

    
    (
      git clone git@github.com:jakewins/boltalyzer.git
      cd boltalyzer && mvn clean package
      ln -s `pwd`/boltalyzer /usr/local/bin/boltalyzer
    )
    
## Use

### 1. Take a tcp dump

The network traffic needs to be unencrypted at the point you take the dump.
The recommended approach is to terminate TLS via nginx, haproxy or some such on the database machine, and then perform the tcpdump on the localhost traffic.

    nohup sudo tcpdump -i lo -w my-network-dump.pcap port 7687 &
    
If you are not recording localhost traffic, you need to pick a different `-i`. 
Use `tcpdump -D` to see available options for `-i`.

More details here: https://danielmiessler.com/study/tcpdump/
    
### 2. Give the dump to boltalyzer

    boltalyzer my-network-dump.pcap

## License

AGPL, see LICENSE 