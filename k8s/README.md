# Yona K8S Deploy
This area has setup for a Kubernetes (K8S) deployment of the Youa server stack and supporting infrastructure.

The deployment uses [Helm](https://github.com/kubernetes/helm) to template the release.  In the stock configuration here, it will create a full stack to be used on a local workstation for development / testing needs.

Locally we recommend the use of [MiniKube](https://kubernetes.io/docs/getting-started-guides/minikube/) for a local workstation setup of a single node Kubernetes environment.   Minikube will create a node using various virtualization technologies.  

----------
## Windows Setup

See http://wiki.yona.nu/display/DEV/Running+Yona+with+Kubernetes

If you don't have Windows Pro edition or you would like to continue to be able to use VirtualBox
on your workstation for other reasons, consider using
[Docker Toolbox](https://www.docker.com/products/docker-toolbox) as one stop program to set a
bunch of components up - Virtualbox, Git for Windows, iso for the dockerboot container, the
docker command, etc.. This system uses Virtualbox as the hypervisor, and has resulted in a
working Minikube and Helm deploy.

Note - The Docker Toolbox has been depricated in favor of Docker for Windows that uses HyperV instead of Virtualbox.  It is possible that this setup (Docker for Windows) has issues - it has not been validated, and so far looks like it is not a working setup.

## Linux Setup
Requirements:

 - ~10GB free space (more would be better)
 - 8GB Free Memory (after all normal desktop usage, etc.)
 - Virtualization System.   This document will use KVM, but others are available.  Virtualbox and VMWare fusion are built into Minikube.
 - CPU that supports virtualization
 - We will be using Ubuntu 16.04 for the examples
 
### Docker
Docker is not required but it helps to have the client installed on the local host for debugging and building images.

### KVM
Install Packages
`sudo apt-get install qemu-kvm libvirt-bin ubuntu-vm-builder bridge-utils`

Add yourself to the group able to manipulate virtual containers
``sudo adduser `id -un` libvirtd``

For good measure, reboot, and check that it works
```
$ virsh list --all
 Id Name                 State
----------------------------------
```

### kubectl
kubectl is the main management program to interact with the Kubernetes API.   

Install from instructions [here](https://kubernetes.io/docs/tasks/tools/install-kubectl/)

### Minikube
The following will download the Minikube binary into /usr/local/bin.   Version 0.19.0 is listed below, but check if a new version is working (0.19.0 current as of May 21, 2017)
``` 
curl -Lo minikube https://storage.googleapis.com/minikube/releases/v0.19.0/minikube-linux-amd64 && chmod +x minikube && sudo mv minikube /usr/local/bin/
```
#### KVM Driver for Minikube
```
wget -O docker-machine-driver-kvm https://github.com/dhiltgen/docker-machine-kvm/releases/download/v0.10.0/docker-machine-driver-kvm-ubuntu16.04 && \
chmod +x docker-machine-driver-kvm && \
sudo mv docker-machine-driver-kvm /usr/local/bin
```
#### Setup Minikube
```
minikube config set vm-driver kvm
minikube config set memory 7192
minikube config set cpus 4
minikube config set disk-size 10G
```
Adjust these values as needed.   The stack was tested with these valuesYou can adjust the disk-size larger (VM images, etc. will be installed under ~/.minikube)

Create/Start the minikube cluster
```
minikube start
```

Minikube will create a KVM instance with the above configuration, and deploy Kubernetes master node to it.
It will setup your kubectl config (~/.kube/config) to point to the K8S api in the virtual environment.

Once setup, use kubectl to confirm you have a node available, and kubectl can connect to it

```
$ kubectl get nodes 
NAME       STATUS    AGE       VERSION
minikube   Ready     1m        v1.6.0
```

You can see a quick list of all the running components on a base K8S install :
 
```
$ kubectl get all --all-namespaces  -a
NAMESPACE     NAME                                READY     STATUS    RESTARTS   AGE
kube-system   po/kube-addon-manager-minikube      1/1       Running   1          20h
kube-system   po/kube-dns-268032401-g0kdt         3/3       Running   96         18h
kube-system   po/kubernetes-dashboard-q4m57       1/1       Running   1          20h

NAMESPACE     NAME                      DESIRED   CURRENT   READY     AGE
kube-system   rc/kubernetes-dashboard   1         1         1         20h

NAMESPACE     NAME                       CLUSTER-IP   EXTERNAL-IP   PORT(S)         AGE
default       svc/kubernetes             10.0.0.1     <none>        443/TCP         20h
kube-system   svc/kube-dns               10.0.0.10    <none>        53/UDP,53/TCP   20h
kube-system   svc/kubernetes-dashboard   10.0.0.164   <nodes>       80:30000/TCP    20h

NAMESPACE     NAME                   DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
kube-system   deploy/kube-dns        1         1         1            1           20h

NAMESPACE     NAME                          DESIRED   CURRENT   READY     AGE
kube-system   rs/kube-dns-268032401         1         1         1         20h
kube-system   rs/tiller-deploy-1491950541   1         1         1         20h
```
#### Persistent Storage
Create a persistent storage volume

 - Change to the yona-server/k8s in the Yona Server repository
 - Run the following command to create the volume.   It will create a 3GB space in the virtual machine to be used by persistent volume claims for the MariaDB and LDAP data.
 
```
kubectl create -f 02_storage.yaml
```

### Setup Helm
Now that we have a Kubernetes cluster up, lets move to helm which will allow us to deploy a templated collection of K8S objects into the cluster.

#### Install Helm
```
curl https://raw.githubusercontent.com/kubernetes/helm/master/scripts/get > get_helm.sh && chmod 700 get_helm.sh && ./get_helm.sh
```

#### Install the 'Tiller' component into the cluster

```
helm init
```

### Deploy Yona Server 

Change to the yona-server/k8s/helm in the Yona Server repository

#### Dependencies 
A custom helm 'chart' called `yona-sever` is under this directory along with another custom chart for ldap that is a dependancy on the `yona-server` chart.

The `yona-server` chart also has a dependancy on the upstream mariadb helm chart.  

List the dependencies with the following:
To get the dependencies pulled down, run the following :
```
helm dependency list yona-server
```

To update and download the dependancies :
```
helm dependency update yona-server
```

#### Update default values
You can customize values for the `chart` by editing yona-server/k8s/helm/values.yaml

The build version is setup in the values file :

```
global:
  release: 527
```

#### Custom Liquibase (until production jenkins is using updated one)
The liquibase update docker image has a pending pull request.  Until this is in the tree used by Jenkins to produce upstream docker images, the LDAP, Quartz, and Categories will not be properly seeded on start up.  
This section is to be removed from the README once all images going forward will have the new setup.

#### Deploy / Upgrade the chart
Deploy the `yona-server` chart with the following :
```
helm upgrade --install --namespace yona --debug yona ./yona-server
```

The upgrade command with the --install option will perform an install if the Helm chart is not already deployed.  
The --namespace will deploy all objects into a `yona` namespace that makes clean up easier.

#### Monitor the standup
You can see the pods being deployed with the following command :
```
kubectl get pods -n yona -a
```

You will see various liquibase containers failing out due to some components not being ready right away - MariaDB, LDAP, etc.

Eventually you will see a completed liquibase, and eventually the various components will be up.

You can also use the -w command to watch the changes to pod status
```
kubectl get pods -n yona -w
```
With the above, once you see that liquibase-update has 'Completed' it is good to go.
 
To view the Logs of any container, use the following command with the full pod name from the `get pods` command above

```
kubectl logs -n yona 527-develop-liquibase-update-5p637
```

#### Kube DNS not working
On initial deploy, I have had some issues with the internal KubeDNS component.  As a result, the hostname for the various services (mariadb, ldap) are not being resolved and the liquibase container will never be able to connect.  If this happens, do the following:

- Get the pod name for the kube-dns component
```
kubectl get pod --all-namespaces -a | grep -i dns
```
- Delete that pod.  The K8S system will deploy a new one
```
kubectl -n kube-system delete pod kube-dns-268032401-g0kdt
```

#### Memory Requirements
I attempted deployment on a 12GB 3 year old laptop.  It allowed for the bare minimum but if I had too much running outside of the kube environment it became starved and usually the first to fail with the internal routing/proxy/dns of K8S

#### Shutdown for redeploy / Things not starting up

The following will stop the chart, and clean up various resources that are not automatically cleaned up when a chart is stopped.
```
helm delete yona; kubectl delete -n yona configmaps --all ; kubectl delete -n yona job --all ; kubectl delete -n yona secrets --all ; kubectl delete pvc -n yona --all
```

This will not remove the persistent volumes (mariadb and ldap), so those will be quicker to start up next time, and will already be seeded to the latest liquibase/batch/qwartz jobs

### Accessing the cluster services

Once everything is up, you probably want to test it.

Get a list of the exposed services with the following :
```
$ minikube service list
|-------------|----------------------|-----------------------------|
|  NAMESPACE  |         NAME         |             URL             |
|-------------|----------------------|-----------------------------|
| default     | kubernetes           | No node port                |
| kube-system | kube-dns             | No node port                |
| kube-system | kubernetes-dashboard | http://192.168.42.170:30000 |
| kube-system | tiller-deploy        | No node port                |
| yona        | admin                | http://192.168.42.170:31000 |
| yona        | admin-actuator       | http://192.168.42.170:31010 |
| yona        | analysis             | http://192.168.42.170:31001 |
| yona        | analysis-actuator    | http://192.168.42.170:31011 |
| yona        | app                  | http://192.168.42.170:31002 |
| yona        | app-actuator         | http://192.168.42.170:31012 |
| yona        | batch                | http://192.168.42.170:31003 |
| yona        | batch-actuator       | http://192.168.42.170:31013 |
| yona        | ldap                 | http://192.168.42.170:31389 |
| yona        | yona-mariadb         | No node port                |
|-------------|----------------------|-----------------------------|

```

Test an API user create :
```
curl -v -H "Content-Type: application/json" -H "Accept-Language: en-US" -H "Yona-Password: spam1234" -d '{"firstName": "Teston", "lastName": "Blurbalback", "mobileNumber": "+15555551212", "nickname": "testonb"}' http://192.168.42.170:31003/users/ 
```

If you get a `Root exception is java.net.UnknownHostException: ldap.yona.svc.cluster.local` then chances are the internal DNS server might have an issue - See the 'Kube DNS not working section above'

