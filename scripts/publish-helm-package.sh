#!/bin/bash

set -ex

BUILD_ID=$1
VERSION=$2
CHART=$3
GITHUB_APP_JWT=$4
HELM_DIR=$5
HELM_REPO=$6

HELM_REPO_DIR=$HELM_DIR/$HELM_REPO
PACKAGE_DIR=$HELM_REPO_DIR/docs
PACKAGE_PATH=$PACKAGE_DIR/$CHART-$VERSION.tgz

rm -rf build
mkdir build
cp -r $CHART build
pushd build
sed --in-place s/_ReplaceWithBuildId_/$BUILD_ID/ $CHART/Chart.yaml
helm dependency update $CHART
helm package -d $PACKAGE_DIR --version $VERSION $CHART
popd

pushd $HELM_REPO_DIR
git pull
git add $PACKAGE_PATH
helm repo index docs --url https://jump.ops.yona.nu/$HELM_REPO
export GIT_AUTHOR_NAME="Yona build server"
export GIT_AUTHOR_EMAIL=dev@yona.nu
export GIT_COMMITTER_NAME=$GIT_AUTHOR_NAME
export GIT_COMMITTER_EMAIL=$GIT_AUTHOR_EMAIL
git commit -am "Package for build $BUILD_ID"
git push https://$x-access-token:${GITHUB_APP_PSW}@github.com/yonadev/$HELM_REPO.git master
popd
