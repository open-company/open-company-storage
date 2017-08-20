#!/bin/sh

# Clone interactions repo
 cd ./opt/
 git clone https://github.com/open-company/open-company-interaction
 # Copy migrations
 cp ./open-company-interaction/src/oc/interaction/db/migrations/* ../src/oc/storage/db/migrations/.