#!/bin/bash

# -e exit as soon as there is an error
# -v verbosely echo script contents
set -ev

# Add the RethinkDB repository
source /etc/lsb-release && echo "deb http://download.rethinkdb.com/apt $DISTRIB_CODENAME main" | sudo tee /etc/apt/sources.list.d/rethinkdb.list
wget -qO- http://download.rethinkdb.com/apt/pubkey.gpg | sudo apt-key add -
sudo apt-get update
# Install the RethinkDB package
sudo apt-get install rethinkdb