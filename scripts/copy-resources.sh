export github_version=$1
export resources_source_folder=$2

[[ -d resources ]] || mkdir resources

pushd resources
echo "Copying base resources from GitHub"
wget -O apple.mobileconfig.xml https://raw.githubusercontent.com/yonadev/yona-server/$github_version/resources/apple.mobileconfig.xml
wget -O dummy.p12 https://raw.githubusercontent.com/yonadev/yona-server/$github_version/resources/dummy.p12
wget -O profile.ovpn https://raw.githubusercontent.com/yonadev/yona-server/$github_version/resources/profile.ovpn
wget -O rootcert.cer https://raw.githubusercontent.com/yonadev/yona-server/$github_version/resources/rootcert.cer
wget -O AppleWWDRCA.cer https://raw.githubusercontent.com/yonadev/yona-server/$github_version/resources/AppleWWDRCA.cer

echo "Copying node-specific resources from $resources_source_folder"
cp -r $resources_source_folder/* .
popd
