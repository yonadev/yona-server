# Hostpath Provisioner for Kubeadm based test clusters

The helm yona setup should work out of the box for Minikube
but if being run against a system without any storage class/provisioner
setup the persistent volume claims will need to be pre-setup or
the tooling here will need to be setup

- Create a /tmp/hostpath-provisioner directory on the host or edit provisioner-deployment.yaml with alternative
- kubectl create in order the entries in this directory

