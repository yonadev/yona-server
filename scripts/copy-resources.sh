export yonatag=build-$1
export resources_source_folder=$2

[[ -d resources ]] || mkdir resources

pushd resources
echo "Copying base resources from GitHub"
wget https://raw.githubusercontent.com/yonadev/yona-server/$yonatag/resources/apple.mobileconfig.xml
wget https://raw.githubusercontent.com/yonadev/yona-server/$yonatag/resources/profile.ovpn
wget https://raw.githubusercontent.com/yonadev/yona-server/$yonatag/resources/rootcert.cer
wget https://raw.githubusercontent.com/yonadev/yona-server/$yonatag/resources/smime.crt
wget https://raw.githubusercontent.com/yonadev/yona-server/$yonatag/resources/smime.key

echo "Copying node-specific resources from $resources_source_folder"
cp -r $resources_source_folder/* .
popd
