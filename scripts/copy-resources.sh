export github_version=$1
export resources_source_folder=$2

[[ -d resources ]] || mkdir resources

pushd resources
echo "Copying base resources from GitHub"
wget https://raw.githubusercontent.com/yonadev/yona-server/$github_version/resources/apple.mobileconfig.xml
wget https://raw.githubusercontent.com/yonadev/yona-server/$github_version/resources/dummy.p12
wget https://raw.githubusercontent.com/yonadev/yona-server/$github_version/resources/profile.ovpn
wget https://raw.githubusercontent.com/yonadev/yona-server/$github_version/resources/rootcert.cer

echo "Copying node-specific resources from $resources_source_folder"
cp -r $resources_source_folder/* .
popd
