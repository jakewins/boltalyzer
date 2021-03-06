# Network analysis for the Neo4j Bolt database protocol

This tool understands Neo4j network traffic happening via the Bolt protocol, and provides useful tooling based on that.
You probably don't need this, as the built-in Neo4j query logging likely solves most of your woes.

You may want to try this tool to get:

- Ability to replay recorded load
- Machine parseable query logs
- Queries running as the database shut down or crashed
- Individual Bolt protocol messages for debugging
- Individual TCP frames, for optimizing network usage

## Install
    
    (
      git clone git@github.com:jakewins/boltalyzer.git
      cd boltalyzer && mvn clean package
      ln -s `pwd`/boltalyzer /usr/local/bin/boltalyzer
    )
    
## Use

### 1. Take a tcpdump

The network traffic *needs to be unencrypted over the interface you point tcpdump to*.
The recommended approach is to terminate TLS via nginx, haproxy or some such on the database machine, and then perform the tcpdump on the localhost traffic.

    nohup sudo tcpdump -i lo -w traffic.pcap port 7687 &
    
If you are not recording localhost traffic, you need to pick a different `-i`. 
Use `tcpdump -D` to see available options for `-i`.

More details here: https://danielmiessler.com/study/tcpdump/
    
### 2. Analyze with boltalyzer

    $ boltalyzer -h
    Usage: boltalyzer [--timemode <mode>] [--timeunit <unit>]
                      [--session <session no>] [--query <query no>]
                      [--skip <n messages>] [--exclude-empty-packets]
                      <command> <TCPDUMP_FILE>
    
    Commands:
    
      boltalyzer log <TCPDUMP_FILE> [options] [--no-results]
                                    [--no-params] [--truncate-queries <n>]
    
          Output a play-by-play of the Bolt traffic in TCPDUMP_FILE.
    
          --no-results  Don't print query results
          --no-params  Don't print parameters
          --truncate-queries <n> Truncate queries at <n> characters
    
      boltalyzer replay <TCPDUMP_FILE> [options] --target bolt://neo4j:neo4j@localhost:7687
    
          Replay the queries in TCPDUMP_FILE against the specified target.
    
      boltalyzer export <TCPDUMP_FILE> [options] [--target path/to/export/to]
    
          Write each query and its parameters to a dedicated JSON file,
          prefixed by the time it was executed
    
    Options
      --timemode [epoch | global-incremental | session-delta | iso8601]  (default: session-delta)
      --timeunit [us | ms]  (default: us)
      --session <session no>  
          Only work on this session, session no is incrementally determined in order of sessions
          appearing in the data dump
      --query <query no>        Only work on this query, query no is incremental per session. This currently only filters
          the actual RUN statement, not related messages.
      --skip <n>  Skip n packets before starting output    (default: 0)
      -h  Print this message


## License

AGPL, see LICENSE 
