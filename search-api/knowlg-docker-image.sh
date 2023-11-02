#!/bin/bash
TAG=$1

docker rmi $(docker images -a | grep knowlg-search | awk '{print $1":"$2}')

cd knowlg-search
sbt dist
cd ../
docker build -f build/knowlg-search/Dockerfile -t knowlg-search:${TAG} .
