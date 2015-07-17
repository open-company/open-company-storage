#!/bin/bash

# -e exit as soon as there is an error
# -v verbosely echo script contents
set -ev

# Add the RethinkDB repository and install the RethinkDB package
sudo add-apt-repository ppa:rethinkdb/ppa -y
sudo apt-get update
sudo apt-get install rethinkdb