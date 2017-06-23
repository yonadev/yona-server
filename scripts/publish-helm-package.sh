#!/bin/bash

set -ex

BUILD_NUMBER=$1
VERSION=$2
CHART=$3
GIT_USR=$4
GIT_PSW=$5
HELM_DIR=$6
HELM_REPO=$7

HELM_REPO_DIR=$HELM_DIR/$HELM_REPO
PACKAGE_DIR=$HELM_REPO_DIR/docs
PACKAGE_PATH=$PACKAGE_DIR/$CHART-$VERSION.tgz

rm -rf build
mkdir build
cp -r $CHART build
pushd build
sed --in-place s/_ReplaceWithBuildNumber_/$BUILD_NUMBER/ $CHART/Chart.yaml
helm dependency update --home $HELM_DIR/.helm --verify $CHART
helm package --home $HELM_DIR/.helm -d $PACKAGE_DIR --version $VERSION $CHART
popd

pushd $HELM_REPO_DIR
git pull
git add $PACKAGE_PATH
helm repo index docs --home $HELM_DIR/.helm --url https://yonadev.github.io/$HELM_REPO
export GIT_AUTHOR_NAME="Yona build server"
export GIT_AUTHOR_EMAIL=dev@yona.nu
export GIT_COMMITTER_NAME=$GIT_AUTHOR_NAME
export GIT_COMMITTER_EMAIL=$GIT_AUTHOR_EMAIL
git commit -am "Package for build $BUILD_NUMBER"
git push https://${GIT_USR}:${GIT_PSW}@github.com/yonadev/$HELM_REPO.git master
popd
