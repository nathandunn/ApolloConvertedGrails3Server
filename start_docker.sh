#!/bin/bash

RUNNING=`docker container ls  --filter name=neo4j | wc -l`
echo $RUNNING

SLEEPING=`docker container ls  --filter name=neo4j --filter status=exited --all  | wc -l `
echo $SLEEPING


if [ "$RUNNING" -eq "2" ];then
  echo "Already running"
elif [ "$SLEEPING" -eq "2" ];then
  echo "Starting sleeping process"
  docker start neo4j
else
  echo "Starting new process"
docker run -it \
       --publish=7474:7474 \
       --publish=7687:7687 \
	   --name=neo4j \
       --volume=$HOME/neo4j/data:/data \
       neo4j:3.3.9
fi
