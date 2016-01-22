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
