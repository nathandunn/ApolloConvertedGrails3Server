#!/bin/bash
brew services stop neo4j  && rm -rf apollo_data/* &&  rm -rf /usr/local/var/neo4j/data/databases/graph.db && brew services start neo4j  && ./grailsw run-app 
