#!/bin/sh

# Clone interactions repo
 cd ./opt/
 git clone https://github.com/open-company/open-company-interaction
 # Run the migrations
 cd open-company-interaction
lein with-profile qa migrate-db