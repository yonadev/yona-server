#*******************************************************************************
# Copyright (c) 2016 Stichting Yona Foundation
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#*******************************************************************************
cd $1
mkdir docker-compose
cd docker-compose
mkdir elk elk/config
cd elk
wget https://raw.githubusercontent.com/yonadev/yona-server/master/docker-compose/elk/docker-compose.yml
cd config
wget https://raw.githubusercontent.com/yonadev/yona-server/master/docker-compose/elk/config/gelf.conf
cd ../..

mkdir yona yona/config
cd yona
wget https://raw.githubusercontent.com/yonadev/yona-server/master/docker-compose/yona/docker-compose.yml
cd config
echo Consider adding a production version of application.properties to `pwd`
cd ../..
